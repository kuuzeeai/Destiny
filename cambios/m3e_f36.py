import os, re

D = "feature/drawer/src/main/kotlin/ai/opencode/feature/drawer/HomeViewDrawer.kt"
H = "feature/session/src/main/kotlin/ai/opencode/feature/session/SessionViewHeader.kt"
T = "app/src/main/res/values/themes.xml"
U = "feature/settings/src/main/kotlin/ai/opencode/feature/settings/FishAudioSettings.kt"
V = "feature/settings/src/main/kotlin/ai/opencode/feature/settings/VoiceSettingsView.kt"

if not os.path.isfile("settings.gradle.kts"):
    raise SystemExit("Corre esto dentro de ~/Destiny")
files = {f: open(f, encoding="utf-8").read() for f in (D, H, T, U, V)}

def change(f, old, new, done):
    if done(files[f]):
        return
    if old not in files[f]:
        raise SystemExit("NO ENCUENTRO un punto en " + f + " (no se cambio nada)")
    files[f] = files[f].replace(old, new, 1)

# 1) Burbuja Terminal sin icono
old = """                        onClick = { showsTerminal = true },
                        icon = SFSymbol.Terminal,
"""
change(D, old, "                        onClick = { showsTerminal = true },\n",
       lambda t: "icon = SFSymbol.Terminal,\n                    )" not in t)

# 2) Numero del porcentaje dentro de la bolita
old = "        SessionContextGauge(fraction = fraction, modifier = Modifier.fillMaxSize().padding(10.dp))\n"
new = """        SessionContextGauge(fraction = fraction, modifier = Modifier.fillMaxSize().padding(5.dp))
        androidx.compose.material3.Text(
            ai.opencode.feature.session.header.SessionContextRing.percent(fraction).toString(),
            modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = OpenCodeTheme.colors.text,
            maxLines = 1,
        )
"""
change(H, old, new, lambda t: "SessionContextRing.percent(fraction).toString()" in t)
if "import ai.opencode.core.ui.OpenCodeTheme\n" not in files[H]:
    files[H] = files[H].replace("import ", "import ai.opencode.core.ui.OpenCodeTheme\nimport ", 1)

# 3) Pantalla de entrada sin icono
change(T, 'parent="Theme.SplashScreen.IconBackground"', 'parent="Theme.SplashScreen"',
       lambda t: 'parent="Theme.SplashScreen"' in t)
change(T, "@drawable/ic_launcher_foreground", "@android:color/transparent",
       lambda t: "@android:color/transparent" in t)
files[T] = files[T].replace('        <item name="windowSplashScreenIconBackgroundColor">@color/ic_launcher_background</item>\n', "")

# 4) Volumen de dia y noche solo con la lectura encendida
change(U, """    SettingsDivider()
    VolumeBlock("Día",""", """    if (!readsOn) return
    SettingsDivider()
    VolumeBlock("Día",""", lambda t: "if (!readsOn) return" in t)
change(U, """    VolumeBlock("Noche", shared(SharedKeys.fishNightVolume), shared(SharedKeys.fishNightStart), !keyText.isNullOrBlank())
""", """    VolumeBlock("Noche", shared(SharedKeys.fishNightVolume), shared(SharedKeys.fishNightStart), !keyText.isNullOrBlank())
    SettingsFooterText(
        "El volumen cambia solo según la hora y es relativo al volumen multimedia del teléfono. No afecta al asistente del micrófono.",
        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
""", lambda t: "SettingsFooterText(" in t)
files[V] = re.sub(r'(header = "Respuestas del chat"), footer = footerText\("[^"]*"\)', r"\1", files[V])

for f, text in files.items():
    open(f, "w", encoding="utf-8").write(text)
    print("  listo", os.path.basename(f))
os.makedirs("cambios", exist_ok=True)
me = "m3e_f36.py"
if os.path.isfile(me):
    os.replace(me, "cambios/" + me)
print("LISTO")
