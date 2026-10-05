package com.iranjan.hotspotscheduler.accessibility

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.iranjan.hotspotscheduler.data.model.CalibrationSignature
import com.iranjan.hotspotscheduler.data.prefs.AutomationPrefs
import com.iranjan.hotspotscheduler.domain.OperationOrder
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

/**
 * Outcome of one complete unattended job: wake -> unlock -> all requested changes -> lock once.
 *
 * Per-step results are kept so a partial failure stays reportable. "hotspot changed, mobile data
 * failed" must never be summarised as success.
 */
data class TransactionResult(
    val hotspot: ToggleResult?,
    val mobileData: ToggleResult?,
    val screenAwake: Boolean,
    val lockedAtEnd: Boolean
) {
    private fun isBad(r: ToggleResult?): Boolean =
        r == ToggleResult.FAILED || r == ToggleResult.BLOCKED

    val anyFailure: Boolean get() = isBad(hotspot) || isBad(mobileData)

    val allSucceeded: Boolean get() = !anyFailure

    fun describe(r: ToggleResult?): String = when (r) {
        null -> "not requested"
        ToggleResult.ALREADY_OK -> "already in the requested state"
        ToggleResult.TOGGLED -> "changed"
        ToggleResult.FAILED -> "FAILED"
        ToggleResult.BLOCKED -> "BLOCKED (the phone stayed locked)"
    }

    val summary: String
        get() = buildList {
            if (hotspot != null) add("hotspot ${describe(hotspot)}")
            if (mobileData != null) add("mobile data ${describe(mobileData)}")
            add(if (screenAwake) "screen woke" else "SCREEN DID NOT WAKE")
            add(if (lockedAtEnd) "phone locked at the end" else "phone left awake at the end")
        }.joinToString("; ")
}

interface HotspotController {
    suspend fun readHotspotState(): Boolean?

    /**
     * Runs one whole scheduled boundary as a single unattended transaction.
     *
     * @param hotspotOn the hotspot target state.
     * @param mobileDataTarget null to leave mobile data alone. The caller resolves the
     *   end-of-window "does another routine still need data" question, because that needs the
     *   routine list and this class deliberately knows nothing about routines.
     * @param password optional per-routine hotspot passphrase.
     */
    suspend fun executeBoundary(
        hotspotOn: Boolean,
        mobileDataTarget: Boolean?,
        password: String? = null
    ): TransactionResult

    /** Live test with the same transaction shape as a scheduled boundary. */
    suspend fun runLiveTest(
        hotspot: Boolean?,
        mobileData: Boolean?,
        password: String? = null
    ): TransactionResult

    suspend fun requestCalibrationDump()
}

/**
 * Drives the Settings UI through the accessibility service. This is the only toggle engine.
 *
 * ## Transaction model
 *
 * Screen ownership belongs to the whole job, never to an individual toggle. An earlier build called
 * `lockScreen()` from a `finally` inside every single toggle, so a routine needing both hotspot and
 * mobile data ran:
 *
 * ```
 * unlock -> hotspot ON -> LOCK -> mobile data ON     (on a now-locked phone)
 * ```
 *
 * The lock now happens exactly once, after every requested step, inside [transaction]. The steps
 * themselves ([setHotspot], [setMobileData]) never touch screen state.
 *
 * Everything is serialised on one mutex: this is a @Singleton, and a "turn off now" from the
 * notification shade must not interleave with a boundary toggle.
 */
