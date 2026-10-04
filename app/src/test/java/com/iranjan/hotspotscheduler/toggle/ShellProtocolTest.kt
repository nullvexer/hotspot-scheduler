package com.iranjan.hotspotscheduler.toggle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ShellService returns "EXIT:<code>\n<output>". The sentinel must be read from the first line
 * only: command output can itself contain a line starting with "EXIT:", and honouring that
 * would fabricate a success code.
 */
class ShellProtocolTest {

    @Test
    fun `parses exit code and output`() {
        val r = ShellProtocol.parse("EXIT:0\nhello\nworld")
        assertEquals(0, r.exitCode)
        assertEquals("hello\nworld", r.output)
        assertTrue(r.success)
    }

    @Test
    fun `parses non-zero exit code`() {
        val r = ShellProtocol.parse("EXIT:1\nboom")
        assertEquals(1, r.exitCode)
        assertFalse(r.success)
    }

    @Test
    fun `output-only EXIT line does not become the exit code`() {
        val raw = "EXIT:0\nfirst\nEXIT:0\nsecond"
        val r = ShellProtocol.parse(raw)
        assertEquals(0, r.exitCode)
        assertTrue(r.output.contains("EXIT:0"))
        assertTrue(r.output.endsWith("second"))
    }

    @Test
    fun `missing sentinel is treated as unbound`() {
        val r = ShellProtocol.parse("no sentinel here")
        assertEquals(ShellProtocol.EXIT_UNBOUND, r.exitCode)
        assertEquals("no sentinel here", r.output)
    }

    @Test
    fun `non numeric exit code is unbound`() {
        val r = ShellProtocol.parse("EXIT:abc\nbody")
        assertEquals(ShellProtocol.EXIT_UNBOUND, r.exitCode)
        assertEquals("body", r.output)
    }

    @Test
    fun `sentinel with trailing whitespace still parses`() {
        val r = ShellProtocol.parse("EXIT:0 \nbody")
        assertEquals(0, r.exitCode)
    }

    @Test
    fun `empty output yields empty string`() {
        val r = ShellProtocol.parse("EXIT:0\n")
        assertEquals(0, r.exitCode)
        assertEquals("", r.output)
    }

    @Test
    fun `sentinel only with no newline`() {
        val r = ShellProtocol.parse("EXIT:124")
        assertEquals(ShellProtocol.EXIT_TIMEOUT, r.exitCode)
        assertEquals("", r.output)
    }

    @Test
    fun `timeout exit code round trips`() {
        val r = ShellProtocol.parse("EXIT:124\npartial\n[timeout after 20s]")
        assertEquals(ShellProtocol.EXIT_TIMEOUT, r.exitCode)
        assertFalse(r.success)
        assertTrue(r.output.contains("timeout"))
    }
}