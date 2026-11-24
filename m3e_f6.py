import sys, pathlib
BASE = pathlib.Path("feature/drawer/src/main/kotlin/ai/opencode/feature/drawer/list")
EDITS = {
 BASE/"DrawerListModel.kt": [
 ("/** The list's view-local inputs beyond Home's state. */\ndata class DrawerListInputs(",
  "/** The filter chips' choice: which sessions the list shows. */\nenum class DrawerFilter { All, Unread, Pinned }\n\n/** The list's view-local inputs beyond Home's state. */\ndata class DrawerListInputs("),
 ("    val checkedIDs: Set<String> = emptySet(),\n)\n\ndata class DrawerListModel(",
  "    val checkedIDs: Set<String> = emptySet(),\n    /** The filter chips' choice. */\n    val filter: DrawerFilter = DrawerFilter.All,\n)\n\ndata class DrawerListModel("),
 ("            val archivedCount = if (state.search.isEmpty()) state.inboxSidebarStatus.archived.size else 0\n            val showsShortcuts = state.search.isEmpty() && (shortcuts.showsCatchUp",
  "            val plain = state.search.isEmpty() && inputs.filter == DrawerFilter.All\n            val archivedCount = if (plain) state.inboxSidebarStatus.archived.size else 0\n            val showsShortcuts = plain && (shortcuts.showsCatchUp"),
 ("                if (serverNote != null && state.search.isEmpty()) add(DrawerListItem.ServerNote)\n                if (state.search.isEmpty()) {",
  "                if (serverNote != null && plain) add(DrawerListItem.ServerNote)\n                if (plain) {"),
 ("            val pinned = sessions.pinned\n            val recents = sessions.recents\n",
  "            val pinned = if (inputs.filter == DrawerFilter.Unread) sessions.pinned.filter { status.isUnread(it) } else sessions.pinned\n            val recents = when (inputs.filter) {\n                DrawerFilter.All -> sessions.recents\n                DrawerFilter.Unread -> sessions.recents.filter { status.isUnread(it) }\n                DrawerFilter.Pinned -> emptyList()\n            }\n"),
 ("            for (draft in state.unsentDrafts) {",
  "            for (draft in (if (inputs.filter == DrawerFilter.All) state.unsentDrafts else emptyList())) {"),
 ],
 BASE/"DrawerListView.kt": [
 ("            showsShortcuts = state.search.isEmpty() && (model.shortcuts.showsCatchUp || model.shortcuts.showsWhatsNew),",
  "            showsShortcuts = state.search.isEmpty() && currentInputs.value.filter == DrawerFilter.All && (model.shortcuts.showsCatchUp || model.shortcuts.showsWhatsNew),"),
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
print("OK f6:", ", ".join(p.name for p in out))
