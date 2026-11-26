import os

F = "feature/drawer/src/main/kotlin/ai/opencode/feature/drawer/list/ArchivedPage.kt"
if not os.path.isfile(F):
    raise SystemExit("NO ENCUENTRO ArchivedPage.kt (corre esto dentro de ~/Destiny)")
t = open(F, encoding="utf-8").read()

START = "@Composable\ninternal fun TerminalEntryRow("
END = "/**\n * The Archived page over"
a = t.find(START)
b = t.find(END)
if a < 0 or b < 0 or b < a:
    raise SystemExit("NO ENCUENTRO TerminalEntryRow")

NEW = r'''@Composable
internal fun TerminalEntryRow(onClick: () -> Unit) {
    val colors = OpenCodeTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = DrawerMetrics.rowEdge, vertical = DrawerTunables.rowGap / 2)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = DrawerMetrics.rowHeight)
            .padding(start = DrawerMetrics.textInset + 2.dp, end = DrawerMetrics.trailingInset + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.width(22.dp), contentAlignment = Alignment.CenterStart) {
            OpenCodeIcon(SFSymbol.Terminal, contentDescription = null, tint = colors.secondaryText)
        }
        Text(
            stringResource(R.string.drawer_terminal_entry),
            style = DrawerTunables.rowFont.font,
            color = colors.text,
            modifier = Modifier.weight(1f),
        )
    }
}

'''
if t[a:b] == NEW:
    raise SystemExit("ya estaba aplicado")
t = t[:a] + NEW + t[b:]
open(F, "w", encoding="utf-8").write(t)
print("  editado ArchivedPage.kt")
print("LISTO")
