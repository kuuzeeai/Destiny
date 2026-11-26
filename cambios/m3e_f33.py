import os

E = "feature/session/src/main/kotlin/ai/opencode/feature/session/FishReplyEffect.kt"
S = "feature/session/src/main/kotlin/ai/opencode/feature/session/SessionSheetView.kt"
for f in (E, S):
    if not os.path.isfile(f):
        raise SystemExit("NO ENCUENTRO " + f + " (corre esto dentro de ~/Destiny)")

# 1) El chat habla la respuesta cuando el interruptor está encendido
t = open(E, encoding="utf-8").read()
old = "                    // Solo habla el asistente (botón de encendido), no el chat escrito.\n"
new = "                    // Habla solo si el interruptor 'Leer respuestas en voz alta' está encendido.\n                    FishSpeech.speakReply(reply.text)\n"
if "FishSpeech.speakReply(reply.text)" in t:
    print("  FishReplyEffect.kt ya estaba")
elif old not in t:
    raise SystemExit("NO ENCUENTRO el punto en FishReplyEffect.kt")
else:
    open(E, "w", encoding="utf-8").write(t.replace(old, new))
    print("  editado FishReplyEffect.kt")

# 2) Interruptor en el menú del título del chat
t = open(S, encoding="utf-8").read()
if "private fun ReadAloudCard" in t:
    print("  SessionSheetView.kt ya estaba")
else:
    call_old = "        TerminalCard(onTerminalTapped)\n"
    if call_old not in t:
        raise SystemExit("NO ENCUENTRO TerminalCard(onTerminalTapped)")
    t = t.replace(call_old, call_old + "        ReadAloudCard()\n", 1)

    card = '''// MARK: Read aloud

@Composable
private fun ReadAloudCard() {
    val reads = ai.opencode.core.sharing.shared(ai.opencode.core.sharing.SharedKeys.fishReadsReplies)
    val on by reads.flow.collectAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("session-sheet-read-aloud")
            .background(cardFill(), cardShape)
            .openCodeHairline(cardShape)
            .clip(cardShape)
            .toggleable(value = on, role = Role.Switch, onValueChange = {
                reads.set(it)
                if (!it) ai.opencode.feature.voice.FishSpeech.stop()
            })
            .padding(horizontal = 16.dp, vertical = if (isAccessibilitySize) 10.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Leer respuestas en voz alta", style = OpenCodeTextStyles.callout, color = OpenCodeTheme.colors.text, modifier = Modifier.weight(1f))
        OpenCodeSwitch(checked = on, onCheckedChange = null)
    }
}

'''
    marker = "// MARK: Always Notify\n"
    if marker not in t:
        raise SystemExit("NO ENCUENTRO MARK Always Notify")
    t = t.replace(marker, card + marker, 1)
    if "import androidx.compose.runtime.collectAsState" not in t:
        t = t.replace("import androidx.compose.runtime.getValue\n", "import androidx.compose.runtime.collectAsState\nimport androidx.compose.runtime.getValue\n", 1)
    open(S, "w", encoding="utf-8").write(t)
    print("  editado SessionSheetView.kt")
print("LISTO")
