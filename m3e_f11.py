import sys, pathlib
V = pathlib.Path("feature/voice/src/main/kotlin/ai/opencode/feature/voice/VoiceView.kt")
S = pathlib.Path("feature/session/src/main/kotlin/ai/opencode/feature/session/ComposerViews.kt")
EDITS = {
 V: [
  ("    const val cornerRadius = 12f\n", "    const val cornerRadius = 28f\n"),
  ("        @Composable @ReadOnlyComposable get() = OpenCodeTheme.colors.floating\n",
   "        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerHigh\n"),
  ("        @Composable @ReadOnlyComposable get() = OpenCodeTheme.colors.border\n",
   "        @Composable @ReadOnlyComposable get() = Color.Transparent\n"),
 ],
 S: [
  ("    const val cornerRadius = 12f\n", "    const val cornerRadius = 28f\n"),
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
print("OK f11:", ", ".join(p.name for p in out))
