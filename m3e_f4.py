import sys
from pathlib import Path

D = "feature/drawer/src/main/kotlin/ai/opencode/feature/drawer/"
PILL = D + "motion/DrawerSearchPill.kt"
HOME = D + "HomeViewDrawer.kt"
ROWS = "feature/settings/src/main/kotlin/ai/opencode/feature/settings/SettingsRows.kt"

def bloque(sangria):
    s = " " * sangria
    return (f"{s}color = OpenCodeSurfaces.color,\n"
            f"{s}border = OpenCodeSurfaces.border,\n"
            f"{s}shadowElevation = OpenCodeSurfaces.raisedShadow,\n")

def nuevo(sangria, color):
    s = " " * sangria
    return f"{s}color = MaterialTheme.colorScheme.{color},\n{s}shadowElevation = 0.dp,\n"

archivos = {
    PILL: [(bloque(8), nuevo(8, "secondaryContainer"), 2)],
    HOME: [
        (bloque(8), nuevo(8, "primaryContainer"), 1),
        (bloque(12), nuevo(12, "secondaryContainer"), 1),
    ],
    ROWS: [
        ("            color = OpenCodeTheme.colors.groupedCard,\n"
         "            shape = RoundedCornerShape(OpenCodeRadius.card),\n"
         "            border = BorderStroke(1.dp, OpenCodeTheme.colors.border),\n",
         "            color = MaterialTheme.colorScheme.surfaceContainer,\n"
         "            shape = RoundedCornerShape(OpenCodeRadius.panel),\n", 1),
    ],
}
nuevos = {}
for ruta_s, cambios in archivos.items():
    ruta = Path(ruta_s)
    if not ruta.exists():
        sys.exit(f"No existe {ruta}. Ejecuta desde ~/Destiny. No se modificó nada.")
    t = ruta.read_text(encoding="utf-8")
    for viejo, nv, veces in cambios:
        n = t.count(viejo)
        if n != veces:
            sys.exit(f"Texto no coincide ({n} veces, se esperaban {veces}) en {ruta.name}: {viejo[:50]!r}. No se modificó nada.")
        t = t.replace(viejo, nv)
    if t.count("{") != t.count("}") or t.count("(") != t.count(")"):
        sys.exit(f"Llaves/paréntesis desbalanceados en {ruta.name}. No se modificó nada.")
    nuevos[ruta] = t
for ruta, t in nuevos.items():
    ruta.write_text(t, encoding="utf-8")
print("OK: buscador y botones del drawer tonales, tarjetas de Ajustes tonales.")