@Singleton
class AccessibilityHotspotControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AutomationPrefs,
    private val notifications: NotificationHelper,
    private val navigator: HotspotNavigator,
    private val screen: ScreenControl
) : HotspotController {

    private val toggleLock = Mutex()

    /** User preference: put the phone back to sleep when the job finishes. */
    @Volatile
    private var lockWhenFinished = true

    fun setLockWhenFinished(enabled: Boolean) {
        lockWhenFinished = enabled
    }

    override suspend fun readHotspotState(): Boolean? = withContext(Dispatchers.Default) {
        val service = AccessibilityServiceHolder.service ?: return@withContext null
        val root = service.rootNode() ?: return@withContext null
        if (!isSettingsPackage(root)) return@withContext null
        val calibration = prefs.calibration()
        val match = NodeMatcher.findToggle(root, calibration, KEYWORD_HOTSPOT) ?: return@withContext null
        NodeMatcher.readState(match)
    }

    override suspend fun executeBoundary(
        hotspotOn: Boolean,
        mobileDataTarget: Boolean?,
        password: String?
    ): TransactionResult = toggleLock.withLock {
        transaction("boundary hotspot=$hotspotOn data=$mobileDataTarget") {
            val valid = validatePassword(password)
            val results = LinkedHashMap<OperationOrder.Feature, ToggleResult>()
            // Dependency-safe order: mobile data before the hotspot when turning things on,
            // because an internet-sharing hotspot needs an upstream to be useful.
            AttemptLog.add("operation order: ${OperationOrder.describe(hotspotOn, mobileDataTarget)}")
            for (feature in OperationOrder.plan(hotspotOn, mobileDataTarget)) {
                // An unexpected lock mid-transaction means a person touched the phone or the
                // system intervened. Stop rather than driving the UI blind or re-attempting the
                // credential.
                if (screen.isDeviceLocked()) {
                    AttemptLog.add("phone locked unexpectedly before ${feature.name}; stopping")
                    results[feature] = ToggleResult.BLOCKED
                    break
                }
                results[feature] = when (feature) {
                    OperationOrder.Feature.HOTSPOT -> setHotspot(hotspotOn == true, valid)
                    OperationOrder.Feature.MOBILE_DATA -> setMobileData(mobileDataTarget == true)
                }
            }
            TransactionResult(
                hotspot = results[OperationOrder.Feature.HOTSPOT],
                mobileData = results[OperationOrder.Feature.MOBILE_DATA],
                screenAwake = true,
                lockedAtEnd = false
            )
        }
    }

    override suspend fun runLiveTest(
        hotspot: Boolean?,
        mobileData: Boolean?,
        password: String?
    ): TransactionResult = toggleLock.withLock {
        transaction("live test hotspot=$hotspot data=$mobileData") {
            val valid = validatePassword(password)
            val results = LinkedHashMap<OperationOrder.Feature, ToggleResult>()
            AttemptLog.add("operation order: ${OperationOrder.describe(hotspot, mobileData)}")
            for (feature in OperationOrder.plan(hotspot, mobileData)) {
                if (screen.isDeviceLocked()) {
                    AttemptLog.add("phone locked unexpectedly before ${feature.name}; stopping")
                    results[feature] = ToggleResult.BLOCKED
                    break
                }
                results[feature] = when (feature) {
                    OperationOrder.Feature.HOTSPOT -> setHotspot(hotspot == true, valid)
                    OperationOrder.Feature.MOBILE_DATA -> setMobileData(mobileData == true)
                }
            }
            TransactionResult(
                hotspot = results[OperationOrder.Feature.HOTSPOT],
                mobileData = results[OperationOrder.Feature.MOBILE_DATA],
                screenAwake = true,
                lockedAtEnd = false
            )
        }
    }

    override suspend fun requestCalibrationDump() {
        AccessibilityServiceHolder.service?.emitCalibrationDump()
    }

    /**
     * Owns the lifecycle of one job. This is the ONLY place in the app that locks the phone.
     *
     * ensure awake -> ensure unlocked -> every requested step -> lock once
     *
     * @return the block's result, with the lock outcome folded in. On an early exit the phone is
     *   still locked, because the user has to unlock it themselves.
     */
    private suspend fun transaction(
        label: String,
        block: suspend () -> TransactionResult
    ): TransactionResult {
        AttemptLog.add("=== transaction start: $label")

        if (!screen.ensureScreenAwake()) {
            val failed = TransactionResult(
                ToggleResult.BLOCKED, null, screenAwake = false, lockedAtEnd = screen.isDeviceLocked()
            )
            AttemptLog.add("transaction aborted: ${failed.summary}")
            notifications.notifyWakeFailed()
            return failed
        }

        if (!ensureUnlocked()) {
            val blocked = TransactionResult(
                ToggleResult.BLOCKED, ToggleResult.BLOCKED,
                screenAwake = true, lockedAtEnd = true
            )
            AttemptLog.add("transaction could not unlock the phone: ${blocked.summary}")
            return blocked
        }

        val result = try {
            block()
        } catch (t: Throwable) {
            Log.e(TAG, "transaction body failed", t)
            AttemptLog.add("transaction body threw: ${t.message}")
            TransactionResult(ToggleResult.FAILED, null, screenAwake = true, lockedAtEnd = false)
        }

        val locked = lockAtEnd()
        val final = result.copy(screenAwake = true, lockedAtEnd = locked)
        AttemptLog.add("=== transaction end: ${final.summary}")
        return final
    }

    /** Puts the phone back to sleep, once, after every step has finished. */
    private suspend fun lockAtEnd(): Boolean {
        if (!lockWhenFinished) {
            AttemptLog.add("leaving the phone awake: 'lock when automation finishes' is off")
            return false
        }
        delay(POST_TOGGLE_SETTLE_MS)
        when {
            !screen.isInteractive() -> return false
            // The user (or Smart Lock) locked it during the job; nothing to undo.
            screen.isDeviceLocked() -> return true
            screen.lockScreen() -> {
                delay(POST_LOCK_SETTLE_MS)
                return true
            }
            else -> {
                AttemptLog.add("could not lock the phone at the end of the transaction")
                return false
            }
        }
    }

    /**
     * Escalating unlock: platform dismissal (swipe lock or trusted state), then one bounded PIN-pad
     * attempt, then wait for the person. Never loops on a credential.
     */
    private suspend fun ensureUnlocked(): Boolean {
        if (!screen.isDeviceLocked()) return true

        if (screen.requestPlatformDismiss()) {
            AttemptLog.add("keyguard dismissed by the platform (non-secure or trusted state)")
            delay(UNLOCK_SETTLE_MS)
            return !screen.isDeviceLocked()
        }

        if (screen.unlockWithCredential()) {
            AttemptLog.add("unlocked by entering the stored credential")
            delay(UNLOCK_SETTLE_MS)
            return !screen.isDeviceLocked()
        }

        AttemptLog.add("automated unlock did not work; waiting for the user")
        return awaitManualUnlock()
    }

    private suspend fun awaitManualUnlock(): Boolean {
        notifications.notifyUnlockRequired()
        AttemptLog.add("waiting up to ${MANUAL_WAIT_MS / 1000}s for the phone to be unlocked")
        val deadline = System.currentTimeMillis() + MANUAL_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (!screen.isDeviceLocked()) {
                AttemptLog.add("unlocked by hand; continuing")
                return true
            }
            screen.ensureScreenAwake()
            delay(1000)
        }
        AttemptLog.add("still locked after ${MANUAL_WAIT_MS / 1000}s")
        return false
    }

    /** One network operation. Never wakes, unlocks or locks: the transaction owns that. */
    private suspend fun setHotspot(targetOn: Boolean, password: String?): ToggleResult {
        val result = withTimeoutOrNull(STEP_TIMEOUT_MS) {
            runToggle(KEYWORD_HOTSPOT, targetOn, useCalibration = true, password = password)
        } ?: ToggleResult.FAILED
        AttemptLog.add("HOTSPOT target=$targetOn -> $result")
        if (result == ToggleResult.FAILED) Log.e(TAG, "hotspot toggle failed targetOn=$targetOn")
        if (result != ToggleResult.FAILED) prefs.setLastKnownHotspotOn(targetOn)
        return result
    }

    /** One network operation. Never wakes, unlocks or locks: the transaction owns that. */
    private suspend fun setMobileData(targetOn: Boolean): ToggleResult {
        val result = withTimeoutOrNull(STEP_TIMEOUT_MS) {
            runToggle(KEYWORD_MOBILE_DATA, targetOn, useCalibration = false, password = null)
        } ?: ToggleResult.FAILED
        AttemptLog.add("MOBILE DATA target=$targetOn -> $result")
        return result
    }

    /**
     * Drives one switch. Stages escalate, and only a definitive result stops the ladder - a
     * FAILED click must still fall through to launch/navigate and the manual wait.
     */
    private suspend fun runToggle(
        rowKeyword: String,
        targetOn: Boolean,
        useCalibration: Boolean,
        password: String?
    ): ToggleResult {
        AttemptLog.add("=== toggle $rowKeyword target=$targetOn start; ${screen.capabilities()}")

        // Stage 0: only when there is genuinely a live, unlocked Settings window in front. A
        // locked phone can still hand back stale cached Settings nodes, and acting on those would
        // be operating on a screen the user cannot see.
        if (screen.isInteractive() && !screen.isDeviceLocked() && settingsForeground()) {
            settle(attempt(rowKeyword, targetOn, useCalibration, password))?.let { return it }
        }

        // Stage 1: make sure Settings is up and the phone is usable.
        launchFor(rowKeyword)
        awaitScreen(rowKeyword, useCalibration, SCREEN_WAIT_MS)
        settle(attempt(rowKeyword, targetOn, useCalibration, password))?.let { return it }

        // Stage 2: navigate through Connections -> Data usage / Mobile hotspot.
        navigateFor(rowKeyword, useCalibration)
        settle(attempt(rowKeyword, targetOn, useCalibration, password))?.let { return it }

        logScreenDump(rowKeyword)
        return ToggleResult.FAILED
    }

    private fun settle(result: ToggleResult?): ToggleResult? =
        if (result != null && result != ToggleResult.FAILED) result else null

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
            AttemptLog.add("password field not found; keeping the existing password")
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
        AttemptLog.add("clicked the hotspot row to open the config screen")
        AttemptLog.add("config screen with a password field=${awaitPasswordField(CONFIG_WAIT_MS)}")
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
                !isSettingsPackage(root) -> null
                else -> NodeMatcher.findToggle(root, calibration, rowKeyword)
            }
        }
    }

    private suspend fun settingsRoot(): AccessibilityNodeInfo? = withContext(Dispatchers.Default) {
        val root = rootNode() ?: return@withContext null
        if (!isSettingsPackage(root)) null else root
    }

    private suspend fun settingsForeground(): Boolean = withContext(Dispatchers.Default) {
        AccessibilityServiceHolder.service?.rootNode()?.let { isSettingsPackage(it) } ?: false
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

    private suspend fun awaitScreen(
        rowKeyword: String,
        useCalibration: Boolean,
        timeoutMs: Long
    ): Boolean {
        val calibration = if (useCalibration) prefs.calibration() else null
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (findToggle(rowKeyword, calibration) != null) return true
            delay(300)
        }
        return false
    }

    private suspend fun logScreenDump(rowKeyword: String) {
        val root = rootNode() ?: return
        val lines = withContext(Dispatchers.Default) { NodeDumper.dumpCompact(root, 40) }
        AttemptLog.add("screen dump for '$rowKeyword':")
        lines.forEach { AttemptLog.add(it) }
    }

    private fun isSettingsPackage(root: AccessibilityNodeInfo): Boolean {
        val pkg = root.packageName?.toString() ?: return false
        if (pkg != SETTINGS_PACKAGE && pkg != SAMSUNG_SETTINGS_PACKAGE) {
            AttemptLog.add("unexpected window: $pkg")
            return false
        }
        return true
    }

    private fun validatePassword(password: String?): String? {
        if (password == null) return null
        if (PassphraseRules.isValid(password)) return password
        AttemptLog.add(
            "routine password rejected by WPA2 rules (${PassphraseRules.rejectionReason(password)}); " +
                "keeping the password already set in Settings"
        )
        return null
    }

    companion object {
        const val SETTINGS_PACKAGE = "com.android.settings"
        const val SAMSUNG_SETTINGS_PACKAGE = "com.samsung.android.settings"
        private const val TAG = "HSAuto"
        private const val STATE_TIMEOUT_MS = 3_000L
        private const val SCREEN_WAIT_MS = 8_000L
        private const val CONFIG_WAIT_MS = 6_000L
        private const val STEP_TIMEOUT_MS = 60_000L
        private const val MANUAL_WAIT_MS = 90_000L
        private const val UNLOCK_SETTLE_MS = 1_200L
        private const val NAVIGATE_DELAY_MS = 1_800L
        private const val POST_TOGGLE_SETTLE_MS = 1_200L
        private const val POST_LOCK_SETTLE_MS = 400L
    }
}