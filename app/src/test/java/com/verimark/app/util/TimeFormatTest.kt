package com.verimark.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeFormatTest {

    @Test
    fun formatMs_zero() {
        assertEquals("00:00", formatMs(0L))
    }

    @Test
    fun formatMs_secondsOnly() {
        assertEquals("00:07", formatMs(7_000L))
    }

    @Test
    fun formatMs_minutesAndSeconds() {
        assertEquals("01:05", formatMs(65_000L))
    }

    @Test
    fun formatMs_rollsIntoMinutes() {
        assertEquals("61:05", formatMs(3_665_000L))
    }

    @Test
    fun formatDate_hasExpectedShape() {
        assertTrue(formatDate(0L).matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }
}
