package com.iranjan.hotspotscheduler.domain.model

import org.junit.Assert.*
import org.junit.Test

class DesiredNetworkStateTest {

    @Test
    fun `allOff returns both features Set to false`() {
        val state = DesiredNetworkState.allOff()
        assertEquals(Target.Set(false), state.hotspot)
        assertEquals(Target.Set(false), state.mobileData)
        assertFalse(state.isFullyUntouched)
    }

    @Test
    fun `untouched returns both features LeaveAlone`() {
        val state = DesiredNetworkState.untouched()
        assertEquals(Target.LeaveAlone, state.hotspot)
        assertEquals(Target.LeaveAlone, state.mobileData)
        assertTrue(state.isFullyUntouched)
    }

    @Test
    fun `targetFor returns correct target per feature`() {
        val state = DesiredNetworkState(Target.Set(true), Target.LeaveAlone)
        assertEquals(Target.Set(true), state.targetFor(Feature.HOTSPOT))
        assertEquals(Target.LeaveAlone, state.targetFor(Feature.MOBILE_DATA))
    }

    @Test
    fun `with returns new state with updated feature`() {
        val state = DesiredNetworkState.untouched()
        val updated = state.with(Feature.HOTSPOT, Target.Set(true))
        assertEquals(Target.Set(true), updated.hotspot)
        assertEquals(Target.LeaveAlone, updated.mobileData)
    }
}