package com.iranjan.hotspotscheduler.automation.logger

import com.iranjan.hotspotscheduler.automation.session.AutomationSession
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationLoggerImpl @Inject constructor() : AutomationLogger {

    override fun logTransition(session: AutomationSession, newState: AutomationSession.State) {
        // Structured log: timestamp, sessionId, oldState -> newState
        println("[AUTOMATION] ${System.currentTimeMillis()} | ${session.sessionId} | ${session.currentState} -> $newState")
    }

    override fun logAction(action: String, details: Map<String, Any>) {
        println("[ACTION] $action | $details")
    }

    override fun logFailure(reason: com.iranjan.hotspotscheduler.domain.model.FailureReason) {
        println("[FAILURE] $reason")
    }
}