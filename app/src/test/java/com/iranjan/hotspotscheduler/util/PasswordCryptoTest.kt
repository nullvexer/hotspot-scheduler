package com.iranjan.hotspotscheduler.util

import org.junit.Assert.*
import org.junit.Test

class PasswordCryptoTest {

    @Test
    fun `encrypt and decrypt round trip`() {
        val original = "TestPassword123!"
        val encrypted = PasswordCrypto.encrypt(original)
        assertNotEquals("", encrypted)
        assertTrue(encrypted.startsWith("1:"))

        val decrypted = PasswordCrypto.decrypt(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun `encrypt empty returns empty`() {
        val encrypted = PasswordCrypto.encrypt("")
        assertEquals("", encrypted)
    }

    @Test
    fun `decrypt null returns null`() {
        val decrypted = PasswordCrypto.decrypt(null)
        assertNull(decrypted)
    }

    @Test
    fun `decrypt empty returns null`() {
        val decrypted = PasswordCrypto.decrypt("")
        assertNull(decrypted)
    }

    @Test
    fun `decrypt wrong format returns null`() {
        val decrypted = PasswordCrypto.decrypt("not-a-valid-format")
        assertNull(decrypted)
    }

    @Test
    fun `decrypt wrong version returns null`() {
        val decrypted = PasswordCrypto.decrypt("2:invalid")
        assertNull(decrypted)
    }

    @Test
    fun `decrypt corrupted data returns null`() {
        val decrypted = PasswordCrypto.decrypt("1:corrupteddata")
        assertNull(decrypted)
    }
}