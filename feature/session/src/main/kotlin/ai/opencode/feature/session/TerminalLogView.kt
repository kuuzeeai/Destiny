package ai.opencode.feature.session

import ai.opencode.core.ui.OpenCodeFonts
import ai.opencode.core.ui.OpenCodeTextStyles
import ai.opencode.core.ui.OpenCodeTheme
import ai.opencode.feature.composer.OpenCodeBottomSheet
import ai.opencode.server.model.OpenCodeToolCall
import ai.opencode.server.model.OpenCodeTranscriptItem
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Los comandos (shell/bash) que el agente ha ejecutado en este chat, en orden. */
internal fun shellCalls(items: List<OpenCodeTranscriptItem>): List<OpenCodeToolCall> =
    items.filterIsInstance<OpenCodeTranscriptItem.Tool>().map { it.call }.filter { it.isShell }

private enum class LineKind { Command, Output, Running, Failed }

private data class TerminalLine(val kind: LineKind, val text: String)

private const val MAX_LINES = 2000

private val ansiEscapes = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")

private fun terminalLines(calls: List<OpenCodeToolCall>): List<TerminalLine> {
    val lines = ArrayList<TerminalLine>()
    for (call in calls) {
        val command = call.input.obj?.get("command")?.string?.trim().orEmpty()
        lines.add(TerminalLine(LineKind.Command, "\$ " + command))
        val output = call.output.joinToString("\n")
            .replace(ansiEscapes, "")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .trim('\n')
        if (output.isNotEmpty()) {
            for (text in output.split("\n")) lines.add(TerminalLine(LineKind.Output, text))
        }
        if (call.status == OpenCodeToolCall.Status.Running || call.status == OpenCodeToolCall.Status.Streaming) {
            lines.add(TerminalLine(LineKind.Running, "..."))
        } else if (call.status == OpenCodeToolCall.Status.Error) {
            lines.add(TerminalLine(LineKind.Failed, call.error?.takeIf { it.isNotBlank() } ?: "error"))
        }
        lines.add(TerminalLine(LineKind.Output, ""))
    }
    return if (lines.size > MAX_LINES) lines.takeLast(MAX_LINES) else lines
}

/**
 * Terminal de solo lectura: todo lo que el agente ejecuta en este chat y lo que responde cada
 * comando, en vivo. No envía nada: solo muestra. Baja sola mientras llega código; si subes a
 * leer se queda quieta, y vuelve a seguir cuando regresas al final.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalLogView(calls: List<OpenCodeToolCall>, onClose: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lines = remember(calls) { terminalLines(calls) }
    val listState = rememberLazyListState()
    // Sigue el final salvo que la persona suba a leer: solo su propio gesto lo cambia.
    var following by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) following = !listState.canScrollForward
        }
    }
    // Cada vez que cambia el contenido (línea nueva o texto que crece) baja al final.
    LaunchedEffect(lines) {
        if (following && lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }
    // La caja negra baja hasta casi el borde de la pantalla (sobre la barra de navegacion), separada por 4 dp.
    val density = LocalDensity.current
    val extraBottomPx = WindowInsets.navigationBars.getBottom(density)
    OpenCodeBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 4.dp)
                .padding(bottom = 4.dp),
        ) {
            Text(
                stringResource(R.string.session_sheet_terminal),
                style = OpenCodeTextStyles.callout.copy(fontWeight = FontWeight.SemiBold),
                color = OpenCodeTheme.colors.text,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val extra = if (constraints.hasBoundedHeight) extraBottomPx else 0
                        val placeable = measurable.measure(
                            constraints.copy(
                                minHeight = constraints.minHeight + extra,
                                maxHeight = constraints.maxHeight + extra,
                            ),
                        )
                        layout(placeable.width, placeable.height - extra) { placeable.place(0, 0) }
                    }
                    .background(Color(0xFF0D1117), RoundedCornerShape(12.dp))
                    .padding(
                        start = 8.dp,
                        end = 8.dp,
                        top = 10.dp,
                        bottom = 10.dp + with(density) { extraBottomPx.toDp() },
                    ),
            ) {
                if (lines.isEmpty()) {
                    Text(
                        stringResource(R.string.session_terminal_empty),
                        color = Color(0xFF8B949E),
                        fontFamily = OpenCodeFonts.monospace,
                        fontSize = 12.sp,
                    )
                } else {
                    SelectionContainer {
                        LazyColumn(state = listState) {
                            items(lines.size) { index ->
                                val line = lines[index]
                                val color = when (line.kind) {
                                    LineKind.Command -> Color(0xFF7EE787)
                                    LineKind.Output -> Color(0xFFC9D1D9)
                                    LineKind.Running -> Color(0xFFE3B341)
                                    LineKind.Failed -> Color(0xFFFF7B72)
                                }
                                Text(
                                    line.text.ifEmpty { " " },
                                    color = color,
                                    fontFamily = OpenCodeFonts.monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    fontWeight = if (line.kind == LineKind.Command) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
