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

class SamsungQuickSettingsDataStrategyTest {

    private lateinit var strategy: SamsungQuickSettingsDataStrategy
    private lateinit var mockAccessibility: AccessibilityRuntime
    private lateinit var mockQSNavigator: QuickSettingsNavigator

    @Before
    fun setUp() {
        mockAccessibility = mock(AccessibilityRuntime::class.java)
        mockQSNavigator = mock(QuickSettingsNavigator::class.java)
        strategy = SamsungQuickSettingsDataStrategy(mockAccessibility, mockQSNavigator)
    }

    @Test
    fun `readState returns tile state`() = runBlocking {
        val mockTile = mock(android.view.accessibility.AccessibilityNodeInfo::class.java)
        `when`(mockQSNavigator.openQuickSettings()).thenReturn(Result.success(Unit))
        `when`(mockQSNavigator.findTile("mobile data")).thenReturn(Result.success(mockTile))
        `when`(mockQSNavigator.getTileState(mockTile)).thenReturn(false)
        `when`(mockQSNavigator.collapseQuickSettings()).thenReturn(Result.success(Unit))

        val result = strategy.readState()
        assertTrue(result is Result.Success)
        assertFalse((result as Result.Success).value)
    }

    @Test
    fun `setState returns CHANGED when state changes`() = runBlocking {
        val mockTile = mock(android.view.accessibility.AccessibilityNodeInfo::class.java)
        `when`(mockQSNavigator.openQuickSettings()).thenReturn(Result.success(Unit))
        `when`(mockQSNavigator.findTile("mobile data")).thenReturn(Result.success(mockTile))
        `when`(mockQSNavigator.getTileState(mockTile)).thenReturn(false) // initially off
        `when`(mockQSNavigator.clickTile(mockTile)).thenReturn(Result.success(Unit))
        `when`(mockQSNavigator.collapseQuickSettings()).thenReturn(Result.success(Unit))
        // verify after
        `when`(mockQSNavigator.openQuickSettings()).thenReturn(Result.success(Unit))
        `when`(mockQSNavigator.findTile("mobile data")).thenReturn(Result.success(mockTile))
        `when`(mockQSNavigator.getTileState(mockTile)).thenReturn(true) // now on
        `when`(mockQSNavigator.collapseQuickSettings()).thenReturn(Result.success(Unit))

        val result = strategy.setState(true, null)
        assertTrue(result is Result.Success)
        assertEquals(StepResult.CHANGED, (result as Result.Success).value)
    }
}