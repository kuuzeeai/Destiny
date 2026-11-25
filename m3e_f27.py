import os, sys

SESSION = "feature/session/src/main/kotlin/ai/opencode/feature/session/"
F = SESSION + "SshTerminalSheet.kt"


def rd(p):
    if not os.path.exists(p):
        sys.exit("NO EXISTE: " + p + " (estas en ~/Destiny?)")
    return open(p, encoding="utf-8").read()


def wr(p, t):
    open(p, "w", encoding="utf-8").write(t)


def add_strings(rel, block):
    t = rd(rel)
    if "ssh_back_hint" in t:
        print("  ya estaba:", rel)
        return
    end = t.rfind("</resources>")
    if end < 0:
        sys.exit("NO ENCUENTRO </resources> en " + rel)
    wr(rel, t[:end] + block + t[end:])
    print("  editado:", rel)


add_strings("feature/session/src/main/res/values/strings.xml",
            '    <string name="ssh_back_hint">Press back again to exit</string>\n')
add_strings("feature/session/src/main/res/values-es/strings.xml",
            '    <string name="ssh_back_hint">Pulsa atrás otra vez para salir</string>\n')

text = rd(F)
if "ssh_back_hint" in text:
    print("  ya estaba:", F)
    print("LISTO")
    sys.exit(0)

IMPORTS = [
    "androidx.compose.ui.window.Dialog",
    "androidx.compose.ui.window.DialogProperties",
    "androidx.compose.material3.MaterialTheme",
    "androidx.compose.material3.OutlinedTextFieldDefaults",
    "androidx.compose.material3.ButtonDefaults",
    "androidx.compose.foundation.clickable",
    "androidx.compose.foundation.layout.height",
    "androidx.compose.foundation.layout.systemBarsPadding",
    "kotlinx.coroutines.delay",
    "android.os.SystemClock",
]
extra = "".join("import " + i + "\n" for i in IMPORTS if ("import " + i + "\n") not in text)
head = "package ai.opencode.feature.session\n\n"
if text.count(head) != 1:
    sys.exit("NO ENCUENTRO el package en " + F)
text = text.replace(head, head + extra, 1)

a1 = "@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun SshTerminalSheet"
a2 = "@Composable\nprivate fun SshHostPrompt"
a3 = "@Composable\nprivate fun ColumnScope.SshTerminalPane"
for a in (a1, a2, a3):
    if text.count(a) != 1:
        sys.exit("NO PUDE EDITAR: ancla no encontrada exactamente una vez:\n" + a)
i1, i2, i3 = text.index(a1), text.index(a2), text.index(a3)
if not (i1 < i2 < i3):
    sys.exit("Orden inesperado en el archivo")

SHEET = r'''@Composable
fun SshTerminalSheet(onClose: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val shell = remember { SshShell(context, SshStore(context), scope) }
    DisposableEffect(shell) { onDispose { shell.disconnect() } }
    LaunchedEffect(shell) { shell.autoConnect() }
    var lastBack by remember { mutableStateOf(0L) }
    var hint by remember { mutableStateOf(false) }
    LaunchedEffect(lastBack) {
        if (lastBack > 0L) {
            hint = true
            delay(2000)
            hint = false
        }
    }
    val connected = shell.phase == SshPhase.Connected && shell.hostPrompt == null
    val background = if (connected) Color(0xFF0D1117) else MaterialTheme.colorScheme.surface
    Dialog(
        onDismissRequest = {
            val now = SystemClock.elapsedRealtime()
            if (lastBack > 0L && now - lastBack < 2000L) onClose() else lastBack = now
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(Modifier.fillMaxSize().background(background)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding()
                    .padding(horizontal = 4.dp)
                    .padding(bottom = 4.dp),
            ) {
                if (!connected) {
                    Text(
                        stringResource(R.string.ssh_title),
                        style = OpenCodeTextStyles.callout.copy(fontWeight = FontWeight.SemiBold),
                        color = OpenCodeTheme.colors.text,
                        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 8.dp),
                    )
                }
                val prompt = shell.hostPrompt
                if (prompt != null) {
                    SshHostPrompt(prompt, onAnswer = shell::confirmTrust)
                } else when (shell.phase) {
                    SshPhase.Connected -> SshTerminalPane(shell)
                    SshPhase.Connecting -> Text(
                        stringResource(R.string.ssh_connecting),
                        color = OpenCodeTheme.colors.text,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    SshPhase.Form -> SshConnectForm(shell)
                }
            }
            if (hint) {
                Text(
                    stringResource(R.string.ssh_back_hint),
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .systemBarsPadding()
                        .padding(bottom = 150.dp)
                        .background(Color(0xE6000000), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

'''

