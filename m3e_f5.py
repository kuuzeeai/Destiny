import sys
from pathlib import Path

DYN = "core/src/main/kotlin/ai/opencode/core/ui/DynamicSystemTheme.kt"
SHP = "core/src/main/kotlin/ai/opencode/core/ui/OpenCodeShapes.kt"
CMP = "feature/session/src/main/kotlin/ai/opencode/feature/session/ComposerViews.kt"

CLARO = """        return lightColorScheme(
            primary = Color(0xFF4F55C7), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE0E0FF), onPrimaryContainer = Color(0xFF0A0C6B),
            secondary = Color(0xFF5C5D72), onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFE1E0F9), onSecondaryContainer = Color(0xFF191A2C),
            tertiary = Color(0xFF78536B), onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFFD8EE), onTertiaryContainer = Color(0xFF2E1126),
            background = Color(0xFFFBF8FF), onBackground = Color(0xFF1B1B21),
            surface = Color(0xFFFBF8FF), onSurface = Color(0xFF1B1B21),
            surfaceVariant = Color(0xFFE4E1EC), onSurfaceVariant = Color(0xFF46464F),
            outline = Color(0xFF777680), outlineVariant = Color(0xFFC7C5D0),
            surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF5F2FA),
            surfaceContainer = Color(0xFFEFEDF4), surfaceContainerHigh = Color(0xFFE9E7EF),
            surfaceContainerHighest = Color(0xFFE4E1E9),
        )
"""
OSCURO = """            return darkColorScheme(
                primary = Color(0xFFBFC2FF), onPrimary = Color(0xFF12159A),
                primaryContainer = Color(0xFF3A3FA8), onPrimaryContainer = Color(0xFFE0E0FF),
                secondary = Color(0xFFC5C4DD), onSecondary = Color(0xFF2E2F42),
                secondaryContainer = Color(0xFF444559), onSecondaryContainer = Color(0xFFE1E0F9),
                tertiary = Color(0xFFE8B9D5), onTertiary = Color(0xFF46263B),
                tertiaryContainer = Color(0xFF5E3C52), onTertiaryContainer = Color(0xFFFFD8EE),
                background = Color(0xFF131318), onBackground = Color(0xFFE4E1E9),
                surface = Color(0xFF131318), onSurface = Color(0xFFE4E1E9),
                surfaceVariant = Color(0xFF46464F), onSurfaceVariant = Color(0xFFC7C5D0),
                outline = Color(0xFF918F9A), outlineVariant = Color(0xFF46464F),
                surfaceContainerLowest = Color(0xFF0E0E13), surfaceContainerLow = Color(0xFF1B1B21),
                surfaceContainer = Color(0xFF1F1F25), surfaceContainerHigh = Color(0xFF2A292F),
                surfaceContainerHighest = Color(0xFF35343A),
            )
"""

archivos = {
    DYN: [
        ("import androidx.compose.material3.dynamicDarkColorScheme\n",
         "import androidx.compose.material3.darkColorScheme\nimport androidx.compose.material3.lightColorScheme\nimport androidx.compose.material3.dynamicDarkColorScheme\n"),
        ("            return dynamicDarkColorScheme(context)\n", OSCURO),
        ("        return dynamicLightColorScheme(context)\n", CLARO),
    ],
    SHP: [
        ("    val control = RoundedCornerShape(OpenCodeRadius.fill)\n",
         "    val control = RoundedCornerShape(28.dp)\n"),
    ],
    CMP: [
        ("import androidx.compose.material3.LocalContentColor\n",
         "import androidx.compose.material3.LocalContentColor\nimport androidx.compose.material3.MaterialTheme\n"),
        ("            color = OpenCodeSurfaces.color,\n"
         "            border = OpenCodeSurfaces.border,\n"
         "            shadowElevation = OpenCodeSurfaces.raisedShadow,\n"
         "        ) {\n"
         "            ComposerPillContent(",
         "            color = MaterialTheme.colorScheme.secondaryContainer,\n"
         "            shadowElevation = 0.dp,\n"
         "        ) {\n"
         "            ComposerPillContent("),
    ],
}
nuevos = {}
for ruta_s, cambios in archivos.items():
    ruta = Path(ruta_s)
    if not ruta.exists():
        sys.exit(f"No existe {ruta}. Ejecuta desde ~/Destiny. No se modificó nada.")
    t = ruta.read_text(encoding="utf-8")
    for viejo, nv in cambios:
        n = t.count(viejo)
        if n != 1:
            sys.exit(f"Texto no coincide ({n} veces) en {ruta.name}: {viejo[:50]!r}. No se modificó nada.")
        t = t.replace(viejo, nv)
    if t.count("{") != t.count("}") or t.count("(") != t.count(")"):
        sys.exit(f"Llaves/paréntesis desbalanceados en {ruta.name}. No se modificó nada.")
    nuevos[ruta] = t
for ruta, t in nuevos.items():
    ruta.write_text(t, encoding="utf-8")
print("OK: paleta vibrante fija, formas de control en píldora, chips del composer tonales.")
