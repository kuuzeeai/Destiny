import os, sys

F = "feature/session/src/main/kotlin/ai/opencode/feature/session/SshTerminalSheet.kt"
if not os.path.exists(F):
    sys.exit("NO EXISTE: " + F + " (estas en ~/Destiny?)")
text = open(F, encoding="utf-8").read()
if "SSH_SENTINEL" in text:
    print("  ya estaba")
    print("LISTO")
    sys.exit(0)


def once(old, new):
    global text
    n = text.count(old)
    if n != 1:
        sys.exit("NO PUDE EDITAR: ancla encontrada " + str(n) + " veces:\n" + old[:120])
    text = text.replace(old, new, 1)


IMPORTS = [
    "androidx.compose.foundation.layout.BoxWithConstraints",
    "androidx.compose.foundation.layout.size",
    "androidx.compose.foundation.text.BasicTextField",
    "androidx.compose.foundation.interaction.MutableInteractionSource",
    "androidx.compose.foundation.isSystemInDarkTheme",
    "androidx.compose.runtime.SideEffect",
    "androidx.compose.ui.focus.FocusRequester",
    "androidx.compose.ui.focus.focusRequester",
    "androidx.compose.ui.platform.LocalSoftwareKeyboardController",
    "androidx.compose.ui.platform.LocalDensity",
    "androidx.compose.ui.platform.LocalView",
    "androidx.compose.ui.text.AnnotatedString",
    "androidx.compose.ui.text.SpanStyle",
    "androidx.compose.ui.text.buildAnnotatedString",
    "androidx.compose.ui.text.TextRange",
    "androidx.compose.ui.text.font.FontFamily",
    "androidx.compose.ui.text.input.TextFieldValue",
    "androidx.compose.ui.graphics.SolidColor",
    "androidx.compose.ui.window.DialogWindowProvider",
    "android.os.Build",
    "android.view.WindowInsetsController",
]
extra = "".join("import " + i + "\n" for i in IMPORTS if ("import " + i + "\n") not in text)
once("package ai.opencode.feature.session\n\n", "package ai.opencode.feature.session\n\n" + extra)

TOP = r'''
private val SSH_FG = 0xFFFFFFFF.toInt()
private val SSH_ANSI = intArrayOf(
    0xFF000000.toInt(), 0xFFCD0000.toInt(), 0xFF00CD00.toInt(), 0xFFCDCD00.toInt(),
    0xFF6495ED.toInt(), 0xFFCD00CD.toInt(), 0xFF00CDCD.toInt(), 0xFFE5E5E5.toInt(),
    0xFF7F7F7F.toInt(), 0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFFFFFF00.toInt(),
    0xFF5C5CFF.toInt(), 0xFFFF00FF.toInt(), 0xFF00FFFF.toInt(), 0xFFFFFFFF.toInt(),
)
private val sshCsiParts = Regex("\u001B\\[([0-9;?<=>]*)([ -/]*)([@-~])")

private fun ssh256(value: Int): Int {
    val i = value.coerceIn(0, 255)
    if (i < 16) return SSH_ANSI[i]
    if (i < 232) {
        val v = i - 16
        val r = v / 36
        val g = (v / 6) % 6
        val b = v % 6
        val lr = if (r == 0) 0 else 55 + r * 40
        val lg = if (g == 0) 0 else 55 + g * 40
        val lb = if (b == 0) 0 else 55 + b * 40
        return (0xFF shl 24) or (lr shl 16) or (lg shl 8) or lb
    }
    val gray = 8 + (i - 232) * 10
    return (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
}

private fun styled(sb: CharSequence, colors: List<Int>, cursor: Int): AnnotatedString {
    val length = maxOf(sb.length, cursor + 1)
    if (length <= 0) return AnnotatedString("")
    return buildAnnotatedString {
        var start = 0
        while (start < length) {
            if (start == cursor) {
                pushStyle(SpanStyle(color = Color.Black, background = Color.White))
                append(if (start < sb.length) sb[start].toString() else " ")
                pop()
                start++
                continue
            }
            val color = if (start < colors.size) colors[start] else SSH_FG
            var end = start + 1
            while (end < length && end != cursor && (if (end < colors.size) colors[end] else SSH_FG) == color) end++
            pushStyle(SpanStyle(color = Color(color)))
            append(buildString { for (k in start until end) append(if (k < sb.length) sb[k] else ' ') })
            pop()
            start = end
        }
    }
}
'''
once('private val sshIncomplete = Regex("\\u001B(\\\\[[0-9;?]*[ -/]*|\\\\][^\\u0007\\u001B]*)?$")\n',
     'private val sshIncomplete = Regex("\\u001B(\\\\[[0-9;?]*[ -/]*|\\\\][^\\u0007\\u001B]*)?$")\n' + TOP)

once('    val lines = mutableStateListOf<String>()\n    var current by mutableStateOf("")\n',
     '    val lines = mutableStateListOf<AnnotatedString>()\n    var current by mutableStateOf(AnnotatedString(""))\n')
