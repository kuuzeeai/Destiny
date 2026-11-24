import sys, pathlib
p = pathlib.Path("feature/drawer/src/main/kotlin/ai/opencode/feature/drawer/motion/DrawerSearchPill.kt")
if not p.exists(): sys.exit("NO EXISTE")
t = p.read_text()
for old, new in [
 ("                AnimatedVisibility(\n                    !isSearching,", "                androidx.compose.animation.AnimatedVisibility(\n                    !isSearching,"),
 ("                AnimatedVisibility(\n                    isSearching,", "                androidx.compose.animation.AnimatedVisibility(\n                    isSearching,"),
]:
    n = t.count(old)
    if n != 1: sys.exit(f"ABORTO: {n} coincidencias para: {old[:60]!r}")
    t = t.replace(old, new)
if t.count("{") != t.count("}") or t.count("(") != t.count(")"): sys.exit("ABORTO: balance roto")
p.write_text(t)
print("OK f8b")
