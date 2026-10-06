package com.iranjan.hotspotscheduler.automation.session

import com.iranjan.hotspotscheduler.domain.model.DesiredNetworkState
import com.iranjan.hotspotscheduler.domain.model.ActualNetworkState
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

class DefaultSessionQueueTest {

    @Test
    fun `enqueue and dequeue works`() = runBlocking {
        val queue = DefaultSessionQueue()
        val session = AutomationSession(
            sessionId = "test-1",
            trigger = AutomationSession.Trigger.MANUAL_TEST,
            desiredState = DesiredNetworkState.allOff(),
            initialState = ActualNetworkState.UNKNOWN
        )
        queue.enqueue(session)
        val dequeued = queue.dequeue()
        assertNotNull(dequeued)
        assertEquals("test-1", dequeued!!.sessionId)
    }

    @Test
    fun `dequeue returns null when empty`() = runBlocking {
        val queue = DefaultSessionQueue()
        val result = queue.dequeue()
        assertNull(result)
    }

    @Test
    fun `cancelAll closes channel`() = runBlocking {
        val queue = DefaultSessionQueue()
        queue.cancelAll()
        val result = queue.dequeue()
        assertNull(result)
    }
}