import sys, pathlib
import re
def balanced(t):
    t = re.sub(r'"(?:\\.|[^"\\])*"', '""', t)
    t = re.sub(r"'(?:\\.|[^'\\])'", "''", t)
    return t.count("{") == t.count("}") and t.count("(") == t.count(")")
KEYS = pathlib.Path("core/src/main/kotlin/ai/opencode/core/sharing/SharedKeys.kt")
SV = pathlib.Path("feature/session/src/main/kotlin/ai/opencode/feature/session/SessionView.kt")
FISH = pathlib.Path("feature/voice/src/main/kotlin/ai/opencode/feature/voice/FishSpeech.kt")
EFFECT = pathlib.Path("feature/session/src/main/kotlin/ai/opencode/feature/session/FishReplyEffect.kt")

FISH_KT = r'''package ai.opencode.feature.voice

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
import kotlinx.coroutines.Job
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
    private var job: Job? = null

    /** Replies are read aloud: the switch is on and a key is saved. */
    val readsReplies: Boolean
        get() = shared(SharedKeys.fishReadsReplies).value && !shared(SharedKeys.fishAPIKey).value.isNullOrBlank()

    /** Reads an agent reply when Fish is switched on. */
    fun speakReply(markdown: String) {
        if (readsReplies) speak(markdown)
    }

    /** Settings' Test voice button. */
    fun test() = speak("Hola, esta es tu voz de Fish Audio.")

    fun speak(markdown: String) {
        val key = shared(SharedKeys.fishAPIKey).value?.trim().orEmpty()
        if (key.isEmpty()) return
        val text = spokenText(markdown)
        if (text.isEmpty()) return
        val voice = shared(SharedKeys.fishVoiceID).value.trim()
        stop()
        job = scope.launch {
            var file: File? = null
            try {
                file = withContext(Dispatchers.IO) { synthesize(key, voice, text) }
                play(file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.voice.notice("Fish Audio couldn’t read the reply: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                file?.delete()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun synthesize(key: String, voice: String, text: String): File {
        val body = JSONObject().put("text", text).put("format", "mp3")
        if (voice.isNotEmpty()) body.put("reference_id", voice)
        val request = Request.Builder()
            .url("https://api.fish.audio/v1/tts")
            .header("Authorization", "Bearer $key")
            .header("model", "s2-pro")
            .post(body.toString().toRequestBody(voiceJsonMediaType))
            .build()
        val response = http.voiceSend(request)
        if (response.statusCode !in 200..299) throw IllegalStateException("HTTP ${response.statusCode}")
        if (response.body.isEmpty()) throw IllegalStateException("empty audio")
        val file = File.createTempFile("fish-", ".mp3", dependency(AppContext).cacheDir)
        file.writeBytes(response.body)
        return file
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
            player.setOnPreparedListener { it.start() }
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

    /** Markdown as it should sound: no code blocks, links, URLs or marks; short enough to start fast. */
    internal fun spokenText(markdown: String): String {
        var t = markdown
        t = Regex("```[\\s\\S]*?```").replace(t, " ")
        t = Regex("!\\[[^\\]]*]\\([^)]*\\)").replace(t, " ")
        t = Regex("\\[([^\\]]+)]\\([^)]*\\)").replace(t, "$1")
        t = Regex("https?://\\S+").replace(t, " ")
        t = Regex("(?m)^\\s{0,3}#{1,6}\\s*").replace(t, "")
        t = Regex("(?m)^\\s*[-*+]\\s+").replace(t, "")
        t = t.replace(Regex("[`*~>|]"), "")
        t = Regex("\\s+").replace(t, " ").trim()
        if (t.length > MAX_CHARS) {
            val cut = t.lastIndexOf('.', MAX_CHARS)
            t = t.substring(0, if (cut > MAX_CHARS / 3) cut + 1 else MAX_CHARS)
        }
        return t
    }

    private const val MAX_CHARS = 1200
}
'''

EFFECT_KT = '''package ai.opencode.feature.session

import ai.opencode.core.store.StoreOf
import ai.opencode.feature.session.SessionFeature.Action
import ai.opencode.feature.session.SessionFeature.State
import ai.opencode.feature.voice.FishSpeech
import ai.opencode.server.model.OpenCodeTranscriptItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Reads the agent's reply aloud (Fish Audio) once its run ends. A new run cuts the last reading;
 * the reply already on screen when the run began is never read.
 */
@Composable
internal fun FishReplyEffect(store: StoreOf<State, Action>) {
    LaunchedEffect(store) {
        var armed = false
        var baseline: String? = null
        var lastSpoken: String? = null
        store.states.collect { state ->
            if (state.isRunning) {
                if (!armed) {
                    armed = true
                    baseline = state.items.lastAssistantReply()?.id
                    FishSpeech.stop()
                }
            } else if (armed) {
                val reply = state.items.lastAssistantReply()
                if (reply != null && !reply.inProgress && reply.id != baseline && reply.id != lastSpoken) {
                    armed = false
                    lastSpoken = reply.id
                    FishSpeech.speakReply(reply.text)
                }
            }
        }
    }
}

private fun List<OpenCodeTranscriptItem>.lastAssistantReply(): OpenCodeTranscriptItem.AssistantMarkdown? =
    lastOrNull { it is OpenCodeTranscriptItem.AssistantMarkdown } as? OpenCodeTranscriptItem.AssistantMarkdown
'''

EDITS = {
 KEYS: [
  ('    val xaiAPIKey = SharedKey.keychain<String?>("xai-api-key", null)\n',
   '    val xaiAPIKey = SharedKey.keychain<String?>("xai-api-key", null)\n\n'
   '    /** Your own Fish Audio key for reading replies aloud; only sent to api.fish.audio. */\n'
   '    val fishAPIKey = SharedKey.keychain<String?>("fish-api-key", null)\n\n'
   '    /** The Fish Audio voice (its model ID) replies are read in; empty uses Fish\'s default voice. */\n'
   '    val fishVoiceID = SharedKey.appStorage("fish-voice-id", "")\n\n'
   '    /** Whether the agent\'s replies are read aloud with Fish Audio. Off by default. */\n'
   '    val fishReadsReplies = SharedKey.appStorage("fish-reads-replies", false)\n'),
 ],
 SV: [
  ("    val importer = rememberSessionAttachmentImporter(store)\n",
   "    val importer = rememberSessionAttachmentImporter(store)\n    if (!isInertPreview) FishReplyEffect(store)\n"),
 ],
}
NEW = {FISH: FISH_KT, EFFECT: EFFECT_KT}
out = {}
for path, edits in EDITS.items():
    if not path.exists(): sys.exit(f"NO EXISTE {path}")
    t = path.read_text()
    for old, new in edits:
        n = t.count(old)
        if n != 1: sys.exit(f"ABORTO: {path.name}: {n} coincidencias para: {old[:70]!r}")
        t = t.replace(old, new)
    if not balanced(t):
        sys.exit(f"ABORTO: balance roto en {path.name}")
    out[path] = t
for path, text in NEW.items():
    if path.exists(): sys.exit(f"YA EXISTE {path}")
    if not balanced(text):
        sys.exit(f"ABORTO: balance roto en {path.name}")
    out[path] = text
for p, t in out.items(): p.write_text(t)
print("OK f16:", ", ".join(p.name for p in out))
