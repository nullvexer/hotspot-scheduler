package com.iranjan.hotspotscheduler.automation.strategies

import com.iranjan.hotspotscheduler.platform.Result
import com.iranjan.hotspotscheduler.platform.accessibility.AccessibilityRuntime
import com.iranjan.hotspotscheduler.platform.accessibility.NodeSelector
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import kotlinx.coroutines.runBlocking

class QuickSettingsNavigatorTest {

    private lateinit var navigator: QuickSettingsNavigator
    private lateinit var mockAccessibility: AccessibilityRuntime

    @Before
    fun setUp() {
        mockAccessibility = mock(AccessibilityRuntime::class.java)
        navigator = QuickSettingsNavigator(mockAccessibility)
    }

    @Test
    fun `openQuickSettings calls global action`() = runBlocking {
        `when`(mockAccessibility.globalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS))
            .thenReturn(Result.success(Unit))

        val result = navigator.openQuickSettings()
        assertTrue(result is Result.Success)
        verify(mockAccessibility).globalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
    }

    @Test
    fun `collapseQuickSettings calls back action`() = runBlocking {
        `when`(mockAccessibility.globalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
            .thenReturn(Result.success(Unit))

        val result = navigator.collapseQuickSettings()
        assertTrue(result is Result.Success)
    }
}