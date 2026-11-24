package ai.opencode.feature.settings

import ai.opencode.core.sharing.SharedKeys
import ai.opencode.core.sharing.shared
import ai.opencode.core.ui.OpenCodeShape
import ai.opencode.core.ui.OpenCodeTextField
import ai.opencode.feature.voice.FishSpeech
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ai.opencode.core.ui.OpenCodeTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** Un campo con su título arriba y, al lado, si ya hay algo guardado. */
@Composable
private fun LabeledField(
    title: String,
    secret: Boolean,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
) {
    val saved = value.isNotBlank()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = SettingsText.title, color = OpenCodeTheme.colors.text, modifier = Modifier.weight(1f))
            Text(
                if (saved) "✓ Guardado" else "Falta",
                style = SettingsText.summary,
                color = if (saved) SettingsTint.Green.color() else OpenCodeTheme.colors.secondaryText,
            )
        }
        OpenCodeTextField(
            value = value,
            onValueChange = onChange,
            placeholder = placeholder,
            singleLine = true,
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = if (secret) {
                KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
            } else {
                KeyboardOptions(autoCorrectEnabled = false)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Menú que se abre para agregar todas las claves de respaldo que quieras; se usan en orden. */
@Composable
private fun KeyListSection(title: String, stored: String?, onSave: (String?) -> Unit) {
    val rows = remember {
        mutableStateListOf<String>().apply {
            addAll(stored.orEmpty().split(Regex("[,;\\s]+")).filter { it.isNotBlank() })
        }
    }
    var open by remember { mutableStateOf(false) }
    fun save() = onSave(rows.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",").ifEmpty { null })
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = SettingsText.title, color = OpenCodeTheme.colors.text, modifier = Modifier.weight(1f))
            Text(
                rows.count { it.isNotBlank() }.toString() + (if (open) "  ^" else "  v"),
                style = SettingsText.summary,
                color = OpenCodeTheme.colors.secondaryText,
            )
        }
        if (open) {
            rows.forEachIndexed { i, v ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OpenCodeTextField(
                        value = v,
                        onValueChange = { rows[i] = it; save() },
                        placeholder = "Pega aquí una clave",
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "X",
                        color = OpenCodeTheme.colors.secondaryText,
                        modifier = Modifier.clickable { rows.removeAt(i); save() }.padding(12.dp),
                    )
                }
            }
            FilledTonalButton(onClick = { rows.add("") }, shape = OpenCodeShape.control) { Text("+ Agregar clave") }
        }
    }
}

/** Ajustes › Voz › Configuración: el modelo (Fish), la clave de Fish, el ID de la voz, la clave de Groq y una prueba. */
@Composable
internal fun VoiceConfigRows() {
    val key = shared(SharedKeys.fishAPIKey)
    val extra = shared(SharedKeys.fishExtraKeys)
    val voice = shared(SharedKeys.fishVoiceID)
    val groq = shared(SharedKeys.groqAPIKey)
    val groqExtra = shared(SharedKeys.groqExtraKeys)
    val keyText by key.flow.collectAsState()
    val extraText by extra.flow.collectAsState()
    val voiceText by voice.flow.collectAsState()
    val groqText by groq.flow.collectAsState()
    val groqExtraText by groqExtra.flow.collectAsState()
    SettingsValueRow("Modelo", "Fish Audio")
    SettingsDivider()
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LabeledField("Fish Audio", true, keyText.orEmpty(), stringResource(R.string.settings_fish_key_placeholder)) {
            key.set(it.trim().ifEmpty { null })
        }
        KeyListSection("Fish Audio · claves de respaldo", extraText) { extra.set(it) }
        LabeledField("ID de la voz", false, voiceText, "Pega aquí el ID de la voz") {
            voice.set(it.trim())
        }
        LabeledField("Groq (para que te escuche)", true, groqText.orEmpty(), "Pega aquí la clave de Groq") {
            groq.set(it.trim().ifEmpty { null })
        }
        KeyListSection("Groq · claves de respaldo", groqExtraText) { groqExtra.set(it) }
        FilledTonalButton(
            onClick = { FishSpeech.test() },
            enabled = !keyText.isNullOrBlank(),
            shape = OpenCodeShape.control,
        ) { Text(stringResource(R.string.settings_fish_test)) }
    }
}

/** Activar el asistente diciendo una palabra, con las palabras editables y el estado de la escucha. */
@Composable
private fun WakeWordSection() {
    val on = shared(SharedKeys.assistWakeOn)
    val words = shared(SharedKeys.assistWakeWords)
    val status = shared(SharedKeys.assistWakeStatus)
    val onValue by on.flow.collectAsState()
    val wordsValue by words.flow.collectAsState()
    val statusValue by status.flow.collectAsState()
    SettingsToggleRow(
        "Activar con la voz (di la palabra, incluso con la pantalla apagada)",
        checked = onValue,
        onCheckedChange = { on.set(it) },
    )
    if (onValue) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Palabras de activación (separadas por coma)", style = SettingsText.title, color = OpenCodeTheme.colors.text)
            OpenCodeTextField(
                value = wordsValue,
                onValueChange = { words.set(it) },
                placeholder = "destino, alia",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(statusValue, style = SettingsText.summary, color = OpenCodeTheme.colors.secondaryText)
        }
    }
}

/** Ajustes › Voz › Interruptores: leer respuestas en voz alta y asistente listo con la app cerrada. */
@Composable
internal fun VoiceToggleRows() {
    val reads = shared(SharedKeys.fishReadsReplies)
    val keepAlive = shared(SharedKeys.assistKeepAlive)
    val islandOnly = shared(SharedKeys.assistIslandOnly)
    val readsOn by reads.flow.collectAsState()
    val keepAliveOn by keepAlive.flow.collectAsState()
    val islandOnlyOn by islandOnly.flow.collectAsState()
    SettingsToggleRow(
        stringResource(R.string.settings_fish_read_replies),
        checked = readsOn,
        onCheckedChange = { reads.set(it) },
    )
    SettingsDivider()
    SettingsToggleRow(
        "Asistente listo (botón de encendido con la app cerrada)",
        checked = keepAliveOn,
        onCheckedChange = { keepAlive.set(it) },
    )
    SettingsDivider()
    SettingsToggleRow(
        "Solo isla (ocultar el recuadro del asistente)",
        checked = islandOnlyOn,
        onCheckedChange = { islandOnly.set(it) },
    )
    SettingsDivider()
    WakeWordSection()
}
