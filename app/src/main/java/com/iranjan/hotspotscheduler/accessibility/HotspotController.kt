package com.iranjan.hotspotscheduler.accessibility

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.iranjan.hotspotscheduler.data.model.CalibrationSignature
import com.iranjan.hotspotscheduler.data.prefs.AutomationPrefs
import com.iranjan.hotspotscheduler.service.NotificationHelper
import com.iranjan.hotspotscheduler.util.PassphraseRules
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

enum class ToggleResult { ALREADY_OK, TOGGLED, FAILED, BLOCKED }

interface HotspotController {
    suspend fun readHotspotState(): Boolean?
    suspend fun setHotspotState(targetOn: Boolean, password: String? = null): ToggleResult
    suspend fun setMobileData(targetOn: Boolean): ToggleResult
    suspend fun requestCalibrationDump()
}

/**
 * Drives the Settings UI through the accessibility service. This is the only toggle engine.
 *
 * A toggle is a single sequence with escalating stages, and each stage only stops the sequence on
 * a definitive result:
 *
 *  0. The target switch is already on screen and readable -> click it, verify, retry once.
 *  1. Turn the screen on and dismiss a non-secure keyguard.
 *  2. Launch the Settings screen and navigate to it.
 *  3. Ask the user to unlock, then poll until they have or the deadline passes.
 *
 * `BLOCKED` means "the app cannot do this on its own" (a secure lock screen) and is distinct from
 * `FAILED` so the caller can tell the user something actionable instead of a generic failure.
 */
@Singleton
class AccessibilityHotspotControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AutomationPrefs,
    private val notifications: NotificationHelper,
    private val navigator: HotspotNavigator,
    private val screen: ScreenControl
) : HotspotController {

    /**
     * Serialises every toggle. This is a @Singleton holding a KeyguardLock; without this a
     * "turn off now" from the shade could restore the keyguard underneath an in-flight boundary
     * toggle, and two attempts would drive the same Settings tree at once.
     */
    private val toggleLock = Mutex()

    override suspend fun readHotspotState(): Boolean? = withContext(Dispatchers.Default) {
        val service = AccessibilityServiceHolder.service ?: return@withContext null
        val root = service.rootNode() ?: return@withContext null
        if (!isSettingsPackage(root)) return@withContext null
        val calibration = prefs.calibration()
        val match = NodeMatcher.findToggle(root, calibration, KEYWORD_HOTSPOT) ?: return@withContext null
        NodeMatcher.readState(match)
    }

    override suspend fun setHotspotState(targetOn: Boolean, password: String?): ToggleResult =
        toggleLock.withLock { setHotspotStateLocked(targetOn, password) }

    private suspend fun setHotspotStateLocked(targetOn: Boolean, password: String?): ToggleResult {
        val validPassword = password?.takeIf { PassphraseRules.isValid(it) }
        if (password != null && validPassword == null) {
            AttemptLog.add(
                "routine password rejected by WPA2 rules " +
                    "(${PassphraseRules.rejectionReason(password)}); keeping the current password"
            )
        }
        val result = withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
            runToggle(KEYWORD_HOTSPOT, targetOn, useCalibration = true, password = validPassword)
        } ?: ToggleResult.FAILED

        AttemptLog.add("HOTSPOT target=$targetOn -> $result")
        when (result) {
            ToggleResult.FAILED -> Log.e(TAG, "hotspot toggle failed targetOn=$targetOn")
            ToggleResult.BLOCKED -> notifications.notifyUnlockRequired()
            else -> prefs.setLastKnownHotspotOn(targetOn)
        }
        return result
    }

    override suspend fun setMobileData(targetOn: Boolean): ToggleResult = toggleLock.withLock {
        val result = withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
            runToggle(KEYWORD_MOBILE_DATA, targetOn, useCalibration = false, password = null)
        } ?: ToggleResult.FAILED
        AttemptLog.add("MOBILE DATA target=$targetOn -> $result")
        Log.i(TAG, "mobile data toggle targetOn=$targetOn result=$result")
        result
    }

    override suspend fun requestCalibrationDump() {
        AccessibilityServiceHolder.service?.emitCalibrationDump()
    }

    private suspend fun runToggle(
        rowKeyword: String,
        targetOn: Boolean,
        useCalibration: Boolean,
        password: String?
    ): ToggleResult {
        try {
            return runToggleInner(rowKeyword, targetOn, useCalibration, password)
        } finally {
            // Never leave the screen on because of a toggle that ended early.
            settleScreen()
        }
    }

    private suspend fun runToggleInner(
        rowKeyword: String,
        targetOn: Boolean,
        useCalibration: Boolean,
        password: String?
    ): ToggleResult {
        AttemptLog.add("=== toggle $rowKeyword target=$targetOn start; ${screen.capabilities()}")

        // Stage 0: the switch may already be on screen (app left in Settings, user is awake).
        settle(attempt(rowKeyword, targetOn, useCalibration, password))?.let { return it }

// Stage 1: wake the screen and ask the platform to clear the keyguard. A trusted state
        // (Smart Lock / Extend Unlock / trusted places) clears here with no user interaction.
        screen.wakeScreen(WAKE_HOLD_MS)
        screen.requestKeyguardDismiss()
        delay(SETTLE_DELAY_MS)
        settle(attempt(rowKeyword, targetOn, useCalibration, password))?.let { return it }

        // Stage 2: launch Settings and navigate. Background activity starts are restricted, so the
        // launch goes through the foreground host activity.
        screen.wakeScreen(WAKE_HOLD_MS)
        if (!screen.isLocked()) {
            launchFor(rowKeyword)
            awaitScreen(rowKeyword, useCalibration, SCREEN_WAIT_MS)
            settle(attempt(rowKeyword, targetOn, useCalibration, password))?.let { return it }
            navigateFor(rowKeyword, useCalibration)
            settle(attempt(rowKeyword, targetOn, useCalibration, password))?.let { return it }
            AttemptLog.add("gave up after launching Settings for '$rowKeyword'")
            return ToggleResult.FAILED
        }

        // Stage 3: waiting on the keyguard.
        return awaitUnlock(rowKeyword, targetOn, useCalibration, password)
    }

