package com.example.dsh.theme

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DshSolarClockTest {
    @Test
    fun shanghaiNoonIsDay() {
        val noonUtc = 12L * 60 * 60 * 1000
        val utcPlus8Noon = noonUtc - 8L * 60 * 60 * 1000
        assertFalse(DshSolarClock.isNight(utcPlus8Noon, utcOffsetMinutes = 480, latitudeDeg = 31.23))
    }

    @Test
    fun shanghaiMidnightIsNight() {
        val midnightUtc = 0L
        val utcPlus8Midnight = midnightUtc - 8L * 60 * 60 * 1000
        assertTrue(DshSolarClock.isNight(utcPlus8Midnight, utcOffsetMinutes = 480, latitudeDeg = 31.23))
    }
}