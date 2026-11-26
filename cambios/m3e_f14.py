import sys, pathlib
C = pathlib.Path("feature/session/src/main/kotlin/ai/opencode/feature/session/ComposerViews.kt")
K = pathlib.Path("feature/settings/src/main/kotlin/ai/opencode/feature/settings/connection/ConnectionCard.kt")
EDITS = {
 C: [
  ("    faceDiameter: Float = ComposerTunables.buttonDiameter,\n) {\n    val reduceMotion = rememberReduceMotion()\n    val fontScale",
   "    faceDiameter: Float = ComposerTunables.buttonDiameter,\n    cornerPercent: Int = 50,\n) {\n    val reduceMotion = rememberReduceMotion()\n    val fontScale"),
  ("            .clip(RoundedCornerShape(percent = 50))\n            .background(background),\n        contentAlignment = Alignment.Center,\n    ) {\n        AnimatedContent(",
   "            .clip(RoundedCornerShape(percent = cornerPercent.coerceIn(0, 100)))\n            .background(background),\n        contentAlignment = Alignment.Center,\n    ) {\n        AnimatedContent("),
  ("    val press = remember { Animatable(1f) }\n    val symbol = control.symbolName\n",
   "    // Shape morph: the circle squares off into Stop and springs back to a circle.\n    val corner by androidx.compose.animation.core.animateIntAsState(\n        targetValue = if (control == ComposerTrailingControl.Stop) 30 else 50,\n        animationSpec = if (reduceMotion) androidx.compose.animation.core.tween(0) else androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 300f),\n        label = \"trailingCorner\",\n    )\n    val press = remember { Animatable(1f) }\n    val symbol = control.symbolName\n"),
  ("        faceDiameter = diameter,\n    )\n}\n\n// endregion\n\n// region Pills",
   "        faceDiameter = diameter,\n        cornerPercent = corner,\n    )\n}\n\n// endregion\n\n// region Pills"),
 ],
 K: [
  ("import ai.opencode.core.motion.MotionTunables\n", "import ai.opencode.core.motion.MotionTunables\nimport ai.opencode.core.motion.motionPress\n"),
  ("            .fillMaxWidth()\n                .clip(cardShape)\n                .background(MaterialTheme.colorScheme.surfaceContainer)\n",
   "            .fillMaxWidth()\n                .motionPress(interaction)\n                .clip(cardShape)\n                .background(MaterialTheme.colorScheme.surfaceContainer)\n"),
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
print("OK f14:", ", ".join(p.name for p in out))
