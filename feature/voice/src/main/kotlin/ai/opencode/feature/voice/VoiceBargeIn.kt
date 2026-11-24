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

    fun stop() {
        job?.cancel()
        job = null
    }

    /** [onSpeech] corre en un hilo de fondo, una sola vez. Se detiene solo cuando Fish termina. */
    @SuppressLint("MissingPermission")
    fun start(onSpeech: () -> Unit) {
        stop()
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
                while (true) {
                    coroutineContext.ensureActive()
                    if (!FishSpeech.active) return@launch
                    val n = r.read(frame, 0, FRAME)
                    if (n <= 0) return@launch
                    var sum = 0.0
                    for (i in 0 until n) sum += frame[i].toDouble() * frame[i]
                    val rms = sqrt(sum / n)
                    frames++
                    if (frames == 1) avg = rms
                    if (frames <= 25) {
                        avg = avg * 0.9 + rms * 0.1
                        continue
                    }
                    if (rms > max(avg * 2.5, 1400.0)) {
                        loud++
                        if (loud >= 12) {
                            onSpeech()
                            return@launch
                        }
                    } else {
                        loud = 0
                        avg = avg * 0.98 + rms * 0.02
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.voice.notice("Barge-in stopped: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                runCatching { recorder?.stop() }
                recorder?.release()
                runCatching { echo?.release() }
                runCatching { noise?.release() }
            }
        }
    }
}
