package com.iranjan.hotspotscheduler.automation.session

import com.iranjan.hotspotscheduler.automation.transaction.TransactionExecutor
import com.iranjan.hotspotscheduler.domain.model.AutomationResult
import com.iranjan.hotspotscheduler.domain.model.DesiredNetworkState
import com.iranjan.hotspotscheduler.domain.scheduler.RoutineEvaluator
import com.iranjan.hotspotscheduler.domain.scheduler.DesiredStateResolver
import com.iranjan.hotspotscheduler.platform.alarm.AlarmScheduler
import com.iranjan.hotspotscheduler.data.datastore.AutomationPreferences
import com.iranjan.hotspotscheduler.data.repository.RoutineRepository
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationCoordinator @Inject constructor(
    private val sessionQueue: SessionQueue,
    private val transactionExecutor: TransactionExecutor,
    private val alarmScheduler: AlarmScheduler,
    private val preferences: AutomationPreferences,
    private val routineRepository: RoutineRepository,
    private val desiredStateResolver: DesiredStateResolver
) {

    private val mutex = Mutex()

    suspend fun executeBoundary(boundary: RoutineEvaluator.Boundary): AutomationResult = mutex.withLock {
        val trigger = if (boundary.isStart) AutomationSession.Trigger.SCHEDULED_START else AutomationSession.Trigger.SCHEDULED_END
        val routine = routineRepository.getRoutine(boundary.routineId)
        val hotspotPassword = if (boundary.isStart) routine?.hotspotPassword else null

        val desiredState = if (boundary.isStart) {
            val inputs = DesiredStateResolver.Inputs(
                now = java.time.Instant.ofEpochMilli(boundary.atMillis),
                zone = java.time.ZoneId.systemDefault(),
                masterEnabled = preferences.masterEnabled.first(),
                paused = preferences.pausedUntilMs.first() > boundary.atMillis,
                capReached = preferences.capHitEpochDay.first() == RoutineEvaluator.todayEpochDay(boundary.atMillis),
                hotspotSuppressed = preferences.suppressedUntilNextWindow.first()
            )
            desiredStateResolver.resolve(inputs)
        } else {
            DesiredNetworkState.allOff()
        }

        val session = AutomationSession(
            sessionId = "auto-${boundary.routineId}-${boundary.atMillis}",
            trigger = trigger,
            desiredState = desiredState,
            initialState = com.iranjan.hotspotscheduler.domain.model.ActualNetworkState.UNKNOWN,
            hotspotPassword = hotspotPassword
        )
        sessionQueue.enqueue(session)
        transactionExecutor.execute(session)
    }

    suspend fun executeManualTest(
        trigger: AutomationSession.Trigger,
        desiredState: DesiredNetworkState = DesiredNetworkState.allOff(),
        hotspotPassword: String? = null
    ): AutomationResult = mutex.withLock {
        val session = AutomationSession(
            sessionId = "manual-${trigger.name}-${System.currentTimeMillis()}",
            trigger = trigger,
            desiredState = desiredState,
            initialState = com.iranjan.hotspotscheduler.domain.model.ActualNetworkState.UNKNOWN,
            hotspotPassword = hotspotPassword
        )
        sessionQueue.enqueue(session)
        transactionExecutor.execute(session)
    }

    suspend fun executeCapEnforcement(): AutomationResult = mutex.withLock {
        val session = AutomationSession(
            sessionId = "cap-${System.currentTimeMillis()}",
            trigger = AutomationSession.Trigger.CAP_ENFORCEMENT,
            desiredState = DesiredNetworkState.allOff(),
            initialState = com.iranjan.hotspotscheduler.domain.model.ActualNetworkState.UNKNOWN
        )
        sessionQueue.enqueue(session)
        transactionExecutor.execute(session)
    }
}