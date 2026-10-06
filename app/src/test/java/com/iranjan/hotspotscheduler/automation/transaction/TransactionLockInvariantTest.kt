package com.iranjan.hotspotscheduler.automation.transaction

import com.iranjan.hotspotscheduler.automation.logger.AutomationLogger
import com.iranjan.hotspotscheduler.automation.session.AutomationSession
import com.iranjan.hotspotscheduler.automation.session.EmergencyStop
import com.iranjan.hotspotscheduler.automation.strategies.NetworkOperationStrategy
import com.iranjan.hotspotscheduler.automation.strategies.OperationStrategyResolver
import com.iranjan.hotspotscheduler.automation.verification.VerificationEngine
import com.iranjan.hotspotscheduler.domain.model.ActualNetworkState
import com.iranjan.hotspotscheduler.domain.model.AutomationResult
import com.iranjan.hotspotscheduler.domain.model.DesiredNetworkState
import com.iranjan.hotspotscheduler.domain.model.Feature
import com.iranjan.hotspotscheduler.domain.model.StepResult
import com.iranjan.hotspotscheduler.domain.model.Target
import com.iranjan.hotspotscheduler.platform.Result
import com.iranjan.hotspotscheduler.platform.keyguard.KeyguardEngine
import com.iranjan.hotspotscheduler.platform.screen.LockResult
import com.iranjan.hotspotscheduler.platform.screen.ScreenSession
import com.iranjan.hotspotscheduler.platform.screen.UnlockResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.eq
import org.mockito.Mockito.isNull
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Spec §73 invariants. These are the tests that matter most:
 *   Invariant 1  No network step can lock the device.
 *   Invariant 3  A transaction has at most one final lock call.
 *   Invariant 5  A failed network step cannot be reported as successful.
 *   Invariant 6  An already-satisfied desired state produces no click.
 */
class TransactionLockInvariantTest {

    private lateinit var screen: ScreenSession
    private lateinit var keyguard: KeyguardEngine
    private lateinit var strategyResolver: OperationStrategyResolver
    private lateinit var verification: VerificationEngine
    private lateinit var logger: AutomationLogger
    private lateinit var emergencyStop: EmergencyStop
    private lateinit var hotspot: NetworkOperationStrategy
    private lateinit var data: NetworkOperationStrategy
    private lateinit var executor: TransactionExecutor

    @Before
    fun setUp() {
        screen = mock(ScreenSession::class.java)
        keyguard = mock(KeyguardEngine::class.java)
        strategyResolver = mock(OperationStrategyResolver::class.java)
        verification = mock(VerificationEngine::class.java)
        logger = mock(AutomationLogger::class.java)
        emergencyStop = EmergencyStop(logger)
        hotspot = mock(NetworkOperationStrategy::class.java)
        data = mock(NetworkOperationStrategy::class.java)

        `when`(strategyResolver.resolveHotspot()).thenReturn(hotspot)
        `when`(strategyResolver.resolveMobileData()).thenReturn(data)
        `when`(screen.ensureAwake()).thenReturn(Result.success(Unit))
        `when`(screen.ensureUnlocked()).thenReturn(Result.success(UnlockResult.ALREADY_UNLOCKED))
        `when`(screen.lock()).thenReturn(Result.success(LockResult.LOCKED))
        `when`(verification.verifyAll(any())).thenReturn(true)
        `when`(hotspot.setState(anyBoolean(), isNull())).thenReturn(Result.success(StepResult.CHANGED))
        `when`(data.setState(anyBoolean(), isNull())).thenReturn(Result.success(StepResult.CHANGED))

        executor = TransactionExecutorImpl(
            screen, keyguard, strategyResolver, verification, logger, emergencyStop
        )
    }

    private fun session(desired: DesiredNetworkState) = AutomationSession(
        sessionId = "inv-test",
        trigger = AutomationSession.Trigger.MANUAL_TEST,
        desiredState = desired,
        initialState = ActualNetworkState.UNKNOWN
    )

    @Test
    fun `Invariant 1 and 3 - two network steps produce exactly one lock call`() = runBlocking {
        val result = executor.execute(
            session(DesiredNetworkState(Target.Set(true), Target.Set(true)))
        )

        assertTrue("expected success, got $result", result is AutomationResult.Success)

        // Invariant 3: exactly one lock for the whole transaction, not one per step.
        verify(screen, times(1)).lock()
    }

    @Test
    fun `Invariant 3 - hotspot-only transaction still locks exactly once`() = runBlocking {
        val result = executor.execute(
            session(DesiredNetworkState(Target.Set(true), Target.LeaveAlone))
        )
        assertTrue(result is AutomationResult.Success)
        verify(screen, times(1)).lock()
        // Unrequested feature must never be touched.
        verify(data, never()).setState(anyBoolean(), isNull())
    }

    @Test
    fun `Invariant 6 - already-satisfied desired state performs no mutation`() = runBlocking {
        `when`(hotspot.setState(anyBoolean(), isNull())).thenReturn(Result.success(StepResult.ALREADY_OK))

        val result = executor.execute(
            session(DesiredNetworkState(Target.Set(true), Target.LeaveAlone))
        )
        assertTrue(result is AutomationResult.Success)
        // Still exactly one lock at the end, even when nothing changed.
        verify(screen, times(1)).lock()
    }

    @Test
    fun `Invariant 5 - failed step is reported as failure, never success`() = runBlocking {
        `when`(data.setState(anyBoolean(), isNull())).thenReturn(Result.success(StepResult.FAILED))

        val result = executor.execute(
            session(DesiredNetworkState(Target.Set(false), Target.Set(false)))
        )
        assertTrue("failed step must not be reported as success", result is AutomationResult.PartialSuccess)
        assertEquals(Feature.MOBILE_DATA, (result as AutomationResult.PartialSuccess).failedFeature)
    }

    @Test
    fun `wake failure aborts before any lock`() = runBlocking {
        `when`(screen.ensureAwake()).thenReturn(Result.failure(Result.Error.Timeout("no wake")))

        val result = executor.execute(session(DesiredNetworkState.allOff()))
        assertTrue(result is AutomationResult.Failed)
        verify(screen, never()).lock()
    }

    @Test
    fun `verification failure aborts before any lock`() = runBlocking {
        `when`(verification.verifyAll(any())).thenReturn(false)

        val result = executor.execute(
            session(DesiredNetworkState(Target.Set(true), Target.LeaveAlone))
        )
        assertTrue(result is AutomationResult.Failed)
        verify(screen, never()).lock()
    }

    @Test
    fun `emergency stop blocks execution before wake`() = runBlocking {
        emergencyStop.stop()

        val thrown = runCatching {
            executor.execute(session(DesiredNetworkState(Target.Set(true), Target.Set(true))))
        }.exceptionOrNull()

        assertTrue(thrown is EmergencyStopException)
        verify(screen, never()).lock()
        verify(screen, never()).ensureAwake()
    }
}