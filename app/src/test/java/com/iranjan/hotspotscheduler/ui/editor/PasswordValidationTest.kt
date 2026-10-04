package com.iranjan.hotspotscheduler.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The editor accepted any password of 8+ characters, while the Shizuku engine requires
 * 8..63 printable ASCII. A stored password outside that range was silently ignored at toggle
 * time, so the UI has to reject it up front.
 */
class PasswordValidationTest {

    @Test
    fun `accepts a normal passphrase`() {
        assertTrue(isUsablePassphrase("mypassword"))
        assertTrue(isUsablePassphrase("Passw0rd!"))
    }

    @Test
    fun `accepts the boundary lengths`() {
        assertTrue(isUsablePassphrase("a".repeat(8)))
        assertTrue(isUsablePassphrase("a".repeat(63)))
    }

    @Test
    fun `rejects too short`() {
        assertFalse(isUsablePassphrase(""))
        assertFalse(isUsablePassphrase("a".repeat(7)))
    }

    @Test
    fun `rejects too long`() {
        assertFalse(isUsablePassphrase("a".repeat(64)))
        assertFalse(isUsablePassphrase("a".repeat(200)))
    }

    @Test
    fun `rejects non ascii`() {
        // These pass the old "length >= 8" check but are rejected by validPassphrase.
        assertFalse(isUsablePassphrase("passwörd1"))
        assertFalse(isUsablePassphrase("パスワード1"))
        assertFalse(isUsablePassphrase("пароль12"))
    }

    @Test
    fun `rejects control characters`() {
        // Tab is code 9, outside the printable 32..126 range the engine accepts.
        assertFalse(isUsablePassphrase("abc\tdefg"))
    }

    @Test
    fun `space is allowed because it is printable ascii`() {
        assertTrue(isUsablePassphrase("abc defg"))
    }

    @Test
    fun `accepts printable ascii up to the length limit`() {
        // The whole printable range is 95 chars, so take a 63-char slice to land on the limit.
        val slice = (32..126).map { it.toChar() }.joinToString("").take(63)
        assertEquals(63, slice.length)
        assertTrue(isUsablePassphrase(slice))
    }
}