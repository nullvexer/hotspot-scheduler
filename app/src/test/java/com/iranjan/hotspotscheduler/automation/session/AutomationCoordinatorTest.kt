package com.iranjan.hotspotscheduler.automation.session

import com.iranjan.hotspotscheduler.automation.transaction.TransactionExecutor
import com.iranjan.hotspotscheduler.domain.model.AutomationResult
import com.iranjan.hotspotscheduler.domain.model.DesiredNetworkState
import com.iranjan.hotspotscheduler.domain.scheduler.RoutineEvaluator
import com.iranjan.hotspotscheduler.platform.alarm.AlarmScheduler
import com.iranjan.hotspotscheduler.data.datastore.AutomationPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class AutomationCoordinatorTest {

    private lateinit var coordinator: AutomationCoordinator
    private lateinit var mockQueue: SessionQueue
    private lateinit var mockExecutor: TransactionExecutor
    private lateinit var mockAlarm: AlarmScheduler
    private lateinit var mockPrefs: AutomationPreferences

    @Before
    fun setUp() {
        mockQueue = mock(SessionQueue::class.java)
        mockExecutor = mock(TransactionExecutor::class.java)
        mockAlarm = mock(AlarmScheduler::class.java)
        mockPrefs = mock(AutomationPreferences::class.java)
        coordinator = AutomationCoordinator(mockQueue, mockExecutor, mockAlarm, mockPrefs)
    }

    @Test
    fun `executeBoundary enqueues session and executes`() = runBlockingTest {
        val boundary = RoutineEvaluator.Boundary(1, true, System.currentTimeMillis())
        val expectedResult = AutomationResult.Success(
            emptyList(),
            com.iranjan.hotspotscheduler.domain.model.LockResult.LOCKED,
            "auto-1-123"
        )
        `when`(mockExecutor.execute(any())).thenReturn(expectedResult)

        val result = coordinator.executeBoundary(boundary)

        assertEquals(expectedResult, result)
        verify(mockQueue).enqueue(any())
        verify(mockExecutor).execute(any())
    }

    @Test
    fun `executeManualTest enqueues manual session`() = runBlockingTest {
        val expectedResult = AutomationResult.Success(
            emptyList(),
            com.iranjan.hotspotscheduler.domain.model.LockResult.LOCKED,
            "manual-test"
        )
        `when`(mockExecutor.execute(any())).thenReturn(expectedResult)

        val result = coordinator.executeManualTest(AutomationSession.Trigger.MANUAL_TEST)

        assertEquals(expectedResult, result)
        verify(mockQueue).enqueue(any())
        verify(mockExecutor).execute(any())
    }
}

import kotlinx.coroutines.runBlocking

fun runBlockingTest(block: suspend () -> Unit) = runBlocking { block() }