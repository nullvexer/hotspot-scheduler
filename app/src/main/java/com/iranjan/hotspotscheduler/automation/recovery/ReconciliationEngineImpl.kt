package com.iranjan.hotspotscheduler.automation.recovery

import com.iranjan.hotspotscheduler.domain.model.ActualNetworkState
import com.iranjan.hotspotscheduler.domain.model.AutomationResult
import com.iranjan.hotspotscheduler.domain.model.DesiredNetworkState
import com.iranjan.hotspotscheduler.domain.model.FailureReason
import com.iranjan.hotspotscheduler.domain.scheduler.DesiredStateResolver
import com.iranjan.hotspotscheduler.domain.scheduler.RoutineEvaluator
import com.iranjan.hotspotscheduler.data.repository.RoutineRepository
import com.iranjan.hotspotscheduler.data.datastore.AutomationPreferences
import com.iranjan.hotspotscheduler.automation.transaction.TransactionExecutor
import com.iranjan.hotspotscheduler.automation.strategies.OperationStrategyResolver
import com.iranjan.hotspotscheduler.automation.verification.VerificationEngine
import com.iranjan.hotspotscheduler.platform.screen.ScreenSession
import com.iranjan.hotspotscheduler.automation.logger.AutomationLogger
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReconciliationEngineImpl @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val preferences: AutomationPreferences,
    private val desiredStateResolver: DesiredStateResolver,
    private val strategyResolver: OperationStrategyResolver,
    private val verificationEngine: VerificationEngine,
    private val screenSession: ScreenSession,
    private val logger: AutomationLogger
) : ReconciliationEngine {

    private val mutex = Mutex()

    override suspend fun reconcile(
        actual: ActualNetworkState,
        desired: DesiredNetworkState
    ): AutomationResult = mutex.withLock {
        val work = actual.workRequired(desired)
        if (work.isEmpty()) {
            logger.logAction("reconcile", mapOf("result" to "no_work_needed"))
            return AutomationResult.Success(
                emptyList(),
                com.iranjan.hotspotscheduler.domain.model.LockResult.SKIPPED,
                "reconcile-${System.currentTimeMillis()}"
            )
        }

        logger.logAction("reconcile", mapOf("work" to work.joinToString()))

        // For each needed feature, execute the strategy
        val stepResults = mutableListOf<com.iranjan.hotspotscheduler.domain.model.StepResult>()

        for (feature in work) {
            val target = desired.targetFor(feature)
            val targetOn = (target as? com.iranjan.hotspotscheduler.domain.model.Target.Set)?.on ?: false

            val strategy = when (feature) {
                com.iranjan.hotspotscheduler.domain.model.Feature.HOTSPOT -> strategyResolver.resolveHotspot()
                com.iranjan.hotspotscheduler.domain.model.Feature.MOBILE_DATA -> strategyResolver.resolveMobileData()
            }

            val password = if (feature == com.iranjan.hotspotscheduler.domain.model.Feature.HOTSPOT) {
                // Try to get password from routine if any active routine has it
                val routines = routineRepository.getEnabledRoutines()
                val now = java.time.Instant.now()
                val zone = java.time.ZoneId.systemDefault()
                val active = routines.filter { RoutineEvaluator.isActive(it, now, zone) }
                active.firstOrNull { it.hotspotTarget == com.iranjan.hotspotscheduler.domain.model.Target.Set(true) }?.hotspotPassword
            } else null

            val stepResult = strategy.setState(targetOn, password)
            val result = when (stepResult) {
                is com.iranjan.hotspotscheduler.platform.Result.Success -> stepResult.value
                is com.iranjan.hotspotscheduler.platform.Result.Failure -> {
                    logger.logFailure(FailureReason.ToggleStateDidNotChange(feature, stepResult.error.detail))
                    com.iranjan.hotspotscheduler.domain.model.StepResult.FAILED
                }
            }

            stepResults.add(result)
            if (result == com.iranjan.hotspotscheduler.domain.model.StepResult.FAILED) {
                return AutomationResult.Failed(
                    FailureReason.ToggleStateDidNotChange(feature, "reconciliation failed for $feature"),
                    stepResults,
                    "reconcile-${System.currentTimeMillis()}"
                )
            }
        }

        // Verify all
        val verified = verificationEngine.verifyAll(desired)
        if (!verified) {
            return AutomationResult.Failed(
                FailureReason.FinalStateMismatch(com.iranjan.hotspotscheduler.domain.model.Feature.HOTSPOT, true, false),
                stepResults,
                "reconcile-${System.currentTimeMillis()}"
            )
        }

        return AutomationResult.Success(stepResults, com.iranjan.hotspotscheduler.domain.model.LockResult.SKIPPED, "reconcile-${System.currentTimeMillis()}")
    }

    suspend fun reconcileAfterCrash(): AutomationResult {
        // Called on app start after unexpected crash
        logger.logAction("reconcile_after_crash", emptyMap())

        val masterEnabled = preferences.masterEnabled.first()
        if (!masterEnabled) return AutomationResult.Blocked(
            com.iranjan.hotspotscheduler.domain.model.BlockReason.MasterDisabled,
            "reconcile-crash-${System.currentTimeMillis()}"
        )

        val routines = routineRepository.getEnabledRoutines()
        val now = java.time.Instant.now()
        val zone = java.time.ZoneId.systemDefault()
        val active = routines.filter { RoutineEvaluator.isActive(it, now, zone) }

        if (active.isEmpty()) {
            // No active routines - ensure everything is OFF
            return reconcile(ActualNetworkState.UNKNOWN, DesiredNetworkState.allOff())
        }

        // Compute desired state from active routines
        val inputs = DesiredStateResolver.Inputs(
            now = now,
            zone = zone,
            masterEnabled = true,
            paused = preferences.pausedUntilMs.first() > now.toEpochMilli(),
            capReached = preferences.capHitEpochDay.first() == RoutineEvaluator.todayEpochDay(now.toEpochMilli()),
            hotspotSuppressed = preferences.suppressedUntilNextWindow.first()
        )
        val desired = desiredStateResolver.resolve(inputs)

        // Read actual state
        val hotspotStrategy = strategyResolver.resolveHotspot()
        val dataStrategy = strategyResolver.resolveMobileData()
        val hotspotState = hotspotStrategy.readState().let { if (it is com.iranjan.hotspotscheduler.platform.Result.Success) it.value else null }
        val dataState = dataStrategy.readState().let { if (it is com.iranjan.hotspotscheduler.platform.Result.Success) it.value else null }
        val actual = ActualNetworkState(hotspotState, dataState)

        return reconcile(actual, desired)
    }
}