package com.iranjan.hotspotscheduler.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The app's only accessibility service. It serves three jobs:
 *
 *  - drives the Settings screen for hotspot / mobile data toggles;
 *  - drives the lock screen's own PIN keypad for unattended unlock;
 *  - provides a node dump for calibration.
 *
 * The service is deliberately registered with NO `android:packageNames` filter, because a filtered
 * service never receives events from the keyguard/SystemUI process and therefore cannot see the
 * PIN keypad at all. To bound the cost of an unfiltered service, every event is checked against a
 * short allowlist of interesting packages and ignored otherwise.
 */
class HotspotAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Set by the unlock engine so it can run gestures and multi-window traversal on this instance. */
    @Volatile
    var automator: KeyguardAutomator? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityServiceHolder.service = this
        automator = KeyguardAutomator(this)
        Log.i(TAG, "accessibility service connected; keyguard automator ready")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in INTERESTING_PACKAGES) return

        if (AccessibilityServiceHolder.calibrationMode &&
            pkg == SETTINGS_PACKAGE &&
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            scope.launch { emitCalibrationDump() }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (AccessibilityServiceHolder.service === this) {
            AccessibilityServiceHolder.service = null
        }
        automator = null
        scope.cancel()
        Log.i(TAG, "accessibility service destroyed")
    }

    fun rootNode(): AccessibilityNodeInfo? = rootInActiveWindow

    suspend fun emitCalibrationDump() = withContext(Dispatchers.Main) {
        val dumps = NodeDumper.dump(rootInActiveWindow)
        AccessibilityServiceHolder.calibrationDumps.value = dumps
        Log.i(TAG, "calibration dump captured: ${dumps.size} nodes")
    }

    companion object {
        const val SETTINGS_PACKAGE = "com.android.settings"
        const val SAMSUNG_SETTINGS_PACKAGE = "com.samsung.android.settings"

        /** Where the keyguard and its PIN bouncer live. */
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        const val KEYGUARD_PACKAGE = "com.android.keyguard"

        /**
         * With the package filter removed, every app's window events arrive here. Only these are
         * acted on.
         */
        val INTERESTING_PACKAGES = setOf(
            SETTINGS_PACKAGE,
            SAMSUNG_SETTINGS_PACKAGE,
            SYSTEM_UI_PACKAGE,
            KEYGUARD_PACKAGE
        )

        private const val TAG = "HSAuto"
    }
}