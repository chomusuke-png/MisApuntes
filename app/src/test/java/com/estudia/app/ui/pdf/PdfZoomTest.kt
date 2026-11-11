package com.estudia.app.ui.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfZoomTest {

    // tres paginas en una pantalla de 400 x 800 px, con 10 px de margen y separacion
    private val layout = PdfLayout(listOf(1.4f, 1.4f, 0.7f), 400f, 800f, 10f, 10f)

    private fun assertSamePoint(before: Triple<Int, Float, Float>, after: Triple<Int, Float, Float>) {
        assertEquals(before.first, after.first)
        assertEquals(before.second, after.second, 0.001f)
        assertEquals(before.third, after.third, 0.001f)
    }

    @Test
    fun pinchingKeepsThePointBetweenTheFingersInPlace() {
        val start = PdfView(0, 0f, 1f, 0f)
        val zoomed = layout.zoom(start, 2f, 300f, 400f, 0f, 0f)
        assertEquals(2f, zoomed.scale, 0f)
        assertSamePoint(layout.pointAt(start, 300f, 400f), layout.pointAt(zoomed, 300f, 400f))
    }

    @Test
    fun theAnchorHoldsAcrossPagesAndRepeatedPinches() {
        var view = PdfView(1, 120f, 1.5f, 80f)
        val anchor = layout.pointAt(view, 150f, 600f)
        repeat(10) { view = layout.zoom(view, 1.07f, 150f, 600f, 0f, 0f) }
        assertSamePoint(anchor, layout.pointAt(view, 150f, 600f))
        repeat(10) { view = layout.zoom(view, 1 / 1.07f, 150f, 600f, 0f, 0f) }
        assertSamePoint(anchor, layout.pointAt(view, 150f, 600f))
    }

    @Test
    fun zoomStaysBetweenOneAndFour() {
        assertEquals(4f, layout.zoom(PdfView(0, 0f, 3f, 0f), 3f, 0f, 0f, 0f, 0f).scale, 0f)
        assertEquals(1f, layout.zoom(PdfView(0, 0f, 1.2f, 0f), 0.5f, 0f, 0f, 0f, 0f).scale, 0f)
    }

    @Test
    fun theViewNeverGoesPastTheEdges() {
        // alejar cerca del borde no puede dejar la vista corrida fuera de las paginas
        val view = layout.zoom(PdfView(0, 0f, 2f, 0f), 0.5f, 380f, 20f, 0f, 0f)
        assertEquals(PdfView(0, 0f, 1f, 0f), view)
        val pastEnd = layout.zoom(PdfView(2, 0f, 1f, 0f), 1f, 0f, 0f, 0f, -5000f)
        assertEquals(0f, pastEnd.x, 0f)
        // el contenido termina en la ultima pagina mas el margen de abajo
        val bottom = layout.pageTop(2, 1f) + 380f * 0.7f + 10f - 800f
        assertEquals(bottom, layout.pageTop(pastEnd.index, 1f) - 10f + pastEnd.offset, 0.01f)
    }

    @Test
    fun movingTwoFingersPansTheView() {
        val view = layout.zoom(PdfView(0, 100f, 2f, 200f), 1f, 100f, 100f, 30f, 40f)
        assertEquals(0, view.index)
        assertEquals(60f, view.offset, 0.01f)
        assertEquals(170f, view.x, 0.01f)
    }

    @Test
    fun scrollingPastAPageMovesToTheNextOne() {
        val view = layout.zoom(PdfView(0, 500f, 1f, 0f), 1f, 0f, 0f, 0f, -60f)
        assertEquals(1, view.index)
        assertEquals(560f - (380f * 1.4f + 10f), view.offset, 0.01f)
    }
}
