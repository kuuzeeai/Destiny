import sys
from pathlib import Path

ruta = Path("feature/session/src/main/kotlin/ai/opencode/feature/session/DraftProjects.kt")
if not ruta.exists():
    sys.exit("No existe DraftProjects.kt. Ejecuta desde ~/Destiny. No se modificó nada.")
t = ruta.read_text(encoding="utf-8")
if "MaterialTheme.colorScheme.secondaryContainer" in t:
    sys.exit("Ya estaba aplicado. No se modificó nada.")

cambios = [
    ("import androidx.compose.foundation.shape.RoundedCornerShape\n",
     "import androidx.compose.foundation.shape.CircleShape\nimport androidx.compose.foundation.shape.RoundedCornerShape\n"),
    ("import androidx.compose.material3.ExperimentalMaterial3Api\n",
     "import androidx.compose.material3.ExperimentalMaterial3Api\nimport androidx.compose.material3.MaterialTheme\n"),
    (".background(OpenCodeTheme.colors.text.copy(alpha = 0.06f), RoundedCornerShape(8.dp)),",
     ".background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),"),
    (".heightIn(min = 48.dp)\n            .background(OpenCodeTheme.colors.text.copy(alpha = 0.06f * highlight), RoundedCornerShape(8.dp))",
     ".heightIn(min = 56.dp)\n            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = highlight), CircleShape)"),
    (".padding(start = 17.dp, end = 16.dp)", ".padding(start = 20.dp, end = 20.dp)"),
]
for viejo, nuevo in cambios:
    n = t.count(viejo)
    if n != 1:
        sys.exit(f"Texto no coincide ({n} veces): {viejo[:60]!r}. No se modificó nada.")
    t = t.replace(viejo, nuevo)
if t.count("{") != t.count("}") or t.count("(") != t.count(")"):
    sys.exit("Llaves/paréntesis desbalanceados. No se modificó nada.")
ruta.write_text(t, encoding="utf-8")
print("OK: lista de proyectos en píldoras tonales (fase 2, paso 1).")
