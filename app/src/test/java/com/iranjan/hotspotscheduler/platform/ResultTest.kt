package com.iranjan.hotspotscheduler.platform

import org.junit.Assert.*
import org.junit.Test

class ResultTest {

    @Test
    fun `success wraps value`() {
        val result = Result.success("test")
        assertTrue(result.isSuccess)
        assertFalse(result.isFailure)
        assertEquals("test", (result as Result.Success).value)
    }

    @Test
    fun `failure wraps error`() {
        val result = Result.failure(Result.Error.Timeout("timed out"))
        assertFalse(result.isSuccess)
        assertTrue(result.isFailure)
        assertEquals("timed out", (result as Result.Failure).error.detail)
    }

    @Test
    fun `success and failure are distinct`() {
        val success = Result.success(Unit)
        val failure = Result.failure(Result.Error.NotAvailable("n/a"))
        assertNotEquals(success, failure)
    }
}