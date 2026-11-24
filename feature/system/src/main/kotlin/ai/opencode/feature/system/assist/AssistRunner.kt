package ai.opencode.feature.system.assist

import ai.opencode.core.log.AppLog
import ai.opencode.feature.system.share.ShareCredentialStore
import ai.opencode.feature.system.share.ShareSendRequest
import ai.opencode.feature.system.share.ShareSender
import ai.opencode.feature.system.share.ShareTargets
import ai.opencode.server.OpenCodeServer
import ai.opencode.server.live.fetchRecentTranscript
import ai.opencode.server.model.OpenCodeTranscriptItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The voice assistant's back end: sends a spoken message to the session last viewed, in the
 * background, then waits for the agent's reply and hands it to [send]'s `speak`.
 */
object AssistRunner {
    enum class Stage { Sending, Waiting, Reading, Failed }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun cancel() {
        job?.cancel()
        job = null
    }

    fun send(text: String, speak: (String) -> Unit, onStage: (Stage, String?) -> Unit) {
        cancel()
        job = scope.launch {
            try {
                run(text, speak, onStage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.voice.notice("Assistant failed: ${e.message ?: e.javaClass.simpleName}")
                onStage(Stage.Failed, "No se pudo enviar")
            }
        }
    }

    private suspend fun replies(server: OpenCodeServer, sessionID: String): List<OpenCodeTranscriptItem> =
        runCatching { fetchRecentTranscript(server, sessionID, 12) }.getOrNull() ?: emptyList()

    private suspend fun run(text: String, speak: (String) -> Unit, onStage: (Stage, String?) -> Unit) {
        val credentials = ShareCredentialStore.live.load()
        val targets = ShareTargets.load()
        val session = targets?.let { t -> t.sessions.firstOrNull { it.id == t.lastViewedSessionID } ?: t.sessions.firstOrNull() }
        if (credentials == null || session == null) {
            onStage(Stage.Failed, "Abre Destiny una vez y entra a un chat")
            return
        }
        val server = credentials.server
        onStage(Stage.Sending, null)
        val known = replies(server, session.id)
            .filterIsInstance<OpenCodeTranscriptItem.AssistantMarkdown>().map { it.id }.toSet()
        ShareSender(server).send(
            ShareSendRequest(
                target = ShareSendRequest.Target.Session(session.id, session.directory),
                messageID = ShareSendRequest.newMessageID(),
                text = text,
            ),
        )
        onStage(Stage.Waiting, null)
        var signature = ""
        var stable = 0
        var waited = 0
        while (waited < 240) {
            delay(1500)
            waited++
            val items = replies(server, session.id)
            if (items.isEmpty()) continue
            val fresh = items.filterIsInstance<OpenCodeTranscriptItem.AssistantMarkdown>().filter { it.id !in known }
            val now = items.joinToString(",") {
                val m = it as? OpenCodeTranscriptItem.AssistantMarkdown
                "${it.id}:${m?.text?.length}:${m?.inProgress}"
            }
            if (now == signature) stable++ else {
                signature = now
                stable = 0
            }
            val reply = fresh.filter { !it.inProgress }.maxByOrNull { it.id } ?: continue
            if (fresh.any { it.inProgress }) continue
            val toolAfter = items.any { it is OpenCodeTranscriptItem.Tool && it.id > reply.id }
            if ((stable >= 4 && !toolAfter) || stable >= 20) {
                onStage(Stage.Reading, null)
                speak(reply.text)
                return
            }
        }
        onStage(Stage.Failed, "No llegó respuesta")
    }
}
