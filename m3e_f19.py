# Parte 19: herramientas estilo Claude (una linea -> hoja "Resumen" -> detalle Solicitud/Respuesta)
# y el infinito de "Trabajando" mas chico y sin la palabra.
# Ejecutar en la raiz del repo (despues de m3e_f18a.py y m3e_f18b.py):  python3 m3e_f19.py
# Si algun texto no coincide, NO cambia nada.
import pathlib, re, sys

R = pathlib.Path("feature/transcript/src/main/kotlin/ai/opencode/feature/transcript/rows")
TR = R / "ToolRows.kt"
SHEET = R / "ToolSheet.kt"
ST = pathlib.Path("feature/session/src/main/kotlin/ai/opencode/feature/session/SessionTranscriptView.kt")
RES = pathlib.Path("feature/transcript/src/main/res")
S_EN = RES / "values/strings.xml"
S_ES = RES / "values-es/strings.xml"

for f in (TR, ST, S_EN, S_ES, R / "InlineRows.kt"):
    if not f.exists():
        sys.exit(f"No encuentro {f}. Ejecuta esto en la raiz del repo.")
if not (R / "InfinityLoader.kt").exists():
    sys.exit("Falta InfinityLoader.kt: ejecuta primero m3e_f18a.py y m3e_f18b.py")

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
    t = re.sub(r'"(?:\\.|[^"\\])*"', '""', t)
    t = re.sub(r"'(?:\\.|[^'\\])'", "''", t)
    return (t.count("{") - t.count("}"), t.count("(") - t.count(")"))

