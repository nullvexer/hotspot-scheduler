package com.iranjan.hotspotscheduler.platform.permissions


import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.AppOpsManagerCompat
import com.iranjan.hotspotscheduler.data.datastore.AutomationPreferences
import com.iranjan.hotspotscheduler.data.encrypted.CredentialVault
import com.iranjan.hotspotscheduler.platform.accessibility.AccessibilityRuntime
import com.iranjan.hotspotscheduler.platform.screen.WakeEngine
import com.iranjan.hotspotscheduler.platform.keyguard.KeyguardEngine
import com.iranjan.hotspotscheduler.automation.strategies.OperationStrategyResolver
import com.iranjan.hotspotscheduler.util.AttemptLog
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CapabilityProbe @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accessibility: AccessibilityRuntime,
    private val wakeEngine: WakeEngine,
    private val keyguardEngine: KeyguardEngine,
    private val strategyResolver: OperationStrategyResolver,
    private val prefs: AutomationPreferences,
    private val credentialVault: CredentialVault
) {

    data class Capability(
        val name: String,
        val status: Status,
        val detail: String
    )

    enum class Status { READY, DEGRADED, BLOCKED }

    data class Report(
        val screenWake: Capability,
        val accessibility: Capability,
        val keyguardDetection: Capability,
        val pinKeypad: Capability,
        val hotspotStrategy: Capability,
        val mobileDataStrategy: Capability,
        val exactAlarm: Capability,
        val backgroundExecution: Capability,
        val batteryOptimization: Capability,
        val overlay: Capability,
        val notifications: Capability,
        val pinConfigured: Capability
    ) {
        val overall: OverallStatus
            get() = when {
                this.allCapabilities.any { it.status == Status.BLOCKED } -> OverallStatus.BLOCKED
                this.allCapabilities.any { it.status == Status.DEGRADED } -> OverallStatus.DEGRADED
                else -> OverallStatus.READY
            }

        val allCapabilities: List<Capability> = listOf(
            screenWake, accessibility, keyguardDetection, pinKeypad,
            hotspotStrategy, mobileDataStrategy, exactAlarm,
            backgroundExecution, batteryOptimization, overlay,
            notifications, pinConfigured
        )
    }

    enum class OverallStatus { READY, DEGRADED, BLOCKED }

    suspend fun runProbe(): Report {
        AttemptLog.add("=== CAPABILITY PROBE START ===")

        val screenWake = probeScreenWake()
        val accessibilityCap = probeAccessibility()
        val keyguardDetection = probeKeyguardDetection()
        val pinKeypad = probePinKeypad()
        val hotspotStrategy = probeHotspotStrategy()
        val mobileDataStrategy = probeMobileDataStrategy()
        val exactAlarm = probeExactAlarm()
        val backgroundExecution = probeBackgroundExecution()
        val batteryOptimization = probeBatteryOptimization()
        val overlay = probeOverlay()
        val notifications = probeNotifications()
        val pinConfigured = probePinConfigured()

        val report = Report(
            screenWake, accessibilityCap, keyguardDetection, pinKeypad,
            hotspotStrategy, mobileDataStrategy, exactAlarm,
            backgroundExecution, batteryOptimization, overlay,
            notifications, pinConfigured
        )

        AttemptLog.add("=== CAPABILITY PROBE END: ${report.overall} ===")
        report.allCapabilities.forEach { c ->
            AttemptLog.add("${c.name}: ${c.status} - ${c.detail}")
        }
        return report
    }

    private suspend fun probeScreenWake(): Capability {
        val result = wakeEngine.ensureAwake()
        return if (result is com.iranjan.hotspotscheduler.platform.Result.Success) {
            Capability("Screen Wake", Status.READY, "Display became interactive")
        } else {
            Capability("Screen Wake", Status.BLOCKED, result.error.detail)
        }
    }

    private suspend fun probeAccessibility(): Capability {
        val state = accessibility.state.value
        return when (state) {
            com.iranjan.hotspotscheduler.platform.accessibility.RuntimeState.CONNECTED ->
                Capability("Accessibility", Status.READY, "Service connected")
            com.iranjan.hotspotscheduler.platform.accessibility.RuntimeState.INTERRUPTED ->
                Capability("Accessibility", Status.DEGRADED, "Service interrupted")
            else ->
                Capability("Accessibility", Status.BLOCKED, "Service not connected")
        }
    }

    private suspend fun probeKeyguardDetection(): Capability {
        val result = keyguardEngine.detectKeyguard()
        return if (result is com.iranjan.hotspotscheduler.platform.Result.Success) {
            Capability("Keyguard Detection", Status.READY, "KeyguardManager accessible")
        } else {
            Capability("Keyguard Detection", Status.BLOCKED, result.error.detail)
        }
    }

    private suspend fun probePinKeypad(): Capability {
        if (!credentialVault.isConfigured()) {
            return Capability("PIN Keypad", Status.DEGRADED, "No PIN configured (auto-unlock disabled)")
        }
        val result = keyguardEngine.revealPinPad()
        return if (result is com.iranjan.hotspotscheduler.platform.Result.Success) {
            Capability("PIN Keypad", Status.READY, "Geometry validated: ${result.value.digitCentres.size}/10 keys")
        } else {
            Capability("PIN Keypad", Status.BLOCKED, result.error.detail)
        }
    }

    private suspend fun probeHotspotStrategy(): Capability {
        val strategy = strategyResolver.resolveHotspot()
        val result = strategy.readState()
        return if (result is com.iranjan.hotspotscheduler.platform.Result.Success) {
            Capability("Hotspot Control", Status.READY, "${strategy::class.simpleName} available")
        } else {
            Capability("Hotspot Control", Status.BLOCKED, result.error.detail)
        }
    }

    private suspend fun probeMobileDataStrategy(): Capability {
        val strategy = strategyResolver.resolveMobileData()
        val result = strategy.readState()
        return if (result is com.iranjan.hotspotscheduler.platform.Result.Success) {
            Capability("Mobile Data Control", Status.READY, "${strategy::class.simpleName} available")
        } else {
            Capability("Mobile Data Control", Status.BLOCKED, result.error.detail)
        }
    }

    private suspend fun probeExactAlarm(): Capability {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(android.app.AlarmManager::class.java)
            val canExact = am?.canScheduleExactAlarms() == true
            return if (canExact) {
                Capability("Exact Alarms", Status.READY, "Permission granted")
            } else {
                Capability("Exact Alarms", Status.BLOCKED, "Permission denied - open Settings > Special access > Alarms")
            }
        }
        return Capability("Exact Alarms", Status.READY, "Pre-Android 12, not required")
    }

    private suspend fun probeBackgroundExecution(): Capability {
        // Check if app is in restricted bucket
        try {
            val usageStats = context.getSystemService(android.app.usage.UsageStatsManager::class.java)
            val bucket = usageStats?.getAppStandbyBucket()
            return if (bucket == android.app.usage.UsageStatsManager.STANDBY_BUCKET_ACTIVE ||
                bucket == android.app.usage.UsageStatsManager.STANDBY_BUCKET_WORKING_SET ||
                bucket == android.app.usage.UsageStatsManager.STANDBY_BUCKET_FREQUENT) {
                Capability("Background Execution", Status.READY, "Bucket: ${bucketName(bucket)}")
            } else {
                Capability("Background Execution", Status.DEGRADED, "Bucket: ${bucketName(bucket)} - may be restricted")
            }
        } catch (e: Exception) {
            return Capability("Background Execution", Status.DEGRADED, "Could not check bucket")
        }
    }

    private suspend fun probeBatteryOptimization(): Capability {
        val pm = context.getSystemService(android.os.PowerManager::class.java)
        val isIgnoring = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        return if (isIgnoring) {
            Capability("Battery Optimization", Status.READY, "Exempted")
        } else {
            Capability("Battery Optimization", Status.BLOCKED, "Not exempted - open Settings > Battery > App > Unrestricted")
        }
    }

    private suspend fun probeOverlay(): Capability {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val hasOverlay = Settings.canDrawOverlays(context)
            return if (hasOverlay) {
                Capability("Overlay Permission", Status.READY, "Granted")
            } else {
                Capability("Overlay Permission", Status.BLOCKED, "Denied - open Settings > Apps > Special access > Display over other apps")
            }
        }
        return Capability("Overlay Permission", Status.READY, "Not required on this Android version")
    }

    private suspend fun probeNotifications(): Capability {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            val areEnabled = nm?.areNotificationsEnabled() == true
            return if (areEnabled) {
                Capability("Notifications", Status.READY, "Enabled")
            } else {
                Capability("Notifications", Status.BLOCKED, "Disabled - open Settings > Notifications")
            }
        }
        return Capability("Notifications", Status.READY, "Runtime permission not required")
    }

    private suspend fun probePinConfigured(): Capability {
        val configured = credentialVault.isConfigured()
        return if (configured) {
            Capability("PIN Configured", Status.READY, "Stored encrypted in DE storage")
        } else {
            Capability("PIN Configured", Status.DEGRADED, "Not set - auto-unlock unavailable")
        }
    }

    private fun bucketName(bucket: Int?): String = when (bucket) {
        android.app.usage.UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "ACTIVE"
        android.app.usage.UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "WORKING_SET"
        android.app.usage.UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "FREQUENT"
        android.app.usage.UsageStatsManager.STANDBY_BUCKET_RARE -> "RARE"
        android.app.usage.UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "RESTRICTED"
        android.app.usage.UsageStatsManager.STANDBY_BUCKET_NEVER -> "NEVER"
        else -> "UNKNOWN"
    }
}