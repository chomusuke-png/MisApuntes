package com.estudia.app.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfStrokeTest {

    @Test
    fun strokesSavedBeforeThePenExistedLoadAsHighlighter() {
        val saved = """{"page":2,"color":-256,"width":0.02,"points":[0.1,0.2,0.3,0.2],"straight":true,"alpha":0.5}"""
        val stroke = Json.decodeFromString(PdfStroke.serializer(), saved)
        assertFalse(stroke.pen)
        assertEquals(2, stroke.page)
        assertEquals(0.5f, stroke.alpha)
    }

    @Test
    fun penSurvivesSavingAndLoading() {
        val stroke = PdfStroke(0, -16777216, 0.003f, listOf(0.1f, 0.1f), pen = true)
        val loaded = Json.decodeFromString(PdfStroke.serializer(), Json.encodeToString(PdfStroke.serializer(), stroke))
        assertTrue(loaded.pen)
        assertEquals(stroke, loaded)
    }
}
