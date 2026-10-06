package com.iranjan.hotspotscheduler.automation.recovery

import com.iranjan.hotspotscheduler.automation.recovery.ReconciliationEngineImpl
import com.iranjan.hotspotscheduler.automation.logger.AutomationLogger
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import kotlinx.coroutines.runBlocking

class CrashRecoveryTest {

    private lateinit var recovery: CrashRecovery
    private lateinit var mockReconciliation: ReconciliationEngineImpl
    private lateinit var mockLogger: AutomationLogger

    @Before
    fun setUp() {
        mockReconciliation = mock(ReconciliationEngineImpl::class.java)
        mockLogger = mock(AutomationLogger::class.java)
        recovery = CrashRecovery(mockReconciliation, mockLogger)
    }

    @Test
    fun `runIfNeeded runs once and returns result`() = runBlocking {
        val expected = com.iranjan.hotspotscheduler.domain.model.AutomationResult.Success(
            emptyList(),
            com.iranjan.hotspotscheduler.domain.model.LockResult.SKIPPED,
            "test"
        )
        `when`(mockReconciliation.reconcileAfterCrash()).thenReturn(expected)

        val result1 = recovery.runIfNeeded()
        val result2 = recovery.runIfNeeded()

        assertEquals(expected, result1)
        assertNull(result2) // Second call returns null because already run
        verify(mockLogger).logAction("crash_recovery", mapOf("trigger" to "app_start"))
    }

    @Test
    fun `reset allows running again`() = runBlocking {
        `when`(mockReconciliation.reconcileAfterCrash()).thenReturn(
            com.iranjan.hotspotscheduler.domain.model.AutomationResult.Success(
                emptyList(),
                com.iranjan.hotspotscheduler.domain.model.LockResult.SKIPPED,
                "test"
            )
        )

        val result1 = recovery.runIfNeeded()
        assertNotNull(result1)

        recovery.reset()

        val result2 = recovery.runIfNeeded()
        assertNotNull(result2)
    }
}