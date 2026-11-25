# Parte 2: bloque de herramientas tipo Claude + infinito en "Trabajando".
# Ejecutar en la raiz del repo DESPUES de la parte 1:  python3 m3e_f18b.py
import pathlib, sys

R = pathlib.Path("feature/transcript/src/main/kotlin/ai/opencode/feature/transcript/rows")
TR = R/"ToolRows.kt"
IR = R/"InlineRows.kt"
ST = pathlib.Path("feature/session/src/main/kotlin/ai/opencode/feature/session/SessionTranscriptView.kt")
for f in (TR, IR, ST):
    if not f.exists():
        sys.exit(f"No encuentro {f}. Ejecuta esto en la raiz del repo.")
if not (R/"InfinityLoader.kt").exists():
    sys.exit("Falta InfinityLoader.kt: ejecuta primero python3 m3e_f18a.py")

def once(t, old, new, label):
    n = t.count(old)
    if n != 1:
        sys.exit(f"[{label}] se esperaba 1 coincidencia y hay {n}. No se cambio nada.")
    return t.replace(old, new)

def region(t, start, end, new, label):
    if t.count(start) != 1 or t.count(end) != 1:
        sys.exit(f"[{label}] no encuentro los limites. No se cambio nada.")
    i, j = t.find(start), t.find(end)
    if j <= i:
        sys.exit(f"[{label}] limites en desorden. No se cambio nada.")
    return t[:i] + new + t[j:]

def deltas(t):
    import re
    t = re.sub(r'"(?:\\.|[^"\\])*"', '""', t)
    t = re.sub(r"'(?:\\.|[^'\\])'", "''", t)
    return (t.count("{") - t.count("}"), t.count("(") - t.count(")"))

# ---------------- ToolRows.kt ----------------
t = TR.read_text(encoding="utf-8")
t0 = deltas(t)

t = once(t, "import androidx.compose.foundation.shape.RoundedCornerShape\n",
         "import androidx.compose.foundation.shape.CircleShape\nimport androidx.compose.foundation.shape.RoundedCornerShape\n", "imp-circle")
t = once(t, "import androidx.compose.ui.draw.clip\n",
         "import androidx.compose.ui.draw.clip\nimport androidx.compose.ui.draw.drawBehind\nimport androidx.compose.ui.draw.drawWithContent\nimport androidx.compose.ui.geometry.Offset\n", "imp-draw")
t = once(t, "import androidx.compose.ui.graphics.graphicsLayer\n",
         "import androidx.compose.ui.graphics.drawscope.clipRect\nimport androidx.compose.ui.graphics.graphicsLayer\n", "imp-cliprect")
t = once(t, "import ai.opencode.feature.transcript.markdown.ui.DisclosedContent\n",
         "import ai.opencode.feature.transcript.markdown.ui.DisclosedContent\nimport ai.opencode.feature.transcript.markdown.ui.LocalBandReveal\n", "imp-reveal")

# Resumen mas chico y gris
t = once(t, 'val style = TranscriptFonts.metaFont().copy(fontFeatureSettings = "tnum")',
         'val style = OpenCodeTextStyles.subheadline.copy(fontFeatureSettings = "tnum")', "header-style")

# Grupo abierto: sin tarjeta gris, pasos con linea conectora
t = once(t, """        DisclosedBand(isExpanded) {
            Column {
                for (row in rows) InlineToolRowView(entry, row, subagents)
            }
        }""", """        DisclosedPlain(isExpanded) {
            Column {
                for ((index, row) in rows.withIndex()) {
                    InlineToolRowView(entry, row, subagents, isFirst = index == 0, isLast = index == rows.lastIndex)
                }
            }
        }""", "group-body")

t = region(t, "@Composable\nprivate fun InlineToolRowView(", "@Composable\nprivate fun InlineRowDisclosure(", r"""@Composable
private fun InlineToolRowView(
    entry: TranscriptEntry,
    row: InlineToolRow,
    subagents: Map<String, TranscriptSubagent>,
    isFirst: Boolean,
    isLast: Boolean,
) {
    val tint = if (row.isFailed) OpenCodeTheme.colors.danger else OpenCodeTheme.colors.tertiaryText
    when (val kind = row.kind) {
        // The subagent card stays a card, on the line.
        is InlineToolRow.Kind.Tool if kind.call.isSubagent() ->
            TimelineStep(isFirst, isLast, null, tint) {
                SubagentToolView(kind.call, subagents, Modifier.padding(vertical = InlineRowMetrics.rowPadding))
            }
        is InlineToolRow.Kind.Notice ->
            TimelineStep(isFirst, isLast, toolGlyph(row), tint) {
                InlineRowLabel(row, Modifier.semantics(mergeDescendants = true) {})
            }
        is InlineToolRow.Kind.Tool ->
            TimelineStep(isFirst, isLast, toolGlyph(row), tint) {
                InlineRowDisclosure(entry, row, listOf(row.id to kind.call))
            }
        is InlineToolRow.Kind.Reads ->
            TimelineStep(isFirst, isLast, toolGlyph(row), tint) {
                InlineRowDisclosure(entry, row, row.memberIDs.zip(kind.calls))
            }
    }
}

""", "row-view")

