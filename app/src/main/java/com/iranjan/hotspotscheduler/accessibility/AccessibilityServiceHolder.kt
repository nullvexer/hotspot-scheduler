package com.iranjan.hotspotscheduler.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.accessibilityservice.GestureDescription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AccessibilityServiceHolder {

    @Volatile
    var service: AccessibilityService? = null

    @Volatile
    var calibrationMode = false

    val calibrationDumps: MutableStateFlow<List<String>> = MutableStateFlow(emptyList())

    internal class ServiceWrapper(private val service: AccessibilityService) {
        val rootInActiveWindow: AccessibilityNodeInfo? get() = service.rootInActiveWindow
        val windows: List<AccessibilityWindowInfo> get() = service.windows ?: emptyList()
        fun dispatchGesture(desc: GestureDescription): Boolean = service.dispatchGesture(desc, null, null)
        fun performGlobalAction(action: Int): Boolean = service.performGlobalAction(action)
    }
}