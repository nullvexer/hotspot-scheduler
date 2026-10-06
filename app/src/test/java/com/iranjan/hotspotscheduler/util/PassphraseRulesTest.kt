package com.iranjan.hotspotscheduler.util

import org.junit.Assert.*
import org.junit.Test

class PassphraseRulesTest {

    @Test
    fun `valid password passes`() {
        assertTrue(PassphraseRules.isValid("Password123"))
        assertTrue(PassphraseRules.isValid("A".repeat(63)))
        assertTrue(PassphraseRules.isValid("!@#\$%^&*()"))
    }

    @Test
    fun `too short fails`() {
        assertFalse(PassphraseRules.isValid("Short1"))
        assertNull(PassphraseRules.rejectionReason("Short1"))
    }

    @Test
    fun `too long fails`() {
        assertFalse(PassphraseRules.isValid("A".repeat(64)))
    }

    @Test
    fun `non-ascii fails`() {
        assertFalse(PassphraseRules.isValid("Passwörd123"))
        assertFalse(PassphraseRules.isValid("密码123"))
    }

    @Test
    fun `control characters fail`() {
        assertFalse(PassphraseRules.isValid("Pass\x00word"))
    }

    @Test
    fun `rejectionReason returns correct message`() {
        assertEquals("too short (min 8 characters)", PassphraseRules.rejectionReason("short"))
        assertEquals("too long (max 63 characters)", PassphraseRules.rejectionReason("A".repeat(64)))
        assertEquals("must be printable ASCII letters, digits or symbols", PassphraseRules.rejectionReason("Passwörd"))
        assertNull(PassphraseRules.rejectionReason("ValidPass123"))
        assertNull(PassphraseRules.rejectionReason(""))
        assertNull(PassphraseRules.rejectionReason(null))
    }
}