KT = r'''package ai.opencode.feature.transcript.rows

import ai.opencode.core.R as CoreR
import ai.opencode.core.ui.OpenCodeIcon
import ai.opencode.core.ui.OpenCodeShape
import ai.opencode.core.ui.OpenCodeSheetHandle
import ai.opencode.core.ui.OpenCodeTextStyles
import ai.opencode.core.ui.OpenCodeTheme
import ai.opencode.feature.transcript.LocalTranscriptRowContext
import ai.opencode.feature.transcript.R
import ai.opencode.feature.transcript.TranscriptAction
import ai.opencode.feature.transcript.TranscriptSubagent
import ai.opencode.server.model.OpenCodeToolCall
import ai.opencode.server.model.nonEmpty
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

// The tools sheet, as Claude shows it: a Summary timeline of every step, and a tap on a step
// opens its detail as Request and Response boxes.

/** Long output folds after this many lines, with a button to show the rest. */
private const val OutputFoldLines = 200

private fun OpenCodeToolCall.isSubagentCall(): Boolean = name == "subagent" || name == "task"

private fun OpenCodeToolCall.isLive(): Boolean =
    status == OpenCodeToolCall.Status.Running || status == OpenCodeToolCall.Status.Streaming

/**
 * The bottom sheet behind the one-line tools row. [selectedID] is the opened step, or null for the
 * timeline. It is an ID rather than the row, so a step that is still running updates live.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ToolTimelineSheet(
    rows: List<InlineToolRow>,
    selectedID: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    subagents: Map<String, TranscriptSubagent>,
) {
    val context = LocalTranscriptRowContext.current
    val colors = OpenCodeTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxHeight = maxOf(LocalConfiguration.current.screenHeightDp.dp - 110.dp, 320.dp)
    val selected = selectedID?.let { id -> rows.firstOrNull { it.id == id } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.groupedCanvas,
        dragHandle = { OpenCodeSheetHandle() },
        shape = OpenCodeShape.sheetTop,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
            if (selected == null) {
                SheetHeader(
                    title = stringResource(R.string.transcript_tool_summary),
                    symbol = "xmark",
                    label = stringResource(CoreR.string.common_close),
                    onLeading = onDismiss,
                )
                LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp)) {
                    itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
                        TimelineRow(
                            row = row,
                            isFirst = index == 0,
                            isLast = index == rows.lastIndex,
                            tappable = row.kind !is InlineToolRow.Kind.Notice,
                        ) {
                            val call = (row.kind as? InlineToolRow.Kind.Tool)?.call
                            val childID = if (call != null && call.isSubagentCall()) call.metadata["sessionID"]?.string else null
                            if (childID != null && subagents[childID]?.isKnown == true) {
                                context.send(TranscriptAction.SubagentTapped(childID))
                                onDismiss()
                            } else {
                                onSelect(row.id)
                            }
                        }
                    }
                }
            } else {
                SheetHeader(
                    title = selected.title,
                    symbol = "chevron.left",
                    label = stringResource(CoreR.string.common_back),
                    onLeading = { onSelect(null) },
                )
                key(selected.id) {
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        val calls = when (val kind = selected.kind) {
                            is InlineToolRow.Kind.Tool -> listOf(kind.call)
                            is InlineToolRow.Kind.Reads -> kind.calls
                            is InlineToolRow.Kind.Notice -> emptyList()
                        }
                        for (call in calls) {
                            CallDetail(
                                call = call,
                                label = if (calls.size > 1) call.inputPath?.trimEnd('/')?.substringAfterLast('/') else null,
                            ) {
                                call.fileReference?.let { context.send(TranscriptAction.OpenFile(it)) }
                                onDismiss()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The sheet's top line: a button at the start (close or back) and the centred title. */
@Composable
private fun SheetHeader(title: String, symbol: String, label: String, onLeading: () -> Unit) {
    val colors = OpenCodeTheme.colors
    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        IconButton(onClick = onLeading, modifier = Modifier.align(Alignment.CenterStart)) {
            OpenCodeIcon(symbol, contentDescription = label, tint = colors.text)
        }
        Text(
            title,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 56.dp)
                .semantics { heading() },
            style = OpenCodeTextStyles.headline,
            color = colors.text,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One step of the timeline: a dot on a thin line that joins the steps, and what the step did. */
@Composable
private fun TimelineRow(
    row: InlineToolRow,
    isFirst: Boolean,
    isLast: Boolean,
    tappable: Boolean,
    onClick: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val style = OpenCodeTextStyles.body
    val lineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
    val vertical = 12.dp
    val dot = when {
        row.isFailed -> colors.danger
        row.isRunning -> colors.text
        else -> colors.tertiaryText
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (tappable) Modifier.rowClickable(onClick = onClick) else Modifier)
            .drawBehind {
                val x = 22.dp.toPx()
                val cy = (vertical + lineHeight / 2).toPx()
                val gap = 10.dp.toPx()
                val stroke = 1.dp.toPx()
                if (!isFirst) drawLine(colors.border, Offset(x, 0f), Offset(x, cy - gap), strokeWidth = stroke)
                if (!isLast) drawLine(colors.border, Offset(x, cy + gap), Offset(x, size.height), strokeWidth = stroke)
            }
            .padding(end = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(44.dp).padding(top = vertical).height(lineHeight), contentAlignment = Alignment.Center) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        }
        Text(
            row.title,
            Modifier.weight(1f).padding(vertical = vertical).inlineRowPulse(row.isRunning),
            style = style,
            color = if (row.isFailed) colors.danger else colors.text,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (tappable) {
            OpenCodeIcon(
                "chevron.right",
                contentDescription = null,
                modifier = Modifier.align(Alignment.CenterVertically).padding(start = 8.dp),
                size = 16.dp,
                tint = colors.tertiaryText,
            )
        }
    }
}

/** A labelled box: “Request” and “Response” sit above their own outlined box. */
@Composable
private fun DetailSection(label: String, content: @Composable () -> Unit) {
    val colors = OpenCodeTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = OpenCodeTextStyles.subheadline.copy(fontWeight = FontWeight.Medium),
            color = colors.secondaryText,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, colors.border, shape)
                .padding(14.dp),
        ) { content() }
    }
}

/** One call opened: what was asked (command or arguments), then what came back. */
@Composable
private fun CallDetail(call: OpenCodeToolCall, label: String?, onOpenFile: () -> Unit) {
    val colors = OpenCodeTheme.colors
    val live = call.isLive()
    val command = if (call.isShell) call.input.obj?.get("command")?.string?.nonEmpty else null
    val arguments = call.arguments
    val output = call.output.joinToString("\n").trim('\n', '\r')
    val showsError = call.error != null && (call.decline == null || call.declineReason != null)
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (label != null) {
            Text(label, style = TranscriptFonts.toolFont.copy(fontWeight = FontWeight.Medium), color = colors.text)
        }
        if (command != null || arguments.isNotEmpty()) {
            DetailSection(stringResource(R.string.transcript_tool_request)) {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (command != null) Text(command, style = TranscriptFonts.toolFont, color = colors.text)
                        for ((name, value) in arguments) {
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = colors.secondaryText)) { append("$name  ") }
                                    withStyle(SpanStyle(color = colors.text)) { append(value) }
                                },
                                style = TranscriptFonts.toolFont,
                            )
                        }
                    }
                }
            }
        }
        DetailSection(stringResource(R.string.transcript_tool_response)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (output.isNotEmpty()) {
                    var showsAll by remember(call.callID) { mutableStateOf(false) }
                    val lineCount = output.split("\n").size
                    SelectionContainer {
                        Text(
                            output,
                            style = TranscriptFonts.toolFont,
                            color = colors.secondaryText,
                            maxLines = if (showsAll) Int.MAX_VALUE else OutputFoldLines,
                            overflow = TextOverflow.Clip,
                        )
                    }
                    if (lineCount > OutputFoldLines) {
                        Text(
                            if (showsAll) stringResource(R.string.transcript_show_less) else pluralStringResource(R.plurals.transcript_tool_output_show_all, lineCount, lineCount),
                            Modifier.rowClickable { showsAll = !showsAll }.padding(vertical = 6.dp),
                            style = OpenCodeTextStyles.footnote.copy(fontWeight = FontWeight.Medium),
                            color = colors.secondaryText,
                        )
                    }
                }
                if (call.decline != null) {
                    call.declineReason?.let { reason ->
                        SelectionContainer { Text(reason, style = TranscriptFonts.toolFont, color = colors.secondaryText) }
                    }
                } else {
                    call.error?.let { error ->
                        SelectionContainer { Text(error, style = TranscriptFonts.toolFont, color = colors.danger) }
                    }
                }
                if (output.isEmpty() && !showsError) {
                    val empty = when {
                        live -> stringResource(R.string.transcript_tool_running)
                        call.decline != null -> stringResource(R.string.transcript_tool_denied)
                        else -> stringResource(R.string.transcript_tool_no_output)
                    }
                    Text(empty, style = TranscriptFonts.toolFont, color = colors.secondaryText)
                }
            }
        }
        val file = call.fileReference
        if (file != null && !live) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .rowClickable(onClick = onOpenFile),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OpenCodeIcon(file.symbolName, contentDescription = null, size = 18.dp, tint = colors.secondaryText)
                Text(
                    stringResource(R.string.transcript_open_named, file.name),
                    Modifier.weight(1f),
                    style = OpenCodeTextStyles.footnote.copy(fontWeight = FontWeight.Medium),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
                OpenCodeIcon("chevron.right", contentDescription = null, size = 14.dp, tint = colors.tertiaryText)
            }
        }
    }
}
'''

