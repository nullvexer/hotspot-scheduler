package com.iranjan.hotspotscheduler.platform.screen

import com.iranjan.hotspotscheduler.platform.Result
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

class ScreenSessionImplTest {

    @Test
    fun `acquire returns success`() = runBlocking {
        val session = ScreenSessionImpl()
        val result = session.acquire()
        assertTrue(result is Result.Success)
    }

    @Test
    fun `ensureAwake returns success`() = runBlocking {
        val session = ScreenSessionImpl()
        val result = session.ensureAwake()
        assertTrue(result is Result.Success)
    }

    @Test
    fun `ensureUnlocked returns already unlocked`() = runBlocking {
        val session = ScreenSessionImpl()
        val result = session.ensureUnlocked()
        assertTrue(result is Result.Success)
        assertEquals(UnlockResult.ALREADY_UNLOCKED, (result as Result.Success).value)
    }

    @Test
    fun `lock returns locked`() = runBlocking {
        val session = ScreenSessionImpl()
        val result = session.lock()
        assertTrue(result is Result.Success)
        assertEquals(LockResult.LOCKED, (result as Result.Success).value)
    }
}