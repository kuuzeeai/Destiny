package ai.opencode.feature.voice

import ai.opencode.core.log.AppLog
import ai.opencode.core.sharing.SharedKeys
import ai.opencode.core.sharing.shared
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Listens with the phone's own microphone and understands the speech with the person's Groq key
 * (Whisper, free tier), so it does not depend on Google's recognizer. One utterance at a time:
 * it records until a pause, sends a WAV, and returns the text. Fish Audio only speaks.
 */
object VoiceListen {
    enum class Failure { Silence, Mic, Network }

    private class MicException : Exception("mic")

    private const val RATE = 16000
    private const val FRAME = 320 // 20 ms
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http: OkHttpClient by lazy { OkHttpClient().withVoiceTimeout(30) }
    private var job: Job? = null
    @Volatile private var groqIndex = 0

    // Vigilante: en qué fase va la escucha y cuándo fue la última señal de vida.
    @Volatile private var phase = "preparando"
    @Volatile private var phaseAt = 0L
    @Volatile private var tick = 0L
    @Volatile private var rec: AudioRecord? = null

    /** Lo que ya dijiste al interrumpir (lo deja VoiceBargeIn); la próxima escucha lo incluye. */
    @Volatile var preroll: ByteArray? = null

    private fun mark(p: String) {
        phase = p
        phaseAt = System.currentTimeMillis()
        tick = phaseAt
    }

    /** A Groq key is saved. */
    val available: Boolean
        get() = !shared(SharedKeys.groqAPIKey).value.isNullOrBlank()

    fun cancel() {
        job?.cancel()
        job = null
    }