once('    private val edit = StringBuilder()\n    private var cursor = 0\n',
     '    private val edit = StringBuilder()\n    private val editColors = ArrayList<Int>()\n    private var curColor = SSH_FG\n    private var cursor = 0\n')
once('shell.setPtyType("dumb", 80, 24, 0, 0)', 'shell.setPtyType("xterm-256color", 80, 24, 0, 0)')
once('            lines.clear()\n            edit.setLength(0)\n            cursor = 0\n            current = ""\n            if (note != null) lines.add(note)\n',
     '            lines.clear()\n            edit.setLength(0)\n            editColors.clear()\n            curColor = SSH_FG\n            cursor = 0\n            current = AnnotatedString("")\n            if (note != null) lines.add(AnnotatedString(note))\n')

a = text.index("    fun feed(chunk: String) {\n")
b = text.index("    fun send(text: String) {\n")
if not a < b:
    sys.exit("Orden inesperado")
FEED = r'''    fun feed(chunk: String) {
        var text = (pendingEscape + chunk).replace(sshOsc, "").replace(sshCharset, "")
        val tail = sshIncomplete.find(text)
        if (tail != null) {
            pendingEscape = tail.value.take(256)
            text = text.substring(0, tail.range.first)
        } else {
            pendingEscape = ""
        }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\u001B') {
                val m = sshCsiParts.matchAt(text, i)
                if (m != null) {
                    csi(m.groupValues[1], m.groupValues[3])
                    i = m.range.last + 1
                } else {
                    i++
                }
                continue
            }
            when (c) {
                '\n' -> {
                    lines.add(styled(edit, editColors, -1))
                    edit.setLength(0)
                    editColors.clear()
                    cursor = 0
                }
                '\r' -> cursor = 0
                '\b' -> if (cursor > 0) cursor--
                '\t' -> repeat(8 - cursor % 8) { put(' ') }
                else -> if (c >= ' ') put(c)
            }
            i++
        }
        if (lines.size > SSH_MAX_LINES) lines.subList(0, 500).clear()
        current = styled(edit, editColors, cursor)
    }

    private fun csi(params: String, fin: Char) {
        val nums = params.trimStart('?', '<', '=', '>').split(';').map { it.toIntOrNull() }
        val first = nums.firstOrNull()
        val count = (first ?: 1).coerceAtLeast(1)
        when (fin) {
            'm' -> sgr(nums.map { it ?: 0 })
            'K' -> when (first ?: 0) {
                0 -> if (cursor < edit.length) {
                    edit.setLength(cursor)
                    while (editColors.size > cursor) editColors.removeAt(editColors.size - 1)
                }
                1 -> for (k in 0 until minOf(cursor + 1, edit.length)) edit.setCharAt(k, ' ')
                else -> {
                    edit.setLength(0)
                    editColors.clear()
                }
            }
            'C' -> cursor += count
            'D' -> cursor = maxOf(0, cursor - count)
            'G' -> cursor = count - 1
            'P' -> {
                val end = minOf(edit.length, cursor + count)
                if (cursor < end) {
                    edit.delete(cursor, end)
                    repeat(end - cursor) { editColors.removeAt(cursor) }
                }
            }
            '@' -> {
                pad(cursor)
                edit.insert(cursor, " ".repeat(count))
                repeat(count) { editColors.add(cursor, curColor) }
            }
        }
    }

    private fun sgr(codes: List<Int>) {
        val list = if (codes.isEmpty()) listOf(0) else codes
        var i = 0
        while (i < list.size) {
            val code = list[i]
            when {
                code == 0 || code == 39 -> curColor = SSH_FG
                code in 30..37 -> curColor = SSH_ANSI[code - 30]
                code in 90..97 -> curColor = SSH_ANSI[code - 90 + 8]
                code == 38 || code == 48 -> {
                    val mode = list.getOrNull(i + 1)
                    if (mode == 5) {
                        if (code == 38) curColor = ssh256(list.getOrNull(i + 2) ?: 0)
                        i += 2
                    } else if (mode == 2) {
                        val r = (list.getOrNull(i + 2) ?: 0).coerceIn(0, 255)
                        val g = (list.getOrNull(i + 3) ?: 0).coerceIn(0, 255)
                        val b = (list.getOrNull(i + 4) ?: 0).coerceIn(0, 255)
                        if (code == 38) curColor = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        i += 4
                    }
                }
            }
            i++
        }
    }

    private fun pad(pos: Int) {
        while (edit.length < pos) {
            edit.append(' ')
            editColors.add(curColor)
        }
    }

    private fun put(c: Char) {
        pad(cursor)
        if (cursor < edit.length) {
            edit.setCharAt(cursor, c)
            editColors[cursor] = curColor
        } else {
            edit.append(c)
            editColors.add(curColor)
        }
        cursor++
    }

    fun resize(cols: Int, rows: Int) {
        val ch = channel ?: return
        runCatching { writer.execute { runCatching { ch.setPtySize(cols, rows, 0, 0) } } }
    }

'''
text = text[:a] + FEED + text[b:]

