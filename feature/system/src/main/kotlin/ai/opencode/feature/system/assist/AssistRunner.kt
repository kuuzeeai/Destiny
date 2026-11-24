package ai.opencode.feature.system.assist

import ai.opencode.core.dependencies.dependency
import ai.opencode.core.log.AppLog
import ai.opencode.core.sharing.SharedKeys
import ai.opencode.core.sharing.shared
import ai.opencode.feature.system.share.ShareCredentialStore
import ai.opencode.feature.system.share.ShareSendRequest
import ai.opencode.feature.system.share.ShareSender
import ai.opencode.feature.system.share.ShareTargets
import ai.opencode.server.OpenCodeClient
import ai.opencode.server.OpenCodeServer
import ai.opencode.server.model.OpenCodeModelRef
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

    private fun isFailure(item: OpenCodeTranscriptItem): Boolean =
        item is OpenCodeTranscriptItem.RunError || (item is OpenCodeTranscriptItem.Notice && item.isError)

    /** A short Spanish line that is fine to read aloud. */
    private fun failureText(item: OpenCodeTranscriptItem): String {
        val raw = when (item) {
            is OpenCodeTranscriptItem.RunError -> item.error.message + " " + (item.error.status ?: "")
            is OpenCodeTranscriptItem.Notice -> item.text
            else -> ""
        }.lowercase()
        return when {
            Regex("credit|quota|billing|balance|insufficient|payment|402|429|rate limit|limit").containsMatchIn(raw) ->
                "Se acabó el crédito o el límite del modelo."
            Regex("unauthorized|api key|invalid key|401|403|forbidden").containsMatchIn(raw) ->
                "La clave del modelo no es válida."
            else -> "Falló el modelo. " + raw.trim().take(100)
        }
    }

    /** Pone en el chat el modelo fijo del asistente, si hay uno. Si falla, sigue con el que tenga. */
    private suspend fun applyModel(server: OpenCodeServer, sessionID: String) {
        val spec = shared(SharedKeys.assistModel).value
        val cut = spec.indexOf('|')
        if (cut <= 0) return
        try {
            val ref = OpenCodeModelRef(spec.substring(cut + 1), spec.substring(0, cut))
            dependency(OpenCodeClient).switchModel(server, sessionID, ref)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.voice.notice("Assistant model failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun replies(server: OpenCodeServer, sessionID: String): List<OpenCodeTranscriptItem> =
        runCatching { fetchRecentTranscript(server, sessionID, 12) }.getOrNull() ?: emptyList()

    private suspend fun run(text: String, speak: (String) -> Unit, onStage: (Stage, String?) -> Unit) {
        val credentials = ShareCredentialStore.live.load()
        val targets = ShareTargets.load()
        val pinnedID = shared(SharedKeys.assistSessionID).value
        val session = targets?.let { t ->
            t.sessions.firstOrNull { pinnedID.isNotEmpty() && it.id == pinnedID }
                ?: t.sessions.firstOrNull { it.id == t.lastViewedSessionID }
                ?: t.sessions.firstOrNull()
        }
        if (credentials == null || session == null) {
            onStage(Stage.Failed, "Abre Destiny una vez y entra a un chat")
            return
        }
        val server = credentials.server
        onStage(Stage.Sending, null)
        applyModel(server, session.id)
        val baseline = replies(server, session.id)
        val known = baseline.filterIsInstance<OpenCodeTranscriptItem.AssistantMarkdown>().map { it.id }.toSet()
        val knownErrors = baseline.filter { isFailure(it) }.map { it.id }.toSet()
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
        while (waited < 600) {
            delay(400)
            waited++
            val items = replies(server, session.id)
            if (items.isEmpty()) continue
            val fresh = items.filterIsInstance<OpenCodeTranscriptItem.AssistantMarkdown>().filter { it.id !in known }
            val failure = items.firstOrNull { isFailure(it) && it.id !in knownErrors }
            if (failure != null) {
                onStage(Stage.Failed, failureText(failure))
                return
            }
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
            if ((stable >= 3 && !toolAfter) || stable >= 12) {
                onStage(Stage.Reading, null)
                speak(reply.text)
                return
            }
        }
        onStage(Stage.Failed, "No llegó respuesta")
    }
}
