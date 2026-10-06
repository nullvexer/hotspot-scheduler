package com.iranjan.hotspotscheduler.platform.screen

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.iranjan.hotspotscheduler.accessibility.ToggleHostActivity
import com.iranjan.hotspotscheduler.data.encrypted.CredentialVault
import com.iranjan.hotspotscheduler.platform.Result
import com.iranjan.hotspotscheduler.platform.keyguard.KeyguardEngine
import com.iranjan.hotspotscheduler.util.AttemptLog
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenSessionImpl @Inject constructor(
    @androidx.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val wakeEngine: WakeEngine,
    private val keyguardEngine: KeyguardEngine,
    private val credentialVault: CredentialVault
) : ScreenSession {

    private val keyguardManager: KeyguardManager? = context.getSystemService(KeyguardManager::class.java)
    private val powerManager: PowerManager? = context.getSystemService(PowerManager::class.java)

    override suspend fun acquire(): Result<Unit> = Result.success(Unit)

    override suspend fun ensureAwake(): Result<Unit> = wakeEngine.ensureAwake()

    override suspend fun ensureUnlocked(): Result<UnlockResult> {
        val km = keyguardManager ?: return Result.success(UnlockResult.FAILED)
        if (!km.isKeyguardLocked) return Result.success(UnlockResult.ALREADY_UNLOCKED)

        // Platform dismiss (non-secure or trusted state)
        if (!km.isKeyguardSecure) {
            AttemptLog.add("attempting platform dismiss (non-secure keyguard)")
            val dismissLaunched = try {
                context.startActivity(ToggleHostActivity.dismissIntent(context))
                true
            } catch (e: Exception) {
                AttemptLog.add("could not start dismiss host: ${e.message}")
                false
            }
            if (dismissLaunched) {
                val deadline = System.currentTimeMillis() + DISMISS_TIMEOUT_MS
                while (System.currentTimeMillis() < deadline) {
                    delay(200)
                    if (!isDeviceLocked()) {
                        AttemptLog.add("keyguard dismissed by platform")
                        return Result.success(UnlockResult.PLATFORM_DISMISSED)
                    }
                }
            }
        }

        // Secure keyguard: need PIN
        val pin = credentialVault.credential()
        if (pin.isNullOrEmpty()) {
            AttemptLog.add("auto unlock enabled but no credential stored")
            return Result.success(UnlockResult.MANUAL_REQUIRED)
        }

        // Detect keyguard
        val detectResult = keyguardEngine.detectKeyguard()
        if (detectResult is Result.Failure) return Result.success(UnlockResult.FAILED)

        // Reveal keypad
        val revealResult = keyguardEngine.revealPinPad()
        if (revealResult is Result.Failure) return Result.success(UnlockResult.FAILED)

        // Enter credential
        val enterResult = keyguardEngine.enterCredential(revealResult.value, pin)
        return when (enterResult) {
            is Result.Success -> when (enterResult.value) {
                CredentialResult.UNLOCKED -> Result.success(UnlockResult.CREDENTIAL_ENTERED)
                CredentialResult.BACKOFF -> Result.success(UnlockResult.MANUAL_REQUIRED)
                else -> Result.success(UnlockResult.FAILED)
            }
            is Result.Failure -> Result.success(UnlockResult.FAILED)
        }
    }

    override suspend fun ensureSettingsForeground(target: SettingsTarget): Result<Unit> {
        // Launch target Settings activity via host activity
        val launched = try {
            context.startActivity(ToggleHostActivity.launchIntent(context, target.packageName, target.className))
            true
        } catch (e: Exception) {
            AttemptLog.add("settings launch failed: ${e.message}")
            false
        }
        if (!launched) return Result.failure(Result.Error.ActionRejected("settings launch failed"))
        delay(NAVIGATE_DELAY_MS)
        return Result.success(Unit)
    }

    override suspend fun release() {}

    override suspend fun lock(): Result<LockResult> {
        val service = com.iranjan.hotspotscheduler.accessibility.AccessibilityServiceHolder.service
        if (service == null) {
            AttemptLog.add("cannot lock: accessibility service not connected")
            return Result.failure(Result.Error.ServiceUnavailable("accessibility service not connected"))
        }
        val done = try {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        } catch (e: Exception) {
            AttemptLog.add("lockScreen failed: ${e.message}")
            false
        }
        if (!done) {
            AttemptLog.add("GLOBAL_ACTION_LOCK_SCREEN rejected")
            return Result.failure(Result.Error.ActionRejected("lock rejected"))
        }
        delay(POST_LOCK_SETTLE_MS)
        return if (isDeviceLocked()) Result.success(LockResult.LOCKED) else Result.success(LockResult.FAILED)
    }

    private fun isDeviceLocked(): Boolean {
        val km = keyguardManager ?: return false
        return try { km.isDeviceLocked } catch (e: Exception) { false }
    }

    companion object {
        private const val DISMISS_TIMEOUT_MS = 12_000L
        private const val NAVIGATE_DELAY_MS = 1_800L
        private const val POST_LOCK_SETTLE_MS = 400L
    }
}