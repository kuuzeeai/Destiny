import sys
from pathlib import Path

L = "feature/drawer/src/main/kotlin/ai/opencode/feature/drawer/list/"
AR = L + "ArchivedPage.kt"
SE = L + "DrawerListSessionCell.kt"

archivos = {
    AR: [
        ("import androidx.compose.material3.Text\n",
         "import androidx.compose.material3.MaterialTheme\nimport androidx.compose.material3.Text\n"),
        ("            .then(if (regularList) Modifier.padding(horizontal = DrawerMetrics.rowEdge) else Modifier)\n"
         "            .clip(RoundedCornerShape(if (regularList) DrawerMetrics.rowCornerRadius else 0.dp))\n",
         "            .then(if (regularList) Modifier.padding(horizontal = DrawerMetrics.rowEdge, vertical = 3.dp) else Modifier)\n"
         "            .clip(RoundedCornerShape(if (regularList) DrawerMetrics.rowCornerRadius else 0.dp))\n"
         "            .background(if (regularList) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)\n"),
    ],
    SE: [
        ("    val cornerRadius = DrawerMetrics.rowCornerRadius\n",
         "    val cornerRadius = DrawerMetrics.rowCornerRadius\n"
         "    val cardFill = MaterialTheme.colorScheme.secondaryContainer\n"),
        ("                    val tintAlpha = checkedTintAlpha()\n",
         "                    if (!row.isLifted) {\n"
         "                        val inset = 2.dp.toPx()\n"
         "                        drawRoundRect(\n"
         "                            cardFill,\n"
         "                            topLeft = Offset(0f, inset),\n"
         "                            size = Size(size.width, (size.height - 2f * inset).coerceAtLeast(0f)),\n"
         "                            cornerRadius = CornerRadius(cornerRadius.toPx()),\n"
         "                        )\n"
         "                    }\n"
         "                    val tintAlpha = checkedTintAlpha()\n"),
    ],
}
nuevos = {}
for ruta_s, cambios in archivos.items():
    ruta = Path(ruta_s)
    if not ruta.exists():
        sys.exit(f"No existe {ruta}. Ejecuta desde ~/Destiny. No se modificó nada.")
    t = ruta.read_text(encoding="utf-8")
    if "cardFill" in t or "MaterialTheme.colorScheme.secondaryContainer else" in t:
        sys.exit(f"Ya estaba aplicado en {ruta.name}. No se modificó nada.")
    for viejo, nuevo in cambios:
        n = t.count(viejo)
        if n != 1:
            sys.exit(f"Texto no coincide ({n} veces) en {ruta.name}: {viejo[:60]!r}. No se modificó nada.")
        t = t.replace(viejo, nuevo)
    if t.count("{") != t.count("}") or t.count("(") != t.count(")"):
        sys.exit(f"Llaves/paréntesis desbalanceados en {ruta.name}. No se modificó nada.")
    nuevos[ruta] = t
for ruta, t in nuevos.items():
    ruta.write_text(t, encoding="utf-8")
print("OK: Archivadas en píldora y sesiones en tarjetas.")