once('    val background = if (connected) Color(0xFF0D1117) else MaterialTheme.colorScheme.surface\n',
     '''    val background = if (connected) Color.Black else MaterialTheme.colorScheme.surface
    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null && Build.VERSION.SDK_INT >= 30) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (connected || darkTheme) 0 else mask, mask)
        }
    }
''')
once('                    .padding(horizontal = 4.dp)\n                    .padding(bottom = 4.dp),\n',
     '                    .padding(horizontal = if (connected) 0.dp else 4.dp)\n                    .padding(bottom = if (connected) 0.dp else 4.dp),\n')

k = text.index("@Composable\nprivate fun SshKeyButton")
PANE = r'''private const val SSH_SENTINEL = " "

@Composable
private fun SshKeyButton(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(42.dp)
            .background(if (active) Color(0xFF606060) else Color.Black)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ColumnScope.SshTerminalPane(shell: SshShell) {
    val density = LocalDensity.current
    val charPx = 18.3f
    val linePx = 36f
    val fontSize = with(density) { (charPx / 0.6f).toSp() }
    val lineHeight = with(density) { linePx.toSp() }
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
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(300)
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    var typed by remember { mutableStateOf(TextFieldValue(SSH_SENTINEL, TextRange(1))) }
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    fun typeText(s: String) {
        following = true
        if (s.isEmpty()) return
        val first = s[0]
        if (ctrl) {
            shell.send((first.uppercaseChar().code and 0x1f).toChar().toString())
            if (s.length > 1) shell.send(s.substring(1))
        } else if (alt) {
            shell.send("\u001B" + first)
            if (s.length > 1) shell.send(s.substring(1))
        } else {
            shell.send(s)
        }
        ctrl = false
        alt = false
    }
    fun press(label: String) {
        following = true
        when (label) {
            "ESC" -> shell.send("\u001B")
            "/" -> typeText("/")
            "—" -> typeText("-")
            "HOME" -> shell.send("\u001B[H")
            "END" -> shell.send("\u001B[F")
            "↑" -> shell.send("\u001B[A")
            "↓" -> shell.send("\u001B[B")
            "→" -> shell.send("\u001B[C")
            "←" -> shell.send("\u001B[D")
            "PGUP" -> shell.send("\u001B[5~")
            "PGDN" -> shell.send("\u001B[6~")
            "⇆" -> shell.send("\t")
            "CTRL" -> ctrl = !ctrl
            "ALT" -> alt = !alt
        }
    }
    BoxWithConstraints(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                runCatching { focus.requestFocus() }
                keyboard?.show()
            },
    ) {
        val cols = (constraints.maxWidth / charPx).toInt().coerceAtLeast(20)
        val screenRows = (constraints.maxHeight / linePx).toInt().coerceAtLeast(5)
        LaunchedEffect(cols, screenRows) { shell.resize(cols, screenRows) }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(total) { index ->
                val line = if (index < shell.lines.size) shell.lines[index] else shell.current
                if (line.length <= cols) {
                    SshRow(if (line.isEmpty()) AnnotatedString(" ") else line, fontSize, lineHeight)
                } else {
                    Column {
                        var s = 0
                        while (s < line.length) {
                            SshRow(line.subSequence(s, minOf(line.length, s + cols)), fontSize, lineHeight)
                            s += cols
                        }
                    }
                }
            }
        }
        BasicTextField(
            value = typed,
            onValueChange = { v ->
                val t = v.text
                if (t.isEmpty()) {
                    following = true
                    shell.send("\u007F")
                } else if (t != SSH_SENTINEL) {
                    val at = t.indexOf(' ')
                    typeText(if (at >= 0) t.removeRange(at, at + 1) else t)
                }
                typed = TextFieldValue(SSH_SENTINEL, TextRange(1))
            },
            modifier = Modifier.size(1.dp).focusRequester(focus),
            singleLine = true,
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.VisiblePassword,
                imeAction = ImeAction.Send,
            ),
            keyboardActions = KeyboardActions(onSend = { shell.send("\r") }),
        )
    }
    val keyRows = listOf(
        listOf("ESC", "/", "—", "HOME", "↑", "END", "PGUP"),
        listOf("⇆", "CTRL", "ALT", "←", "↓", "→", "PGDN"),
    )
    for (keys in keyRows) {
        Row(Modifier.fillMaxWidth()) {
            for (label in keys) {
                SshKeyButton(
                    label = label,
                    active = (label == "CTRL" && ctrl) || (label == "ALT" && alt),
                    modifier = Modifier.weight(1f),
                    onClick = { press(label) },
                )
            }
        }
    }
}

@Composable
private fun SshRow(text: AnnotatedString, fontSize: androidx.compose.ui.unit.TextUnit, lineHeight: androidx.compose.ui.unit.TextUnit) {
    Text(
        text,
        color = Color.White,
        fontFamily = FontFamily.Monospace,
        fontSize = fontSize,
        lineHeight = lineHeight,
        softWrap = false,
        maxLines = 1,
    )
}
'''
text = text[:k] + PANE
open(F, "w", encoding="utf-8").write(text)
print("  editado")
print("LISTO")
