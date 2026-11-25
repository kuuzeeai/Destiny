package ai.opencode.feature.transcript.rows

import ai.opencode.core.motion.rememberReduceMotion
import ai.opencode.core.ui.OpenCodeIcon
import ai.opencode.core.ui.OpenCodeIconSize
import ai.opencode.core.ui.OpenCodeTextStyles
import ai.opencode.core.ui.OpenCodeTheme
import ai.opencode.core.ui.RollingNumber
import ai.opencode.feature.media.AgentFileCards
import ai.opencode.feature.media.TranscriptImagePreview
import ai.opencode.feature.media.TranscriptMetrics
import ai.opencode.feature.transcript.InlineRowMetrics
import ai.opencode.feature.transcript.LocalTranscriptRowContext
import ai.opencode.feature.transcript.R
import ai.opencode.feature.transcript.TranscriptAction
import ai.opencode.feature.transcript.TranscriptDisclosureState
import ai.opencode.feature.transcript.TranscriptEntry
import ai.opencode.feature.transcript.TranscriptSubagent
import ai.opencode.feature.transcript.markdown.ui.BandClock
import ai.opencode.feature.transcript.markdown.ui.DisclosedContent
import ai.opencode.feature.transcript.markdown.ui.LocalBandReveal
import ai.opencode.feature.transcript.markdown.ui.DisclosedTransition
import ai.opencode.feature.transcript.markdown.ui.TranscriptBand
import ai.opencode.feature.transcript.user.TranscriptDragPayload
import ai.opencode.feature.transcript.user.transcriptDragSource
import ai.opencode.server.model.AgentDisplayName
import ai.opencode.server.model.OpenCodeQuestion
import ai.opencode.server.model.OpenCodeToolCall
import ai.opencode.server.model.OpenCodeTranscriptItem
import ai.opencode.server.model.TranscriptNoticeKind
import ai.opencode.server.model.nonEmpty
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

private val Kind = TranscriptDisclosureState.Kind.Tools

private fun OpenCodeToolCall.isSubagent(): Boolean = name == "subagent" || name == "task"

private fun OpenCodeToolCall.isRunningOrStreaming(): Boolean =
    status == OpenCodeToolCall.Status.Running || status == OpenCodeToolCall.Status.Streaming

// Tool rows

/** iOS `toolView(id:call:)`: a lone subagent card or an answered question; other tools only show in groups. */
@Composable
internal fun ToolView(entry: TranscriptEntry, id: String, call: OpenCodeToolCall, subagents: Map<String, TranscriptSubagent>) {
    if (call.isSubagent()) {
        SubagentToolView(call, subagents)
    } else {
        val questions = call.questions ?: return
        InlineQuestionView(entry, id, call, questions)
    }
}

/**
 * iOS `subagentToolView`: like the desktop card, the glyph alone says this subagent is working;
 * the session's own “Working…” line stays the main run's. Opens the child when it's known.
 */
@Composable
internal fun SubagentToolView(call: OpenCodeToolCall, subagents: Map<String, TranscriptSubagent>, modifier: Modifier = Modifier) {
    val context = LocalTranscriptRowContext.current
    val colors = OpenCodeTheme.colors
    val childID = call.metadata["sessionID"]?.string
    val child = childID?.let { subagents[it] }
    val isKnown = child?.isKnown == true
    val name = AgentDisplayName.of(child?.agent ?: call.input.obj?.get("agent")?.string) ?: TranscriptNoticeKind.Source.Subagent.title
    val running = call.isRunningOrStreaming() || child?.isRunning == true
    val failed = call.status == OpenCodeToolCall.Status.Error
    val phase = when {
        failed -> AgentGlyphState.Phase.Failed
        running -> if (call.status == OpenCodeToolCall.Status.Streaming || childID == null) AgentGlyphState.Phase.Spawning else AgentGlyphState.Phase.Running
        else -> AgentGlyphState.Phase.Done
    }
    val shape = RoundedCornerShape(TranscriptMetrics.cardCornerRadius)
    val failedLabel = stringResource(R.string.transcript_tool_failed)
    val runningLabel = stringResource(R.string.transcript_tool_running)
    val completedLabel = stringResource(R.string.transcript_tool_completed)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = TranscriptMetrics.minimumHitSide)
            .clip(shape)
            .background(colors.userBubble)
            .clickable(enabled = isKnown, role = Role.Button, onClickLabel = if (isKnown) stringResource(R.string.transcript_subagent_open_click_label) else null) {
                if (childID != null) context.send(TranscriptAction.SubagentTapped(childID))
            }
            .semantics(mergeDescendants = true) {
                stateDescription = if (failed) failedLabel else if (running) runningLabel else completedLabel
            }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(18.dp).heightIn(min = 20.dp), contentAlignment = Alignment.Center) {
            AgentActivityGlyph(AgentGlyphState(childID, phase, needsInput = child?.needsInput == true), Modifier.clearAndSetSemantics {})
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TranscriptMetrics.compact)) {
            Text(name, style = OpenCodeTextStyles.subheadline.copy(fontWeight = FontWeight.Medium), color = colors.text)
            (child?.title ?: call.input.obj?.get("description")?.string)?.let {
                Text(it, style = OpenCodeTextStyles.subheadline, color = colors.secondaryText)
            }
            if (failed) {
                Text(failedLabel, style = OpenCodeTextStyles.caption1.copy(fontWeight = FontWeight.Medium), color = colors.danger)
            }
        }
        if (isKnown) {
            OpenCodeIcon("chevron.right", contentDescription = null, Modifier.align(Alignment.CenterVertically), size = 14.dp, tint = colors.tertiaryText)
        }
    }
}

