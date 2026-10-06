package com.iranjan.hotspotscheduler.automation.transaction

import com.iranjan.hotspotscheduler.automation.session.AutomationSession
import com.iranjan.hotspotscheduler.automation.session.EmergencyStop
import com.iranjan.hotspotscheduler.automation.verification.VerificationEngine
import com.iranjan.hotspotscheduler.automation.strategies.OperationStrategyResolver
import com.iranjan.hotspotscheduler.domain.model.AutomationResult
import com.iranjan.hotspotscheduler.domain.model.LockResult
import com.iranjan.hotspotscheduler.domain.model.StepResult
import com.iranjan.hotspotscheduler.platform.keyguard.KeyguardEngine
import com.iranjan.hotspotscheduler.platform.screen.ScreenSession
import com.iranjan.hotspotscheduler.automation.logger.AutomationLogger
import com.iranjan.hotspotscheduler.domain.scheduler.OperationOrder
import com.iranjan.hotspotscheduler.platform.Result
import com.iranjan.hotspotscheduler.util.AttemptLog
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionExecutorImpl @Inject constructor(
    private val screenSession: ScreenSession,
    private val keyguardEngine: KeyguardEngine,
    private val strategyResolver: OperationStrategyResolver,
    private val verificationEngine: VerificationEngine,
    private val logger: AutomationLogger,
    private val emergencyStop: EmergencyStop
) : TransactionExecutor {

    override suspend fun execute(session: AutomationSession): AutomationResult {
        logger.logTransition(session, AutomationSession.State.PREPARING)

        emergencyStop.checkAndThrow()

        // 1. Wake
        session.transitionTo(AutomationSession.State.WAKE_REQUESTED)
        val wakeResult = screenSession.ensureAwake()
        if (wakeResult is Result.Failure) {
            return AutomationResult.Failed(
                com.iranjan.hotspotscheduler.domain.model.FailureReason.ScreenWakeFailed(wakeResult.error.detail),
                sessionId = session.sessionId
            )
        }
        session.transitionTo(AutomationSession.State.SCREEN_AWAKE)

        emergencyStop.checkAndThrow()

        // 2. Unlock (one planned attempt)
        session.transitionTo(AutomationSession.State.KEYGUARD_CHECK)
        val unlockResult = screenSession.ensureUnlocked()
        if (unlockResult is Result.Failure) {
            return AutomationResult.Failed(
                com.iranjan.hotspotscheduler.domain.model.FailureReason.UnlockVerificationFailed(unlockResult.error.detail),
                sessionId = session.sessionId
            )
        }
        session.transitionTo(AutomationSession.State.AUTOMATION_READY)

        emergencyStop.checkAndThrow()

        // 3. Network steps (ordered by OperationOrder)
        val steps = OperationOrder.plan(session.desiredState.hotspot, session.desiredState.mobileData)
        val stepResults = mutableListOf<StepResult>()

        for (feature in steps) {
            emergencyStop.checkAndThrow()

            session.transitionTo(stepState(feature))
            val target = session.desiredState.targetFor(feature)
            val targetOn = (target as? com.iranjan.hotspotscheduler.domain.model.Target.Set)?.on ?: false

            val strategy = when (feature) {
                com.iranjan.hotspotscheduler.domain.model.Feature.HOTSPOT -> strategyResolver.resolveHotspot()
                com.iranjan.hotspotscheduler.domain.model.Feature.MOBILE_DATA -> strategyResolver.resolveMobileData()
            }

            val password = if (feature == com.iranjan.hotspotscheduler.domain.model.Feature.HOTSPOT) {
                session.desiredState.hotspotPassword
            } else null

            val stepResult = strategy.setState(targetOn, password)
            val result = when (stepResult) {
                is Result.Success -> stepResult.value
                is Result.Failure -> {
                    AttemptLog.add("${feature.name} step failed: ${stepResult.error}")
                    StepResult.FAILED
                }
            }

            stepResults.add(result)
            session.perStepResults[feature] = result

            if (result == StepResult.FAILED || result == StepResult.BLOCKED) {
                val failureReason = if (stepResult is Result.Failure) {
                    com.iranjan.hotspotscheduler.domain.model.FailureReason.ToggleStateDidNotChange(feature, stepResult.error.detail)
                } else {
                    com.iranjan.hotspotscheduler.domain.model.FailureReason.ToggleStateDidNotChange(feature, "step returned $result")
                }
                return AutomationResult.PartialSuccess(
                    stepResults,
                    LockResult.FAILED,
                    feature,
                    failureReason,
                    session.sessionId
                )
            }
            delay(100)
        }

        emergencyStop.checkAndThrow()

        // 4. Final verification
        session.transitionTo(AutomationSession.State.FINAL_STATE_VERIFICATION)
        val verified = verificationEngine.verifyAll(session.desiredState)
        if (!verified) {
            return AutomationResult.Failed(
                com.iranjan.hotspotscheduler.domain.model.FailureReason.FinalStateMismatch(com.iranjan.hotspotscheduler.domain.model.Feature.HOTSPOT, true, false),
                stepResults,
                session.sessionId
            )
        }

        emergencyStop.checkAndThrow()

        // 5. Lock exactly once
        session.transitionTo(AutomationSession.State.LOCK_REQUESTED)
        val lockResult = screenSession.lock()
        session.transitionTo(AutomationSession.State.LOCK_VERIFICATION)

        val finalLock = if (lockResult is Result.Success) LockResult.LOCKED else LockResult.FAILED

        session.transitionTo(AutomationSession.State.SUCCESS)
        logger.logTransition(session, AutomationSession.State.SUCCESS)

        return AutomationResult.Success(stepResults, finalLock, session.sessionId)
    }

    private fun stepState(feature: com.iranjan.hotspotscheduler.domain.model.Feature): AutomationSession.State =
        when (feature) {
            com.iranjan.hotspotscheduler.domain.model.Feature.HOTSPOT -> AutomationSession.State.HOTSPOT_STEP
            com.iranjan.hotspotscheduler.domain.model.Feature.MOBILE_DATA -> AutomationSession.State.MOBILE_DATA_STEP
        }
}