package ai.opencode.feature.voice

import ai.opencode.core.log.AppLog
import ai.opencode.core.sharing.SharedKeys
import ai.opencode.core.sharing.shared
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Escucha el micrófono mientras Fish habla y avisa cuando tú empiezas a hablar encima.
 * Usa el cancelador de eco del celular; con audífonos va mejor que con altavoz.
 */
object VoiceBargeIn {
    private const val RATE = 16000
    private const val FRAME = 320 // 20 ms
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var job: Job? = null

    /** Para el diagnóstico: el nivel de tu voz y el umbral que debe superar. */
    @Volatile var level = 0
    @Volatile var threshold = 0

    // Después de detectar tu voz, sigue grabando hasta que arranca la escucha (así no hay hueco).
    @Volatile private var tailing = false
    @Volatile private var tailStop = false
    @Volatile private var tailAt = 0L
    private val tail = java.io.ByteArrayOutputStream()
    private val tailLock = Any()

    /** Para de escuchar; si ya oyó tu voz, la deja terminar sola para no perder lo que dices. */
    fun stop() {
        if (tailing) return
        cancelNow()
    }

    private fun cancelNow() {
        job?.cancel()
        job = null
    }

    /** Lo que se oyó entre detectar tu voz y que arrancara la escucha. Espera a que el micrófono se suelte. */
    suspend fun takeTail(): ByteArray? {
        val j = job
        if (tailing && j != null) {
            tailStop = true
            kotlinx.coroutines.withTimeoutOrNull(1500) { j.join() }
        }
        val fresh = System.currentTimeMillis() - tailAt < 4000
        val bytes = synchronized(tailLock) {
            val b = tail.toByteArray()
            tail.reset()
            b
        }
        return if (fresh && bytes.isNotEmpty()) bytes else null
    }

    /** [onSpeech] corre en un hilo de fondo, una sola vez. Se detiene solo cuando Fish termina. */
    @SuppressLint("MissingPermission")
    fun start(onSpeech: () -> Unit) {
        cancelNow()
        tailing = false
        tailAt = 0L
        synchronized(tailLock) { tail.reset() }
        VoiceListen.preroll = null
        if (!shared(SharedKeys.assistBargeIn).value) return
        job = scope.launch {
            var recorder: AudioRecord? = null
            var echo: AcousticEchoCanceler? = null
            var noise: NoiseSuppressor? = null
            try {
                val minBuffer = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                if (minBuffer <= 0) return@launch
                val r = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    max(minBuffer, RATE),
                )
                recorder = r
                if (r.state != AudioRecord.STATE_INITIALIZED) return@launch
                runCatching {
                    if (AcousticEchoCanceler.isAvailable()) {
                        echo = AcousticEchoCanceler.create(r.audioSessionId)
                        echo?.setEnabled(true)
                    }
                }
                runCatching {
                    if (NoiseSuppressor.isAvailable()) {
                        noise = NoiseSuppressor.create(r.audioSessionId)
                        noise?.setEnabled(true)
                    }
                }
                r.startRecording()
                if (r.recordingState != AudioRecord.RECORDSTATE_RECORDING) return@launch
                val frame = ShortArray(FRAME)
                var avg = 0.0
                var frames = 0
                var loud = 0
                val ring = ByteArray(RATE * 3)
                var pos = 0
                var filled = 0
                while (true) {
                    coroutineContext.ensureActive()
                    if (!FishSpeech.active) return@launch
                    val n = r.read(frame, 0, FRAME)
                    if (n <= 0) return@launch
                    var sum = 0.0
                    for (i in 0 until n) {
                        val s = frame[i].toInt()
                        sum += s.toDouble() * s
                        ring[pos] = (s and 0xFF).toByte()
                        ring[pos + 1] = (s shr 8).toByte()
                        pos = (pos + 2) % ring.size
                    }
                    filled = min(ring.size, filled + n * 2)
                    val rms = sqrt(sum / n)
                    frames++
                    if (frames == 1) avg = rms
                    if (frames <= 25) {
                        avg = avg * 0.9 + rms * 0.1
                        continue
                    }
                    val thr = max(avg * 2.0, 700.0)
                    level = rms.toInt()
                    threshold = thr.toInt()
                    if (rms > thr) {
                        loud += 2
                        if (loud >= 20) {
                            val take = min(filled, RATE * 3)
                            val snap = ByteArray(take)
                            val start = (pos - take + ring.size) % ring.size
                            for (k in 0 until take) snap[k] = ring[(start + k) % ring.size]
                            VoiceListen.preroll = snap
                            tailAt = System.currentTimeMillis()
                            tailStop = false
                            tailing = true
                            onSpeech()
                            val deadline = System.currentTimeMillis() + 2500
                            while (!tailStop && System.currentTimeMillis() < deadline) {
                                coroutineContext.ensureActive()
                                val m = r.read(frame, 0, FRAME)
                                if (m <= 0) break
                                val chunk = ByteArray(m * 2)
                                for (i in 0 until m) {
                                    val s = frame[i].toInt()
                                    chunk[2 * i] = (s and 0xFF).toByte()
                                    chunk[2 * i + 1] = (s shr 8).toByte()
                                }
                                synchronized(tailLock) { tail.write(chunk, 0, chunk.size) }
                            }
                            return@launch
                        }
                    } else {
                        if (loud > 0) loud--
                        avg = avg * 0.98 + rms * 0.02
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.voice.notice("Barge-in stopped: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                tailing = false
                runCatching { recorder?.stop() }
                recorder?.release()
                runCatching { echo?.release() }
                runCatching { noise?.release() }
            }
        }
    }
}
