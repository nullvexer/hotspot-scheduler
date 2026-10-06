package com.iranjan.hotspotscheduler.platform.screen


import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.iranjan.hotspotscheduler.accessibility.ToggleHostActivity
import com.iranjan.hotspotscheduler.platform.Result
import com.iranjan.hotspotscheduler.util.AttemptLog
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WakeEngine @Inject constructor(@ApplicationContext private val context: Context) {

    private val powerManager: PowerManager? = context.getSystemService(PowerManager::class.java)

    suspend fun ensureAwake(): Result<Unit> {
        if (isInteractive()) return Result.success(Unit)

        AttemptLog.add("screen off, requesting wake via host activity")
        val hostLaunched = try {
            context.startActivity(ToggleHostActivity.wakeIntent(context))
            true
        } catch (e: Exception) {
            AttemptLog.add("wake host launch failed: ${e.message}")
            false
        }

        if (hostLaunched && awaitInteractive(HOST_WAKE_TIMEOUT_MS)) {
            AttemptLog.add("screen is interactive (host activity)")
            return Result.success(Unit)
        }

        AttemptLog.add("host activity wake failed or timed out, trying wake lock fallback")
        return tryWakeLockFallback()
    }

    private fun isInteractive(): Boolean = try {
        powerManager?.isInteractive == true
    } catch (e: Exception) {
        false
    }

    private suspend fun awaitInteractive(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isInteractive()) return true
            delay(100)
        }
        return false
    }

    private suspend fun tryWakeLockFallback(): Result<Unit> {
        try {
            powerManager?.let { pm ->
                @Suppress("DEPRECATION")
                val lock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "HotspotScheduler:wake"
                )
                lock.setReferenceCounted(false)
                lock.acquire(WAKE_LOCK_HOLD_MS)
                if (awaitInteractive(WAKE_LOCK_SETTLE_MS)) {
                    AttemptLog.add("screen is interactive (wake lock fallback)")
                    return Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            AttemptLog.add("wake lock fallback failed: ${e.message}")
        }
        AttemptLog.add("screen did not become interactive; automation cannot continue")
        return Result.failure(Result.Error.Timeout("screen wake failed"))
    }

    companion object {
        private const val TAG = "WakeEngine"
        private const val HOST_WAKE_TIMEOUT_MS = 4_000L
        private const val WAKE_LOCK_HOLD_MS = 20_000L
        private const val WAKE_LOCK_SETTLE_MS = 4_000L
    }
}