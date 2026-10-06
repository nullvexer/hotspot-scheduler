package com.iranjan.hotspotscheduler.automation.recovery

import com.iranjan.hotspotscheduler.automation.recovery.ReconciliationEngineImpl
import com.iranjan.hotspotscheduler.domain.model.ActualNetworkState
import com.iranjan.hotspotscheduler.domain.model.AutomationResult
import com.iranjan.hotspotscheduler.domain.model.DesiredNetworkState
import com.iranjan.hotspotscheduler.domain.model.Target
import com.iranjan.hotspotscheduler.automation.strategies.OperationStrategyResolver
import com.iranjan.hotspotscheduler.automation.verification.VerificationEngine
import com.iranjan.hotspotscheduler.platform.screen.ScreenSession
import com.iranjan.hotspotscheduler.automation.logger.AutomationLogger
import com.iranjan.hotspotscheduler.data.repository.RoutineRepository
import com.iranjan.hotspotscheduler.data.datastore.AutomationPreferences
import com.iranjan.hotspotscheduler.domain.scheduler.DesiredStateResolver
import com.iranjan.hotspotscheduler.domain.scheduler.RoutineEvaluator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import kotlinx.coroutines.runBlocking

class ReconciliationEngineImplTest {

    private lateinit var engine: ReconciliationEngineImpl
    private lateinit var mockRepo: RoutineRepository
    private lateinit var mockPrefs: AutomationPreferences
    private lateinit var mockDesiredState: DesiredStateResolver
    private lateinit var mockStrategyResolver: OperationStrategyResolver
    private lateinit var mockVerification: VerificationEngine
    private lateinit var mockScreen: ScreenSession
    private lateinit var mockLogger: AutomationLogger

    @Before
    fun setUp() {
        mockRepo = mock(RoutineRepository::class.java)
        mockPrefs = mock(AutomationPreferences::class.java)
        mockDesiredState = mock(DesiredStateResolver::class.java)
        mockStrategyResolver = mock(OperationStrategyResolver::class.java)
        mockVerification = mock(VerificationEngine::class.java)
        mockScreen = mock(ScreenSession::class.java)
        mockLogger = mock(AutomationLogger::class.java)
        engine = ReconciliationEngineImpl(
            mockRepo, mockPrefs, mockDesiredState, mockStrategyResolver,
            mockVerification, mockScreen, mockLogger
        )
    }

    @Test
    fun `reconcile returns success when no work needed`() = runBlocking {
        val actual = ActualNetworkState(true, true)
        val desired = DesiredNetworkState(Target.Set(true), Target.Set(true))
        val result = engine.reconcile(actual, desired)
        assertTrue(result is AutomationResult.Success)
    }

    @Test
    fun `reconcileAfterCrash runs without error`() = runBlocking {
        `when`(mockPrefs.masterEnabled.first()).thenReturn(false)
        val result = engine.reconcileAfterCrash()
        assertTrue(result is AutomationResult.Blocked)
    }
}