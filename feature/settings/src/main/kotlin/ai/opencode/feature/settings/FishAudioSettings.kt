package ai.opencode.feature.settings

import ai.opencode.core.sharing.SharedKeys
import ai.opencode.core.sharing.shared
import ai.opencode.core.ui.OpenCodeShape
import ai.opencode.core.ui.OpenCodeTextField
import ai.opencode.feature.voice.FishSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** Ajustes › Voz › Configuración: el modelo (Fish), la clave de Groq, la de Fish, el ID de la voz y una prueba. */
@Composable
internal fun VoiceConfigRows() {
    val key = shared(SharedKeys.fishAPIKey)
    val voice = shared(SharedKeys.fishVoiceID)
    val groq = shared(SharedKeys.groqAPIKey)
    val keyText by key.flow.collectAsState()
    val voiceText by voice.flow.collectAsState()
    val groqText by groq.flow.collectAsState()
    SettingsValueRow("Modelo", "Fish Audio")
    SettingsDivider()
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OpenCodeTextField(
            value = groqText.orEmpty(),
            onValueChange = { groq.set(it.trim().ifEmpty { null }) },
            placeholder = "Clave de Groq (para que te escuche)",
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        OpenCodeTextField(
            value = keyText.orEmpty(),
            onValueChange = { key.set(it.trim().ifEmpty { null }) },
            placeholder = stringResource(R.string.settings_fish_key_placeholder),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        OpenCodeTextField(
            value = voiceText,
            onValueChange = { voice.set(it.trim()) },
            placeholder = "Voz: pega aquí el ID de la voz",
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        FilledTonalButton(
            onClick = { FishSpeech.test() },
            enabled = !keyText.isNullOrBlank(),
            shape = OpenCodeShape.control,
        ) { Text(stringResource(R.string.settings_fish_test)) }
    }
}

/** Ajustes › Voz › Interruptores: leer respuestas en voz alta y asistente listo con la app cerrada. */
@Composable
internal fun VoiceToggleRows() {
    val reads = shared(SharedKeys.fishReadsReplies)
    val keepAlive = shared(SharedKeys.assistKeepAlive)
    val readsOn by reads.flow.collectAsState()
    val keepAliveOn by keepAlive.flow.collectAsState()
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
}
