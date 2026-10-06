package com.iranjan.hotspotscheduler.platform.keyguard

import com.iranjan.hotspotscheduler.platform.Result

interface KeyguardEngine {
    suspend fun detectKeyguard(): Result<KeyguardInfo>
    suspend fun revealPinPad(): Result<PinPadGeometry>
    suspend fun enterCredential(geometry: PinPadGeometry, pin: String): Result<CredentialResult>
    suspend fun verifyUnlocked(): Boolean
}

data class KeyguardInfo(val isShowing: Boolean, val isSecure: Boolean, val packageName: String?)
data class PinPadGeometry(
    val digitCentres: Map<Int, Pair<Float, Float>>,
    val enterCentre: Pair<Float, Float>?,
    val sourcePackage: String,
    val validated: Boolean
)
enum class CredentialResult { UNLOCKED, KEYPAD_NOT_FOUND, DIGIT_FAILED, STILL_LOCKED, NOT_CONFIGURED, BACKOFF }