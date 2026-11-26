import sys, pathlib
B = pathlib.Path("feature/drawer/src/main/kotlin/ai/opencode/feature/drawer")
OLD_HDR = '''                        .padding(start = DrawerMetrics.textInset, end = DrawerSearchPillDefaults.size + DrawerTunables.searchGap)
                        .drawerSearchTitleFade(viewState.search)
                    ServerFlowTitle(store = store, onManage = viewState::manageServers, modifier = titleModifier)
                    DrawerSearchPill(
                        state = viewState.search,
                        text = search,
                        onTextChange = { store.send(HomeFeature.Action.SearchChanged(it)) },
                        reducedMotion = reducedMotion,
                        onEnd = viewState::endSearch,
                        keyboardHold = viewState.keyboardHold,
                    )
                }
            }
                if (!selection.isActive) Row('''
NEW_HDR = '''                        .padding(horizontal = DrawerMetrics.textInset)
                    ServerFlowTitle(store = store, onManage = viewState::manageServers, modifier = titleModifier)
                }
            }
                if (!selection.isActive) DrawerSearchPill(
                    state = viewState.search,
                    text = search,
                    onTextChange = { store.send(HomeFeature.Action.SearchChanged(it)) },
                    reducedMotion = reducedMotion,
                    onEnd = viewState::endSearch,
                    modifier = Modifier
                        .padding(horizontal = DrawerMetrics.rowEdge)
                        .padding(bottom = 8.dp),
                    keyboardHold = viewState.keyboardHold,
                )
                if (!selection.isActive) Row('''
OLD_VIS = '''            AnimatedVisibility(
                isSearching,
                enter = fadeIn(DrawerSearchPillDefaults.morph(opening = true)),
                exit = fadeOut(DrawerSearchPillDefaults.morph(opening = false)),
                modifier = Modifier.weight(1f),
            ) {
                field(Modifier.fillMaxWidth())
            }
'''
NEW_VIS = '''            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                AnimatedVisibility(
                    !isSearching,
                    enter = fadeIn(DrawerSearchPillDefaults.morph(opening = false)),
                    exit = fadeOut(DrawerSearchPillDefaults.morph(opening = true)),
                ) {
                    Text(stringResource(CoreR.string.common_search), style = OpenCodeTextStyles.body, color = OpenCodeTheme.colors.tertiaryText, maxLines = 1)
                }
                AnimatedVisibility(
                    isSearching,
                    enter = fadeIn(DrawerSearchPillDefaults.morph(opening = true)),
                    exit = fadeOut(DrawerSearchPillDefaults.morph(opening = false)),
                ) {
                    field(Modifier.fillMaxWidth())
                }
            }
'''
EDITS = {
 B/"HomeViewDrawer.kt": [(OLD_HDR, NEW_HDR)],
 B/"motion/DrawerSearchPill.kt": [
  ("    val restPadding = (size - DrawerSearchPillDefaults.glyphWidth) / 2\n\n", ""),
  ("leadingPadding = if (searching) textInset else restPadding,", "leadingPadding = textInset,"),
  ("trailingPadding = if (searching) 8.dp else restPadding,", "trailingPadding = 8.dp,"),
  ("leadingPadding = if (isSearching) textInset else restPadding,", "leadingPadding = textInset,"),
  ("trailingPadding = if (isSearching) 8.dp else restPadding,", "trailingPadding = 8.dp,"),
  ("modifier = if (searching) Modifier.weight(1f) else Modifier,", "modifier = Modifier.weight(1f),"),
  ("                        size.value + (openWidth - size.value) * p\n", "                        full.value + (openWidth - full.value) * p\n"),
  (OLD_VIS, NEW_VIS),
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
    if t.count("{") != t.count("}") or t.count("(") != t.count(")"):
        sys.exit(f"ABORTO: balance roto en {path.name}")
    out[path] = t
for p, t in out.items(): p.write_text(t)
print("OK f8:", ", ".join(p.name for p in out))
