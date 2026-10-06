package com.iranjan.hotspotscheduler.automation.logger

import com.iranjan.hotspotscheduler.automation.session.AutomationSession
import com.iranjan.hotspotscheduler.domain.model.FailureReason

interface AutomationLogger {
    fun logTransition(session: AutomationSession, newState: AutomationSession.State)
    fun logAction(action: String, details: Map<String, Any>)
    fun logFailure(reason: FailureReason)
}