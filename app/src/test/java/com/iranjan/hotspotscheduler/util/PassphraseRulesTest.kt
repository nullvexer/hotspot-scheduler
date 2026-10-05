package com.iranjan.hotspotscheduler.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WPA2 passphrase rules. These previously lived in the deleted Shizuku command builder and in the
 * editor as a separate, weaker "length >= 8" check, so a password the engine could never use was
 * accepted by the UI, encrypted, stored, and then silently ignored at toggle time.
 */
class PassphraseRulesTest {

    @Test
    fun `accepts a normal passphrase`() {
        assertTrue(PassphraseRules.isValid("mypassword"))
        assertTrue(PassphraseRules.isValid("Passw0rd!"))
        assertTrue(PassphraseRules.isValid("abc defg"))
    }

    @Test
    fun `accepts the boundary lengths`() {
        assertTrue(PassphraseRules.isValid("a".repeat(PassphraseRules.MIN_LENGTH)))
        assertTrue(PassphraseRules.isValid("a".repeat(PassphraseRules.MAX_LENGTH)))
    }

    @Test
    fun `rejects too short`() {
        assertFalse(PassphraseRules.isValid(""))
        assertFalse(PassphraseRules.isValid("a".repeat(PassphraseRules.MIN_LENGTH - 1)))
    }

    @Test
    fun `rejects too long`() {
        assertFalse(PassphraseRules.isValid("a".repeat(PassphraseRules.MAX_LENGTH + 1)))
        assertFalse(PassphraseRules.isValid("a".repeat(200)))
    }

    @Test
    fun `rejects null`() {
        assertFalse(PassphraseRules.isValid(null))
    }

    @Test
    fun `rejects non ascii`() {
        assertFalse(PassphraseRules.isValid("passwörd1"))
        assertFalse(PassphraseRules.isValid("パスワード1"))
        assertFalse(PassphraseRules.isValid("пароль12"))
    }

    @Test
    fun `rejects control characters`() {
        assertFalse(PassphraseRules.isValid("abc\tdefg"))
        assertFalse(PassphraseRules.isValid("abc\ndefg"))
    }

    @Test
    fun `accepts printable ascii across the range`() {
        val slice = (32..126).map { it.toChar() }.joinToString("").take(PassphraseRules.MAX_LENGTH)
        assertEquals(PassphraseRules.MAX_LENGTH, slice.length)
        assertTrue(PassphraseRules.isValid(slice))
    }

    @Test
    fun `rejection reason is null only when valid or empty`() {
        assertNull(PassphraseRules.rejectionReason(null))
        assertNull(PassphraseRules.rejectionReason(""))
        assertNull(PassphraseRules.rejectionReason("goodpassword"))
        assertNotNull(PassphraseRules.rejectionReason("short"))
        assertNotNull(PassphraseRules.rejectionReason("a".repeat(100)))
        assertNotNull(PassphraseRules.rejectionReason("passwörd1"))
    }
}