package com.estudia.app.ui.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureLockTest {

    @Test
    fun nothingMovesUntilTheGestureIsClear() {
        val lock = GestureLock(slop = 10f)
        assertEquals(Triple(1f, 0f, 0f), lock.filter(1.01f, 2f, 0f, span = 200f))
        assertEquals(GestureLock.Mode.NONE, lock.mode)
    }

    @Test
    fun aPinchHoldsThePanBack() {
        val lock = GestureLock(slop = 10f)
        lock.filter(1.1f, 1f, 0f, span = 200f)
        assertEquals(GestureLock.Mode.ZOOM, lock.mode)
        assertEquals(Triple(1.05f, 0f, 0f), lock.filter(1.05f, 5f, 3f, span = 200f))
    }

    @Test
    fun aDragHoldsTheZoomBack() {
        val lock = GestureLock(slop = 10f)
        lock.filter(1f, 0f, 20f, span = 200f)
        assertEquals(GestureLock.Mode.PAN, lock.mode)
        assertEquals(Triple(1f, 0f, 15f), lock.filter(1.02f, 0f, 15f, span = 200f))
    }

    @Test
    fun movingALotReleasesTheOtherGesture() {
        val lock = GestureLock(slop = 10f)
        lock.filter(1.1f, 0f, 0f, span = 200f)
        repeat(6) { lock.filter(1f, 0f, 12f, span = 200f) }
        assertEquals(GestureLock.Mode.BOTH, lock.mode)
        assertEquals(Triple(1.02f, 0f, 12f), lock.filter(1.02f, 0f, 12f, span = 200f))
    }
}
