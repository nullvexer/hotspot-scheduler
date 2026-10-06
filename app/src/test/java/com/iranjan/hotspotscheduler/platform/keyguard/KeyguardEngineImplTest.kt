package com.iranjan.hotspotscheduler.platform.keyguard

import com.iranjan.hotspotscheduler.platform.Result
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

class KeyguardEngineImplTest {

    @Test
    fun `detectKeyguard returns not showing`() = runBlocking {
        val engine = KeyguardEngineImpl()
        val result = engine.detectKeyguard()
        assertTrue(result is Result.Success)
        assertFalse((result as Result.Success).value.isShowing)
    }

    @Test
    fun `revealPinPad returns unvalidated geometry`() = runBlocking {
        val engine = KeyguardEngineImpl()
        val result = engine.revealPinPad()
        assertTrue(result is Result.Success)
        assertFalse((result as Result.Success).value.validated)
    }

    @Test
    fun `enterCredential returns unlocked`() = runBlocking {
        val engine = KeyguardEngineImpl()
        val geometry = PinPadGeometry(emptyMap(), null, "test", false)
        val result = engine.enterCredential(geometry, "1234")
        assertTrue(result is Result.Success)
        assertEquals(CredentialResult.UNLOCKED, (result as Result.Success).value)
    }

    @Test
    fun `verifyUnlocked returns true`() = runBlocking {
        val engine = KeyguardEngineImpl()
        assertTrue(engine.verifyUnlocked())
    }
}