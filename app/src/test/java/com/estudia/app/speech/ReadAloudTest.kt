package com.estudia.app.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAloudTest {

    @Test
    fun shortTextIsReadInOnePiece() {
        assertEquals(listOf("Hola mundo."), splitForSpeech("  Hola\n  mundo. "))
        assertEquals(emptyList<String>(), splitForSpeech(" \n "))
    }

    @Test
    fun longTextIsCutBetweenSentences() {
        val text = "Uno dos tres. Cuatro cinco seis. Siete ocho nueve."
        val chunks = splitForSpeech(text, max = 20)
        assertEquals(listOf("Uno dos tres.", "Cuatro cinco seis.", "Siete ocho nueve."), chunks)
    }

    @Test
    fun aSentenceLongerThanTheLimitIsCutAtASpace() {
        val chunks = splitForSpeech("palabra ".repeat(10).trim(), max = 20)
        assertTrue(chunks.all { it.length <= 20 })
        assertEquals("palabra ".repeat(10).trim(), chunks.joinToString(" "))
    }

    @Test
    fun pdfLinesAreJoinedAndHyphenatedWordsRebuilt() {
        val page = "La hemato-\nlogía estudia\nla sangre.\n\nSegundo párrafo."
        assertEquals(listOf("La hematología estudia la sangre.", "Segundo párrafo."), pdfPageSegments(page))
    }

    @Test
    fun aHyphenBeforeACapitalLetterIsKept() {
        assertEquals(listOf("Covid-19 y Sars- Cov."), pdfPageSegments("Covid-19 y Sars-\nCov."))
    }

    @Test
    fun longPdfParagraphsAreGroupedBySentences() {
        val page = "Primera oración aquí. Segunda oración aquí. Tercera oración aquí."
        assertEquals(
            listOf("Primera oración aquí. Segunda oración aquí.", "Tercera oración aquí."),
            pdfPageSegments(page, target = 45),
        )
        assertEquals(emptyList<String>(), pdfPageSegments("  \n\n  "))
    }

    @Test
    fun editorReadingStartsAtTheGivenParagraph() {
        assertEquals(listOf("Uno", "Dos") to 1, parseEditorReading("""{"parts":["Uno","Dos"],"start":1}"""))
        assertEquals(listOf("Uno") to 0, parseEditorReading("""{"parts":["Uno"],"start":7}"""))
        assertEquals(null, parseEditorReading("""{"parts":[],"start":0}"""))
        assertEquals(null, parseEditorReading(""))
    }

    @Test
    fun chunksKnowWhereTheyStartInTheText() {
        val text = "Uno dos tres. Cuatro cinco seis. Siete ocho nueve."
        val chunks = splitForSpeech(text, max = 20)
        val offsets = chunkOffsets(text, chunks)
        chunks.forEachIndexed { k, chunk -> assertEquals(chunk, text.substring(offsets[k], offsets[k] + chunk.length)) }
    }

    @Test
    fun pdfSegmentsRememberWhereEachLetterCameFrom() {
        val page = "La hemato-\nlogía  estudia\nla sangre.\n\nSegundo párrafo."
        val segments = pdfPageReading(page)
        assertEquals(listOf("La hematología estudia la sangre.", "Segundo párrafo."), segments.map { it.text })
        segments.forEach { s ->
            s.text.forEachIndexed { i, c -> if (c != ' ') assertEquals(c, page[s.map[i]]) }
        }
        // la palabra unida apunta a sus dos mitades en el original
        val word = segments[0]
        val start = word.text.indexOf("hematología")
        assertEquals(page.indexOf("hemato"), word.map[start])
        assertEquals(page.indexOf("logía") + 4, word.map[start + "hematología".length - 1])
    }

    @Test
    fun boxesOfALineAreJoinedAndANewLineStartsAnother() {
        fun box(l: Float, t: Float) = floatArrayOf(l, t, l + 0.05f, t + 0.02f)
        val boxes = box(0.1f, 0.1f) + box(0.15f, 0.1f) + floatArrayOf(Float.NaN, Float.NaN, Float.NaN, Float.NaN) +
            box(0.25f, 0.101f) + box(0.1f, 0.2f)
        val lines = lineBoxes(boxes, 0, 4)
        assertEquals(2, lines.size)
        assertEquals(0.1f, lines[0].left, 0.0001f)
        assertEquals(0.3f, lines[0].right, 0.0001f)
        assertEquals(0.2f, lines[1].top, 0.0001f)
        assertEquals(emptyList<Box>(), lineBoxes(boxes, 2, 2))
    }

    @Test
    fun speedsReadNaturally() {
        assertEquals("1×", speedLabel(1f))
        assertEquals("1,25×", speedLabel(1.25f))
        assertEquals("0,5×", speedLabel(0.5f))
    }

    /** motor que no suena: guarda lo que se le pidio leer y deja avisar palabras a mano */
    private class FakeEngine : VoiceEngine {
        val spoken = mutableListOf<Pair<String, Float>>()
        var listener: VoiceEngine.Listener? = null

        override fun init(onResult: (String?) -> Unit) = onResult(null)

        override fun speak(text: String, speed: Float, listener: VoiceEngine.Listener) {
            spoken += text to speed
            this.listener = listener
        }

        override fun stop() {}

        override fun shutdown() {}
    }

    @Test
    fun changingTheSpeedWhileReadingContinuesFromTheCurrentWord() {
        val engine = FakeEngine()
        val reader = ReadAloud(engine, 1f)
        val words = mutableListOf<Pair<Int, Int>>()
        reader.onWord = { _, start, end -> words += start to end }
        reader.start(listOf("Uno dos tres cuatro"), 0)
        engine.listener!!.onWord(8, 12)
        reader.speed = 1.5f
        assertEquals("tres cuatro" to 1.5f, engine.spoken.last())
        // las palabras siguen con posiciones del trozo entero
        engine.listener!!.onWord(5, 11)
        assertEquals(13 to 19, words.last())
    }

    @Test
    fun changingTheSpeedWhilePausedOnlyAppliesOnResume() {
        val engine = FakeEngine()
        val reader = ReadAloud(engine, 1f)
        reader.start(listOf("Uno dos tres"), 0)
        engine.listener!!.onWord(4, 7)
        reader.pause()
        reader.speed = 0.5f
        assertEquals(1, engine.spoken.size)
        reader.resume()
        assertEquals("dos tres" to 0.5f, engine.spoken.last())
    }
}
