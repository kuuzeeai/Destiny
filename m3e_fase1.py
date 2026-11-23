#!/usr/bin/env python3
"""Fase 1 del rediseño Material 3 Expressive: formas, tipografía, colores y componentes base.
Ejecutar en la raíz del repo:  python3 m3e_fase1.py
Si algún texto no se encuentra, NO modifica nada y avisa cuál falló."""
import sys, pathlib

UI = pathlib.Path("core/src/main/kotlin/ai/opencode/core/ui")
if not UI.is_dir():
    sys.exit("Ejecuta esto en la raíz del repo (donde está settings.gradle.kts)")

# (archivo, [(viejo, nuevo), ...])
EDITS = [
    ("OpenCodeShapes.kt", [
        ("val xs: Dp = 4.dp", "val xs: Dp = 6.dp"),
        ("val sm: Dp = 6.dp", "val sm: Dp = 12.dp"),
        ("val fill: Dp = 8.dp", "val fill: Dp = 16.dp"),
        ("val control: Dp = 8.dp", "val control: Dp = 16.dp"),
        ("val card: Dp = 10.dp", "val card: Dp = 20.dp"),
        ("val tile: Dp = 10.dp", "val tile: Dp = 20.dp"),
        ("val panel: Dp = 12.dp", "val panel: Dp = 28.dp"),
        ("val sheet: Dp = 16.dp", "val sheet: Dp = 28.dp"),
    ]),
    ("OpenCodeTypography.kt", [
        ("val largeTitle = style(28, 34, FontWeight.Medium, -0.4)", "val largeTitle = style(32, 40, FontWeight.Bold)"),
        ("val title1 = style(24, 30, FontWeight.Medium, -0.32)", "val title1 = style(28, 36, FontWeight.Bold)"),
        ("val title2 = style(20, 26, FontWeight.Medium, -0.32)", "val title2 = style(22, 28, FontWeight.SemiBold)"),
        ("val title3 = style(18, 24, FontWeight.Medium, -0.16)", "val title3 = style(20, 28, FontWeight.SemiBold)"),
        ("val headline = style(16, 22, FontWeight.Medium, -0.16)", "val headline = style(16, 24, FontWeight.SemiBold, 0.1)"),
        ("val body = style(15, 23)", "val body = style(16, 24)"),
        ("val callout = style(15, 21)", "val callout = style(16, 22)"),
    ]),
    ("DynamicSystemTheme.kt", [
        ("groupedCanvas = scheme.surfaceContainer,", "groupedCanvas = scheme.surface,"),
        ("groupedCard = scheme.surfaceBright,", "groupedCard = scheme.surfaceContainerHigh,"),
    ]),
    ("OpenCodeButtons.kt", [
        ("        shape = OpenCodeShape.control,\n        colors = colors,\n        contentPadding = OpenCodeButtonDefaults.contentPadding,\n        interactionSource = interactions,\n        content = content,\n    )\n}\n\n/** [OpenCodePrimaryButton] with a text label. */",
         "        shape = OpenCodeShape.capsule,\n        colors = colors,\n        contentPadding = OpenCodeButtonDefaults.contentPadding,\n        interactionSource = interactions,\n        content = content,\n    )\n}\n\n/** [OpenCodePrimaryButton] with a text label. */"),
        ("val container = OpenCodeSurfaces.neutralFill", "val container = MaterialTheme.colorScheme.secondaryContainer"),
        ("contentColor = OpenCodeTheme.colors.text)\n    }\n    val interactions", "contentColor = MaterialTheme.colorScheme.onSecondaryContainer)\n    }\n    val interactions"),
        ("        shape = OpenCodeShape.control,\n        border = OpenCodeSurfaces.border,\n", "        shape = OpenCodeShape.capsule,\n"),
    ]),
    ("TapTarget.kt", [
        ("shape = OpenCodeShape.card,", "shape = OpenCodeShape.circle,"),
        ("border = if (tint == null) OpenCodeSurfaces.border else null,", "border = null,"),
    ]),
    ("OpenCodeSurfaces.kt", [
        ("    border(OpenCodeSurfaces.hairline, OpenCodeTheme.colors.border, shape)", "    this"),
        ("BorderStroke(hairline, OpenCodeTheme.colors.border)", "BorderStroke(hairline, Color.Transparent)"),
        ("    !isDark -> canvas\n        theme == AppTheme.System -> codeBand.copy(alpha = 1f)\n",
         "    theme == AppTheme.System -> userBubble.copy(alpha = 1f)\n        !isDark -> canvas\n"),
    ]),
]

# 1) Preparar todo en memoria; si algo falla, no se escribe nada.
nuevos = {}
for nombre, cambios in EDITS:
    ruta = UI / nombre
    if not ruta.exists():
        sys.exit(f"FALTA el archivo {ruta}")
    texto = ruta.read_text(encoding="utf-8")
    for viejo, nuevo in cambios:
        if texto.count(viejo) != 1:
            sys.exit(f"NO se encontró (o aparece más de una vez) en {nombre}:\n---\n{viejo}\n---\nNo se modificó nada.")
        texto = texto.replace(viejo, nuevo)
    nuevos[ruta] = texto

# 2) Formas de Material: escala expressive (más redondeada)
ruta = UI / "OpenCodeShapes.kt"
t = nuevos[ruta]
viejo_shapes = """    extraSmall = RoundedCornerShape(OpenCodeRadius.sm),
    small = RoundedCornerShape(OpenCodeRadius.sm),
    medium = RoundedCornerShape(OpenCodeRadius.fill),
    large = RoundedCornerShape(OpenCodeRadius.card),
    extraLarge = RoundedCornerShape(OpenCodeRadius.sheet),"""
nuevo_shapes = """    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),"""
if t.count(viejo_shapes) != 1:
    sys.exit("NO se encontró la escala de formas en OpenCodeShapes.kt. No se modificó nada.")
nuevos[ruta] = t.replace(viejo_shapes, nuevo_shapes)

for ruta, texto in nuevos.items():
    ruta.write_text(texto, encoding="utf-8")
    print("OK ", ruta)
print("\nListo. Ahora: git add -A && git commit -m 'Diseño Material 3 Expressive: fase 1' && git push")