PANE = r'''@Composable
private fun SshKeyButton(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(44.dp)
            .background(if (active) Color(0xFF30363D) else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color(0xFFC9D1D9), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ColumnScope.SshTerminalPane(shell: SshShell) {
    val listState = rememberLazyListState()
    var following by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) following = !listState.canScrollForward
        }
    }
    val total = shell.lines.size + 1
    LaunchedEffect(total, shell.current) {
        if (following) listState.scrollToItem(total - 1)
    }
    Box(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        SelectionContainer {
            LazyColumn(state = listState) {
                items(total) { index ->
                    val text = if (index < shell.lines.size) shell.lines[index] else shell.current
                    Text(
                        text.ifEmpty { " " },
                        color = Color(0xFFC9D1D9),
                        fontFamily = OpenCodeFonts.monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }
    var input by remember { mutableStateOf("") }
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    fun submit() {
        shell.send(input + "\n")
        input = ""
    }
    fun press(label: String) {
        when (label) {
            "ESC" -> shell.send("\u001B")
            "/" -> input += "/"
            "—" -> input += "-"
            "HOME" -> shell.send("\u001B[H")
            "END" -> shell.send("\u001B[F")
            "↑" -> shell.send("\u001B[A")
            "↓" -> shell.send("\u001B[B")
            "→" -> shell.send("\u001B[C")
            "←" -> shell.send("\u001B[D")
            "PGUP" -> shell.send("\u001B[5~")
            "PGDN" -> shell.send("\u001B[6~")
            "TAB" -> {
                shell.send(input + "\t")
                input = ""
            }
            "CTRL" -> ctrl = !ctrl
            "ALT" -> alt = !alt
        }
    }
    val light = Color(0xFFC9D1D9)
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { new ->
                if ((ctrl || alt) && new.length == input.length + 1 && new.startsWith(input)) {
                    val c = new.last()
                    if (ctrl) shell.send((c.uppercaseChar().code and 0x1f).toChar().toString())
                    else shell.send("\u001B" + c)
                    ctrl = false
                    alt = false
                } else {
                    input = new
                }
            },
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(fontFamily = OpenCodeFonts.monospace, fontSize = 14.sp, color = light),
            placeholder = { Text(stringResource(R.string.ssh_command_hint), color = Color(0xFF8B949E)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = light,
                unfocusedTextColor = light,
                focusedBorderColor = Color(0xFF8B949E),
                unfocusedBorderColor = Color(0xFF30363D),
                cursorColor = light,
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Send,
            ),
            keyboardActions = KeyboardActions(onSend = { submit() }),
        )
        TextButton(
            onClick = { submit() },
            colors = ButtonDefaults.textButtonColors(contentColor = light),
        ) { Text(stringResource(R.string.ssh_send)) }
    }
    val rows = listOf(
        listOf("ESC", "/", "—", "HOME", "↑", "END", "PGUP"),
        listOf("TAB", "CTRL", "ALT", "←", "↓", "→", "PGDN"),
    )
    for (row in rows) {
        Row(Modifier.fillMaxWidth()) {
            for (label in row) {
                SshKeyButton(
                    label = label,
                    active = (label == "CTRL" && ctrl) || (label == "ALT" && alt),
                    modifier = Modifier.weight(1f),
                    onClick = { press(label) },
                )
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(
            onClick = { shell.send("\u0003") },
            colors = ButtonDefaults.textButtonColors(contentColor = light),
        ) { Text(stringResource(R.string.ssh_ctrl_c)) }
        TextButton(
            onClick = { shell.clear() },
            colors = ButtonDefaults.textButtonColors(contentColor = light),
        ) { Text(stringResource(R.string.ssh_clear)) }
        TextButton(
            onClick = { shell.leave() },
            colors = ButtonDefaults.textButtonColors(contentColor = light),
        ) { Text(stringResource(R.string.ssh_disconnect)) }
    }
}
'''

text = text[:i1] + SHEET + text[i2:i3] + PANE
wr(F, text)
print("  editado:", F)
print("LISTO")
