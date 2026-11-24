package ai.opencode.feature.settings

import ai.opencode.core.diagnostics.HidesFromScreenshots
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

/** Settings › Voice › Fish Audio: read replies aloud, the key, the voice ID and a test. */
@Composable
internal fun FishAudioRows() {
    val reads = shared(SharedKeys.fishReadsReplies)
    val key = shared(SharedKeys.fishAPIKey)
    val voice = shared(SharedKeys.fishVoiceID)
    val keepAlive = shared(SharedKeys.assistKeepAlive)
    val keepAliveOn by keepAlive.flow.collectAsState()
    val readsOn by reads.flow.collectAsState()
    val keyText by key.flow.collectAsState()
    val voiceText by voice.flow.collectAsState()
    val groq = shared(SharedKeys.groqAPIKey)
    val groqText by groq.flow.collectAsState()
    // The key is a credential.
    HidesFromScreenshots()
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
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            placeholder = stringResource(R.string.settings_fish_voice_placeholder),
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        OpenCodeTextField(
            value = groqText.orEmpty(),
            onValueChange = { groq.set(it.trim().ifEmpty { null }) },
            placeholder = "Clave de Groq (para entender tu voz)",
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        FilledTonalButton(
            onClick = { FishSpeech.test() },
            enabled = !keyText.isNullOrBlank(),
            shape = OpenCodeShape.control,
        ) { Text(stringResource(R.string.settings_fish_test)) }
    }
}
