import sys, pathlib
K = pathlib.Path("feature/settings/src/main/kotlin/ai/opencode/feature/settings/connection/ConnectionCard.kt")
C = pathlib.Path("feature/session/src/main/kotlin/ai/opencode/feature/session/ComposerViews.kt")
EDITS = {
 K: [
  ("    val cornerRadius = OpenCodeRadius.card\n", "    val cornerRadius = OpenCodeRadius.panel\n"),
  ("                .background(colors.groupedCard)\n                .border(1.dp, colors.border, cardShape)\n",
   "                .background(MaterialTheme.colorScheme.surfaceContainer)\n"),
 ],
 C: [
  ("@Composable\nfun ComposerControlLabel(",
   "@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)\n@Composable\nfun ComposerControlLabel("),
  ("CircularProgressIndicator(Modifier.size(16.dp), color = foreground, strokeWidth = 2.dp)",
   "androidx.compose.material3.LoadingIndicator(modifier = Modifier.size(28.dp), color = foreground)"),
 ],
}
out = {}
for path, edits in EDITS.items():
    if not path.exists(): sys.exit(f"NO EXISTE {path}")
    t = path.read_text()
    for old, new in edits:
        n = t.count(old)
        if n != 1: sys.exit(f"ABORTO: {path.name}: {n} coincidencias para: {old[:60]!r}")
        t = t.replace(old, new)
    if t.count("{") != t.count("}") or t.count("(") != t.count(")"):
        sys.exit(f"ABORTO: balance roto en {path.name}")
    out[path] = t
for p, t in out.items(): p.write_text(t)
print("OK f13:", ", ".join(p.name for p in out))
