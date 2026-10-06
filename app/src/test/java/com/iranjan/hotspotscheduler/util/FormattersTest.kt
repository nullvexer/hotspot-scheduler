package com.iranjan.hotspotscheduler.util

import org.junit.Assert.*
import org.junit.Test

class FormattersTest {

    @Test
    fun `formatBytes formats correctly`() {
        assertEquals("500 B", Formatters.formatBytes(500))
        assertEquals("1.0 KB", Formatters.formatBytes(1024))
        assertEquals("1.5 KB", Formatters.formatBytes(1536))
        assertEquals("1.0 MB", Formatters.formatBytes(1024 * 1024))
        assertEquals("1.5 MB", Formatters.formatBytes(1536 * 1024))
        assertEquals("1.0 GB", Formatters.formatBytes(1024 * 1024 * 1024))
    }

    @Test
    fun `formatCapMb formats correctly`() {
        assertEquals("100 MB", Formatters.formatCapMb(100L))
        assertEquals("unlimited", Formatters.formatCapMb(null))
    }
}