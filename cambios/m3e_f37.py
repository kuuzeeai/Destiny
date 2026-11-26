import os

S = "feature/voice/src/main/kotlin/ai/opencode/feature/voice/FishSpeech.kt"
U = "feature/settings/src/main/kotlin/ai/opencode/feature/settings/FishAudioSettings.kt"
if not os.path.isfile("settings.gradle.kts"):
    raise SystemExit("Corre esto dentro de ~/Destiny")
files = {f: open(f, encoding="utf-8").read() for f in (S, U)}

def change(f, old, new):
    if new in files[f]:
        return
    if old not in files[f]:
        raise SystemExit("NO ENCUENTRO un punto en " + f + " (no se cambio nada)")
    files[f] = files[f].replace(old, new, 1)

if "private fun applyPhoneVolume" not in files[S]:
    change(S, "fun test(percent: Int = 100) = speak(\"Hola, esta es tu voz de Fish Audio.\", percent / 100f)",
              "fun test(percent: Int? = null) = speak(\"Hola, esta es tu voz de Fish Audio.\", percent?.let { it / 100f })")
    change(S, "fun speak(markdown: String, volume: Float = 1f, onDone: () -> Unit = {}) {",
              "fun speak(markdown: String, volume: Float? = null, onDone: () -> Unit = {}) {")
    change(S, "        job = scope.launch {\n            try {\n",
              "        job = scope.launch {\n            if (volume != null) applyPhoneVolume(volume)\n            try {\n")
    change(S, "play(file, volume)\n", "play(file)\n")
    change(S, "            onDone()\n        }\n", "            restorePhoneVolume()\n            onDone()\n        }\n")
    change(S, "        job?.cancel()\n        job = null\n", "        job?.cancel()\n        job = null\n        restorePhoneVolume()\n")
    change(S, "private suspend fun play(file: File, volume: Float) {", "private suspend fun play(file: File) {")
    change(S, "            player.setVolume(volume, volume)\n", "")
    # Sin la linea anterior, "change" la daria por hecha: se quita aqui
    files[S] = files[S].replace("            player.setVolume(volume, volume)\n", "")
    change(S, "    private suspend fun play(file: File) {",
'''    /** Volumen multimedia que habia antes de leer, y el que puso la lectura. */
    private var previousVolume: Int? = null
    private var appliedVolume = 0

    private fun audioManager() =
        dependency(AppContext).applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Pone el volumen multimedia en [level] (0 a 1) mientras dura la lectura. */
    private fun applyPhoneVolume(level: Float) {
        runCatching {
            val audio = audioManager()
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val before = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            appliedVolume = Math.round(level * max).coerceIn(1, max)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, appliedVolume, 0)
            if (previousVolume == null) previousVolume = before
        }
    }

    /** Devuelve el volumen que habia, salvo que lo hayas cambiado tu mientras leia. */
    private fun restorePhoneVolume() {
        val before = previousVolume ?: return
        previousVolume = null
        runCatching {
            val audio = audioManager()
            if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) == appliedVolume) {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, before, 0)
            }
        }
    }

    private suspend fun play(file: File) {''')

old = "es relativo al volumen multimedia del teléfono."
files[U] = files[U].replace(old, "se aplica aunque el teléfono esté en otro volumen; al terminar vuelve como estaba.")
files[U] = files[U].replace("El volumen cambia solo según la hora y se aplica", "El volumen cambia según la hora y se aplica")

for f, text in files.items():
    open(f, "w", encoding="utf-8").write(text)
    print("  listo", os.path.basename(f))
os.makedirs("cambios", exist_ok=True)
if os.path.isfile("m3e_f37.py"):
    os.replace("m3e_f37.py", "cambios/m3e_f37.py")
print("LISTO")
