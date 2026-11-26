package ai.opencode.feature.session

import ai.opencode.core.store.StoreOf
import ai.opencode.feature.session.SessionFeature.Action
import ai.opencode.feature.session.SessionFeature.State
import ai.opencode.server.model.OpenCodeTranscriptItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** Avisa a [ReplyWatcher] cuando empieza y termina una respuesta; él la lee. */
@Composable
internal fun FishReplyEffect(store: StoreOf<State, Action>) {
    LaunchedEffect(store) {
        var running = false
        store.states.collect { state ->
            val session = state.session ?: return@collect
            if (state.isRunning && !running) {
                running = true
                val known = state.items
                    .filterIsInstance<OpenCodeTranscriptItem.AssistantMarkdown>()
                    .filter { !it.inProgress }
                    .map { it.id }
                    .toSet()
                ReplyWatcher.watch(state.server, session.id, known)
            } else if (!state.isRunning && running) {
                running = false
                ReplyWatcher.runEnded(session.id)
            }
        }
    }
}
