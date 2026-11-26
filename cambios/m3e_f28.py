F = "feature/session/src/main/kotlin/ai/opencode/feature/session/SshTerminalSheet.kt"
IMP = "import androidx.compose.foundation.layout.fillMaxSize\n"
HEAD = "package ai.opencode.feature.session\n\n"
t = open(F, encoding="utf-8").read()
if IMP in t:
    print("  ya estaba")
elif t.count(HEAD) != 1:
    raise SystemExit("NO ENCUENTRO el package")
else:
    open(F, "w", encoding="utf-8").write(t.replace(HEAD, HEAD + IMP, 1))
    print("  editado")
print("LISTO")
