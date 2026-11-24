package ai.opencode.assist

import ai.opencode.core.sharing.SharedKeys
import ai.opencode.core.sharing.shared
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Escucha una palabra de activacion (por defecto "destino" y "alia") con Vosk, sin internet.
 * El modelo en espanol (~40 MB) se descarga una vez. Al oir la palabra abre el asistente.
 */
object WakeWord {
    private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip"
    private const val RATE = 16000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var model: Model? = null

    /** Mientras el asistente esta abierto no se escucha la palabra (el microfono es suyo). */
    @Volatile var paused = false

    private fun status(text: String) {
        runCatching { shared(SharedKeys.assistWakeStatus).set(text) }
    }

    private fun readWords(): List<String> =
        shared(SharedKeys.assistWakeWords).value
            .split(Regex("[,;]+"))
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

    @Synchronized
    fun start(context: Context) {
        job?.cancel()
        val app = context.applicationContext
        job = scope.launch {
            try {
                loop(app)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                status("Error: " + (e.message ?: e.javaClass.simpleName))
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        status("Apagado")
    }

    private fun heard(text: String, words: List<String>): Boolean {
        val padded = " $text "
        return words.any { padded.contains(" $it ") }
    }

    private fun ensureModel(ctx: Context): File {
        val dir = File(ctx.filesDir, "vosk-es")
        if (File(dir, "am").isDirectory) return dir
        status("Descargando modelo (40 MB)…")
        val zip = File(ctx.cacheDir, "vosk-es.zip")
        val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            zip.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var last = 0
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) {
                        val pct = (done * 100 / total).toInt()
                        if (pct >= last + 5) {
                            last = pct
                            status("Descargando modelo… $pct%")
                        }
                    }
                }
            }
        }
        status("Preparando modelo…")
        val tmp = File(ctx.filesDir, "vosk-es.tmp")
        tmp.deleteRecursively()
        tmp.mkdirs()
        val root = tmp.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                val rel = entry.name.substringAfter('/', "")
                if (rel.isNotEmpty()) {
                    val f = File(tmp, rel)
                    if (!f.canonicalPath.startsWith(root)) throw IllegalStateException("zip inválido")
                    if (entry.isDirectory) {
                        f.mkdirs()
                    } else {
                        f.parentFile?.mkdirs()
                        f.outputStream().use { zin.copyTo(it) }
                    }
                }
                entry = zin.nextEntry
            }
        }
        zip.delete()
        dir.deleteRecursively()
        if (!tmp.renameTo(dir)) throw IllegalStateException("no pude guardar el modelo")
        return dir
    }

    @SuppressLint("MissingPermission")
    private suspend fun loop(ctx: Context) {
        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status("Falta el permiso del micrófono")
            return
        }
        val m = model ?: run {
            val dir = ensureModel(ctx)
            status("Cargando modelo…")
            Model(dir.absolutePath).also { model = it }
        }
        while (currentCoroutineContext().isActive) {
            if (paused) {
                delay(300)
                continue
            }
            val words = readWords()
            if (words.isEmpty()) {
                status("Escribe al menos una palabra")
                delay(1500)
                continue
            }
            val grammar = JSONArray(words + "[unk]").toString()
            val rec = Recognizer(m, RATE.toFloat(), grammar)
            val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val ar = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, 16000),
            )
            if (ar.state != AudioRecord.STATE_INITIALIZED) {
                ar.release()
                rec.close()
                status("No pude abrir el micrófono")
                delay(3000)
                continue
            }
            var hit = false
            try {
                ar.startRecording()
                status("Escuchando: " + words.joinToString(", "))
                val buf = ShortArray(3200)
                var reads = 0
                while (currentCoroutineContext().isActive && !paused && !hit) {
                    val n = ar.read(buf, 0, buf.size)
                    if (n < 0) break
                    if (n == 0) {
                        delay(30)
                        continue
                    }
                    val ready = rec.acceptWaveForm(buf, n)
                    val json = JSONObject(if (ready) rec.result else rec.partialResult)
                    val text = json.optString(if (ready) "text" else "partial")
                    if (heard(text, words)) hit = true
                    reads++
                    // Si cambiaste las palabras en Ajustes, vuelve a empezar con las nuevas.
                    if (reads % 10 == 0 && readWords() != words) break
                }
            } finally {
                runCatching { ar.stop() }
                ar.release()
                rec.close()
            }
            if (hit) {
                status("Te oí, abriendo el asistente…")
                if (!AssistInteractionService.show()) {
                    status("No pude abrir el asistente (¿Destiny es tu asistente digital?)")
                }
                delay(2500)
            }
        }
    }
}
