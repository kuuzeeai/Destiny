import base64, os, zlib

S = "feature/settings/src/main/kotlin/ai/opencode/feature/settings/"
UI = S + "FishAudioSettings.kt"
VIEW = S + "VoiceSettingsView.kt"
SPEECH = "feature/voice/src/main/kotlin/ai/opencode/feature/voice/FishSpeech.kt"
KEYS = "core/src/main/kotlin/ai/opencode/core/sharing/SharedKeys.kt"

for f in (UI, VIEW, SPEECH, KEYS):
    if not os.path.isfile(f):
        raise SystemExit("NO ENCUENTRO " + f + " (corre esto dentro de ~/Destiny)")

files = {f: open(f, encoding="utf-8").read() for f in (VIEW, SPEECH, KEYS)}

def change(f, old, new, done):
    t = files[f]
    if done in t:
        return
    if old not in t:
        raise SystemExit("NO ENCUENTRO un punto en " + f + " (no se cambio nada)")
    files[f] = t.replace(old, new, 1)

# Claves nuevas: volumen y hora de inicio de dia y noche
change(KEYS, "    val fishReadsReplies = ",
       """    val fishDayVolume = SharedKey.appStorage("fish-day-volume", 100)
    val fishNightVolume = SharedKey.appStorage("fish-night-volume", 40)
    val fishDayStart = SharedKey.appStorage("fish-day-start", 7)
    val fishNightStart = SharedKey.appStorage("fish-night-start", 21)

    /** Whether the agent's replies are read aloud. */
    val fishReadsReplies = """, "fishDayVolume")

# Volumen solo para las respuestas del chat; el asistente no cambia
change(SPEECH, "        if (readsReplies) speak(markdown)\n",
       "        if (readsReplies) speak(markdown, replyVolume())\n", "speak(markdown, replyVolume())")
change(SPEECH, """    fun test() = speak("Hola, esta es tu voz de Fish Audio.")\n""",
       """    fun test(percent: Int = 100) = speak("Hola, esta es tu voz de Fish Audio.", percent / 100f)

    /** Volumen (0 a 1) segun la hora: de dia o de noche. */
    private fun replyVolume(): Float {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val day = shared(SharedKeys.fishDayStart).value
        val night = shared(SharedKeys.fishNightStart).value
        val isDay = when {
            day == night -> true
            day < night -> hour >= day && hour < night
            else -> hour >= day || hour < night
        }
        val percent = shared(if (isDay) SharedKeys.fishDayVolume else SharedKeys.fishNightVolume).value
        return percent.coerceIn(0, 100) / 100f
    }
""", "fun replyVolume")
change(SPEECH, "fun speak(markdown: String, onDone: () -> Unit = {}) {",
       "fun speak(markdown: String, volume: Float = 1f, onDone: () -> Unit = {}) {", "volume: Float = 1f")
change(SPEECH, "play(file)\n", "play(file, volume)\n", "play(file, volume)")
change(SPEECH, "private suspend fun play(file: File) {", "private suspend fun play(file: File, volume: Float) {",
       "play(file: File, volume: Float)")
change(SPEECH, "            player.setAudioAttributes(attributes)\n",
       "            player.setAudioAttributes(attributes)\n            player.setVolume(volume, volume)\n",
       "player.setVolume(volume, volume)")

# Pantalla de Voz en tres bloques
t = files[VIEW]
if "KeyRows()" not in t:
    a = t.find('        settingsSection(key = "assistant"')
    end = "            GroqRows()\n        }\n"
    b = t.find(end)
    if a < 0 or b < 0:
        raise SystemExit("NO ENCUENTRO las secciones en VoiceSettingsView.kt (no se cambio nada)")
    new = """        settingsSection(key = "replies", header = "Respuestas del chat", footer = footerText("El volumen cambia solo según la hora y es relativo al volumen multimedia del teléfono. No afecta al asistente del micrófono.")) {
            ReplyRows()
        }
        settingsSection(key = "assistant", header = "Asistente (botón de encendido)", footer = footerText("Si no eliges chat, usa el último que viste.")) {
            AssistantRows()
            SettingsDivider()
            BehaviorRows()
        }
        settingsSection(key = "keys", header = "Claves y voz", footer = footerText("Las claves se quedan en este teléfono. Si una se agota, se usan las de respaldo.")) {
            KeyRows()
        }
"""
    files[VIEW] = t[:a] + new + t[b + len(end):]

UI_DATA = """
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
"""

for f, text in files.items():
    open(f, "w", encoding="utf-8").write(text)
    print("  listo", os.path.basename(f))
open(UI, "wb").write(zlib.decompress(base64.b64decode(UI_DATA)))
print("  listo FishAudioSettings.kt")
print("LISTO")
