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
import kotlinx.coroutines.ensureActive
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
 * Listens with the phone's own microphone and understands the speech with the person's Fish Audio
 * account (`POST /v1/asr`), so it does not depend on Google's recognizer. One utterance at a time:
 * it records until a pause, sends a WAV, and returns the text.
 */
object FishListen {
    enum class Failure { Silence, Mic, Network }

    private class MicException : Exception("mic")

    private const val RATE = 16000
    private const val FRAME = 320 // 20 ms
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http: OkHttpClient by lazy { OkHttpClient().withVoiceTimeout(30) }
    private var job: Job? = null

    /** A Fish Audio key is saved. */
    val available: Boolean
        get() = !shared(SharedKeys.fishAPIKey).value.isNullOrBlank()

    fun cancel() {
        job?.cancel()
        job = null
    }

    /** Callbacks run on a background thread. */
    fun listen(onTranscribing: () -> Unit, onText: (String) -> Unit, onFail: (Failure, String?) -> Unit) {
        cancel()
        val key = shared(SharedKeys.fishAPIKey).value?.trim().orEmpty()
        job = scope.launch {
            try {
                val pcm = record()
                if (pcm == null) {
                    onFail(Failure.Silence, null)
                    return@launch
                }
                onTranscribing()
                val text = transcribe(key, wav(pcm))
                if (text.isEmpty()) onFail(Failure.Silence, null) else onText(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: MicException) {
                onFail(Failure.Mic, null)
            } catch (e: SecurityException) {
                onFail(Failure.Mic, null)
            } catch (e: Exception) {
                AppLog.voice.notice("Fish Audio couldn’t understand the audio: ${e.message ?: e.javaClass.simpleName}")
                onFail(Failure.Network, e.message)
            }
        }
    }

    /** Records until about a second of silence after speech; null if nobody spoke for 7 seconds. */
    @SuppressLint("MissingPermission")
    private suspend fun record(): ByteArray? {
        val minBuffer = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) throw MicException()
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            max(minBuffer, RATE),
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw MicException()
        }
        val out = ByteArrayOutputStream()
        val frame = ShortArray(FRAME)
        val bytes = ByteArray(FRAME * 2)
        try {
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) throw MicException()
            var frames = 0
            var noise = 0.0
            var threshold = 450.0
            var loud = 0
            var loudFrames = 0
            var speaking = false
            var firstLoud = 0
            var lastLoud = 0
            var waitStart = 0
            while (true) {
                coroutineContext.ensureActive()
                val n = recorder.read(frame, 0, FRAME)
                if (n <= 0) throw MicException()
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
                if (frames <= 15) {
                    noise += rms / 15
                    if (frames == 15) threshold = max(min(noise, 400.0) * 3, 450.0)
                    continue
                }
                if (rms > threshold) {
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
                if (speaking && (frames - lastLoud) * 20 >= 1000) {
                    if (loudFrames >= 6) break
                    speaking = false
                    loud = 0
                    loudFrames = 0
                    waitStart = frames
                }
                if (!speaking && (frames - waitStart) * 20 >= 7000) return null
                if (frames * 20 >= 30000) break
            }
            val all = out.toByteArray()
            val from = max(0, (firstLoud - 15) * FRAME * 2)
            val to = min(all.size, (lastLoud + 10) * FRAME * 2)
            return if (to > from) all.copyOfRange(from, to) else null
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
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
            .addFormDataPart("audio", "speech.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("ignore_timestamps", "true")
            .addFormDataPart("tag_audio_events", "false")
        val lang = Locale.getDefault().language
        if (lang.length == 2) body.addFormDataPart("language", lang)
        val request = Request.Builder()
            .url("https://api.fish.audio/v1/asr")
            .header("Authorization", "Bearer $key")
            .header("model", "transcribe-1-pro")
            .post(body.build())
            .build()
        val response = http.voiceSend(request)
        if (response.statusCode !in 200..299) throw IllegalStateException("HTTP ${response.statusCode}")
        val text = JSONObject(String(response.body, Charsets.UTF_8)).optString("text", "")
        return Regex("\\s+").replace(Regex("\\[[^\\]]*]").replace(text, " "), " ").trim()
    }
}
