package com.iranjan.hotspotscheduler.core

import com.iranjan.hotspotscheduler.data.model.Routine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BoundaryKeyTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")

    @Test
    fun `boundary key round trips through atMillis`() {
        val b = RoutineEvaluator.Boundary(routineId = 42L, isStart = true, atMillis = 1_700_000_000_000L)
        assertEquals(b.atMillis, RoutineEvaluator.boundaryAtMillis(b.key))
    }

    @Test
    fun `end boundary key round trips`() {
        val b = RoutineEvaluator.Boundary(routineId = 7L, isStart = false, atMillis = 1_699_999_999_000L)
        assertEquals(b.atMillis, RoutineEvaluator.boundaryAtMillis(b.key))
    }

    @Test
    fun `blank and null keys yield null so fresh install is detectable`() {
        assertNull(RoutineEvaluator.boundaryAtMillis(null))
        assertNull(RoutineEvaluator.boundaryAtMillis(""))
        assertNull(RoutineEvaluator.boundaryAtMillis("   "))
    }

    @Test
    fun `malformed keys yield null`() {
        assertNull(RoutineEvaluator.boundaryAtMillis("no-colons"))
        assertNull(RoutineEvaluator.boundaryAtMillis("42:S:"))
        assertNull(RoutineEvaluator.boundaryAtMillis("42:S:notanumber"))
        assertNull(RoutineEvaluator.boundaryAtMillis(":S:123"))
    }

    @Test
    fun `start and end keys differ for the same millisecond`() {
        val start = RoutineEvaluator.Boundary(1L, true, 1000L).key
        val end = RoutineEvaluator.Boundary(1L, false, 1000L).key
        assertTrue(start != end)
        assertEquals(1000L, RoutineEvaluator.boundaryAtMillis(start))
        assertEquals(1000L, RoutineEvaluator.boundaryAtMillis(end))
    }

    /**
     * startMinutes == endMinutes previously collapsed to a full 24h window, which makes the
     * routine permanently active and permanently applies its data cap. It must not.
     */
    @Test
    fun `equal start and end is treated as a zero length window, not all day`() {
        val routine = Routine(
            id = 1L,
            name = "same",
            days = setOf(1, 2, 3, 4, 5, 6, 7),
            startMinutes = 600,
            endMinutes = 600,
            capMb = null,
            enabled = true
        )
        val date = LocalDate.of(2024, 3, 5)
        val window = RoutineEvaluator.windowOnDay(routine, date, zone)
        assertNull("equal start/end must not produce an all-day window", window)
    }

    @Test
    fun `one minute window is honoured`() {
        val routine = Routine(
            id = 1L,
            name = "minute",
            days = setOf(2),
            startMinutes = 600,
            endMinutes = 601,
            capMb = null,
            enabled = true
        )
        val date = LocalDate.of(2024, 3, 5)
        val w = RoutineEvaluator.windowOnDay(routine, date, zone)!!
        assertEquals(60_000L, w.second - w.first)
    }
}