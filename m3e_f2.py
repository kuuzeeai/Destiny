import sys
from pathlib import Path

DP = "feature/session/src/main/kotlin/ai/opencode/feature/session/DraftProjects.kt"
DS = "feature/drawer/src/main/kotlin/ai/opencode/feature/drawer/style/DrawerStyle.kt"
SC = "feature/drawer/src/main/kotlin/ai/opencode/feature/drawer/list/DrawerListSupportCells.kt"

archivos = {
    "app/src/main/res/values/strings.xml": [
        ('<string name="app_name" translatable="false">OpenCode</string>',
         '<string name="app_name" translatable="false">Destiny</string>'),
    ],
    DP: [
        ("import androidx.compose.ui.draw.blur\n",
         "import androidx.compose.ui.draw.blur\nimport androidx.compose.ui.draw.clip\n"),
        (".background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = highlight), CircleShape)\n",
         ".background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = highlight), CircleShape)\n            .clip(CircleShape)\n"),
    ],
    DS: [
        ("    val textInset: Dp = 10.dp\n", "    val textInset: Dp = 16.dp\n"),
        ("    val rowCornerRadius: Dp = 6.dp\n", "    val rowCornerRadius: Dp = 24.dp\n"),
    ],
    SC: [
        ("import androidx.compose.material3.DropdownMenuItem\n",
         "import androidx.compose.material3.DropdownMenuItem\nimport androidx.compose.material3.MaterialTheme\n"),
    ],
}

nuevos = {}
for ruta_s, cambios in archivos.items():
    ruta = Path(ruta_s)
    if not ruta.exists():
        sys.exit(f"No existe {ruta}. Ejecuta desde ~/Destiny. No se modificó nada.")
    t = ruta.read_text(encoding="utf-8")
    for viejo, nuevo in cambios:
        n = t.count(viejo)
        if n != 1:
            sys.exit(f"Texto no coincide ({n} veces) en {ruta.name}: {viejo[:60]!r}. No se modificó nada.")
        t = t.replace(viejo, nuevo)
    if ruta_s == SC:
        viejo = "    val canvas = OpenCodeTheme.colors.drawerCanvas\n"
        if t.count(viejo) != 2:
            sys.exit(f"Texto no coincide ({t.count(viejo)} veces) en {ruta.name}: canvas. No se modificó nada.")
        t = t.replace(viejo, "    val canvas = MaterialTheme.colorScheme.secondaryContainer\n")
    if t.count("{") != t.count("}") or t.count("(") != t.count(")"):
        sys.exit(f"Llaves/paréntesis desbalanceados en {ruta.name}. No se modificó nada.")
    nuevos[ruta] = t
for ruta, t in nuevos.items():
    ruta.write_text(t, encoding="utf-8")
print("OK: nombre Destiny, ripple en píldora y tarjetas tonales en el drawer.")