// Used N

/** The collapsed Used N header: “Used 12 tools”, or a red “· 1 failed” (iOS `InlineToolGroupHeaderLabel`). */
@Composable
private fun InlineToolGroupHeaderLabel(header: InlineToolGroupHeader, expanded: Boolean, modifier: Modifier = Modifier, runningTitle: String? = null) {
    val colors = OpenCodeTheme.colors
    val style = OpenCodeTextStyles.subheadline.copy(fontFeatureSettings = "tnum")
    val failedCount = stringResource(R.string.transcript_tool_group_failed_count, header.failed)
    if (isAccessibilityTextSize()) {
        val text = buildAnnotatedString {
            withStyle(SpanStyle(color = colors.secondaryText)) { append(runningTitle ?: header.title) }
            if (header.failed > 0) withStyle(SpanStyle(color = colors.danger)) { append(" · $failedCount") }
            header.cue?.let { withStyle(SpanStyle(color = colors.secondaryText)) { append(" · $it") } }
        }
        Text(text, modifier, style = style)
    } else {
        // Top-aligned, not by baseline: the pieces share one style, so their baselines already
        // agree (RollingNumber's canvas rounds its ascender up, under a pixel). Baseline alignment
        // queried RollingNumber's alignment lines during measure while it swapped its subtree
        // (canvas ↔ crossfaded text on lifecycle and fit changes), which crashes compose-ui
        // 1.12.1 with "LayoutNode N not found in RectList" (b/549552303, fixed in 1.13.0-beta01;
        // found against a live server). Restore alignByBaseline once Compose is on 1.13.
        Row(modifier) {
            // RollingNumber falls back to plain text when it doesn't fit (iOS `ViewThatFits`).
            if (runningTitle != null) {
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
            // The desktop's disclosure chevron.
            val reducedMotion = rememberReduceMotion()
            val rotation by animateFloatAsState(
                if (!reducedMotion && expanded) 90f else 0f,
                if (reducedMotion) snap() else DisclosureMotion.animation(expanded),
                label = "ToolGroupChevron",
            )
            OpenCodeIcon(
                if (reducedMotion && expanded) "chevron.down" else "chevron.right",
                contentDescription = null,
                modifier = Modifier.align(Alignment.CenterVertically).padding(start = 4.dp).graphicsLayer { rotationZ = rotation },
                size = 16.dp,
                tint = colors.tertiaryText,
            )
        }
    }
}

/**
 * iOS `inlineToolGroupView`: “Used 12 tools”, opening to one row per tool on the band code and
 * tables use. Grouping, notices and counting are unchanged. [key] is the row's identity, so the
 * group stays open when a member joins ahead of its first one.
 */
@Composable
internal fun InlineToolGroupView(entry: TranscriptEntry, key: String, tools: List<OpenCodeTranscriptItem>, subagents: Map<String, TranscriptSubagent>) {
    val context = LocalTranscriptRowContext.current
    val images = entry.toolImageAttachments
    // One summary per turn: the turn's first group draws it over every step of the turn; the
    // other groups, and every group while the turn runs, draw only what they alone carry (images).
    val group = entry as? TranscriptEntry.ToolGroup
    val showsSummary = group?.showsSummary != false
    val steps = group?.turnTools ?: tools
    var sheetOpen by remember(key) { mutableStateOf(false) }
    var selectedID by remember(key) { mutableStateOf<String?>(null) }
    val openLabel = stringResource(R.string.transcript_tool_summary_open_click_label)
    val toolImageLabel = stringResource(R.string.transcript_tool_image)
    Column {
        if (showsSummary) {
            val header = InlineToolGroupHeader(steps)
            val rows = InlineToolRow.rows(steps)
            InlineToolGroupHeaderLabel(
                header,
                false,
                Modifier
                    .fillMaxWidth()
                    .rowHitTarget(
                        target = Modifier
                            .rowClickable { sheetOpen = true }
                            .clearAndSetSemantics {
                                contentDescription = header.spoken
                                role = Role.Button
                                onClick(openLabel) { sheetOpen = true; true }
                            },
                    ),
                null,
            )
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
        if (images.isNotEmpty()) {
            ToolImageMosaic(images, Modifier.padding(top = if (showsSummary) TranscriptMetrics.clustered else 0.dp)) { id ->
                TranscriptImagePreview.fromAttachments(images, id, toolImageLabel, context.server)?.let { context.send(TranscriptAction.PreviewImage(it)) }
            }
        }
        val videos = entry.toolVideoReferences
        if (videos.isNotEmpty()) AgentFileCards(videos, onOpen = { context.send(TranscriptAction.OpenFile(it)) })
    }
}

@Composable
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
        is InlineToolRow.Kind.Notice, is InlineToolRow.Kind.Message ->
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

@Composable
private fun InlineRowDisclosure(entry: TranscriptEntry, row: InlineToolRow, bodies: List<Pair<String, OpenCodeToolCall>>) {
    val disclosure = LocalTranscriptRowContext.current.disclosure
    val expanded = disclosure.isOpen(Kind, row.id)
    val toggle = { disclosure.toggle(Kind, row.id, entry.id, crossFades = true) }
    val failedLabel = stringResource(R.string.transcript_tool_failed)
    val runningLabel = stringResource(R.string.transcript_tool_running)
    val expandedLabel = stringResource(R.string.transcript_expanded)
    val collapsedLabel = stringResource(R.string.transcript_collapsed)
    var detailFits by remember(row.id) { mutableStateOf(true) }
    Column {
        InlineRowLabel(
            row,
            Modifier
                .rowHitTarget(
                    top = 0.dp,
                    bottom = 0.dp,
                    target = Modifier.rowClickable(
                        role = Role.Button,
                        onClickLabel = if (expanded) stringResource(R.string.transcript_tool_hide_click_label) else stringResource(R.string.transcript_tool_show_click_label),
                        onClick = toggle,
                    ),
                )
                .semantics(mergeDescendants = true) {
                    stateDescription = listOfNotNull(
                        if (row.isFailed) failedLabel else if (row.isRunning) runningLabel else null,
                        if (expanded) expandedLabel else collapsedLabel,
                    ).joinToString(", ")
                },
            onDetailFits = { detailFits = it },
        )
        DisclosedContent(expanded) {
            Column(Modifier.padding(top = TranscriptMetrics.compact), verticalArrangement = Arrangement.spacedBy(TranscriptMetrics.clustered)) {
                val headerDetail: String? = null
                for ((id, call) in bodies) ToolBody(entry, id, call, headerDetail)
            }
        }
    }
}

/**
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

/** The tool's glyph, by name (the desktop shows one per tool kind). */
private fun toolGlyph(row: InlineToolRow): String {
    if (row.detailIsCommand) return "terminal"
    if (row.kind is InlineToolRow.Kind.Reads) return "doc.text"
    val name = row.name.lowercase()
    return when {
        // Before "write": todowrite.
        "todo" in name -> "checklist"
        "read" in name -> "doc.text"
        "write" in name -> "doc"
        "edit" in name || "patch" in name -> "pencil"
        "grep" in name || "glob" in name || "search" in name || "find" in name || "list" in name -> "magnifyingglass"
        "fetch" in name || "web" in name -> "globe"
        else -> "hammer.fill"
    }
}

@Composable
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

/** iOS `foldedOutputLines`: long output folds after a screenful. */
private const val foldedOutputLines = 16

/**
 * The desktop app's console block (iOS `toolBody`): what ran (command or readable arguments)
 * above a hairline, then the wrapped result. Long output folds after a screenful. Never raw JSON:
 * scalars read as text, structures as compact JSON per argument.
 */
@Composable
private fun ToolBody(entry: TranscriptEntry, id: String, call: OpenCodeToolCall, headerDetail: String? = null) {
    val context = LocalTranscriptRowContext.current
    val colors = OpenCodeTheme.colors
    val shellCommand = if (call.isShell) call.input.obj?.get("command")?.string?.nonEmpty else null
    val command = if (shellCommand == headerDetail) null else shellCommand
    val arguments = call.arguments.filter { (_, value) -> value != headerDetail }
    val output = call.output.joinToString("\n").trim('\n', '\r')
    val shape = RoundedCornerShape(TranscriptMetrics.controlCornerRadius)
    val hasInput = command != null || arguments.isNotEmpty()
    Column(
        Modifier
            .padding(end = TranscriptMetrics.compact, bottom = TranscriptMetrics.clustered)
            .fillMaxWidth()
            .clip(shape)
            .background(colors.userBubble),
    ) {
        if (hasInput) {
            SelectionContainer {
                Column(Modifier.fillMaxWidth().padding(TranscriptMetrics.standard), verticalArrangement = Arrangement.spacedBy(TranscriptMetrics.compact)) {
                    if (command != null) WrappedCode(command, colors.text)
                    for ((key, value) in arguments) {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = colors.secondaryText)) { append("$key  ") }
                                withStyle(SpanStyle(color = colors.text)) { append(value) }
                            },
                            style = TranscriptFonts.toolFont,
                        )
                    }
                }
            }
        }
        val showsError = call.error != null && (call.decline == null || call.declineReason != null)
        if (output.isNotEmpty() || showsError) {
            if (hasInput) HorizontalDivider(thickness = 0.5.dp, color = colors.border)
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(TranscriptMetrics.standard),
                verticalArrangement = Arrangement.spacedBy(TranscriptMetrics.clustered),
            ) {
                if (output.isNotEmpty()) {
                    val outputKey = "$id-output"
                    val showsAllOutput = context.disclosure.isOpen(Kind, outputKey)
                    val outputLines = output.split("\n").size
                    SelectionContainer {
                        WrappedCode(output, colors.secondaryText, maxLines = if (showsAllOutput) Int.MAX_VALUE else foldedOutputLines)
                    }
                    if (outputLines > foldedOutputLines) {
                        Box(
                            Modifier
                                .heightIn(min = TranscriptMetrics.minimumHitSide)
                                .rowHitTarget(top = 0.dp, bottom = 0.dp, target = Modifier.rowClickable { context.disclosure.toggle(Kind, outputKey, entry.id) }),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                if (showsAllOutput) stringResource(R.string.transcript_show_less) else pluralStringResource(R.plurals.transcript_tool_output_show_all, outputLines, outputLines),
                                style = OpenCodeTextStyles.footnote.copy(fontWeight = FontWeight.Medium),
                                color = colors.secondaryText,
                            )
                        }
                    }
                }
                if (call.decline != null) {
                    call.declineReason?.let { reason ->
                        SelectionContainer { Text(reason, style = TranscriptFonts.toolFont, color = colors.secondaryText) }
                    }
                } else {
                    call.error?.let { error ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OpenCodeIcon("exclamationmark.circle.fill", contentDescription = null, Modifier.padding(top = 1.dp), size = 14.dp, tint = colors.danger)
                            SelectionContainer { Text(error, style = TranscriptFonts.toolFont, color = colors.danger) }
                        }
                    }
                }
            }
        }
        val file = call.fileReference
        if (file != null && !call.isRunningOrStreaming()) {
            HorizontalDivider(thickness = 0.5.dp, color = colors.border)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = TranscriptMetrics.minimumHitSide)
                    .transcriptDragSource(
                        TranscriptDragPayload.AgentFile(file, context.server, context.directory),
                        cornerRadius = TranscriptMetrics.controlCornerRadius,
                    )
                    .clickable(role = Role.Button) { context.send(TranscriptAction.OpenFile(file)) }
                    .padding(horizontal = TranscriptMetrics.standard),
                horizontalArrangement = Arrangement.spacedBy(TranscriptMetrics.clustered),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val name = file.name
                val style = OpenCodeTextStyles.footnote.copy(fontWeight = FontWeight.Medium)
                OpenCodeIcon(file.symbolName, contentDescription = null, size = OpenCodeIconSize.small, tint = colors.secondaryText)
                Text(stringResource(R.string.transcript_open_named, name), Modifier.weight(1f), style = style, color = colors.text, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                OpenCodeIcon("chevron.right", contentDescription = null, size = 14.dp, tint = colors.tertiaryText)
            }
        }
    }
}

