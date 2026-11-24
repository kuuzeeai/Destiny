import sys, pathlib
D = pathlib.Path("feature/drawer/src/main")
VD = D/"kotlin/ai/opencode/feature/drawer/HomeViewDrawer.kt"
CHIPS = '''                        keyboardHold = viewState.keyboardHold,
                    )
                }
            }
                if (!selection.isActive) Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = DrawerMetrics.rowEdge)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for ((value, label) in listOf(
                        DrawerFilter.All to R.string.drawer_filter_all,
                        DrawerFilter.Unread to R.string.drawer_filter_unread,
                        DrawerFilter.Pinned to R.string.drawer_filter_pinned,
                    )) {
                        FilterChip(
                            selected = filter == value,
                            onClick = { filter = value },
                            label = { Text(localized(label)) },
                            shape = CircleShape,
                        )
                    }
                }
            }
        }

        if (viewState.showsArchived || archivedTransition.isVisible) ArchivedPage('''
EDITS = {
 VD: [
 ("import androidx.compose.material3.MaterialTheme\n",
  "import androidx.compose.material3.FilterChip\nimport androidx.compose.material3.MaterialTheme\nimport androidx.compose.foundation.horizontalScroll\nimport androidx.compose.foundation.rememberScrollState\nimport androidx.compose.foundation.shape.CircleShape\nimport androidx.compose.runtime.mutableStateOf\nimport ai.opencode.core.strings.localized\nimport ai.opencode.feature.drawer.list.DrawerFilter\n"),
 ("    val inputs = DrawerListInputs(\n",
  "    var filter by remember { mutableStateOf(DrawerFilter.All) }\n    val inputs = DrawerListInputs(\n"),
 ("        checkedIDs = selection.ids,\n    )\n    val actions = remember(store, viewState) {",
  "        checkedIDs = selection.ids,\n        filter = filter,\n    )\n    val actions = remember(store, viewState) {"),
 ("            Box(\n                Modifier\n                    .fillMaxWidth()\n                    .padding(horizontal = DrawerMetrics.rowEdge)\n                    .padding(top = insetTop + DrawerHeaderTop, bottom = DrawerTunables.headerBottom),\n                contentAlignment = Alignment.CenterEnd,\n            ) {\n                SelectionHeaderSwap(",
  "            Column {\n            Box(\n                Modifier\n                    .fillMaxWidth()\n                    .padding(horizontal = DrawerMetrics.rowEdge)\n                    .padding(top = insetTop + DrawerHeaderTop, bottom = DrawerTunables.headerBottom),\n                contentAlignment = Alignment.CenterEnd,\n            ) {\n                SelectionHeaderSwap("),
 ('''                        keyboardHold = viewState.keyboardHold,
                    )
                }
            }
        }

        if (viewState.showsArchived || archivedTransition.isVisible) ArchivedPage(''', CHIPS),
 ],
 D/"res/values/strings.xml": [
 ("</resources>", '    <string name="drawer_filter_all">All</string>\n    <string name="drawer_filter_unread">Unread</string>\n    <string name="drawer_filter_pinned">Pinned</string>\n</resources>'),
 ],
 D/"res/values-es/strings.xml": [
 ("</resources>", '    <string name="drawer_filter_all">Todas</string>\n    <string name="drawer_filter_unread">Sin leer</string>\n    <string name="drawer_filter_pinned">Fijadas</string>\n</resources>'),
 ],
}
out = {}
for path, edits in EDITS.items():
    if not path.exists(): sys.exit(f"NO EXISTE {path}")
    t = path.read_text()
    for old, new in edits:
        n = t.count(old)
        if n != 1: sys.exit(f"ABORTO: {path.name}: {n} coincidencias para: {old[:70]!r}")
        t = t.replace(old, new)
    if path.suffix == ".kt" and (t.count("{") != t.count("}") or t.count("(") != t.count(")")):
        sys.exit(f"ABORTO: balance roto en {path.name}")
    out[path] = t
for p, t in out.items(): p.write_text(t)
print("OK f7:", ", ".join(p.name for p in out))
