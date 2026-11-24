package ai.opencode.feature.session

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
