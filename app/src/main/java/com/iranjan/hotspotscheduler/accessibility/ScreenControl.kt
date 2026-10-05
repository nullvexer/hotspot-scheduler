package com.iranjan.hotspotscheduler.accessibility

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.iranjan.hotspotscheduler.util.LockCredentialVault
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Screen on/off, lock detection, and the entry point for unattended unlock.
 *
 * Turning the screen off uses `AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN` (API 28+). That
 * replaced a Device Administrator, which was a much heavier permission for the same result.
 *
 * Lock handling has two layers, in order:
 *  1. `KeyguardManager.requestDismissKeyguard()` — the platform dismisses a non-secure keyguard or
 *     one in a trusted state (Smart Lock / Extend Unlock) with no interaction.
 *  2. If a credential is still required, [unlockWithCredential] drives the PIN pad itself through
 *     the accessibility service. See [KeyguardAutomator] for why that is one bounded attempt.
 */
@Singleton
class ScreenControl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vault: LockCredentialVault
) {

    private val powerManager: PowerManager? = context.getSystemService(PowerManager::class.java)
    private val keyguardManager: KeyguardManager? = context.getSystemService(KeyguardManager::class.java)

    /** True when a PIN, pattern or password (or a locked SIM) is set. */
    fun secureLockPresent(): Boolean = try {
        keyguardManager?.isKeyguardSecure == true
    } catch (t: Throwable) {
        false
    }

    /** Authoritative "needs authentication" check; unlike isKeyguardLocked it ignores a swipe lock. */
    fun isDeviceLocked(): Boolean = try {
        keyguardManager?.isDeviceLocked ?: false
    } catch (t: Throwable) {
        false
    }

    fun isInteractive(): Boolean = try {
        powerManager?.isInteractive == true
    } catch (t: Throwable) {
        false
    }

    /** Smart Lock / Extend Unlock settings, for the setup hint. */
    fun smartLockIntent(): Intent =
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
            true
        } catch (t: Throwable) {
            Log.e(TAG, "wakeScreen failed", t)
            AttemptLog.add("wake failed: ${t.message}")
            false
        }
    }

    /**
     * Asks the platform to dismiss the keyguard. Succeeds for a non-secure lock or a trusted state.
     */
    suspend fun requestPlatformDismiss(): Boolean {
        val km = keyguardManager ?: return false
        if (!km.isKeyguardLocked) return true
        if (km.isKeyguardSecure) return false
        val launched = try {
            context.startActivity(ToggleHostActivity.dismissIntent(context))
            true
        } catch (t: Throwable) {
            AttemptLog.add("could not start the keyguard host activity: ${t.message}")
            false
        }
        if (!launched) return false
        val deadline = System.currentTimeMillis() + DISMISS_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(200)
            if (!isDeviceLocked()) return true
        }
        return false
    }

    /**
     * One bounded attempt to unlock by driving the PIN pad.
     *
     * Returns true when the device is unlocked afterwards. Deliberately never loops: repeatedly
     * submitting a credential risks Android's lockout and, on some devices, a secure wipe.
     */
    suspend fun unlockWithCredential(): Boolean {
        if (!vault.isEnabled()) {
            AttemptLog.add("auto unlock is switched off")
            return false
        }
        val pin = vault.credential()
        if (pin.isNullOrEmpty()) {
            AttemptLog.add("auto unlock is enabled but no credential is stored")
            return false
        }
        val automator = AccessibilityServiceHolder.service?.automator
        if (automator == null) {
            AttemptLog.add("auto unlock needs the accessibility service; it is not connected")
            return false
        }

        val lastFailure = vault.lastFailedAttemptAt()
        val sinceFailure = System.currentTimeMillis() - lastFailure
        if (lastFailure > 0L && sinceFailure < KeyguardAutomator.LOCKOUT_GUARD_MS) {
            AttemptLog.add(
                "skipping unlock: a previous attempt failed ${sinceFailure / 1000}s ago " +
                    "(guard is ${KeyguardAutomator.LOCKOUT_GUARD_MS / 1000}s to avoid a lockout)"
            )
            return false
        }

        return when (val outcome = automator.unlock(pin)) {
            is KeyguardAutomator.Outcome.Unlocked -> {
                AttemptLog.add("unlocked by entering the stored credential")
                true
            }
            is KeyguardAutomator.Outcome.KeypadNotFound -> {
                AttemptLog.add("the PIN pad was not found on the lock screen; not retrying")
                vault.markFailedAttempt(System.currentTimeMillis())
                false
            }
            is KeyguardAutomator.Outcome.DigitFailed -> {
                AttemptLog.add("could not press '${outcome.digit}' on the PIN pad; not retrying")
                vault.markFailedAttempt(System.currentTimeMillis())
                false
            }
            is KeyguardAutomator.Outcome.StillLocked -> {
                AttemptLog.add("the credential was entered but the device is still locked")
                vault.markFailedAttempt(System.currentTimeMillis())
                false
            }
            is KeyguardAutomator.Outcome.NotConfigured -> {
                AttemptLog.add("no usable numeric credential is configured")
                false
            }
            is KeyguardAutomator.Outcome.Backoff -> false
        }
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

    /**
     * Locks the screen through the accessibility global action.
     *
     * This needs no device administrator and works from API 28, so it replaced one.
     */
    fun lockScreen(): Boolean {
        val service = AccessibilityServiceHolder.service
        if (service == null) {
            AttemptLog.add("cannot lock the screen: the accessibility service is not connected")
            return false
        }
        return try {
            val done = service.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
            )
            if (!done) {
                AttemptLog.add("GLOBAL_ACTION_LOCK_SCREEN was rejected by the service")
            }
            done
        } catch (t: Throwable) {
            Log.e(TAG, "lockScreen failed", t)
            AttemptLog.add("lockScreen failed: ${t.message}")
            false
        }
    }

    fun capabilities(): String = buildString {
        append("interactive=").append(isInteractive())
        append(" deviceLocked=").append(isDeviceLocked())
        append(" secure=").append(secureLockPresent())
    }

    companion object {
        private const val TAG = "HSScreen"
        private const val DEFAULT_WAKE_HOLD_MS = 90_000L
        private const val DISMISS_TIMEOUT_MS = 12_000L
    }
}