@Composable
private fun WrappedCode(text: String, color: Color, maxLines: Int = Int.MAX_VALUE) {
    Text(text, Modifier.fillMaxWidth(), style = TranscriptFonts.toolFont, color = color, maxLines = maxLines, overflow = TextOverflow.Clip)
}

// Answered questions

/**
 * iOS `inlineQuestionView`: “Reply to Maya · Send it”, opening to the draft or question as a muted
 * quote and the options as offered, yours primary with a ✓. The answer leaves the header as it
 * opens, so it shows once.
 */
@Composable
private fun InlineQuestionView(entry: TranscriptEntry, id: String, call: OpenCodeToolCall, questions: List<OpenCodeQuestion>) {
    val disclosure = LocalTranscriptRowContext.current.disclosure
    val colors = OpenCodeTheme.colors
    val expanded = disclosure.isOpen(Kind, id)
    val summary = QuestionRowSummary(questions, call)
    val (lead, value) = summary.parts
    val lines = InlineQuestion.lines(questions, InlineQuestion.options(call), call.questionAnswers, summary.decline != null, summary.reason)
    val toggle = { disclosure.toggle(Kind, id, entry.id, crossFades = true) }
    val style = TranscriptFonts.metaFont()
    val expandedLabel = stringResource(R.string.transcript_expanded)
    val collapsedLabel = stringResource(R.string.transcript_collapsed)
    val hideQuestionLabel = stringResource(R.string.transcript_question_hide_click_label)
    val showQuestionLabel = stringResource(R.string.transcript_question_show_click_label)
    Column {
        val target = Modifier
            .rowClickable(onClick = toggle)
            .clearAndSetSemantics {
                contentDescription = summary.spoken
                stateDescription = if (expanded) expandedLabel else collapsedLabel
                role = Role.Button
                onClick(if (expanded) hideQuestionLabel else showQuestionLabel) { toggle(); true }
            }
        val headerModifier = Modifier.fillMaxWidth().rowHitTarget(target = target)
        if (isAccessibilityTextSize()) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.secondaryText)) { append(lead) }
                    if (!expanded && value != null) withStyle(SpanStyle(color = colors.text)) { append(" · $value") }
                },
                headerModifier,
                style = style,
            )
        } else {
            // Top-aligned (one style, so the baselines agree): baseline alignment while
            // AnimatedVisibility adds or drops its text crashes compose-ui 1.12.1 (b/549552303;
            // see InlineToolGroupHeaderLabel).
            Row(headerModifier) {
                Text(lead, style = style, color = colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (value != null) {
                    AnimatedVisibility(!expanded, Modifier.weight(1f, fill = false), enter = DisclosedTransition.enter, exit = DisclosedTransition.exit) {
                        Text(" · $value", style = style, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        DisclosedBand(expanded) {
            Column {
                for (line in lines) InlineQuestionLine(line)
            }
        }
    }
}

@Composable
private fun InlineQuestionLine(line: InlineQuestion.Line) {
    val colors = OpenCodeTheme.colors
    val color = when (line.kind) {
        InlineQuestion.Line.Kind.Chosen, InlineQuestion.Line.Kind.Decision -> colors.text
        InlineQuestion.Line.Kind.Header, InlineQuestion.Line.Kind.Prompt, InlineQuestion.Line.Kind.Note -> colors.secondaryText
        InlineQuestion.Line.Kind.Unchosen -> colors.tertiaryText
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = InlineRowMetrics.rowPadding).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SelectionContainer(Modifier.alignByBaseline().weight(1f, fill = false)) {
            Text(
                line.text,
                style = if (line.kind == InlineQuestion.Line.Kind.Header) {
                    OpenCodeTextStyles.caption1.copy(fontWeight = FontWeight.SemiBold)
                } else {
                    OpenCodeTextStyles.subheadline
                },
                color = color,
            )
        }
        if (line.kind == InlineQuestion.Line.Kind.Chosen) {
            OpenCodeIcon("checkmark", contentDescription = stringResource(R.string.transcript_question_chosen_description), Modifier.align(Alignment.CenterVertically).size(12.dp), size = 12.dp, tint = colors.secondaryText)
        }
    }
}

// Bands

/**
 * An opened Used N group's or answered question's band: helper B's [TranscriptBand] on the
 * row's own disclosure spring ([BandClock]), drawn below the row while it closes.
 */
@Composable
private fun DisclosedBand(isOpen: Boolean, content: @Composable () -> Unit) {
    BandClock(isOpen, Modifier.fillMaxWidth()) {
        TranscriptBand(verticalPadding = InlineRowMetrics.bandPadding) { Column { content() } }
    }
}

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
