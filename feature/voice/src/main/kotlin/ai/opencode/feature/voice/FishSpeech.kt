package ai.opencode.feature.voice

import ai.opencode.core.dependencies.AppContext
import ai.opencode.core.dependencies.dependency
import ai.opencode.core.log.AppLog
import ai.opencode.core.sharing.SharedKeys
import ai.opencode.core.sharing.shared
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.PowerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

/**
 * Reads text aloud with the person's own Fish Audio account (`POST /v1/tts`, MP3), then plays it
 * on the media stream. One reading at a time: a new one, or [stop], cuts the last. The key lives
 * in the keychain-backed [SharedKeys.fishAPIKey] and is only sent to api.fish.audio.
 */
object FishSpeech {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val http: OkHttpClient by lazy { OkHttpClient().withVoiceTimeout(60) }
    @Volatile
    private var job: Job? = null

    @Volatile private var fishModel = "s2-pro"
    @Volatile var startedAt = 0L
    @Volatile var firstSoundAt = 0L

    /** El último error de Fish (para mostrarlo en la burbuja); null si todo fue bien. */
    @Volatile var lastError: String? = null

    /** Qué clave de Fish se usa (vacío si solo hay una), para la línea gris. */
    @Volatile var keyLabel = ""
    @Volatile private var keyIndex = 0

