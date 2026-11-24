import sys, pathlib
C = pathlib.Path("feature/composer/src/main/kotlin/ai/opencode/feature/composer")
IMP_A = "import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.background\nimport androidx.compose.foundation.shape.CircleShape\nimport androidx.compose.material3.MaterialTheme\nimport androidx.compose.ui.draw.clip\n"
PILL = "\n                .padding(vertical = 7.dp)\n                .clip(CircleShape)\n                .background(MaterialTheme.colorScheme.secondaryContainer)\n                .padding(horizontal = 12.dp),"
PILL2 = PILL.replace("\n                ", "\n            ")
EDITS = {
 C/"AgentPicker.kt": [
  ("import androidx.compose.foundation.clickable\n", IMP_A),
  ('                .testTag("composer-agent")\n                .offset(y = -targetDrop),',
   '                .testTag("composer-agent")\n                .offset(y = -targetDrop)' + PILL),
 ],
 C/"modelpicker/OnePickerChip.kt": [
  ("import androidx.compose.foundation.clickable\n", IMP_A + "import androidx.compose.foundation.layout.padding\n"),
  ('            .testTag("composer-model")\n            .offset(y = -targetDrop),',
   '            .testTag("composer-model")\n            .offset(y = -targetDrop)' + PILL2),
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
print("OK f10:", ", ".join(p.name for p in out))
