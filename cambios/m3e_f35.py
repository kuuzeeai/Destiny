import base64, glob, json, os, shutil, zlib

DATA = """
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
ixf4TJG/rarHNv+WnbiwUePqav4/PTJYzg==
"""
d = json.loads(zlib.decompress(base64.b64decode(DATA)))
if not os.path.isfile("settings.gradle.kts"):
    raise SystemExit("Corre esto dentro de ~/Destiny")
keys = "core/src/main/kotlin/ai/opencode/core/sharing/SharedKeys.kt"
if "fishDayVolume" not in open(keys, encoding="utf-8").read():
    raise SystemExit("Primero aplica m3e_f34.py")

files = {}
for f, start, end, new, mark in d["edits"]:
    if f not in files:
        files[f] = open(f, encoding="utf-8").read()
    t = files[f]
    done = mark[1:] not in t if mark.startswith("!") else mark in t
    if done:
        continue
    a = t.find(start)
    if a < 0:
        raise SystemExit("NO ENCUENTRO un punto en " + f + " (no se cambio nada)")
    if end is None:
        b = len(t)
    elif end == start:
        b = a
    else:
        b = t.find(end, a)
        if b < 0:
            raise SystemExit("NO ENCUENTRO el final en " + f + " (no se cambio nada)")
    files[f] = t[:a] + new + t[b:]

files.update(d["new"])
for f, text in files.items():
    os.makedirs(os.path.dirname(f), exist_ok=True)
    open(f, "w", encoding="utf-8").write(text)
    print("  listo", os.path.basename(f))

# Orden: los scripts de cambios pasan a la carpeta cambios/
os.makedirs("cambios", exist_ok=True)
for f in glob.glob("m3e_*.py") + glob.glob("limpiar.sh"):
    shutil.move(f, os.path.join("cambios", f))
# limpiar.sh borraba las pruebas: que ya no lo haga
p = "cambios/limpiar.sh"
if os.path.isfile(p):
    lines = open(p, encoding="utf-8").read().split("\n")
    lines = [l for l in lines if not l.startswith("find . -type d \\( -path '*/src/test'")]
    open(p, "w", encoding="utf-8").write("\n".join(lines))
print("LISTO")
