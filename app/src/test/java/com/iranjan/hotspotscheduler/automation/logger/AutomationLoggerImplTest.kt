package com.iranjan.hotspotscheduler.automation.logger

import com.iranjan.hotspotscheduler.automation.session.AutomationSession
import com.iranjan.hotspotscheduler.domain.model.FailureReason
import org.junit.Assert.*
import org.junit.Test

class AutomationLoggerImplTest {

    @Test
    fun `logTransition does not throw`() {
        val logger = AutomationLoggerImpl()
        val session = AutomationSession(
            sessionId = "test-1",
            trigger = AutomationSession.Trigger.MANUAL_TEST,
            desiredState = com.iranjan.hotspotscheduler.domain.model.DesiredNetworkState.allOff(),
            initialState = com.iranjan.hotspotscheduler.domain.model.ActualNetworkState.UNKNOWN
        )
        logger.logTransition(session, AutomationSession.State.WAKE_REQUESTED)
    }

    @Test
    fun `logAction does not throw`() {
        val logger = AutomationLoggerImpl()
        logger.logAction("test_action", mapOf("key" to "value"))
    }

    @Test
    fun `logFailure does not throw`() {
        val logger = AutomationLoggerImpl()
        logger.logFailure(FailureReason.ScreenWakeFailed("test"))
    }
}