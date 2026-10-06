package com.iranjan.hotspotscheduler.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HotspotAccessibilityService : AccessibilityService() {

    private val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityServiceHolder.service = this
        Log.i("HSAccessibility", "accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Events will be processed via AccessibilityRuntime in Phase 4
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (AccessibilityServiceHolder.service === this) {
            AccessibilityServiceHolder.service = null
        }
        Log.i("HSAccessibility", "accessibility service destroyed")
    }
}