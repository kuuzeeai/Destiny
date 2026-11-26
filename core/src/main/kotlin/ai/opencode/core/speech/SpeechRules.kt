package ai.opencode.core.speech

/** Reglas de la lectura en voz alta, sin Android, para poder probarlas. */
object SpeechRules {
    /** ¿La hora [hour] (0-23) cae en el tramo de día que empieza en [dayStart] y acaba en [nightStart]? */
    fun isDay(hour: Int, dayStart: Int, nightStart: Int): Boolean = when {
        dayStart == nightStart -> true
        dayStart < nightStart -> hour >= dayStart && hour < nightStart
        else -> hour >= dayStart || hour < nightStart
    }

    /** El markdown como debe sonar: sin código, enlaces ni marcas. Se lee completo. */
    fun spokenText(markdown: String): String {
        var t = markdown
        t = Regex("```[\\s\\S]*?```").replace(t, " ")
        t = Regex("!\\[[^\\]]*]\\([^)]*\\)").replace(t, " ")
        t = Regex("\\[([^\\]]+)]\\([^)]*\\)").replace(t, "$1")
        t = Regex("https?://\\S+").replace(t, " ")
        t = Regex("(?m)^\\s{0,3}#{1,6}\\s*").replace(t, "")
        t = Regex("(?m)^\\s*[-*+]\\s+").replace(t, "")
        t = t.replace(Regex("[`*~>|]"), "")
        return Regex("\\s+").replace(t, " ").trim()
    }
}
