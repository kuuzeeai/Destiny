package ai.opencode.feature.session

import ai.opencode.feature.system.assist.AssistRunner
import ai.opencode.feature.voice.FishSpeech
import ai.opencode.server.OpenCodeServer
import ai.opencode.server.live.fetchRecentTranscript
import ai.opencode.server.model.OpenCodeTranscriptItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Espera la respuesta del chat y la lee, aunque salgas del chat o apagues la pantalla.
 * La app pone en [hold] el servicio en primer plano que la mantiene despierta.
 */
object ReplyWatcher {
    @Volatile var hold: ((Boolean) -> Unit)? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var watching: String? = null
    private var ended = false

    fun watch(server: OpenCodeServer, sessionID: String, known: Set<String>) {
        if (!FishSpeech.readsReplies) return
        // Si lo mandó el asistente, ya lo lee él.
        if (AssistRunner.waitingOn == sessionID) return
        if (job?.isActive == true && watching == sessionID && !ended) return
        job?.cancel()
        FishSpeech.stop()
        watching = sessionID
        ended = false
        hold?.invoke(true)
        job = scope.launch {
            val reply = awaitReply(server, sessionID, known)
            if (reply != null) {
                FishSpeech.speakReply(reply)
                delay(500)
                while (FishSpeech.active) delay(500)
            }
            hold?.invoke(false)
        }
    }

    fun runEnded(sessionID: String) {
        if (watching == sessionID) ended = true
    }

    /** Botón «Detener» de la notificación. */
    fun stop() {
        job?.cancel()
        job = null
        FishSpeech.stop()
        hold?.invoke(false)
    }

    private suspend fun awaitReply(server: OpenCodeServer, sessionID: String, known: Set<String>): String? {
        var signature = ""
        var stable = 0
        var endedPolls = 0
        val newest = known.maxOrNull().orEmpty()
        val deadline = System.currentTimeMillis() + 30 * 60 * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(1000)
            if (ended) endedPolls++
            val items = withContext(Dispatchers.IO) {
                runCatching { fetchRecentTranscript(server, sessionID, 12) }.getOrNull()
            }.orEmpty()
            val replies = items.filterIsInstance<OpenCodeTranscriptItem.AssistantMarkdown>()
            val fresh = replies.filter { it.id !in known && it.id > newest }
            val now = items.joinToString(",") {
                val m = it as? OpenCodeTranscriptItem.AssistantMarkdown
                "${it.id}:${m?.text?.length}:${m?.inProgress}"
            }
            if (now == signature) stable++ else {
                signature = now
                stable = 0
            }
            val reply = fresh.maxByOrNull { it.id }
            if (reply != null && fresh.none { it.inProgress }) {
                val toolAfter = items.any { it is OpenCodeTranscriptItem.Tool && it.id > reply.id }
                if (ended && stable >= 2) return reply.text
                if (!toolAfter && stable >= 20) return reply.text
            }
            if (endedPolls > 30) return null
        }
        return null
    }
}
