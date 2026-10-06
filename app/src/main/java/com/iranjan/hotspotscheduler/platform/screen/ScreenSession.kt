package com.iranjan.hotspotscheduler.platform.screen

import com.iranjan.hotspotscheduler.platform.Result

interface ScreenSession {
    suspend fun acquire(): Result<Unit>
    suspend fun ensureAwake(): Result<Unit>
    suspend fun ensureUnlocked(): Result<UnlockResult>
    suspend fun ensureSettingsForeground(target: SettingsTarget): Result<Unit>
    suspend fun release()
    suspend fun lock(): Result<LockResult>
}

enum class UnlockResult { ALREADY_UNLOCKED, PLATFORM_DISMISSED, CREDENTIAL_ENTERED, MANUAL_REQUIRED, FAILED }
enum class LockResult { LOCKED, ALREADY_LOCKED, FAILED, SKIPPED }
data class SettingsTarget(val packageName: String, val className: String)