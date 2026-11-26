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
import kotlin.math.roundToInt
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.foundation.layout.ColumnScope
import ai.opencode.core.ui.SFSymbol
import ai.opencode.core.ui.OpenCodeIcon
import ai.opencode.core.sharing.Shared
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

/** Un campo con su título arriba. */
@Composable
private fun LabeledField(
    title: String,
    secret: Boolean,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = SettingsText.summary, color = OpenCodeTheme.colors.secondaryText, modifier = Modifier.padding(horizontal = 4.dp))
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

/** Una fila que se abre y se cierra; cerrada solo dice cómo está. */
@Composable
private fun ExpandableRow(title: String, summary: String, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(false) }
    SettingsRowFrame(onClick = { open = !open }) {
        SettingsRowText(title, summary, Modifier.weight(1f))
        OpenCodeIcon(
            if (open) SFSymbol.ChevronDown else SFSymbol.ChevronRight,
            contentDescription = null,
            size = 16.dp,
            tint = OpenCodeTheme.colors.secondaryText,
        )
    }
    if (open) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

private fun hourLabel(hour: Int): String {
    val h = if (hour % 12 == 0) 12 else hour % 12
    return "$h:00 " + if (hour < 12) "a. m." else "p. m."
}

/** Volumen de un horario: la hora en que empieza, la barra y un botón para oírlo. */
@Composable
private fun VolumeBlock(title: String, volume: Shared<Int>, start: Shared<Int>, canTest: Boolean) {
    val saved by volume.flow.collectAsState()
    val from by start.flow.collectAsState()
    var shown by remember(saved) { mutableStateOf(saved.toFloat()) }
    SettingsMenuRow(
        title = "$title empieza",
        value = hourLabel(from),
        selection = from,
        options = (0..23).map { SettingsMenuOption(it, hourLabel(it)) },
        onSelect = { start.set(it) },
    )
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Volumen: ${shown.roundToInt()} %",
                style = SettingsText.summary,
                color = OpenCodeTheme.colors.secondaryText,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { FishSpeech.test(shown.roundToInt()) }, enabled = canTest) { Text("Probar") }
        }
        Slider(
            value = shown,
            onValueChange = { shown = it },
            onValueChangeFinished = { volume.set(shown.roundToInt()) },
            valueRange = 5f..100f,
        )
    }
}

/** Ajustes › Voz › Respuestas del chat: leerlas o no, y con qué volumen de día y de noche. */
@Composable
internal fun ReplyRows() {
    val reads = shared(SharedKeys.fishReadsReplies)
    val readsOn by reads.flow.collectAsState()
    val keyText by shared(SharedKeys.fishAPIKey).flow.collectAsState()
    SettingsToggleRow(
        stringResource(R.string.settings_fish_read_replies),
        checked = readsOn,
        onCheckedChange = {
            reads.set(it)
            if (!it) FishSpeech.stop()
        },
    )
    if (!readsOn) return
    SettingsDivider()
    VolumeBlock("Día", shared(SharedKeys.fishDayVolume), shared(SharedKeys.fishDayStart), !keyText.isNullOrBlank())
    SettingsDivider()
    VolumeBlock("Noche", shared(SharedKeys.fishNightVolume), shared(SharedKeys.fishNightStart), !keyText.isNullOrBlank())
    SettingsFooterText(
        "El volumen cambia solo según la hora y es relativo al volumen multimedia del teléfono. No afecta al asistente del micrófono.",
        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
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
                rows.count { it.isNotBlank() }.toString() + if (open) "  ▾" else "  ▸",
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

/** Ajustes › Voz › Claves: Fish (habla) y Groq (te entiende), cerradas hasta que se tocan. */
@Composable
internal fun KeyRows() {
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
    ExpandableRow("Fish Audio (la voz que habla)", if (keyText.isNullOrBlank()) "Falta la clave" else "Clave guardada") {
        LabeledField("Clave", true, keyText.orEmpty(), stringResource(R.string.settings_fish_key_placeholder)) {
            key.set(it.trim().ifEmpty { null })
        }
        KeyListSection("Claves de respaldo", extraText) { extra.set(it) }
        LabeledField("ID de la voz", false, voiceText, "Pega aquí el ID de la voz") { voice.set(it.trim()) }
        FilledTonalButton(
            onClick = { FishSpeech.test() },
            enabled = !keyText.isNullOrBlank(),
            shape = OpenCodeShape.control,
        ) { Text(stringResource(R.string.settings_fish_test)) }
    }
    SettingsDivider()
    ExpandableRow("Groq (lo que te entiende)", if (groqText.isNullOrBlank()) "Falta la clave" else "Clave guardada") {
        LabeledField("Clave", true, groqText.orEmpty(), "Pega aquí la clave de Groq") {
            groq.set(it.trim().ifEmpty { null })
        }
        KeyListSection("Claves de respaldo", groqExtraText) { groqExtra.set(it) }
    }
}

/** Ajustes › Voz › Asistente: que siga listo con la app cerrada y mostrar solo la isla. */
@Composable
internal fun BehaviorRows() {
    val keepAlive = shared(SharedKeys.assistKeepAlive)
    val islandOnly = shared(SharedKeys.assistIslandOnly)
    val keepAliveOn by keepAlive.flow.collectAsState()
    val islandOnlyOn by islandOnly.flow.collectAsState()
    SettingsToggleRow("Funcionar con la app cerrada", checked = keepAliveOn, onCheckedChange = { keepAlive.set(it) })
    SettingsDivider()
    SettingsToggleRow("Mostrar solo la isla", checked = islandOnlyOn, onCheckedChange = { islandOnly.set(it) })
}
