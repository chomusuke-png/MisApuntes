package com.estudia.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeCurvesTest {

    @Test
    fun aSinglePointHasNoCurves() {
        assertEquals(0, smoothCurves(listOf(0.1f, 0.2f)).size)
    }

    @Test
    fun twoPointsMakeAStraightLine() {
        assertArrayEquals(floatArrayOf(1f, 0f, 1f, 0f), smoothCurves(listOf(0f, 0f, 1f, 0f)), 0f)
    }

    @Test
    fun cornersAreRoundedThroughMidpointsAndTheStrokeEndsAtTheLastPoint() {
        val curves = smoothCurves(listOf(0f, 0f, 2f, 0f, 2f, 2f, 4f, 2f))
        assertArrayEquals(
            floatArrayOf(
                2f, 0f, 2f, 1f,
                2f, 2f, 3f, 2f,
                4f, 2f, 4f, 2f,
            ),
            curves, 0f,
        )
    }

    @Test
    fun segmentsFollowTheCurveAndBlendTheWidths() {
        val segments = widthSegments(listOf(0f, 0f, 2f, 0f, 2f, 2f), listOf(1f, 1f, 0.5f), steps = 2)
        assertEquals(2 * 2 * 5, segments.size)
        // parte en el primer punto y termina en el ultimo
        assertEquals(0f, segments[0], 0f)
        assertEquals(0f, segments[1], 0f)
        assertEquals(2f, segments[17], 0f)
        assertEquals(2f, segments[18], 0f)
        // cada tramo empieza donde termina el anterior
        for (i in 5 until segments.size step 5) {
            assertEquals(segments[i - 3], segments[i], 0f)
            assertEquals(segments[i - 2], segments[i + 1], 0f)
        }
        // el grosor baja de a poco hacia el del ultimo punto
        val widths = (4 until segments.size step 5).map { segments[it] }
        assertEquals(widths.sortedDescending(), widths)
        assertTrue(widths.first() <= 1f && widths.last() > 0.5f)
    }

    @Test
    fun withoutStabilizerThePenFollowsTheFingerExactly() {
        val pen = PenInput(0f, 1f, 0f, 0f, 0)
        pen.move(10f, 5f, 8)
        assertEquals(10f, pen.x, 0f)
        assertEquals(5f, pen.y, 0f)
    }

    @Test
    fun theStabilizerTrailsBehindAndCatchesUp() {
        val pen = PenInput(1f, 1f, 0f, 0f, 0)
        pen.move(10f, 0f, 8)
        assertTrue(pen.x > 0f && pen.x < 10f)
        repeat(100) { pen.move(10f, 0f, 16L + it * 8) }
        assertEquals(10f, pen.x, 0.01f)
    }

    @Test
    fun fastStrokesGetThinnerAndSlowOnesThicker() {
        val fast = PenInput(0f, 1f, 0f, 0f, 0)
        val slow = PenInput(0f, 1f, 0f, 0f, 0)
        repeat(50) {
            fast.move((it + 1) * 24f, 0f, (it + 1) * 8L)
            slow.move((it + 1) * 0.4f, 0f, (it + 1) * 8L)
        }
        assertTrue(fast.width < 0.6f)
        assertTrue(slow.width > 1.2f)
    }
}
