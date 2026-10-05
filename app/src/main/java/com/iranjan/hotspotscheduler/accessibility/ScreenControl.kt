package com.iranjan.hotspotscheduler.accessibility

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Screen and lock-screen control for the accessibility engine.
 *
 * What Android actually allows, and what it does not:
 *
 *  - Turning the screen ON: allowed. A `PARTIAL_WAKE_LOCK`/`FULL_WAKE_LOCK` plus
 *    `Activity.setTurnScreenOn(true)` wakes the display; no permission beyond WAKE_LOCK.
 *  - Turning the screen OFF: only via `DevicePolicyManager.lockNow()`, which requires the app to
 *    be an active device administrator. There is no other public API.
 *  - Dismissing the keyguard: `KeyguardManager.KeyguardLock.disableKeyguard()` works ONLY while
 *    the keyguard is not secure. With a PIN, pattern or password set, Android ignores it.
 *
 * There is no way to type a PIN into the lock screen: the keyguard is not an accessibility
 * window and does not accept injected input. When a secure lock is present the best available
 * behaviour is to wake the screen, tell the user, and wait for them to unlock. See
 * [secureLockPresent] and [autoUnlockPossible].
 */
@Singleton
class ScreenControl @Inject constructor(@ApplicationContext private val context: Context) {

    private val powerManager: PowerManager? = context.getSystemService(PowerManager::class.java)
    private val keyguardManager: KeyguardManager? = context.getSystemService(KeyguardManager::class.java)
    private val devicePolicyManager: DevicePolicyManager? =
        context.getSystemService(DevicePolicyManager::class.java)

    private val adminReceiver = ComponentName(context, ScreenOffAdminReceiver::class.java)

    /** True when a PIN, pattern or password is set. */
    fun secureLockPresent(): Boolean = try {
        keyguardManager?.isKeyguardSecure == true
    } catch (t: Throwable) {
        false
    }

    /**
     * Whether the app can dismiss the lock screen by itself. False whenever a secure lock is set,
     * which is the single most important thing for the user to understand before relying on this.
     */
    fun autoUnlockPossible(): Boolean = !secureLockPresent()

    fun isInteractive(): Boolean = try {
        powerManager?.isInteractive == true
    } catch (t: Throwable) {
        false
    }

    fun isLocked(): Boolean = try {
        keyguardManager?.isKeyguardLocked == true
    } catch (t: Throwable) {
        false
    }

    fun hasDeviceAdmin(): Boolean = try {
        devicePolicyManager?.isAdminActive(adminReceiver) == true
    } catch (t: Throwable) {
        false
    }

    /** Intent that opens the system screen where the user activates the device administrator. */
    fun deviceAdminIntent(): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminReceiver)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Intent showing the current lock-screen security setting, so a PIN can be removed. */
    fun lockScreenSettingsIntent(): Intent =
        Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Turns the display on if it is off. The wake lock is time-boxed so it cannot be held
     * indefinitely; the caller decides how long the screen must stay awake.
     */
    fun wakeScreen(holdMs: Long = DEFAULT_WAKE_HOLD_MS): Boolean {
        val pm = powerManager ?: return false
        if (pm.isInteractive) {
            AttemptLog.add("screen already on")
            return true
        }
        return try {
            @Suppress("DEPRECATION")
            val lock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "HotspotScheduler:wake"
            )
            lock.setReferenceCounted(false)
            lock.acquire(holdMs)
            AttemptLog.add("woke screen (held ${holdMs}ms)")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "wakeScreen failed", t)
            AttemptLog.add("wake failed: ${t.message}")
            false
        }
    }

    /**
     * Asks Android to dismiss a non-secure keyguard (swipe-only or no lock at all).
     *
     * Returns true when the keyguard is now not showing. With a secure lock this cannot work and
     * the caller must fall back to waiting for the user.
     */
    fun dismissKeyguardIfPermitted(): Boolean {
        val km = keyguardManager ?: return false
        if (!km.isKeyguardLocked) return true
        if (km.isKeyguardSecure) {
            AttemptLog.add("secure keyguard present; automatic dismiss is not permitted by Android")
            return false
        }
        return try {
            km.newKeyguardLock("HotspotScheduler:dismiss").disableKeyguard()
            // isKeyguardLocked can lag the request briefly, so allow a moment to settle.
            var unlocked = false
            repeat(10) {
                if (!km.isKeyguardLocked) {
                    unlocked = true
                    return@repeat
                }
                Thread.sleep(50)
            }
            AttemptLog.add("non-secure keyguard dismiss ${if (unlocked) "succeeded" else "did not take"}")
            unlocked
        } catch (t: Throwable) {
            AttemptLog.add("keyguard dismiss failed: ${t.message}")
            false
        }
    }

    /**
     * Turns the screen off (and locks it) via the device administrator. No-ops with a logged
     * reason when device admin is not granted, because there is no alternative API.
     */
    fun lockNow(): Boolean {
        val dpm = devicePolicyManager ?: return false
        if (!hasDeviceAdmin()) {
            AttemptLog.add("cannot turn the screen off: device administrator is not enabled")
            return false
        }
        return try {
            dpm.lockNow()
            AttemptLog.add("screen turned off via device admin")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "lockNow failed", t)
            AttemptLog.add("lockNow failed: ${t.message}")
            false
        }
    }

    /** One-line summary of what the app can and cannot do right now, for diagnostics. */
    fun capabilities(): String = buildString {
        append("screenInteractive=").append(isInteractive())
        append(" locked=").append(isLocked())
        append(" secureLock=").append(secureLockPresent())
        append(" autoUnlock=").append(autoUnlockPossible())
        append(" deviceAdmin=").append(hasDeviceAdmin())
    }

    companion object {
        private const val TAG = "HSScreen"
        private const val DEFAULT_WAKE_HOLD_MS = 60_000L
    }
}