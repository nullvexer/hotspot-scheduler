package com.iranjan.hotspotscheduler.automation.strategies

import com.iranjan.hotspotscheduler.domain.model.StepResult
import com.iranjan.hotspotscheduler.platform.Result
import com.iranjan.hotspotscheduler.platform.accessibility.AccessibilityRuntime
import com.iranjan.hotspotscheduler.automation.strategies.QuickSettingsNavigator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import kotlinx.coroutines.runBlocking

class SamsungQuickSettingsHotspotStrategyTest {

    private lateinit var strategy: SamsungQuickSettingsHotspotStrategy
    private lateinit var mockAccessibility: AccessibilityRuntime
    private lateinit var mockQSNavigator: QuickSettingsNavigator

    @Before
    fun setUp() {
        mockAccessibility = mock(AccessibilityRuntime::class.java)
        mockQSNavigator = mock(QuickSettingsNavigator::class.java)
        strategy = SamsungQuickSettingsHotspotStrategy(mockAccessibility, mockQSNavigator)
    }

    @Test
    fun `readState returns tile state`() = runBlocking {
        val mockTile = mock(android.view.accessibility.AccessibilityNodeInfo::class.java)
        `when`(mockQSNavigator.openQuickSettings()).thenReturn(Result.success(Unit))
        `when`(mockQSNavigator.findTile("hotspot")).thenReturn(Result.success(mockTile))
        `when`(mockQSNavigator.getTileState(mockTile)).thenReturn(true)
        `when`(mockQSNavigator.collapseQuickSettings()).thenReturn(Result.success(Unit))

        val result = strategy.readState()
        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).value)
    }

    @Test
    fun `setState returns ALREADY_OK when already in target state`() = runBlocking {
        val mockTile = mock(android.view.accessibility.AccessibilityNodeInfo::class.java)
        `when`(mockQSNavigator.openQuickSettings()).thenReturn(Result.success(Unit))
        `when`(mockQSNavigator.findTile("hotspot")).thenReturn(Result.success(mockTile))
        `when`(mockQSNavigator.getTileState(mockTile)).thenReturn(true)
        `when`(mockQSNavigator.collapseQuickSettings()).thenReturn(Result.success(Unit))

        val result = strategy.setState(true, null)
        assertTrue(result is Result.Success)
        assertEquals(StepResult.ALREADY_OK, (result as Result.Success).value)
    }
}