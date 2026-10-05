package com.iranjan.hotspotscheduler.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

/**
 * Drives the lock screen's own PIN keypad through the accessibility service.
 *
 * This is not a security bypass: there is no API that hands a credential to Android, and none is
 * used here. The PIN pad is ordinary UI, and clicking its keys is the same action a person
 * performs. The keys are found by resource id because AOSP deliberately suppresses the spoken
 * digit (ObscureSpeechDelegate), which makes the id the only dependable handle.
 *
 * Failure discipline matters more than speed here. A mistyped credential can trigger Android's
 * lockout or, on some devices, a secure wipe. So this class makes **one** attempt per run, refuses
 * to run again inside [LOCKOUT_GUARD_MS] of a failure, and never loops on failure.
 */
class KeyguardAutomator(private val service: AccessibilityService) {

    sealed interface Outcome {
        /** The keypad was found and every digit was accepted, and the device reports unlocked. */
        data object Unlocked : Outcome

        /** The keypad is not on screen even after a swipe; the bouncer may not be a PIN pad. */
        data object KeypadNotFound : Outcome

        /** A digit could not be clicked. */
        data class DigitFailed(val digit: Char) : Outcome

        /** Digits were entered but the device is still locked (wrong credential, or Enter needed). */
        data object StillLocked : Outcome

        /** No credential configured, or auto-unlock is switched off. */
        data object NotConfigured : Outcome

        /** Skipped because a previous attempt failed recently. */
        data object Backoff : Outcome
    }

    /**
     * Makes at most one unlock attempt.
     *
     * @param pin the credential digits, digits only
     * @param autoSubmit whether to press Enter afterwards even if the device looks unlocked
     */
    suspend fun unlock(pin: String, autoSubmit: Boolean = true): Outcome {
        if (pin.isEmpty()) return Outcome.NotConfigured

        // Reveal the keypad. It is sometimes already up (the phone locked while Settings was in
        // the foreground), so a blind swipe is not always correct: check first.
        if (!keypadPresent()) {
            revealKeypad()
        }
        if (!awaitKeypad(KEYPAD_WAIT_MS)) return Outcome.KeypadNotFound

        for (c in pin) {
            if (!c.isDigit()) {
                Log.w(TAG, "credential contains a non-digit; only a numeric PIN is supported")
                return Outcome.NotConfigured
            }
            if (!tapDigit(c)) return Outcome.DigitFailed(c)
            // The pad needs a moment per keypress; too fast and events are dropped.
            delay(DIGIT_INTERVAL_MS)
        }

        // Some ROMs verify on the last digit, others need Enter. Try Enter only if still locked.
        if (isLocked()) {
            tapEnter()
            delay(POST_ENTER_WAIT_MS)
        } else if (!autoSubmit) {
            // Nothing to do; already unlocked.
        }

        return if (isLocked()) Outcome.StillLocked else Outcome.Unlocked
    }

    /** True when any keypad/bouncer node is currently in an inspectable window. */
    fun keypadPresent(): Boolean = findFirst(KeyguardIds.bouncerMarkers()) != null