# El comando ya no va en la fila: se ve completo al abrir el paso
t = once(t, "val headerDetail = if (bodies.size == 1 && detailFits) row.detail else null",
         "val headerDetail: String? = null", "header-detail")

t = region(t, "/**\n * The desktop's tool row (`basic-tool.css`)", "/** The tool's glyph, by name", r"""/**
 * One step of the opened group: its icon sits on a thin vertical line that joins the steps, and
 * what it shows (title, then the opened command and output) hangs to the right.
 */
@Composable
private fun TimelineStep(
    isFirst: Boolean,
    isLast: Boolean,
    glyph: String?,
    tint: Color,
    content: @Composable () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val lineHeight = with(LocalDensity.current) { OpenCodeTextStyles.footnote.lineHeight.toDp() }
    val centreY = InlineRowMetrics.rowPadding + lineHeight / 2
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val x = 10.dp.toPx()
                val cy = centreY.toPx()
                val gap = 11.dp.toPx()
                val stroke = 1.dp.toPx()
                if (!isFirst) drawLine(colors.border, Offset(x, 0f), Offset(x, cy - gap), strokeWidth = stroke)
                if (!isLast) drawLine(colors.border, Offset(x, cy + gap), Offset(x, size.height), strokeWidth = stroke)
            },
    ) {
        Box(
            Modifier.padding(top = InlineRowMetrics.rowPadding).width(20.dp).height(lineHeight),
            contentAlignment = Alignment.Center,
        ) {
            if (glyph != null) {
                OpenCodeIcon(glyph, contentDescription = null, size = 14.dp, tint = tint)
            } else {
                Box(Modifier.size(5.dp).clip(CircleShape).background(colors.tertiaryText))
            }
        }
        Column(Modifier.padding(start = 30.dp)) { content() }
    }
}

/** A step's line: a short plain-language title, small and muted; only failures are marked, in red. */
@Composable
private fun InlineRowLabel(row: InlineToolRow, modifier: Modifier = Modifier, onDetailFits: (Boolean) -> Unit = {}) {
    val style = OpenCodeTextStyles.footnote
    Row(modifier.fillMaxWidth().padding(vertical = InlineRowMetrics.rowPadding)) {
        InlineRowText(row, style, onDetailFits)
    }
}

""", "row-label")

t = region(t, "@Composable\nprivate fun RowScope.InlineRowText(", "/** iOS `foldedOutputLines`: long output folds after a screenful. */", r"""@Composable
private fun RowScope.InlineRowText(row: InlineToolRow, style: TextStyle, onDetailFits: (Boolean) -> Unit) {
    val colors = OpenCodeTheme.colors
    Text(
        row.title,
        Modifier.weight(1f, fill = false).inlineRowPulse(row.isRunning),
        style = style,
        color = if (row.isFailed) colors.danger else colors.secondaryText,
        maxLines = if (isAccessibilityTextSize()) Int.MAX_VALUE else 1,
        overflow = TextOverflow.Ellipsis,
    )
    // Denying is an answer, not a failure.
    if (row.isDenied) {
        Text(" · " + stringResource(R.string.transcript_tool_denied), style = style, color = colors.secondaryText, maxLines = 1)
    }
}

""", "row-text")

# Un solo recuadro para el detalle
t = once(t, """            .clip(shape)
            // The page's own fill, so the box stands off the opened group's faint band around it
            // (on the band's fill it nearly vanished in light).
            .background(colors.canvas)
            .border(0.5.dp, colors.border, shape),""", """            .clip(shape)
            .background(colors.userBubble),""", "body-box")
t = once(t, "                    .background(colors.userBubble.copy(alpha = colors.userBubble.alpha * 0.6f))\n", "", "body-output-bg")