    /** La clave principal y las de respaldo, sin repetir. */
    private fun fishKeys(): List<String> {
        val first = shared(SharedKeys.fishAPIKey).value?.trim().orEmpty()
        val more = shared(SharedKeys.fishExtraKeys).value.orEmpty().split(Regex("[,;\\s]+"))
        return (listOf(first) + more).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    /** Fish está preparando o reproduciendo sonido ahora mismo. */
    val active: Boolean
        get() = job?.isActive == true

    /** Replies are read aloud: the switch is on and a key is saved. */
    val readsReplies: Boolean
        get() = shared(SharedKeys.fishReadsReplies).value && !shared(SharedKeys.fishAPIKey).value.isNullOrBlank()

    /** Reads an agent reply when Fish is switched on. */
    fun speakReply(markdown: String) {
        if (readsReplies) speak(markdown, replyVolume())
    }

    /** Settings' Test voice button. */
    fun test(percent: Int? = null) = speak("Hola, esta es tu voz de Fish Audio.", percent?.let { it / 100f })

    /** Volumen (0 a 1) segun la hora: de dia o de noche. */
    private fun replyVolume(): Float {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val day = shared(SharedKeys.fishDayStart).value
        val night = shared(SharedKeys.fishNightStart).value
        val isDay = ai.opencode.core.speech.SpeechRules.isDay(hour, day, night)
        val percent = shared(if (isDay) SharedKeys.fishDayVolume else SharedKeys.fishNightVolume).value
        return percent.coerceIn(0, 100) / 100f
    }

    /** Reads [markdown] sentence by sentence (the next one is made while the last plays); [onDone] runs when it ends. */
    fun speak(markdown: String, volume: Float? = null, onDone: () -> Unit = {}) {
        val key = shared(SharedKeys.fishAPIKey).value?.trim().orEmpty()
        if (key.isEmpty()) return
        val text = spokenText(markdown)
        if (text.isEmpty()) return
        val voice = shared(SharedKeys.fishVoiceID).value.trim()
        stop()
        startedAt = System.currentTimeMillis()
        firstSoundAt = 0L
        job = scope.launch {
            if (volume != null) applyPhoneVolume(volume)
            try {
                coroutineScope {
                    val parts = chunks(text)
                    var next: Deferred<File>? = async(Dispatchers.IO) { synthesize(key, voice, parts[0]) }
                    for (i in parts.indices) {
                        val file = next!!.await()
                        next = if (i + 1 < parts.size) async(Dispatchers.IO) { synthesize(key, voice, parts[i + 1]) } else null
                        try {
                            play(file)
                        } finally {
                            file.delete()
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
                AppLog.voice.notice("Fish Audio couldn’t read the reply: ${e.message ?: e.javaClass.simpleName}")
            }
            restorePhoneVolume()
            onDone()
        }
    }

    /** First sentence alone (so sound starts fast), then groups of about 350 characters. */
    private fun chunks(text: String): List<String> {
        val sentences = Regex("(?<=[.!?…])\\s+").split(text).filter { it.isNotBlank() }
        if (sentences.isEmpty()) return listOf(text)
        val out = mutableListOf(sentences[0])
        var cur = StringBuilder()
        for (sentence in sentences.drop(1)) {
            if (cur.isNotEmpty() && cur.length + sentence.length > 350) {
                out += cur.toString()
                cur = StringBuilder()
            }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(sentence)
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out
    }

    fun stop() {
        job?.cancel()
        job = null
        restorePhoneVolume()
    }

    private suspend fun synthesize(key: String, voice: String, text: String): File {
        val body = JSONObject().put("text", text).put("format", "mp3")
        if (voice.isNotEmpty()) body.put("reference_id", voice)
        suspend fun call(k: String, m: String) = http.voiceSend(
            Request.Builder()
                .url("https://api.fish.audio/v1/tts")
                .header("Authorization", "Bearer $k")
                .header("model", m)
                .post(body.toString().toRequestBody(voiceJsonMediaType))
                .build(),
        )
        fun noCredit(code: Int) = code == 402 || code == 401
        val keys = fishKeys().ifEmpty { listOf(key) }
        var used = keyIndex.coerceIn(0, keys.lastIndex)
        var response = call(keys[used], fishModel)
        var tries = 1
        // Si esta clave no tiene crédito (402) o no vale (401), prueba la siguiente.
        while (noCredit(response.statusCode) && tries < keys.size) {
            used = (used + 1) % keys.size
            tries++
            response = call(keys[used], fishModel)
        }
        if (response.statusCode == 402 && fishModel != "s2.1-pro-free") {
            // Ninguna clave tiene crédito para s2-pro: usa el modelo gratis y se queda con él.
            fishModel = "s2.1-pro-free"
            used = 0
            response = call(keys[0], fishModel)
        }
        keyIndex = used
        keyLabel = if (keys.size > 1) " · clave ${used + 1}/${keys.size}" else ""
        if (response.statusCode !in 200..299) throw IllegalStateException("HTTP ${response.statusCode}: " + String(response.body, Charsets.UTF_8).take(120))
        if (response.body.isEmpty()) throw IllegalStateException("empty audio")
        val file = File.createTempFile("fish-", ".mp3", dependency(AppContext).cacheDir)
        file.writeBytes(response.body)
        return file
    }

    /** Volumen multimedia que habia antes de leer, y el que puso la lectura. */
    private var previousVolume: Int? = null
    private var appliedVolume = 0

    private fun audioManager() =
        dependency(AppContext).applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Pone el volumen multimedia en [level] (0 a 1) mientras dura la lectura. */
    private fun applyPhoneVolume(level: Float) {
        runCatching {
            val audio = audioManager()
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val before = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            appliedVolume = Math.round(level * max).coerceIn(1, max)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, appliedVolume, 0)
            if (previousVolume == null) previousVolume = before
        }
    }

    /** Devuelve el volumen que habia, salvo que lo hayas cambiado tu mientras leia. */
    private fun restorePhoneVolume() {
        val before = previousVolume ?: return
        previousVolume = null
        runCatching {
            val audio = audioManager()
            if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) == appliedVolume) {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, before, 0)
            }
        }
    }

    private suspend fun play(file: File) {
        val context = dependency(AppContext).applicationContext
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        audio.requestAudioFocus(focus)
        val finished = CompletableDeferred<Unit>()
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(attributes)
            player.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            player.setDataSource(file.absolutePath)
            player.setOnPreparedListener {
                if (firstSoundAt == 0L) firstSoundAt = System.currentTimeMillis()
                it.start()
            }
            player.setOnCompletionListener { finished.complete(Unit) }
            player.setOnErrorListener { _, what, _ ->
                finished.completeExceptionally(IllegalStateException("playback error $what"))
                true
            }
            player.prepareAsync()
            finished.await()
        } finally {
            runCatching { player.release() }
            audio.abandonAudioFocusRequest(focus)
        }
    }

    internal fun spokenText(markdown: String): String = ai.opencode.core.speech.SpeechRules.spokenText(markdown)
}
