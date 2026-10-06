package com.iranjan.hotspotscheduler.automation.transaction

import com.iranjan.hotspotscheduler.automation.session.AutomationSession
import com.iranjan.hotspotscheduler.automation.verification.VerificationEngine
import com.iranjan.hotspotscheduler.domain.model.AutomationResult
import com.iranjan.hotspotscheduler.domain.model.DesiredNetworkState
import com.iranjan.hotspotscheduler.domain.model.Feature
import com.iranjan.hotspotscheduler.domain.model.Target
import com.iranjan.hotspotscheduler.platform.keyguard.KeyguardEngine
import com.iranjan.hotspotscheduler.platform.screen.ScreenSession
import com.iranjan.hotspotscheduler.automation.logger.AutomationLogger
import com.iranjan.hotspotscheduler.platform.Result
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class TransactionExecutorImplTest {

    private lateinit var executor: TransactionExecutorImpl
    private lateinit var mockScreen: ScreenSession
    private lateinit var mockKeyguard: KeyguardEngine
    private lateinit var mockVerification: VerificationEngine
    private lateinit var mockLogger: AutomationLogger

    @Before
    fun setUp() {
        mockScreen = mock(ScreenSession::class.java)
        mockKeyguard = mock(KeyguardEngine::class.java)
        mockVerification = mock(VerificationEngine::class.java)
        mockLogger = mock(AutomationLogger::class.java)
        executor = TransactionExecutorImpl(mockScreen, mockKeyguard, mockVerification, mockLogger)
    }

    @Test
    fun `execute returns Success when all steps succeed`() = runBlockingTest {
        val session = AutomationSession(
            sessionId = "test-1",
            trigger = AutomationSession.Trigger.MANUAL_TEST,
            desiredState = DesiredNetworkState(Target.Set(true), Target.Set(true)),
            initialState = com.iranjan.hotspotscheduler.domain.model.ActualNetworkState.UNKNOWN
        )

        `when`(mockScreen.ensureAwake()).thenReturn(Result.success(Unit))
        `when`(mockScreen.ensureUnlocked()).thenReturn(Result.success(com.iranjan.hotspotscheduler.platform.screen.UnlockResult.ALREADY_UNLOCKED))
        `when`(mockVerification.verifyAll(any())).thenReturn(true)
        `when`(mockScreen.lock()).thenReturn(Result.success(LockResult.LOCKED))

        val result = executor.execute(session)

        assertTrue(result is AutomationResult.Success)
        verify(mockLogger).logTransition(session, AutomationSession.State.SUCCESS)
    }

    @Test
    fun `execute returns Failed when wake fails`() = runBlockingTest {
        val session = AutomationSession(
            sessionId = "test-2",
            trigger = AutomationSession.Trigger.MANUAL_TEST,
            desiredState = DesiredNetworkState.allOff(),
            initialState = com.iranjan.hotspotscheduler.domain.model.ActualNetworkState.UNKNOWN
        )

        `when`(mockScreen.ensureAwake()).thenReturn(Result.failure(Result.Error.Timeout("wake timeout")))

        val result = executor.execute(session)

        assertTrue(result is AutomationResult.Failed)
        assertTrue(result.reason is com.iranjan.hotspotscheduler.domain.model.FailureReason.ScreenWakeFailed)
    }

    @Test
    fun `execute returns Failed when unlock fails`() = runBlockingTest {
        val session = AutomationSession(
            sessionId = "test-3",
            trigger = AutomationSession.Trigger.MANUAL_TEST,
            desiredState = DesiredNetworkState.allOff(),
            initialState = com.iranjan.hotspotscheduler.domain.model.ActualNetworkState.UNKNOWN
        )

        `when`(mockScreen.ensureAwake()).thenReturn(Result.success(Unit))
        `when`(mockScreen.ensureUnlocked()).thenReturn(Result.failure(Result.Error.NotAvailable("keyguard unavailable")))

        val result = executor.execute(session)

        assertTrue(result is AutomationResult.Failed)
        assertTrue(result.reason is com.iranjan.hotspotscheduler.domain.model.FailureReason.UnlockVerificationFailed)
    }

    @Test
    fun `execute returns Failed when final verification fails`() = runBlockingTest {
        val session = AutomationSession(
            sessionId = "test-4",
            trigger = AutomationSession.Trigger.MANUAL_TEST,
            desiredState = DesiredNetworkState.allOff(),
            initialState = com.iranjan.hotspotscheduler.domain.model.ActualNetworkState.UNKNOWN
        )

        `when`(mockScreen.ensureAwake()).thenReturn(Result.success(Unit))
        `when`(mockScreen.ensureUnlocked()).thenReturn(Result.success(com.iranjan.hotspotscheduler.platform.screen.UnlockResult.ALREADY_UNLOCKED))
        `when`(mockVerification.verifyAll(any())).thenReturn(false)

        val result = executor.execute(session)

        assertTrue(result is AutomationResult.Failed)
    }

    @Test
    fun `execute returns PartialSuccess when step fails`() = runBlockingTest {
        // This test would need a real OperationStrategyResolver to properly test step failures
        // Placeholder for Phase 8-10 implementation
    }
}

import kotlinx.coroutines.runBlocking

fun runBlockingTest(block: suspend () -> Unit) = runBlocking { block() }