t = t.rstrip("\n") + "\n" + r"""
/**
 * An opened group's body with no card around it: it grows open and closed on the row's own
 * disclosure spring like [DisclosedBand], and its content fades and rises in behind the edge.
 */
@Composable
private fun DisclosedPlain(isOpen: Boolean, content: @Composable () -> Unit) {
    BandClock(isOpen, Modifier.fillMaxWidth()) {
        val reveal = LocalBandReveal.current
        val reduceMotion = rememberReduceMotion()
        val rise = with(LocalDensity.current) { 6.dp.toPx() }
        Box(
            Modifier
                .fillMaxWidth()
                .drawWithContent {
                    val r = reveal()
                    val grow = if (reduceMotion) 1f else r
                    clipRect(bottom = size.height * grow) { this@drawWithContent.drawContent() }
                }
                .graphicsLayer {
                    val r = reveal()
                    val lag = if (reduceMotion) r else ((r - 0.25f) / 0.75f).coerceIn(0f, 1f)
                    alpha = lag
                    translationY = if (reduceMotion) 0f else (1 - lag) * rise
                },
        ) { content() }
    }
}
"""
if deltas(t) != t0:
    sys.exit("ToolRows.kt quedo con llaves/parentesis desbalanceados. No se cambio nada.")

# ---------------- InlineRows.kt ----------------
u = IR.read_text(encoding="utf-8")
u0 = deltas(u)
u = once(u, "    /** Only commands are mono. */\n", r"""    /** A short plain-language title (“Reading build.gradle”), shown instead of the raw command. */
    val title: String
        get() = when (kind) {
            is Kind.Tool -> stepTitle(kind.call)
            is Kind.Reads -> localizedPlural(R.plurals.transcript_step_reading_files, kind.calls.size, kind.calls.size)
            is Kind.Notice -> name
        }

    /** Only commands are mono. */
""", "title-prop")
u = once(u, "        /**\n         * A row's one-line detail.", r"""        /** What a step did, in a few plain words, never the raw command. */
        fun stepTitle(call: OpenCodeToolCall): String {
            val described = call.input.obj?.get("description")?.string?.trim()?.substringBefore('\n')?.nonEmpty
            if (described != null) return described
            if (call.isShell) return localized(R.string.transcript_step_command)
            val target = call.inputPath?.trimEnd('/')?.substringAfterLast('/')?.nonEmpty
            val name = call.name.lowercase()
            return when {
                "todo" in name -> localized(R.string.transcript_step_todo)
                "web" in name || "fetch" in name -> localized(R.string.transcript_step_web)
                "read" in name ->
                    if (target != null) localized(R.string.transcript_step_reading, target) else localized(R.string.transcript_step_reading_file)
                "write" in name ->
                    if (target != null) localized(R.string.transcript_step_writing, target) else localized(R.string.transcript_step_writing_file)
                "edit" in name || "patch" in name ->
                    if (target != null) localized(R.string.transcript_step_editing, target) else localized(R.string.transcript_step_editing_file)
                "grep" in name || "glob" in name || "search" in name || "find" in name || "list" in name ->
                    localized(R.string.transcript_step_searching)
                else -> call.displayName
            }
        }

        /**
         * A row's one-line detail.""", "step-title")
if deltas(u) != u0:
    sys.exit("InlineRows.kt quedo desbalanceado. No se cambio nada.")

# ---------------- SessionTranscriptView.kt ----------------
s = ST.read_text(encoding="utf-8")
s0 = deltas(s)
s = once(s, "import ai.opencode.feature.transcript.rows.AgentGlyphState\n",
         "import ai.opencode.feature.transcript.rows.AgentGlyphState\nimport ai.opencode.feature.transcript.rows.InfinityLoader\n", "imp-loader")
s = once(s, """    // The subagent cards' orb, scaled with the body text it leads.
    val orbSize = (18 * fontScale).dp
""", """    // The app's infinity mark, scaled with the body text it leads.
    val loaderWidth = (32 * fontScale).dp
""", "loader-size")
s = once(s, """        AgentActivityGlyph(
            source = AgentActivitySource.Session(activity),
            size = orbSize,
            isPaused = activity.phase == WorkingActivity.Phase.stopping || activity.isHeld,
            modifier = Modifier.clearAndSetSemantics {},
        )""", """        InfinityLoader(
            modifier = Modifier.clearAndSetSemantics {},
            width = loaderWidth,
            animate = !(activity.phase == WorkingActivity.Phase.stopping || activity.isHeld || activity.needsInput),
        )""", "loader-use")
if deltas(s) != s0:
    sys.exit("SessionTranscriptView.kt quedo desbalanceado. No se cambio nada.")

TR.write_text(t, encoding="utf-8")
IR.write_text(u, encoding="utf-8")
ST.write_text(s, encoding="utf-8")
print("listo: ToolRows.kt, InlineRows.kt y SessionTranscriptView.kt actualizados")