/**
     * The screen is locked. A trusted state (Smart Lock, Extend Unlock / trusted places) is
     * dismissed immediately and without user interaction; otherwise the platform puts the
     * credential UI up and we wait, re-requesting because Smart Lock can become trusted at any
     * moment during the window.
     */
    private suspend fun awaitUnlock(
        rowKeyword: String,
        targetOn: Boolean,
        useCalibration: Boolean,
        password: String?
    ): ToggleResult {
        val deadline = System.currentTimeMillis() + UNLOCK_WAIT_MS
        var requested = false
        var announced = false
        while (System.currentTimeMillis() < deadline) {
            screen.wakeScreen(WAKE_HOLD_MS)
            when (screen.requestKeyguardDismiss()) {
                ScreenControl.DismissOutcome.DISMISSED -> {
                    AttemptLog.add("keyguard cleared; resuming automation")
                    launchFor(rowKeyword)
                    awaitScreen(rowKeyword, useCalibration, SCREEN_WAIT_MS)
                    settle(attempt(rowKeyword, targetOn, useCalibration, password))?.let { return it }
                    navigateFor(rowKeyword, useCalibration)
                    settle(attempt(rowKeyword, targetOn, useCalibration, password))?.let { return it }
                    return ToggleResult.FAILED
                }
                ScreenControl.DismissOutcome.SECURED -> {
                    requested = true
                    if (!announced) {
                        // Tell the user once, and tell them the thing that actually helps.
                        AttemptLog.add(
                            "a credential is required to unlock; notifying the user once and " +
                                "keeping the phone awake"
                        )
                        notifications.notifyUnlockRequired()
                        announced = true
                    }
                }
                ScreenControl.DismissOutcome.UNKNOWN -> requested = true
            }
            delay(500)
        }
        AttemptLog.add("still locked after ${UNLOCK_WAIT_MS / 1000}s (requested=$requested)")
        return if (requested) ToggleResult.BLOCKED else ToggleResult.FAILED
    }

    /** Only a definitive result stops the ladder; FAILED means "try the next stage". */
    private fun settle(result: ToggleResult?): ToggleResult? =
        when (result) {
            null, ToggleResult.FAILED -> null
            else -> result
        }

    /**
     * Restores the screen afterwards: turns it back off if we turned it on, unless the user is
     * actively using the phone or the toggle failed and the screen is still needed.
     */
    private suspend fun settleScreen() {
        if (!turnScreenOffAfterToggle) return
        delay(POST_TOGGLE_SETTLE_MS)
        when {
            !screen.isInteractive() -> Unit
            screen.isLocked() -> Unit
            !screen.lockNow() -> AttemptLog.add("screen left on: device administrator is not enabled")
            else -> delay(POST_LOCK_SETTLE_MS)
        }
    }

    private suspend fun navigateFor(rowKeyword: String, useCalibration: Boolean): Boolean {
        val calibration = if (useCalibration) prefs.calibration() else null
        val intermediates = if (rowKeyword == KEYWORD_MOBILE_DATA) {
            listOf("data usage", "connections")
        } else {
            listOf("mobile hotspot and tethering", "connections")
        }
        for (label in intermediates) {
            val row = withContext(Dispatchers.Default) {
                NodeMatcher.findClickableRow(rootNode(), label)
            } ?: continue
            withContext(Dispatchers.Default) {
                row.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            AttemptLog.add("clicked '$label' to navigate")
            delay(NAVIGATE_DELAY_MS)
            if (findToggle(rowKeyword, calibration) != null) return true
        }
        return false
    }

    private suspend fun logScreenDump(rowKeyword: String) {
        val root = rootNode() ?: return
        val lines = withContext(Dispatchers.Default) { NodeDumper.dumpCompact(root, 40) }
        AttemptLog.add("screen dump for '$rowKeyword':")
        lines.forEach { AttemptLog.add(it) }
    }

private suspend fun launchFor(rowKeyword: String): Boolean {
        val launched = if (rowKeyword == KEYWORD_HOTSPOT) {
            navigator.launchHotspotSettings(screen)
        } else {
            navigator.launchDataUsageSettings(screen)
        }
        val root = rootNode()
        AttemptLog.add("launched=$launched screen=${root?.className} pkg=${root?.packageName}")
        return launched
    }

    private suspend fun attempt(
        rowKeyword: String,
        targetOn: Boolean,
        useCalibration: Boolean,
        password: String?
    ): ToggleResult? {
        val calibration = if (useCalibration) prefs.calibration() else null
        var openedConfig = false
        var match = findToggle(rowKeyword, calibration)
        if (match == null) {
            if (rowKeyword == KEYWORD_HOTSPOT && password != null && targetOn) {
                openHotspotConfigScreen()
                openedConfig = true
                match = findToggle(rowKeyword, calibration) ?: return null
            } else {
                return null
            }
        }
        AttemptLog.add("match=${match.source}")

        if (password != null && targetOn && rowKeyword == KEYWORD_HOTSPOT) {
            // Only touch the password field when it is already on screen. Navigating to the config
            // screen from here would move away from the switch we just matched, and the routine
            // would then fail *because* it carries a password.
            applyPassword(password, allowNavigation = openedConfig)
            match = findToggle(rowKeyword, calibration) ?: return null
        }

        val before = withContext(Dispatchers.Default) { NodeMatcher.readState(match) }
        if (before == null) {
            AttemptLog.add("state unreadable")
            return null
        }
        AttemptLog.add("state before=$before")
        if (before == targetOn) return ToggleResult.ALREADY_OK

        withContext(Dispatchers.Default) {
            match.clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        AttemptLog.add("click sent")
        if (awaitStateChange(before, rowKeyword, calibration)) {
            AttemptLog.add("state changed OK")
            return ToggleResult.TOGGLED
        }

        val retryMatch = findToggle(rowKeyword, calibration)
        if (retryMatch != null) {
            val stateAfter = withContext(Dispatchers.Default) { NodeMatcher.readState(retryMatch) }
            if (stateAfter == targetOn) return ToggleResult.TOGGLED
            if (stateAfter == before) {
                AttemptLog.add("retrying click once")
                withContext(Dispatchers.Default) {
                    retryMatch.clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                if (awaitStateChange(before, rowKeyword, calibration)) {
                    AttemptLog.add("state changed OK after retry")
                    return ToggleResult.TOGGLED
                }
            }
        }
        logScreenDump(rowKeyword)
        return ToggleResult.FAILED
    }

    private suspend fun applyPassword(password: String, allowNavigation: Boolean) {
        var editor = withContext(Dispatchers.Default) {
            NodeMatcher.findPasswordEditor(settingsRoot())
        }
        if (editor == null && allowNavigation) {
            openHotspotConfigScreen()
            editor = withContext(Dispatchers.Default) {
                NodeMatcher.findPasswordEditor(settingsRoot())
            }
        }
        if (editor == null) {
            AttemptLog.add("password field not found; keeping existing password")
            return
        }
        val applied = withContext(Dispatchers.Default) { NodeMatcher.setText(editor, password) }
        AttemptLog.add("password applied=$applied")
        delay(400)
    }

    private suspend fun openHotspotConfigScreen() {
        val row = withContext(Dispatchers.Default) {
            NodeMatcher.findClickableRow(settingsRoot(), KEYWORD_HOTSPOT)
        } ?: run {
            AttemptLog.add("hotspot row not found for click-through")
            return
        }
        withContext(Dispatchers.Default) {
            row.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        AttemptLog.add("clicked hotspot row to open config screen")
        val opened = awaitPasswordField(CONFIG_WAIT_MS)
        AttemptLog.add("config screen with password field=$opened")
    }

    private suspend fun findToggle(
        rowKeyword: String,
        calibration: CalibrationSignature?
    ): ToggleMatch? {
        val service = AccessibilityServiceHolder.service ?: run {
            AttemptLog.add("accessibility service not connected")
            return null
        }
        return withContext(Dispatchers.Default) {
            val root = service.rootNode()
            when {
                root == null -> null
                !isSettingsPackage(root) -> {
                    AttemptLog.add("ignoring own window; waiting for settings")
                    null
                }
                else -> NodeMatcher.findToggle(root, calibration, rowKeyword)
            }
        }
    }

    private suspend fun settingsRoot(): AccessibilityNodeInfo? = withContext(Dispatchers.Default) {
        val root = rootNode() ?: return@withContext null
        if (!isSettingsPackage(root)) null else root
    }

    private suspend fun awaitPasswordField(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val root = settingsRoot()
            if (root != null && NodeMatcher.findPasswordEditor(root) != null) return true
            delay(300)
        }
        return false
    }

    private suspend fun rootNode(): AccessibilityNodeInfo? =
        AccessibilityServiceHolder.service?.rootNode()

    private suspend fun awaitStateChange(
        before: Boolean,
        rowKeyword: String,
        calibration: CalibrationSignature?
    ): Boolean {
        val deadline = System.currentTimeMillis() + STATE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(300)
            val match = findToggle(rowKeyword, calibration) ?: continue
            val state = withContext(Dispatchers.Default) { NodeMatcher.readState(match) }
            if (state != null && state != before) return true
        }
        return false
    }

    private suspend fun awaitScreen(rowKeyword: String, useCalibration: Boolean, timeoutMs: Long): Boolean {
        val calibration = if (useCalibration) prefs.calibration() else null
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (findToggle(rowKeyword, calibration) != null) return true
            delay(300)
        }
        return false
    }

    private fun isSettingsPackage(root: AccessibilityNodeInfo): Boolean {
        val pkg = root.packageName?.toString() ?: return false
        val ok = pkg == SETTINGS_PACKAGE || pkg == SAMSUNG_SETTINGS_PACKAGE
        if (!ok) AttemptLog.add("unexpected window: $pkg")
        return ok
    }

    companion object {
        const val SETTINGS_PACKAGE = "com.android.settings"
        const val SAMSUNG_SETTINGS_PACKAGE = "com.samsung.android.settings"
        private const val TAG = "HSAuto"
        private const val STATE_TIMEOUT_MS = 3_000L
        private const val SCREEN_WAIT_MS = 6_000L
        private const val CONFIG_WAIT_MS = 6_000L
private const val UNLOCK_WAIT_MS = 150_000L
        private const val TOTAL_TIMEOUT_MS = 180_000L
        private const val WAKE_HOLD_MS = 120_000L
        private const val SETTLE_DELAY_MS = 1_200L
        private const val NAVIGATE_DELAY_MS = 1_800L
        private const val NAVIGATE_EVERY = 8
        private const val POST_TOGGLE_SETTLE_MS = 1_200L
        private const val POST_LOCK_SETTLE_MS = 400L

        /**
         * Set from the Setup screen. When true, a completed toggle turns the screen back off via
         * the device administrator, provided the user was not already using the phone.
         */
        @Volatile
        var turnScreenOffAfterToggle: Boolean = true
    }
}