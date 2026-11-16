package com.estudia.app.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimingTest {

    private fun pieces(text: String, max: Int = 220) = sentenceSpans(text, max).map { text.substring(it.first, it.last + 1) }

    @Test
    fun textIsSplitIntoSentences() {
        assertEquals(listOf("Hola.", "¿Cómo estás?", "Bien: gracias"), pieces("Hola. ¿Cómo estás? Bien: gracias", max = 220).let {
            // los dos puntos tambien cortan, para que la voz empiece antes
            listOf(it[0], it[1], it[2] + " " + it[3])
        })
        assertEquals(listOf("Uno.", "Dos."), pieces("  Uno.   Dos.  "))
        assertEquals(emptyList<String>(), pieces("   "))
    }

    @Test
    fun numbersWithDotsAreNotCut() {
        assertEquals(listOf("Pesa 2.5 kg y mide 1.80 m."), pieces("Pesa 2.5 kg y mide 1.80 m."))
    }

    @Test
    fun longSentencesAreCutAtACommaOrSpace() {
        val text = "Esta es una oración bastante larga, que sigue y sigue sin terminar nunca jamás"
        val parts = pieces(text, max = 40)
        assertTrue(parts.all { it.length <= 40 })
        assertEquals("Esta es una oración bastante larga,", parts[0])
        assertEquals(text, parts.joinToString(" "))
    }

    @Test
    fun spansPointIntoTheOriginalText() {
        val text = "Primera parte. Segunda parte."
        sentenceSpans(text).forEach { assertEquals(text.substring(it.first, it.last + 1).trim(), text.substring(it.first, it.last + 1)) }
        assertEquals(15, sentenceSpans(text)[1].first)
    }

    @Test
    fun wordsKeepTheirPlaceInTheParagraph() {
        val text = "Hola mundo. Adiós"
        val words = wordSpans(text.substring(12), offset = 12)
        assertEquals(listOf(12..16), words)
        assertEquals("Adiós", text.substring(words[0].first, words[0].last + 1))
    }

    @Test
    fun longWordsAndPausesTakeLonger() {
        val text = "Sí, hematología."
        val weights = wordWeights(text, wordSpans(text))
        assertTrue(weights[1] > weights[0])
        val noPause = wordWeights("Sí hematología", wordSpans("Sí hematología"))
        assertTrue(weights[0] > noPause[0])
    }

    @Test
    fun theWordAtAPointFollowsTheWeights() {
        val weights = floatArrayOf(1f, 3f)
        assertEquals(0, wordAt(weights, 0f))
        assertEquals(0, wordAt(weights, 0.2f))
        assertEquals(1, wordAt(weights, 0.3f))
        assertEquals(1, wordAt(weights, 1f))
        assertEquals(-1, wordAt(FloatArray(0), 0.5f))
    }
}
