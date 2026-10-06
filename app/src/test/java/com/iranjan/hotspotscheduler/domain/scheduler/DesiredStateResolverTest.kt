package com.iranjan.hotspotscheduler.domain.scheduler

import com.iranjan.hotspotscheduler.domain.model.DesiredNetworkState
import com.iranjan.hotspotscheduler.domain.model.Routine
import com.iranjan.hotspotscheduler.domain.model.Target
import com.iranjan.hotspotscheduler.data.repository.RoutineRepository
import com.iranjan.hotspotscheduler.data.datastore.AutomationPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import java.time.Instant
import java.time.ZoneId

class DesiredStateResolverTest {

    private lateinit var resolver: DesiredStateResolver
    private lateinit var mockRepo: RoutineRepository
    private lateinit var mockPrefs: AutomationPreferences

    @Before
    fun setUp() {
        mockRepo = mock(RoutineRepository::class.java)
        mockPrefs = mock(AutomationPreferences::class.java)
        resolver = DesiredStateResolver(mockRepo, mockPrefs)
    }

    @Test
    fun `masterDisabled returns untouched`() = runBlockingTest {
        val inputs = DesiredStateResolver.Inputs(
            now = Instant.now(),
            zone = ZoneId.systemDefault(),
            masterEnabled = false,
            paused = false,
            capReached = false,
            hotspotSuppressed = false
        )
        val result = resolver.resolve(inputs)
        assertEquals(DesiredNetworkState.untouched(), result)
    }

    @Test
    fun `paused returns untouched`() = runBlockingTest {
        val inputs = DesiredStateResolver.Inputs(
            now = Instant.now(),
            zone = ZoneId.systemDefault(),
            masterEnabled = true,
            paused = true,
            capReached = false,
            hotspotSuppressed = false
        )
        val result = resolver.resolve(inputs)
        assertEquals(DesiredNetworkState.untouched(), result)
    }

    @Test
    fun `noActiveRoutines returns allOff`() = runBlockingTest {
        `when`(mockRepo.getEnabledRoutines()).thenReturn(emptyList())
        val inputs = DesiredStateResolver.Inputs(
            now = Instant.now(),
            zone = ZoneId.systemDefault(),
            masterEnabled = true,
            paused = false,
            capReached = false,
            hotspotSuppressed = false
        )
        val result = resolver.resolve(inputs)
        assertEquals(DesiredNetworkState.allOff(), result)
    }

    @Test
    fun `activeRoutineWithDataAndHotspot returns bothOn`() = runBlockingTest {
        val now = Instant.now()
        val routine = Routine(
            id = 1, name = "Test", enabled = true,
            daysOfWeek = java.time.DayOfWeek.values().toSet(),
            startTime = now.atZone(ZoneId.systemDefault()).toLocalTime().minusMinutes(30),
            endTime = now.atZone(ZoneId.systemDefault()).toLocalTime().plusMinutes(30),
            hotspotTarget = Target.Set(true),
            mobileDataTarget = Target.Set(true)
        )
        `when`(mockRepo.getEnabledRoutines()).thenReturn(listOf(routine))
        val inputs = DesiredStateResolver.Inputs(now, ZoneId.systemDefault(), true, false, false, false)
        val result = resolver.resolve(inputs)
        assertEquals(Target.Set(true), result.hotspot)
        assertEquals(Target.Set(true), result.mobileData)
    }

    @Test
    fun `capReached forces hotspotOff`() = runBlockingTest {
        val now = Instant.now()
        val routine = Routine(
            id = 1, name = "Test", enabled = true,
            daysOfWeek = java.time.DayOfWeek.values().toSet(),
            startTime = now.atZone(ZoneId.systemDefault()).toLocalTime().minusMinutes(30),
            endTime = now.atZone(ZoneId.systemDefault()).toLocalTime().plusMinutes(30),
            hotspotTarget = Target.Set(true),
            mobileDataTarget = Target.Set(true)
        )
        `when`(mockRepo.getEnabledRoutines()).thenReturn(listOf(routine))
        val inputs = DesiredStateResolver.Inputs(now, ZoneId.systemDefault(), true, false, true, false)
        val result = resolver.resolve(inputs)
        assertEquals(Target.Set(false), result.hotspot)
        assertEquals(Target.Set(true), result.mobileData)
    }

    @Test
    fun `suppressed forces hotspotOff`() = runBlockingTest {
        val now = Instant.now()
        val routine = Routine(
            id = 1, name = "Test", enabled = true,
            daysOfWeek = java.time.DayOfWeek.values().toSet(),
            startTime = now.atZone(ZoneId.systemDefault()).toLocalTime().minusMinutes(30),
            endTime = now.atZone(ZoneId.systemDefault()).toLocalTime().plusMinutes(30),
            hotspotTarget = Target.Set(true),
            mobileDataTarget = Target.Set(true)
        )
        `when`(mockRepo.getEnabledRoutines()).thenReturn(listOf(routine))
        val inputs = DesiredStateResolver.Inputs(now, ZoneId.systemDefault(), true, false, false, true)
        val result = resolver.resolve(inputs)
        assertEquals(Target.Set(false), result.hotspot)
    }
}

// Simple runBlockingTest for JUnit 4
import kotlinx.coroutines.runBlocking

fun runBlockingTest(block: suspend () -> Unit) = runBlocking { block() }