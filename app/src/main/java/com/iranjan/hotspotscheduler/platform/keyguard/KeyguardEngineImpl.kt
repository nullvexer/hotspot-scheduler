package com.iranjan.hotspotscheduler.platform.keyguard

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.iranjan.hotspotscheduler.data.encrypted.CredentialVault
import com.iranjan.hotspotscheduler.platform.Result
import com.iranjan.hotspotscheduler.platform.accessibility.AccessibilityRuntime
import com.iranjan.hotspotscheduler.util.AttemptLog
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyguardEngineImpl @Inject constructor(
    @androidx.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val accessibility: AccessibilityRuntime,
    private val pinPadResolver: PinPadResolver,
    private val credentialVault: CredentialVault
) : KeyguardEngine {

    private val keyguardManager: KeyguardManager? = context.getSystemService(KeyguardManager::class.java)

    override suspend fun detectKeyguard(): Result<KeyguardInfo> {
        val km = keyguardManager ?: return Result.failure(Result.Error.ServiceUnavailable("KeyguardManager unavailable"))
        val showing = km.isKeyguardLocked
        val secure = km.isKeyguardSecure
        val pkg = try { accessibility.state.value.packageName } catch (e: Exception) { null }
        return Result.success(KeyguardInfo(showing, secure, pkg))
    }

    override suspend fun revealPinPad(): Result<PinPadGeometry> {
        if (!accessibility.state.value == com.iranjan.hotspotscheduler.platform.accessibility.RuntimeState.CONNECTED) {
            return Result.failure(Result.Error.ServiceUnavailable("accessibility not connected"))
        }

        if (keypadPresent()) {
            AttemptLog.add("keypad already present, skipping reveal")
        } else {
            AttemptLog.add("revealing keypad via swipe")
            if (!swipeToReveal()) {
                AttemptLog.add("swipe failed to reveal keypad, trying tap fallback")
                tapToReveal()
            }
            delay(700)
        }

        if (!keypadPresent()) {
            AttemptLog.add("keypad not present after reveal attempt")
            return Result.failure(Result.Error.NotAvailable("keypad not found after reveal"))
        }

        return pinPadResolver.detectGeometry()
    }

    override suspend fun enterCredential(geometry: PinPadGeometry, pin: String): Result<CredentialResult> {
        if (pin.isEmpty()) return Result.success(CredentialResult.NOT_CONFIGURED)

        val lastFailure = credentialVault.lastFailedAttemptAt()
        val sinceFailure = System.currentTimeMillis() - lastFailure
        if (lastFailure > 0 && sinceFailure < LOCKOUT_GUARD_MS) {
            AttemptLog.add("skipping unlock: previous attempt failed ${sinceFailure / 1000}s ago (guard ${LOCKOUT_GUARD_MS / 1000}s)")
            return Result.success(CredentialResult.BACKOFF)
        }

        AttemptLog.add("entering credential (${pin.length} digits)")
        for (c in pin) {
            if (!c.isDigit()) {
                return Result.success(CredentialResult.NOT_CONFIGURED)
            }
            val centre = geometry.centreOf(c - '0') ?: return Result.success(CredentialResult.DIGIT_FAILED)
            val tapResult = tapAtCentre(centre.first, centre.second)
            if (!tapResult) return Result.success(CredentialResult.DIGIT_FAILED)
            delay(DIGIT_INTERVAL_MS)
        }

        if (!verifyUnlocked()) {
            AttemptLog.add("credential entered but still locked, trying Enter")
            val enterCentre = geometry.enterCentre
            if (enterCentre != null) {
                tapAtCentre(enterCentre.first, enterCentre.second)
                delay(POST_ENTER_WAIT_MS)
            }
        }

        return if (verifyUnlocked()) {
            AttemptLog.add("unlocked successfully")
            Result.success(CredentialResult.UNLOCKED)
        } else {
            AttemptLog.add("credential failed")
            credentialVault.markFailedAttempt(System.currentTimeMillis())
            Result.success(CredentialResult.STILL_LOCKED)
        }
    }

    override suspend fun verifyUnlocked(): Boolean {
        val km = keyguardManager ?: return false
        return try { !km.isDeviceLocked } catch (e: Exception) { false }
    }

    private fun keypadPresent(): Boolean {
        for (marker in KeyguardIds.bouncerMarkers()) {
            val selector = com.iranjan.hotspotscheduler.platform.accessibility.NodeSelector(resourceIds = listOf(marker))
            val result = accessibility.findNodes(selector)
            if (result is Result.Success && result.value.isNotEmpty()) return true
        }
        return false
    }

    private fun swipeToReveal(): Boolean {
        val (fx, startY, endY) = KeyguardIds.swipeFraction()
        val w = getDisplayWidth().toFloat()
        val h = getDisplayHeight().toFloat()
        val path = android.graphics.Path().apply {
            moveTo(w * fx, h * startY)
            lineTo(w * fx, h * endY)
        }
        return try {
            accessibility.gesture(path, SWIPE_MS) is Result.Success
        } catch (e: Exception) { false }
    }

    private fun tapToReveal(): Boolean {
        val w = getDisplayWidth().toFloat()
        val h = getDisplayHeight().toFloat()
        val path = android.graphics.Path().apply { moveTo(w / 2f, h * 0.75f) }
        return try {
            accessibility.gesture(path, TAP_MS) is Result.Success
        } catch (e: Exception) { false }
    }

    private fun tapAtCentre(fx: Float, fy: Float): Boolean {
        val w = getDisplayWidth().toFloat()
        val h = getDisplayHeight().toFloat()
        val path = android.graphics.Path().apply { moveTo(w * fx, h * fy) }
        return try {
            accessibility.gesture(path, TAP_MS) is Result.Success
        } catch (e: Exception) { false }
    }

    private fun getDisplayWidth(): Int =
        try { context.resources.displayMetrics.widthPixels } catch (e: Exception) { 1080 }

    private fun getDisplayHeight(): Int =
        try { context.resources.displayMetrics.heightPixels } catch (e: Exception) { 2400 }

    companion object {
        private const val TAG = "KeyguardEngine"
        private const val DIGIT_INTERVAL_MS = 90L
        private const val TAP_MS = 60L
        private const val SWIPE_MS = 320L
        private const val POST_ENTER_WAIT_MS = 2_500L
        private const val LOCKOUT_GUARD_MS = 5 * 60 * 1000L
    }
}