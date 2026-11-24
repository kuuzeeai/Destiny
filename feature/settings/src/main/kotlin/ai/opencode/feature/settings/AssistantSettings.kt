package ai.opencode.feature.settings

import ai.opencode.core.dependencies.dependency
import ai.opencode.core.sharing.SharedKeys
import ai.opencode.core.sharing.shared
import ai.opencode.feature.system.share.ShareCredentialStore
import ai.opencode.feature.system.share.ShareTargets
import ai.opencode.server.OpenCodeClient
import ai.opencode.server.model.OpenCodeModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val LAST_CHAT = "Último chat visto"
private const val CHAT_MODEL = "El que ya tiene el chat"

/** Ajustes › Voz › Asistente: chat fijo y modelo fijo. */
@Composable
internal fun AssistantRows() {
    val chat = shared(SharedKeys.assistSessionID)
    val chatID by chat.flow.collectAsState()
    val model = shared(SharedKeys.assistModel)
    val modelSpec by model.flow.collectAsState()
    val sessions = remember {
        runCatching { ShareTargets.load() }.getOrNull()?.sessions.orEmpty().distinctBy { it.id }.take(40)
    }
    var models by remember { mutableStateOf<List<OpenCodeModel>>(emptyList()) }
    LaunchedEffect(Unit) {
        val server = ShareCredentialStore.live.load()?.server
        if (server != null) {
            models = withContext(Dispatchers.IO) {
                runCatching { dependency(OpenCodeClient).modelCatalog(server, sessions.firstOrNull()?.directory).models }
                    .getOrDefault(emptyList())
            }
        }
    }
    val chatValue = when {
        chatID.isEmpty() -> LAST_CHAT
        else -> sessions.firstOrNull { it.id == chatID }?.title?.ifBlank { "Sin título" } ?: "Chat guardado"
    }
    SettingsMenuRow(
        title = "Chat del asistente",
        value = chatValue,
        selection = chatID,
        sections = listOf(
            null to (
                listOf(SettingsMenuOption("", LAST_CHAT)) +
                    sessions.map { SettingsMenuOption(it.id, it.title.ifBlank { "Sin título" }, it.projectName) }
                ),
        ),
        onSelect = { chat.set(it) },
    )
    SettingsDivider()
    val modelValue = if (modelSpec.isEmpty()) CHAT_MODEL else {
        models.firstOrNull { "${it.providerID}|${it.key}" == modelSpec }?.name ?: modelSpec.substringAfter('|')
    }
    val firstSection: Pair<String?, List<SettingsMenuOption<String>>> = null to listOf(SettingsMenuOption("", CHAT_MODEL))
    SettingsMenuRow(
        title = "Modelo del asistente",
        value = modelValue,
        selection = modelSpec,
        sections = listOf(firstSection) + models.groupBy { it.providerID }.map { (provider, list) ->
            val entry: Pair<String?, List<SettingsMenuOption<String>>> =
                provider to list.map { SettingsMenuOption("${it.providerID}|${it.key}", it.name) }
            entry
        },
        onSelect = { model.set(it) },
    )
}
