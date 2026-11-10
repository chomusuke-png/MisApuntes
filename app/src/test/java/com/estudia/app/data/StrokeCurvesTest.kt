package com.estudia.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun withPressureTheWidthIgnoresTheSpeed() {
        val soft = PenInput(0f, 1f, 0f, 0f, 0, pressure = 0.1f)
        val hard = PenInput(0f, 1f, 0f, 0f, 0, pressure = 0.9f)
        assertTrue(soft.width < hard.width)
        repeat(50) {
            // apretar fuerte y rapido da un trazo grueso, y suave y lento uno fino
            hard.move((it + 1) * 24f, 0f, (it + 1) * 8L, 0.9f)
            soft.move((it + 1) * 0.4f, 0f, (it + 1) * 8L, 0.1f)
        }
        assertTrue(hard.width > 1.2f)
        assertTrue(soft.width < 0.6f)
    }

    @Test
    fun aScreenThatAlwaysReportsTheSamePressureIsIgnored() {
        val range = PressureRange()
        repeat(20) { assertNull(range.normalize(1f)) }
        assertNull(range.normalize(0.95f))
    }

    @Test
    fun fingerPressureIsScaledToTheRangeSeenOnTheDevice() {
        val range = PressureRange()
        assertNull(range.normalize(0.3f))
        assertEquals(1f, range.normalize(0.5f)!!, 0f)
        assertEquals(0f, range.normalize(0.3f)!!, 0f)
        assertEquals(0.5f, range.normalize(0.4f)!!, 0.001f)
    }

    @Test
    fun theLearnedRangeSurvivesReopeningTheApp() {
        val range = PressureRange()
        range.normalize(0.2f)
        range.normalize(0.6f)
        assertEquals(0.5f, PressureRange(range.min, range.max).normalize(0.4f)!!, 0.001f)
    }

    @Test
    fun aLoneExtremePressureIsForgottenWithUse() {
        val range = PressureRange()
        range.normalize(0.2f)
        range.normalize(0.4f)
        range.normalize(1f)
        range.endStroke()
        val stretched = range.normalize(0.4f)!!
        repeat(60) {
            range.normalize(0.2f)
            range.normalize(0.4f)
            range.endStroke()
        }
        assertTrue(range.normalize(0.4f)!! > stretched + 0.5f)
    }

    private val line = PdfStroke(0, 0, 0.01f, listOf(0.1f, 0.5f, 0.9f, 0.5f))

    @Test
    fun erasingAwayFromTheStrokeLeavesItUntouched() {
        assertNull(line.erase(50f, 10f, 5f, 100f, 100f))
    }

    @Test
    fun erasingTheMiddleSplitsTheStrokeInTwo() {
        val pieces = line.erase(50f, 50f, 5f, 100f, 100f)!!
        assertEquals(2, pieces.size)
        val (left, right) = pieces
        // el corte queda en el borde del circulo, a 5 px de radio mas medio grosor
        assertEquals(0.1f, left.points.first(), 0.001f)
        assertTrue(left.points[left.points.size - 2] in 0.40f..0.445f)
        assertTrue(right.points.first() in 0.555f..0.60f)
        assertEquals(0.9f, right.points[right.points.size - 2], 0.001f)
    }

    @Test
    fun erasingAnEndShortensTheStroke() {
        val pieces = line.erase(10f, 50f, 5f, 100f, 100f)!!
        assertEquals(1, pieces.size)
        assertTrue(pieces[0].points.first() > 0.15f)
    }

    @Test
    fun erasingAllOfItLeavesNothing() {
        assertEquals(emptyList<PdfStroke>(), line.erase(50f, 50f, 60f, 100f, 100f))
        val dot = PdfStroke(0, 0, 0.01f, listOf(0.5f, 0.5f))
        assertEquals(emptyList<PdfStroke>(), dot.erase(50f, 50f, 5f, 100f, 100f))
    }

    @Test
    fun piecesKeepTheirLookAndTheirWidths() {
        val pen = PdfStroke(2, -1, 0.01f, listOf(0.1f, 0.5f, 0.5f, 0.5f, 0.9f, 0.5f), alpha = 0.5f, pen = true, widths = listOf(0.5f, 1f, 1.3f))
        val pieces = pen.erase(50f, 50f, 5f, 100f, 100f)!!
        assertEquals(2, pieces.size)
        pieces.forEach {
            assertEquals(it.points.size / 2, it.widths.size)
            assertEquals(pen.copy(points = it.points, widths = it.widths), it)
        }
        assertEquals(0.5f, pieces[0].widths.first(), 0f)
        assertEquals(1.3f, pieces[1].widths.last(), 0f)
    }

    @Test
    fun aStraightHighlightStaysStraightAfterErasing() {
        val pieces = line.copy(straight = true).erase(50f, 50f, 5f, 100f, 100f)!!
        assertEquals(2, pieces.size)
        pieces.forEach { assertEquals(4, it.points.size) }
    }
}
