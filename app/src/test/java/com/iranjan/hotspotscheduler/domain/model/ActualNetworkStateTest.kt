package com.iranjan.hotspotscheduler.domain.model

import org.junit.Assert.*
import org.junit.Test

class ActualNetworkStateTest {

    @Test
    fun `UNKNOWN returns null for both features`() {
        val state = ActualNetworkState.UNKNOWN
        assertNull(state.hotspot)
        assertNull(state.mobileData)
    }

    @Test
    fun `stateOf returns correct value per feature`() {
        val state = ActualNetworkState(true, false)
        assertTrue(state.stateOf(Feature.HOTSPOT)!!)
        assertFalse(state.stateOf(Feature.MOBILE_DATA)!!)
    }

    @Test
    fun `workRequired returns features that differ from desired`() {
        val actual = ActualNetworkState(false, false)
        val desired = DesiredNetworkState(Target.Set(true), Target.Set(true))
        val work = actual.workRequired(desired)
        assertEquals(2, work.size)
        assertTrue(work.contains(Feature.HOTSPOT))
        assertTrue(work.contains(Feature.MOBILE_DATA))
    }

    @Test
    fun `workRequired excludes LeaveAlone features`() {
        val actual = ActualNetworkState(false, false)
        val desired = DesiredNetworkState(Target.Set(true), Target.LeaveAlone)
        val work = actual.workRequired(desired)
        assertEquals(1, work.size)
        assertTrue(work.contains(Feature.HOTSPOT))
        assertFalse(work.contains(Feature.MOBILE_DATA))
    }

    @Test
    fun `workRequired excludes already satisfied features`() {
        val actual = ActualNetworkState(true, false)
        val desired = DesiredNetworkState(Target.Set(true), Target.Set(false))
        val work = actual.workRequired(desired)
        assertTrue(work.isEmpty())
    }

    @Test
    fun `satisfies returns true when all desired states match`() {
        val actual = ActualNetworkState(true, false)
        val desired = DesiredNetworkState(Target.Set(true), Target.Set(false))
        assertTrue(actual.satisfies(desired))
    }

    @Test
    fun `satisfies returns false when any desired state differs`() {
        val actual = ActualNetworkState(true, true)
        val desired = DesiredNetworkState(Target.Set(true), Target.Set(false))
        assertFalse(actual.satisfies(desired))
    }

    @Test
    fun `satisfies returns true for untouched desired state`() {
        val actual = ActualNetworkState(false, true)
        val desired = DesiredNetworkState.untouched()
        assertTrue(actual.satisfies(desired))
    }
}