    /** Callbacks run on a background thread. */
    fun listen(
        onTranscribing: () -> Unit,
        onText: (String) -> Unit,
        onFail: (Failure, String?) -> Unit,
        onReady: () -> Unit = {},
    ) {
        cancel()
        val key = shared(SharedKeys.groqAPIKey).value?.trim().orEmpty()
        mark("preparando")
        val mine = scope.launch {
            try {
                val pcm = record(onReady)
                if (pcm == null) {
                    onFail(Failure.Silence, null)
                    return@launch
                }
                mark("transcribiendo")
                onTranscribing()
                val text = transcribe(key, wav(pcm))
                if (text.isEmpty()) onFail(Failure.Silence, null) else onText(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: MicException) {
                if (isActive) onFail(Failure.Mic, null)
            } catch (e: SecurityException) {
                onFail(Failure.Mic, null)
            } catch (e: Exception) {
                AppLog.voice.notice("Groq couldn’t understand the audio: ${e.message ?: e.javaClass.simpleName}")
                onFail(Failure.Network, e.message)
            }
        }
        job = mine
        scope.launch {
            while (mine.isActive) {
                delay(500)
                val now = System.currentTimeMillis()
                val why: String? = when (phase) {
                    "preparando" -> if (now - phaseAt > 4000) "el micrófono no arrancó" else null
                    "grabando" -> when {
                        now - tick > 3000 -> "el micrófono no da audio"
                        now - phaseAt > 35000 -> "la grabación no terminó"
                        else -> null
                    }
                    "transcribiendo" -> if (now - phaseAt > 40000) "Groq no respondió" else null
                    else -> null
                }
                if (why != null) {
                    AppLog.voice.notice("Listen stuck: $why")
                    mine.cancel()
                    runCatching { rec?.stop() }
                    runCatching { rec?.release() }
                    onFail(Failure.Mic, why)
                    return@launch
                }
            }
        }
    }

    /** Records until about a second of silence after speech; null if nobody spoke for 7 seconds. */
    @SuppressLint("MissingPermission")
    private suspend fun record(onReady: () -> Unit): ByteArray? {
        val minBuffer = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) throw MicException()
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            max(minBuffer, RATE),
        )
        rec = recorder
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw MicException()
        }
        val head = preroll
        preroll = null
        // Lo que se oyó mientras arrancaba esta escucha (sin huecos).
        val tail = try {
            VoiceBargeIn.takeTail()
        } catch (e: CancellationException) {
            recorder.release()
            rec = null
            throw e
        }
        val pre = if (head != null && tail != null) head + tail else head
        val out = ByteArrayOutputStream()
        if (pre != null) out.write(pre)
        val frame = ShortArray(FRAME)
        val bytes = ByteArray(FRAME * 2)
        try {
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) throw MicException()
            var frames = 0
            var noise = Double.MAX_VALUE
            var threshold = 450.0
            var loud = 0
            var loudFrames = 0
            var speaking = false
            var firstLoud = 0
            var speechLevel = 0.0
            var lastLoud = 0
            var waitStart = 0
            if (pre != null) {
                speaking = true
                loudFrames = 6
            }
            while (true) {
                coroutineContext.ensureActive()
                val n = recorder.read(frame, 0, FRAME)
                if (n <= 0) throw MicException()
                tick = System.currentTimeMillis()
                var sum = 0.0
                for (i in 0 until n) {
                    val s = frame[i].toInt()
                    bytes[2 * i] = (s and 0xFF).toByte()
                    bytes[2 * i + 1] = (s shr 8).toByte()
                    sum += s.toDouble() * s
                }
                out.write(bytes, 0, n * 2)
                val rms = sqrt(sum / n)
                frames++
                if (frames == 1) {
                    mark("grabando")
                    onReady()
                }
                if (frames <= 8) {
                    noise = min(noise, rms)
                    if (frames == 8) threshold = min(max(noise * 2.5, 220.0), 700.0)
                    continue
                }
                if (!speaking && rms < threshold) {
                    noise = noise * 0.97 + rms * 0.03
                    threshold = min(max(noise * 2.5, 220.0), 900.0)
                }
                val limit = if (speaking) max(threshold, speechLevel * 0.25) else threshold
                if (rms > limit) {
                    if (rms > threshold) speechLevel = if (speechLevel == 0.0) rms else speechLevel * 0.95 + rms * 0.05
                    loud++
                    loudFrames++
                    lastLoud = frames
                    if (!speaking && loud >= 3) {
                        speaking = true
                        firstLoud = frames - 2
                    }
                } else if (!speaking) {
                    loud = 0
                }
                if (speaking && (frames - lastLoud) * 20 >= 800) {
                    if (loudFrames >= 6) break
                    speaking = false
                    speechLevel = 0.0
                    loud = 0
                    loudFrames = 0
                    waitStart = frames
                }
                if (!speaking && (frames - waitStart) * 20 >= 7000) return null
                if (frames * 20 >= 20000) break
            }
            val all = out.toByteArray()
            val from = if (pre != null) 0 else max(0, (firstLoud - 30) * FRAME * 2)
            val to = min(all.size, (lastLoud + 10) * FRAME * 2 + (pre?.size ?: 0))
            return if (to > from) boost(all.copyOfRange(from, to)) else null
        } finally {
            rec = null
            runCatching { recorder.stop() }
            runCatching { recorder.release() }
        }
    }

    /** Raises quiet speech (up to 8x) so Whisper hears it well. */
    private fun boost(pcm: ByteArray): ByteArray {
        var peak = 1
        var i = 0
        while (i + 1 < pcm.size) {
            val s = (pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)
            peak = max(peak, kotlin.math.abs(s))
            i += 2
        }
        val gain = min(8.0, 20000.0 / peak)
        if (gain <= 1.2) return pcm
        i = 0
        while (i + 1 < pcm.size) {
            val s = (pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)
            val v = (s * gain).toInt().coerceIn(-32768, 32767)
            pcm[i] = (v and 0xFF).toByte()
            pcm[i + 1] = (v shr 8).toByte()
            i += 2
        }
        return pcm
    }

    private fun wav(pcm: ByteArray): ByteArray {
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray())
        h.putInt(36 + pcm.size)
        h.put("WAVE".toByteArray())
        h.put("fmt ".toByteArray())
        h.putInt(16)
        h.putShort(1.toShort())
        h.putShort(1.toShort())
        h.putInt(RATE)
        h.putInt(RATE * 2)
        h.putShort(2.toShort())
        h.putShort(16.toShort())
        h.put("data".toByteArray())
        h.putInt(pcm.size)
        return h.array() + pcm
    }

    private suspend fun transcribe(key: String, wav: ByteArray): String {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "speech.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("model", "whisper-large-v3-turbo")
            .addFormDataPart("temperature", "0")
        val lang = Locale.getDefault().language
        if (lang.length == 2) body.addFormDataPart("language", lang)
        val built = body.build()
        fun request(k: String) = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/transcriptions")
            .header("Authorization", "Bearer $k")
            .post(built)
            .build()
        val keys = (listOf(key) + shared(SharedKeys.groqExtraKeys).value.orEmpty().split(Regex("[,;\\s]+")))
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            .ifEmpty { listOf(key) }
        var used = groqIndex.coerceIn(0, keys.lastIndex)
        var response = http.voiceSend(request(keys[used]))
        var tries = 1
        // Si esta clave no vale o se quedó sin crédito o sin cupo (401, 402, 403, 429), prueba la siguiente.
        while (response.statusCode in listOf(401, 402, 403, 429) && tries < keys.size) {
            used = (used + 1) % keys.size
            tries++
            response = http.voiceSend(request(keys[used]))
        }
        groqIndex = used
        if (response.statusCode !in 200..299) throw IllegalStateException("HTTP ${response.statusCode}")
        val text = JSONObject(String(response.body, Charsets.UTF_8)).optString("text", "")
        return Regex("\\s+").replace(Regex("\\[[^\\]]*]").replace(text, " "), " ").trim()
    }
}
