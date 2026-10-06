package com.iranjan.hotspotscheduler.domain.scheduler

import com.iranjan.hotspotscheduler.domain.model.Routine
import com.iranjan.hotspotscheduler.domain.model.Target
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class RoutineEvaluatorTest {

    private val utc = ZoneOffset.UTC
    private val routine = Routine(
        id = 1,
        name = "Test",
        enabled = true,
        daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
        startTime = LocalTime.of(2, 0),
        endTime = LocalTime.of(3, 0),
        hotspotTarget = Target.Set(true),
        mobileDataTarget = Target.Set(true)
    )

    @Test
    fun `activeRoutines returns routine within window`() {
        val monday0230 = Instant.parse("2026-01-05T02:30:00Z") // Monday
        val active = RoutineEvaluator.activeRoutines(listOf(routine), monday0230.toEpochMilli(), utc)
        assertEquals(1, active.size)
    }

    @Test
    fun `activeRoutines excludes routine outside window`() {
        val monday0100 = Instant.parse("2026-01-05T01:00:00Z")
        val active = RoutineEvaluator.activeRoutines(listOf(routine), monday0100.toEpochMilli(), utc)
        assertTrue(active.isEmpty())
    }

    @Test
    fun `activeRoutines excludes routine on wrong day`() {
        val saturday0230 = Instant.parse("2026-01-03T02:30:00Z") // Saturday not in daysOfWeek
        val active = RoutineEvaluator.activeRoutines(listOf(routine), saturday0230.toEpochMilli(), utc)
        assertTrue(active.isEmpty())
    }

    @Test
    fun `activeRoutines excludes disabled routine`() {
        val disabled = routine.copy(enabled = false)
        val monday0230 = Instant.parse("2026-01-05T02:30:00Z")
        val active = RoutineEvaluator.activeRoutines(listOf(disabled), monday0230.toEpochMilli(), utc)
        assertTrue(active.isEmpty())
    }

    @Test
    fun `windowOnDay handles overnight window`() {
        val overnight = routine.copy(startTime = LocalTime.of(23, 0), endTime = LocalTime.of(1, 0))
        val monday = java.time.LocalDate.parse("2026-01-05")
        val window = RoutineEvaluator.windowOnDay(overnight, monday, utc)
        assertNotNull(window)
        val (start, end) = window!!
        assertTrue(start < end)
        // Should span midnight: start Monday 23:00, end Tuesday 01:00
    }

    @Test
    fun `windowOnDay returns null for zero-length window`() {
        val zero = routine.copy(startTime = LocalTime.of(2, 0), endTime = LocalTime.of(2, 0))
        val monday = java.time.LocalDate.parse("2026-01-05")
        val window = RoutineEvaluator.windowOnDay(zero, monday, utc)
        assertNull(window)
    }

    @Test
    fun `nextBoundary finds next start`() {
        val monday0100 = Instant.parse("2026-01-05T01:00:00Z")
        val next = RoutineEvaluator.nextBoundary(listOf(routine), monday0100.toEpochMilli(), utc)
        assertNotNull(next)
        assertTrue(next!!.isStart)
    }

    @Test
    fun `nextBoundary finds next end`() {
        val monday0230 = Instant.parse("2026-01-05T02:30:00Z")
        val next = RoutineEvaluator.nextBoundary(listOf(routine), monday0230.toEpochMilli(), utc)
        assertNotNull(next)
        assertFalse(next!!.isStart)
    }

    @Test
    fun `lastBoundary finds previous end`() {
        val monday0330 = Instant.parse("2026-01-05T03:30:00Z")
        val last = RoutineEvaluator.lastBoundary(listOf(routine), monday0330.toEpochMilli(), utc)
        assertNotNull(last)
        assertFalse(last!!.isStart)
    }

    @Test
    fun `todayEpochDay returns correct day`() {
        val monday = Instant.parse("2026-01-05T12:00:00Z")
        val day = RoutineEvaluator.todayEpochDay(monday.toEpochMilli(), utc)
        assertEquals(monday.atZone(utc).toLocalDate().toEpochDay(), day)
    }

    @Test
    fun `nextMidnight returns next day midnight`() {
        val monday = Instant.parse("2026-01-05T12:00:00Z")
        val midnight = RoutineEvaluator.nextMidnight(monday.toEpochMilli(), utc)
        val expected = Instant.parse("2026-01-06T00:00:00Z")
        assertEquals(expected.toEpochMilli(), midnight)
    }

    @Test
    fun `isActive matches activeRoutines logic`() {
        val monday0230 = Instant.parse("2026-01-05T02:30:00Z")
        assertTrue(RoutineEvaluator.isActive(routine, monday0230, utc))
        val monday0100 = Instant.parse("2026-01-05T01:00:00Z")
        assertFalse(RoutineEvaluator.isActive(routine, monday0100, utc))
    }

    @Test
    fun `boundaryAtMillis parses key correctly`() {
        val millis = RoutineEvaluator.boundaryAtMillis("1:S:1234567890")
        assertEquals(1234567890L, millis!!)
    }

    @Test
    fun `boundaryAtMillis returns null for blank`() {
        assertNull(RoutineEvaluator.boundaryAtMillis(""))
        assertNull(RoutineEvaluator.boundaryAtMillis(null))
    }
}