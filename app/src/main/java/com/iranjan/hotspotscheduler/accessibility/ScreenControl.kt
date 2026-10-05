package com.iranjan.hotspotscheduler.accessibility

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Screen, keyguard and launch control for the accessibility engine.
 *
 * Unlock behaviour follows the documented contract of
 * [android.app.KeyguardManager.requestDismissKeyguard]:
 *
 *  > "If the Keyguard is not secure or the device is currently in a trusted state, calling this
 *  method will immediately dismiss the Keyguard without any user interaction. If the Keyguard is
 *  secure and the device is not in a trusted state, this will bring up the UI so the user can enter
 *  their credentials."
 *
 * A "trusted state" is Smart Lock, Extend Unlock / trusted places, or a recognised device. So the
 * app unlocks unattended whenever Android says it may: no credential, swipe-only, or a trusted
 * state. It keeps trying for the rest of the routine's budget, because Smart Lock can flip to
 * trusted at any moment (you walk in with the phone, a home Wi-Fi appears).
 *
 * Turning the screen off uses [DevicePolicyManager.lockNow], the only public API for it.
 * Turning another Activity on goes through [ToggleHostActivity] because Android blocks background
 * activity starts.
 */
@Singleton
class ScreenControl @Inject constructor(@ApplicationContext private val context: Context) {

    enum class DismissOutcome { DISMISSED, SECURED, UNKNOWN }

    private val powerManager: PowerManager? = context.getSystemService(PowerManager::class.java)
    private val keyguardManager: KeyguardManager? = context.getSystemService(KeyguardManager::class.java)
    private val devicePolicyManager: DevicePolicyManager? =
        context.getSystemService(DevicePolicyManager::class.java)

    private val adminReceiver = ComponentName(context, ScreenOffAdminReceiver::class.java)

    /** True when a PIN, pattern or password (or a locked SIM) is set. */
    fun secureLockPresent(): Boolean = try {
        keyguardManager?.isKeyguardSecure == true
    } catch (t: Throwable) {
        false
    }

    /**
     * Whether an unattended unlock can succeed right now. A secure lock is still unlockable while
     * the device counts as trusted (Smart Lock / trusted places), so this is a live reading, not a
     * permanent verdict.
     */
    fun autoUnlockPossible(): Boolean = !secureLockPresent() || !isLocked()

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

    fun deviceAdminIntent(): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminReceiver)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Screen that lists Smart Lock, Extend Unlock and trusted places. */
    fun smartLockIntent(): Intent =
        Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun lockScreenSettingsIntent(): Intent =
        Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Turns the display on if it is off. The wake lock is time-boxed so it cannot leak. */
    fun wakeScreen(holdMs: Long = DEFAULT_WAKE_HOLD_MS): Boolean {
        val pm = powerManager ?: return false
        if (pm.isInteractive) return true
        return try {
            @Suppress("DEPRECATION")
            val lock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "HotspotScheduler:wake"
            )
            lock.setReferenceCounted(false)
            lock.acquire(holdMs)
            AttemptLog.add("woke the screen (held ${holdMs}ms)")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "wakeScreen failed", t)
            AttemptLog.add("wake failed: ${t.message}")
            false
        }
    }

    /**
     * Asks the platform to dismiss the keyguard and reports what actually happened.
     *
     * Uses the supported [KeyguardManager.requestDismissKeyguard] (API 26+) rather than the
     * deprecated `KeyguardLock`, because only the new API understands a trusted state and can turn
     * the screen on as part of the dismissal. The host activity performs the request; the outcome
     * is then read from the real keyguard state rather than an activity result, which also covers
     * the case where Smart Lock lets the phone unlock a moment later.
     */
    suspend fun requestKeyguardDismiss(): DismissOutcome {
        val km = keyguardManager ?: return DismissOutcome.UNKNOWN
        if (!km.isKeyguardLocked) return DismissOutcome.DISMISSED

        val launched = try {
            context.startActivity(ToggleHostActivity.dismissIntent(context))
            true
        } catch (t: Throwable) {
            AttemptLog.add("could not start the keyguard host activity: ${t.message}")
            false
        }
        if (!launched) return DismissOutcome.UNKNOWN

        val deadline = System.currentTimeMillis() + DISMISS_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(250)
            if (!km.isKeyguardLocked) {
                AttemptLog.add("keyguard dismissed (secure=${secureLockPresent()})")
                return DismissOutcome.DISMISSED
            }
        }
        AttemptLog.add("keyguard still showing (secure=${secureLockPresent()}); a credential is needed")
        return if (secureLockPresent()) DismissOutcome.SECURED else DismissOutcome.UNKNOWN
    }

    /**
     * Starts a Settings screen from the already-visible host activity, which is the only way to open
     * another Activity from the background on modern Android.
     */
    suspend fun launchSettings(pkg: String, cls: String): Boolean = try {
        context.startActivity(ToggleHostActivity.launchIntent(context, pkg, cls))
        AttemptLog.add("host launch requested for $pkg/$cls")
        true
    } catch (t: Throwable) {
        Log.e(TAG, "launchSettings failed", t)
        AttemptLog.add("host launch failed: ${t.message}")
        false
    }

    /** Turns the screen off (and locks it) via the device administrator. */
    fun lockNow(): Boolean {
        val dpm = devicePolicyManager ?: return false
        if (!hasDeviceAdmin()) {
            AttemptLog.add("screen left on: device administrator is not enabled")
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

    /** One-line snapshot for the diagnostics log. */
    fun capabilities(): String = buildString {
        append("interactive=").append(isInteractive())
        append(" locked=").append(isLocked())
        append(" secure=").append(secureLockPresent())
        append(" autoUnlock=").append(autoUnlockPossible())
        append(" deviceAdmin=").append(hasDeviceAdmin())
    }

    companion object {
        private const val TAG = "HSScreen"
        private const val DEFAULT_WAKE_HOLD_MS = 90_000L
        private const val DISMISS_TIMEOUT_MS = 15_000L
    }
}

