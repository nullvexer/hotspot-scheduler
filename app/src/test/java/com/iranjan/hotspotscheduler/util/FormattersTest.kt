package com.iranjan.hotspotscheduler.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The cap field round-trips through a text representation, and the editor used to render 1500 MB
 * as "1.5" and then read it back as 1.5 GB = 1536 MB, inflating the cap on every save.
 */
class FormattersTest {

    @Test
    fun `format bytes below one kilobyte`() {
        assertEquals("0 B", Formatters.formatBytes(0))
        assertEquals("512 B", Formatters.formatBytes(512))
        assertEquals("1023 B", Formatters.formatBytes(1023))
    }

    @Test
    fun `format bytes in kilobytes and megabytes`() {
        assertEquals("1.0 KB", Formatters.formatBytes(1024))
        assertEquals("1.5 MB", Formatters.formatBytes(1024L * 1024 * 3 / 2))
        assertEquals("1.00 GB", Formatters.formatBytes(1024L * 1024 * 1024))
    }

    @Test
    fun `cap formatting switches to GB only on whole gigabytes`() {
        assertEquals("500 MB", Formatters.formatCapMb(500))
        assertEquals("1023 MB", Formatters.formatCapMb(1023))
        assertEquals("1 GB", Formatters.formatCapMb(1024))
        assertEquals("2 GB", Formatters.formatCapMb(2048))
        assertEquals("1536 MB", Formatters.formatCapMb(1536))
    }

    @Test
    fun `mb values round trip through the mb unit`() {
        for (mb in listOf(1L, 500L, 1023L, 9999L)) {
            assertEquals(mb, Formatters.unitToMb(Formatters.mbToUnitValue(mb, false), false))
        }
    }

    @Test
    fun `whole gb values round trip exactly`() {
        for (gb in listOf(1L, 2L, 5L, 20L)) {
            val mb = gb * 1024
            assertEquals(mb, Formatters.unitToMb(Formatters.mbToUnitValue(mb, true), true))
        }
    }

    @Test
    fun `fractional gb survives three decimal places`() {
        // 1500 MB = 1.46484375 GB; with 3 decimals it becomes 1.465 GB = 1500 MB. Anything less
        // than 3 decimals would round to 1536.
        val mb = 1500L
        val displayed = 1.465
        assertEquals(mb, Formatters.unitToMb(displayed, true))
    }

    @Test
    fun `unit conversion basics`() {
        assertEquals(1024L, Formatters.unitToMb(1.0, true))
        assertEquals(1536L, Formatters.unitToMb(1.5, true))
        assertEquals(500L, Formatters.unitToMb(500.0, false))
        assertEquals(1.5, Formatters.mbToUnitValue(1536L, true), 0.0001)
        assertEquals(500.0, Formatters.mbToUnitValue(500L, false), 0.0001)
    }
}