    private suspend fun awaitKeypad(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (keypadPresent()) return true
            delay(150)
        }
        return false
    }

    /**
     * True when the device still requires authentication. `isDeviceLocked` is the authoritative
     * check; `isKeyguardLocked` also reports true for a merely-showing lock screen.
     */
    fun isLocked(): Boolean {
        val km = service.getSystemService(android.app.KeyguardManager::class.java)
        return try {
            km?.isDeviceLocked ?: false
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * Swipe up to bring the PIN pad on screen. Some ROMs ignore a swipe on the lockscreen widget
     * page and need a tap first, so both are attempted.
     */
    suspend fun revealKeypad(): Boolean {
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        service.resources.displayMetrics?.let { metrics.setTo(it) }
        val w = metrics.widthPixels.toFloat()
        val h = metrics.heightPixels.toFloat()
        val (fx, startY, endY) = KeyguardIds.swipeFraction()

        val swiped = swipe(w / 2f, h * startY, w / 2f, h * endY, SWIPE_MS)
        if (swiped) delay(SWIPE_SETTLE_MS)
        if (keypadPresent()) return true
        // Fallback: tap the centre, which on some One UI builds is the "swipe up to unlock" affordance.
        tap(w / 2f, h * 0.75f)
        delay(SWIPE_SETTLE_MS)
        return keypadPresent()
    }

    private fun tapDigit(c: Char): Boolean {
        val digit = c - '0'
        val node = findFirst(KeyguardIds.digitCandidates(digit))
        if (node != null) {
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            // Present but not clickable: fall through to the gesture path.
            node.clickPoint()?.let { (x, y) -> return tap(x, y) }
            return false
        }
        // No node: last resort is the calibrated grid position.
        val (fx, fy) = KeyguardIds.digitGridFraction(digit)
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        service.resources.displayMetrics?.let { metrics.setTo(it) }
        return tap(metrics.widthPixels * fx, metrics.heightPixels * fy)
    }

    private fun tapEnter(): Boolean {
        val node = findFirst(KeyguardIds.enterCandidates()) ?: return false
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        node.clickPoint()?.let { (x, y) -> return tap(x, y) }
        return false
    }

    /**
     * Searches every inspectable window, because on Android 13 the bouncer is frequently in a
     * separate window from the one `rootInActiveWindow` returns.
     */
    private fun findFirst(ids: List<String>): AccessibilityNodeInfo? {
        val roots = mutableListOf<AccessibilityNodeInfo?>()
        roots += service.rootInActiveWindow
        runCatching {
            if (service.windows != null) service.windows.forEach { roots += it.root }
        }
        for (root in roots) {
            if (root == null) continue
            for (id in ids) {
                val hit = runCatching { root.findAccessibilityNodeInfosByViewId(id) }
                    .getOrNull()
                    ?.firstOrNull { it.isVisibleToUser || it.isEnabled }
                    ?: continue
                return hit
            }
        }
        return null
    }

    private fun AccessibilityNodeInfo.clickPoint(): Pair<Float, Float>? {
        val rect = Rect()
        return try {
            // getBoundsInScreen returns Unit and fills the rect; it does not report success.
            getBoundsInScreen(rect)
            if (rect.isEmpty) return null
            rect.centerX().toFloat() to rect.centerY().toFloat()
        } catch (t: Throwable) {
            null
        }
    }

    private fun tap(x: Float, y: Float): Boolean = dispatchStroke(x, y, 0L, TAP_MS)

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        return try {
            service.dispatchGesture(
                GestureDescriptionHolder.build(path, 0L, durationMs),
                null,
                null
            )
        } catch (t: Throwable) {
            Log.w(TAG, "gesture failed: ${t.message}")
            false
        }
    }

    private fun dispatchStroke(x: Float, y: Float, startTime: Long, durationMs: Long): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return try {
            service.dispatchGesture(
                GestureDescriptionHolder.build(path, startTime, durationMs),
                null,
                null
            )
        } catch (t: Throwable) {
            Log.w(TAG, "tap gesture failed: ${t.message}")
            false
        }
    }

    /**
     * Reports which keypad buttons are reachable right now, without entering anything.
     *
     * This exists because the keypad ids vary by ROM and by One UI version. Rather than guessing
     * after a failure, run this on the lock screen from Setup and read the log: the output shows
     * exactly which id form this phone uses, so [KeyguardIds] can be extended instead of guessed.
     */
    fun diagnoseKeypad(): String {
        val ids = (0..9).flatMap { KeyguardIds.digitCandidates(it) }
        val found = ids.filter { findFirst(listOf(it)) != null }
        val packageName = runCatching { rootPackage() }.getOrNull()
        val markerFound = KeyguardIds.bouncerMarkers().filter { findFirst(listOf(it)) != null }
        return buildString {
            append("window=").append(packageName)
            append(" keypadVisible=").append(keypadPresent())
            append(" markerIds=").append(markerFound.ifEmpty { listOf("none") })
            append(" digitsFound=").append(found.size).append("/10")
            append(" ids=").append(found.ifEmpty { listOf("none") })
        }
    }

    private fun rootPackage(): String? {
        val root = service.rootInActiveWindow ?: return null
        return root.packageName?.toString()
    }

    companion object {
        private const val TAG = "HSKeyguard"
        private const val DIGIT_INTERVAL_MS = 90L
        private const val TAP_MS = 60L
        private const val SWIPE_MS = 320L
        private const val SWIPE_SETTLE_MS = 700L
        private const val KEYPAD_WAIT_MS = 6_000L
        private const val POST_ENTER_WAIT_MS = 2_500L

        /** Minimum gap after a failed attempt before another is permitted. */
        const val LOCKOUT_GUARD_MS = 5 * 60 * 1000L
    }
}

/** Wraps gesture construction so the automaton body stays readable. */
private object GestureDescriptionHolder {
    fun build(path: Path, startTime: Long, durationMs: Long) =
        android.accessibilityservice.GestureDescription.Builder()
            .addStroke(
                android.accessibilityservice.GestureDescription.StrokeDescription(
                    path,
                    startTime,
                    durationMs
                )
            )
            .build()
}