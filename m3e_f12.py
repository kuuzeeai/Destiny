import sys, pathlib
F = pathlib.Path("core/src/main/kotlin/ai/opencode/core/ui/OpenCodeTextField.kt")
C = pathlib.Path("feature/session/src/main/kotlin/ai/opencode/feature/session/ComposerViews.kt")
T = pathlib.Path("feature/transcript/src/main/kotlin/ai/opencode/feature/transcript/TranscriptTunables.kt")
EDITS = {
 F: [
  ('''        val container = if (colors.isDark) {
            lerp(scheme.surfaceContainerLow, scheme.surfaceContainerHigh, 0.5f)
        } else {
            scheme.surfaceContainerLowest
        }
''', "        val container = scheme.surfaceContainerHigh\n"),
  ("        val border = if (colors.strongBorders) colors.tertiaryText else scheme.outline\n",
   "        val border = if (colors.strongBorders) colors.tertiaryText else Color.Transparent\n"),
  ("        val shadow = if (colors.isDark) Color.Transparent else Color.Black.copy(alpha = 0.25f)\n",
   "        val shadow = Color.Transparent\n"),
 ],
 C: [
  ("            .clip(RoundedCornerShape(8.dp))\n            .background(background),",
   "            .clip(RoundedCornerShape(percent = 50))\n            .background(background),"),
 ],
 T: [
  ('"Bubble corner radius", default = 10f, range = 8.0..28.0', '"Bubble corner radius", default = 24f, range = 8.0..28.0'),
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
print("OK f12:", ", ".join(p.name for p in out))
