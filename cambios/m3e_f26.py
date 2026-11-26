import os, sys
D = "feature/drawer/src/main/kotlin/ai/opencode/feature/drawer/"
def edit(rel, anchor, new, marker):
    if not os.path.exists(rel):
        sys.exit("NO EXISTE: " + rel)
    text = open(rel, encoding="utf-8").read()
    if marker in text:
        print("  ya estaba:", rel)
        return
    n = text.count(anchor)
    if n != 1:
        sys.exit("NO PUDE EDITAR " + rel + ": ancla " + str(n) + " veces")
    open(rel, "w", encoding="utf-8").write(text.replace(anchor, new, 1))
    print("  editado:", rel)
L = D + "list/InboxSidebarList.kt"
edit(L,
     "    openArchived: () -> Unit,\n    modifier: Modifier = Modifier,\n) {\n    val rows by store.observe { it.inboxSidebarRows() }",
     "    openArchived: () -> Unit,\n    openTerminal: () -> Unit = {},\n    modifier: Modifier = Modifier,\n) {\n    val rows by store.observe { it.inboxSidebarRows() }",
     "openTerminal: () -> Unit = {},")
edit(L,
     "            if (archivedCount > 0) {\n                item(key = \"inbox-archived\") {",
     "            item(key = \"inbox-terminal\") {\n                TerminalEntryRow(onClick = openTerminal)\n            }\n            if (archivedCount > 0) {\n                item(key = \"inbox-archived\") {",
     "inbox-terminal")
edit(D + "HomeViewDrawer.kt",
     "            openArchived = openArchived,\n            modifier = Modifier.fillMaxSize().then(beneathArchived),",
     "            openArchived = openArchived,\n            openTerminal = { showsTerminal = true },\n            modifier = Modifier.fillMaxSize().then(beneathArchived),",
     "openTerminal = { showsTerminal = true },\n            modifier")
print("LISTO")
