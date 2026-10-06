package com.iranjan.hotspotscheduler.domain.automation

import com.iranjan.hotspotscheduler.domain.model.ActualNetworkState
import com.iranjan.hotspotscheduler.domain.model.DesiredNetworkState
import com.iranjan.hotspotscheduler.domain.model.Feature
import com.iranjan.hotspotscheduler.domain.model.Target
import org.junit.Assert.*
import org.junit.Test

class AutomationSessionTest {

    @Test
    fun `initial state is IDLE`() {
        val session = AutomationSession(
            sessionId = "test-1",
            trigger = AutomationSession.Trigger.SCHEDULED_START,
            desiredState = DesiredNetworkState.allOff(),
            initialState = ActualNetworkState.UNKNOWN
        )
        assertEquals(AutomationSession.State.IDLE, session.currentState)
        assertFalse(session.isTerminal)
    }

    @Test
    fun `transitionTo updates state`() {
        val session = AutomationSession(
            sessionId = "test-1",
            trigger = AutomationSession.Trigger.SCHEDULED_START,
            desiredState = DesiredNetworkState.allOff(),
            initialState = ActualNetworkState.UNKNOWN
        )
        session.transitionTo(AutomationSession.State.WAKE_REQUESTED)
        assertEquals(AutomationSession.State.WAKE_REQUESTED, session.currentState)
    }

    @Test
    fun `isTerminal true for SUCCESS and FAILED`() {
        val session = AutomationSession(
            sessionId = "test-1",
            trigger = AutomationSession.Trigger.SCHEDULED_START,
            desiredState = DesiredNetworkState.allOff(),
            initialState = ActualNetworkState.UNKNOWN
        )
        session.transitionTo(AutomationSession.State.SUCCESS)
        assertTrue(session.isTerminal)
        session.transitionTo(AutomationSession.State.FAILED)
        assertTrue(session.isTerminal)
    }

    @Test
    fun `elapsedMs increases over time`() {
        val session = AutomationSession(
            sessionId = "test-1",
            trigger = AutomationSession.Trigger.SCHEDULED_START,
            desiredState = DesiredNetworkState.allOff(),
            initialState = ActualNetworkState.UNKNOWN
        )
        val first = session.elapsedMs
        Thread.sleep(10)
        val second = session.elapsedMs
        assertTrue(second >= first)
    }

    @Test
    fun `perStepResults records step outcomes`() {
        val session = AutomationSession(
            sessionId = "test-1",
            trigger = AutomationSession.Trigger.SCHEDULED_START,
            desiredState = DesiredNetworkState.allOff(),
            initialState = ActualNetworkState.UNKNOWN
        )
        session.perStepResults[Feature.HOTSPOT] = com.iranjan.hotspotscheduler.domain.model.StepResult.CHANGED
        assertEquals(com.iranjan.hotspotscheduler.domain.model.StepResult.CHANGED, session.perStepResults[Feature.HOTSPOT])
    }
}