tr = TR.read_text(encoding="utf-8")
if "ToolTimelineSheet" in tr or SHEET.exists():
    sys.exit("Esto ya esta aplicado (ToolTimelineSheet existe). No se cambio nada.")

# ---------------- ToolRows.kt ----------------
d0 = deltas(tr)

tr = once(tr,
    "private fun InlineToolGroupHeaderLabel(header: InlineToolGroupHeader, expanded: Boolean, modifier: Modifier = Modifier) {",
    "private fun InlineToolGroupHeaderLabel(header: InlineToolGroupHeader, expanded: Boolean, modifier: Modifier = Modifier, runningTitle: String? = null) {",
    "header-sig")

tr = once(tr,
    "withStyle(SpanStyle(color = colors.secondaryText)) { append(header.title) }",
    "withStyle(SpanStyle(color = colors.secondaryText)) { append(runningTitle ?: header.title) }",
    "header-a11y")

tr = once(tr, """            RollingNumber(header.title, style = style, color = colors.secondaryText)
            if (header.failed > 0) {
                RollingNumber(" · $failedCount", style = style, color = colors.danger)
            }
            header.cue?.let {
                Text(" · $it", style = style, color = colors.secondaryText, maxLines = 1, softWrap = false)
            }
""", """            if (runningTitle != null) {
                // Mientras una herramienta corre, la linea dice que esta haciendo.
                Text(
                    runningTitle,
                    Modifier.weight(1f, fill = false).inlineRowPulse(true),
                    style = style,
                    color = colors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                RollingNumber(header.title, style = style, color = colors.secondaryText)
                if (header.failed > 0) {
                    RollingNumber(" · $failedCount", style = style, color = colors.danger)
                }
                header.cue?.let {
                    Text(" · $it", style = style, color = colors.secondaryText, maxLines = 1, softWrap = false)
                }
            }
""", "header-body")

