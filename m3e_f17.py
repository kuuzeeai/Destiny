import sys, pathlib
import re
def balanced(t):
    t = re.sub(r'"(?:\\.|[^"\\])*"', '""', t)
    t = re.sub(r"'(?:\\.|[^'\\])'", "''", t)
    return t.count("{") == t.count("}") and t.count("(") == t.count(")")
S = pathlib.Path("feature/settings/src/main")
VV = S/"kotlin/ai/opencode/feature/settings/VoiceSettingsView.kt"
FS = S/"kotlin/ai/opencode/feature/settings/FishAudioSettings.kt"
EN = S/"res/values/strings.xml"
ES = S/"res/values-es/strings.xml"

FS_KT = '''package ai.opencode.feature.settings

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
    val readsOn by reads.flow.collectAsState()
    val keyText by key.flow.collectAsState()
    val voiceText by voice.flow.collectAsState()
    // The key is a credential.
    HidesFromScreenshots()
    SettingsToggleRow(
        stringResource(R.string.settings_fish_read_replies),
        checked = readsOn,
        onCheckedChange = { reads.set(it) },
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
        FilledTonalButton(
            onClick = { FishSpeech.test() },
            enabled = !keyText.isNullOrBlank(),
            shape = OpenCodeShape.control,
        ) { Text(stringResource(R.string.settings_fish_test)) }
    }
}
'''
STR_EN = ('    <string name="settings_fish_header">Fish Audio</string>\n'
 '    <string name="settings_fish_read_replies">Read replies aloud</string>\n'
 '    <string name="settings_fish_key_placeholder">Fish Audio API key</string>\n'
 '    <string name="settings_fish_voice_placeholder">Voice ID (optional)</string>\n'
 '    <string name="settings_fish_test">Test voice</string>\n'
 '    <string name="settings_fish_footer">Dictate with the mic in the composer and the reply is read in your Fish Audio voice. The key stays on this phone.</string>\n'
 '</resources>')
STR_ES = ('    <string name="settings_fish_header">Fish Audio</string>\n'
 '    <string name="settings_fish_read_replies">Leer respuestas en voz alta</string>\n'
 '    <string name="settings_fish_key_placeholder">Clave API de Fish Audio</string>\n'
 '    <string name="settings_fish_voice_placeholder">ID de voz (opcional)</string>\n'
 '    <string name="settings_fish_test">Probar voz</string>\n'
 '    <string name="settings_fish_footer">Dicta con el micrófono del compositor y la respuesta se lee con tu voz de Fish Audio. La clave se queda en este teléfono.</string>\n'
 '</resources>')
FISH_SECTION = ('        settingsSection(key = "fish", header = fishHeader, footer = footerText(fishFooter)) {\n'
 '            FishAudioRows()\n'
 '        }\n'
 '        settingsSection(key = "options", header = callsHeader) {')

EDITS = {
 VV: [
  ("    val raiseToTalkHint = stringResource(R.string.settings_voice_raise_to_talk_hint)\n",
   "    val raiseToTalkHint = stringResource(R.string.settings_voice_raise_to_talk_hint)\n    val fishHeader = stringResource(R.string.settings_fish_header)\n    val fishFooter = stringResource(R.string.settings_fish_footer)\n"),
  ('        settingsSection(key = "options", header = callsHeader) {', FISH_SECTION),
 ],
 EN: [("</resources>", STR_EN)],
 ES: [("</resources>", STR_ES)],
}
out = {}
for path, edits in EDITS.items():
    if not path.exists(): sys.exit(f"NO EXISTE {path}")
    t = path.read_text()
    for old, new in edits:
        n = t.count(old)
        if n != 1: sys.exit(f"ABORTO: {path.name}: {n} coincidencias para: {old[:70]!r}")
        t = t.replace(old, new)
    if path.suffix == ".kt" and not balanced(t):
        sys.exit(f"ABORTO: balance roto en {path.name}")
    out[path] = t
if FS.exists(): sys.exit(f"YA EXISTE {FS}")
if not balanced(FS_KT):
    sys.exit("ABORTO: balance roto en FishAudioSettings.kt")
out[FS] = FS_KT
for p, t in out.items(): p.write_text(t)
print("OK f17:", ", ".join(p.name for p in out))
