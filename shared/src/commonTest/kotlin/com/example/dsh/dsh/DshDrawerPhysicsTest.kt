package com.example.dsh.dsh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DshDrawerPhysicsTest {
    @Test
    fun closingFlingWinsEvenAboveHalfway() {
        assertEquals(0f, DshDrawerPhysics.target(0.8f, -900f))
        assertEquals(1f, DshDrawerPhysics.target(0.2f, 900f))
        assertEquals(0f, DshDrawerPhysics.target(0.5f, 0f))
        assertEquals(1f, DshDrawerPhysics.target(0.51f, 0f))
    }

    @Test
    fun dragContinuesFromInterruptedPositionAndCanReverse() {
        assertEquals(0.5f, DshDrawerPhysics.progress(0.7f, -60f, 300f), 0.0001f)
        assertEquals(0.8f, DshDrawerPhysics.progress(0.7f, 30f, 300f), 0.0001f)
        assertEquals(0f, DshDrawerPhysics.progress(0.7f, -1000f, 300f))
        assertEquals(1f, DshDrawerPhysics.progress(0.7f, 1000f, 300f))
    }

    @Test
    fun bezierUsesTimeCoordinateAndNeverOvershoots() {
        assertEquals(0f, DshDrawerPhysics.ease(0f))
        assertEquals(1f, DshDrawerPhysics.ease(1f))
        assertEquals(0.77f, DshDrawerPhysics.ease(0.245f), 0.00001f)
        var previous = 0f
        for (i in 0..1000) {
            val current = DshDrawerPhysics.ease(i / 1000f)
            assertTrue(current in previous..1f)
            previous = current
        }
    }

    @Test
    fun homeShiftLeavesAboutTwentyPercentPeek() {
        val page = 360f
        val drawer = DshDrawerMotion.drawerWidth(page)
        assertEquals(288f, drawer, 0.01f)
        val openShift = DshDrawerMotion.homeShift(1f, page, drawer)
        assertTrue(openShift in 270f..288f)
        assertEquals(0f, DshDrawerMotion.drawerShift(1f, drawer))
        assertEquals(-drawer, DshDrawerMotion.drawerShift(0f, drawer))
    }
}
