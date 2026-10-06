package com.iranjan.hotspotscheduler.automation.session

import com.iranjan.hotspotscheduler.domain.automation.TimeoutPolicy
import com.iranjan.hotspotscheduler.domain.model.ActualNetworkState
import com.iranjan.hotspotscheduler.domain.model.DesiredNetworkState
import com.iranjan.hotspotscheduler.domain.model.FailureReason
import com.iranjan.hotspotscheduler.domain.model.Feature
import com.iranjan.hotspotscheduler.domain.model.StepResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AutomationSession(
    val sessionId: String,
    val trigger: Trigger,
    val desiredState: DesiredNetworkState,
    val initialState: ActualNetworkState,
    val hotspotPassword: String? = null,
    val timeoutPolicy: TimeoutPolicy = TimeoutPolicy.default
) {
    enum class Trigger {
        SCHEDULED_START,
        SCHEDULED_END,
        MANUAL_TEST,
        MANUAL_TOGGLE,
        CAP_ENFORCEMENT,
        RECONCILIATION
    }

    enum class State {
        IDLE,
        PREPARING,
        WAKE_REQUESTED,
        SCREEN_AWAKE,
        KEYGUARD_CHECK,
        PLATFORM_DISMISS_ATTEMPT,
        PIN_REQUIRED,
        PIN_PAD_REVEAL,
        PIN_PAD_DETECTED,
        CREDENTIAL_ENTRY,
        UNLOCK_VERIFICATION,
        AUTOMATION_READY,
        NETWORK_PREPARATION,
        HOTSPOT_STEP,
        MOBILE_DATA_STEP,
        FINAL_STATE_VERIFICATION,
        RESTORING_UI,
        LOCK_REQUESTED,
        LOCK_VERIFICATION,
        SUCCESS,
        FAILED
    }

    var currentState: State = State.IDLE
        private set

    val perStepResults = mutableMapOf<Feature, StepResult>()
    var failureReason: FailureReason? = null
    private val startedAt = System.currentTimeMillis()

    fun transitionTo(newState: State) {
        currentState = newState
    }

    val isTerminal: Boolean
        get() = currentState in setOf(State.SUCCESS, State.FAILED)

    val elapsedMs: Long
        get() = System.currentTimeMillis() - startedAt

    val isTimedOut: Boolean
        get() = elapsedMs > timeoutPolicy.totalTransaction.toMillis()
}

interface SessionQueue {
    suspend fun enqueue(session: AutomationSession)
    suspend fun dequeue(): AutomationSession?
    suspend fun cancelAll()
}

class DefaultSessionQueue : SessionQueue {
    private val channel = Channel<AutomationSession>(Channel.UNLIMITED)
    private val mutex = Mutex()

    override suspend fun enqueue(session: AutomationSession) = mutex.withLock {
        channel.send(session)
    }

    override suspend fun dequeue(): AutomationSession? = mutex.withLock {
        channel.receiveOrNull()
    }

    override suspend fun cancelAll() = mutex.withLock {
        channel.close()
    }
}