NEW_VIEW = """internal fun InlineToolGroupView(entry: TranscriptEntry, key: String, tools: List<OpenCodeTranscriptItem>, subagents: Map<String, TranscriptSubagent>) {
    val context = LocalTranscriptRowContext.current
    val images = entry.toolImageAttachments
    val header = InlineToolGroupHeader(tools)
    val rows = InlineToolRow.rows(tools)
    // Una sola linea, como Claude: si una herramienta corre dice que hace, y al tocarla abre la hoja.
    val runningTitle = rows.lastOrNull { it.isRunning }?.title
    var sheetOpen by remember(key) { mutableStateOf(false) }
    var selectedID by remember(key) { mutableStateOf<String?>(null) }
    val openLabel = stringResource(R.string.transcript_tool_summary_open_click_label)
    val toolImageLabel = stringResource(R.string.transcript_tool_image)
    Column {
        InlineToolGroupHeaderLabel(
            header,
            false,
            Modifier
                .fillMaxWidth()
                .rowHitTarget(
                    target = Modifier
                        .rowClickable { sheetOpen = true }
                        .clearAndSetSemantics {
                            contentDescription = runningTitle ?: header.spoken
                            role = Role.Button
                            onClick(openLabel) { sheetOpen = true; true }
                        },
                ),
            runningTitle,
        )
        if (images.isNotEmpty()) {
            ToolImageMosaic(images, Modifier.padding(top = TranscriptMetrics.clustered)) { id ->
                TranscriptImagePreview.fromAttachments(images, id, toolImageLabel, context.server)?.let { context.send(TranscriptAction.PreviewImage(it)) }
            }
        }
        val videos = entry.toolVideoReferences
        if (videos.isNotEmpty()) AgentFileCards(videos, onOpen = { context.send(TranscriptAction.OpenFile(it)) })
        if (sheetOpen) {
            ToolTimelineSheet(
                rows = rows,
                selectedID = selectedID,
                onSelect = { selectedID = it },
                onDismiss = {
                    sheetOpen = false
                    selectedID = null
                },
                subagents = subagents,
            )
        }
    }
}

"""
tr = region(tr,
    "internal fun InlineToolGroupView(entry: TranscriptEntry, key: String, tools: List<OpenCodeTranscriptItem>, subagents: Map<String, TranscriptSubagent>) {",
    "@Composable\nprivate fun InlineToolRowView(",
    NEW_VIEW, "group-view")

if deltas(tr) != d0:
    sys.exit(f"[ToolRows.kt] llaves/parentesis desbalanceados {d0} -> {deltas(tr)}. No se cambio nada.")

# ---------------- SessionTranscriptView.kt ----------------
st = ST.read_text(encoding="utf-8")
s0 = deltas(st)
st = once(st, "val loaderWidth = (32 * fontScale).dp", "val loaderWidth = (24 * fontScale).dp", "loader-size")
st = once(st, "    val spacing = (8 * fontScale).dp\n",
    "    val spacing = (8 * fontScale).dp\n"
    "    // Solo el infinito: el texto queda para lo que el agente necesita decir (detener, pedir datos, reintento).\n"
    "    val showsText = activity.phase == WorkingActivity.Phase.stopping || activity.needsInput ||\n"
    "        (activity.retry != null && activity.phase == WorkingActivity.Phase.running)\n",
    "loader-showstext")
st = once(st, "            modifier = Modifier.clearAndSetSemantics {},\n            width = loaderWidth,",
    "            modifier = if (showsText) Modifier.clearAndSetSemantics {} else Modifier.semantics { contentDescription = activity.title },\n            width = loaderWidth,",
    "loader-semantics")
st = once(st, """        } else {
            Text(activity.title, style = style, color = OpenCodeTheme.colors.secondaryText)
        }
""", """        } else if (showsText) {
            Text(activity.title, style = style, color = OpenCodeTheme.colors.secondaryText)
        }
""", "working-text")
if deltas(st) != s0:
    sys.exit(f"[SessionTranscriptView.kt] llaves/parentesis desbalanceados {s0} -> {deltas(st)}. No se cambio nada.")

# ---------------- strings ----------------
en = S_EN.read_text(encoding="utf-8")
es = S_ES.read_text(encoding="utf-8")
EN_ADD = """
    <!-- Tools sheet -->
    <string name="transcript_tool_summary">Summary</string>
    <string name="transcript_tool_summary_open_click_label">Open the tools summary</string>
    <string name="transcript_tool_request">Request</string>
    <string name="transcript_tool_response">Response</string>
    <string name="transcript_tool_no_output">No output</string>
</resources>"""
ES_ADD = """
    <!-- Hoja de herramientas -->
    <string name="transcript_tool_summary">Resumen</string>
    <string name="transcript_tool_summary_open_click_label">Abrir el resumen de herramientas</string>
    <string name="transcript_tool_request">Solicitud</string>
    <string name="transcript_tool_response">Respuesta</string>
    <string name="transcript_tool_no_output">Sin salida</string>
</resources>"""
if "transcript_tool_summary" in en or "transcript_tool_summary" in es:
    sys.exit("Los textos nuevos ya existen en strings.xml. No se cambio nada.")
en = once(en, "</resources>", EN_ADD[1:], "strings-en")
es = once(es, "</resources>", ES_ADD[1:], "strings-es")

# ---------------- todo coincidio: escribir ----------------
SHEET.write_text(KT, encoding="utf-8")
TR.write_text(tr, encoding="utf-8")
ST.write_text(st, encoding="utf-8")
S_EN.write_text(en, encoding="utf-8")
S_ES.write_text(es, encoding="utf-8")
print("Listo. Archivos tocados:")
for f in (SHEET, TR, ST, S_EN, S_ES):
    print(" -", f)
print("Ahora sube los cambios y compila con GitHub Actions.")
