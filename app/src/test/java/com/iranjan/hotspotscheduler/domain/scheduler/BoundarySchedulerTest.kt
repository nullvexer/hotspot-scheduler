package com.iranjan.hotspotscheduler.domain.scheduler

import com.iranjan.hotspotscheduler.domain.model.Routine
import com.iranjan.hotspotscheduler.domain.model.Target
import com.iranjan.hotspotscheduler.data.repository.RoutineRepository
import com.iranjan.hotspotscheduler.data.datastore.AutomationPreferences
import com.iranjan.hotspotscheduler.domain.scheduler.DesiredStateResolver
import com.iranjan.hotspotscheduler.platform.alarm.AlarmScheduler
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import java.time.Instant
import java.time.ZoneId

class BoundarySchedulerTest {

    private lateinit var scheduler: BoundaryScheduler
    private lateinit var mockRepo: RoutineRepository
    private lateinit var mockPrefs: AutomationPreferences
    private lateinit var mockAlarm: AlarmScheduler
    private lateinit var mockDesiredState: DesiredStateResolver

    @Before
    fun setUp() {
        mockRepo = mock(RoutineRepository::class.java)
        mockPrefs = mock(AutomationPreferences::class.java)
        mockAlarm = mock(AlarmScheduler::class.java)
        mockDesiredState = mock(DesiredStateResolver::class.java)
        scheduler = BoundaryScheduler(mockRepo, mockPrefs, mockAlarm, mockDesiredState)
    }

    @Test
    fun `rescheduleAll calls alarm scheduler with routines and master flag`() = runBlockingTest {
        val routine = Routine(
            id = 1, name = "Test", enabled = true,
            daysOfWeek = java.time.DayOfWeek.values().toSet(),
            startTime = java.time.LocalTime.of(2, 0),
            endTime = java.time.LocalTime.of(3, 0),
            hotspotTarget = Target.Set(true),
            mobileDataTarget = Target.Set(true)
        )
        `when`(mockPrefs.masterEnabled.first()).thenReturn(true)
        `when`(mockRepo.getEnabledRoutines()).thenReturn(listOf(routine))

        scheduler.rescheduleAll()

        verify(mockAlarm).rescheduleAll(listOf(routine), true)
    }

    @Test
    fun `rescheduleAll passes masterDisabled false to alarm`() = runBlockingTest {
        `when`(mockPrefs.masterEnabled.first()).thenReturn(false)
        `when`(mockRepo.getEnabledRoutines()).thenReturn(emptyList())

        scheduler.rescheduleAll()

        verify(mockAlarm).rescheduleAll(emptyList(), false)
    }
}

import kotlinx.coroutines.runBlocking

fun runBlockingTest(block: suspend () -> Unit) = runBlocking { block() }