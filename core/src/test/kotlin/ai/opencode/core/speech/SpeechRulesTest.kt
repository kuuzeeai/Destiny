package ai.opencode.core.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechRulesTest {
    @Test fun dayAndNight() {
        assertTrue(SpeechRules.isDay(7, 7, 21))
        assertTrue(SpeechRules.isDay(20, 7, 21))
        assertFalse(SpeechRules.isDay(21, 7, 21))
        assertFalse(SpeechRules.isDay(3, 7, 21))
    }

    @Test fun dayThatCrossesMidnight() {
        assertTrue(SpeechRules.isDay(23, 18, 2))
        assertTrue(SpeechRules.isDay(1, 18, 2))
        assertFalse(SpeechRules.isDay(12, 18, 2))
    }

    @Test fun markdownIsCleaned() {
        assertEquals("Hola mundo", SpeechRules.spokenText("## **Hola** [mundo](https://x.com)\n```\ncode\n```"))
    }

    @Test fun longRepliesAreNotCut() {
        val long = "Una frase. ".repeat(500).trim()
        assertEquals(long, SpeechRules.spokenText(long))
    }
}
