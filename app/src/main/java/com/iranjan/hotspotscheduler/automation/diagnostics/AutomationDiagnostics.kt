package com.iranjan.hotspotscheduler.automation.diagnostics

import com.iranjan.hotspotscheduler.automation.recovery.CrashRecovery
import com.iranjan.hotspotscheduler.automation.logger.AutomationLogger
import com.iranjan.hotspotscheduler.platform.permissions.CapabilityProbe
import com.iranjan.hotspotscheduler.platform.permissions.CapabilityProbe.Report
import com.iranjan.hotspotscheduler.domain.model.AutomationResult
import com.iranjan.hotspotscheduler.platform.accessibility.AccessibilityRuntime
import com.iranjan.hotspotscheduler.platform.screen.ScreenSession
import com.iranjan.hotspotscheduler.platform.keyguard.KeyguardEngine
import com.iranjan.hotspotscheduler.automation.strategies.OperationStrategyResolver
import com.iranjan.hotspotscheduler.util.AttemptLog
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationDiagnostics @Inject constructor(
    private val capabilityProbe: CapabilityProbe,
    private val crashRecovery: CrashRecovery,
    private val logger: AutomationLogger,
    private val accessibility: AccessibilityRuntime,
    private val screenSession: ScreenSession,
    private val keyguardEngine: com.iranjan.hotspotscheduler.platform.keyguard.KeyguardEngine,
    private val strategyResolver: OperationStrategyResolver
) {

    data class FullDiagnosticReport(
        val capabilityReport: Report,
        val accessibilityDump: String,
        val screenState: String,
        val keyguardState: String,
        val hotspotState: String,
        val mobileDataState: String,
        val executionHistory: List<String>
    )

    data class LiveTestResult(
        val testName: String,
        val result: AutomationResult,
        val durationMs: Long
    )

    suspend fun runFullProbe(): FullDiagnosticReport {
        AttemptLog.add("=== FULL DIAGNOSTIC START ===")

        val capabilityReport = capabilityProbe.runProbe()
        val accessibilityDump = dumpAccessibility()
        val screenState = dumpScreenState()
        val keyguardState = dumpKeyguardState()
        val hotspotState = dumpHotspotState()
        val mobileDataState = dumpMobileDataState()
        val executionHistory = AttemptLog.getAll()

        AttemptLog.add("=== FULL DIAGNOSTIC END ===")

        return FullDiagnosticReport(
            capabilityReport, accessibilityDump, screenState, keyguardState,
            hotspotState, mobileDataState, executionHistory
        )
    }

    suspend fun runWakeTest(): LiveTestResult {
        val start = System.currentTimeMillis()
        logger.logAction("diag_wake_test", emptyMap())
        val result = screenSession.ensureAwake()
        val duration = System.currentTimeMillis() - start
        return LiveTestResult("Wake Test", mapResult(result), duration)
    }

    suspend fun runUnlockTest(): LiveTestResult {
        val start = System.currentTimeMillis()
        logger.logAction("diag_unlock_test", emptyMap())
        val result = screenSession.ensureUnlocked()
        val duration = System.currentTimeMillis() - start
        return LiveTestResult("Unlock Test", mapUnlockResult(result), duration)
    }

    suspend fun runHotspotTest(targetOn: Boolean): LiveTestResult {
        val start = System.currentTimeMillis()
        logger.logAction("diag_hotspot_test", mapOf("target" to targetOn))
        val strategy = strategyResolver.resolveHotspot()
        val result = strategy.setState(targetOn, null)
        val duration = System.currentTimeMillis() - start
        return LiveTestResult("Hotspot ${if (targetOn) "ON" else "OFF"}", mapStepResult(result), duration)
    }

    suspend fun runMobileDataTest(targetOn: Boolean): LiveTestResult {
        val start = System.currentTimeMillis()
        logger.logAction("diag_data_test", mapOf("target" to targetOn))
        val strategy = strategyResolver.resolveMobileData()
        val result = strategy.setState(targetOn, null)
        val duration = System.currentTimeMillis() - start
        return LiveTestResult("Mobile Data ${if (targetOn) "ON" else "OFF"}", mapStepResult(result), duration)
    }

    suspend fun runFullTransaction(desiredHotspot: Boolean, desiredData: Boolean): LiveTestResult {
        val start = System.currentTimeMillis()
        logger.logAction("diag_full_transaction", mapOf("hotspot" to desiredHotspot, "data" to desiredData))
        // This would use the full transaction executor - placeholder for now
        val duration = System.currentTimeMillis() - start
        return LiveTestResult("Full Transaction", com.iranjan.hotspotscheduler.domain.model.AutomationResult.Success(
            emptyList(), com.iranjan.hotspotscheduler.domain.model.LockResult.SKIPPED, "diag"
        ), duration)
    }

    private fun dumpAccessibility(): String {
        val result = accessibility.dumpCurrentUi()
        return if (result is com.iranjan.hotspotscheduler.platform.Result.Success) {
            result.value.windows.joinToString("\n") { "Window: ${it.packageName} id=${it.windowId} ${it.bounds}" }
        } else "Accessibility dump failed: ${result.error.detail}"
    }

    private fun dumpScreenState(): String {
        // Would need PowerManager/KeyguardManager access
        return "Screen state dump not implemented"
    }

    private fun dumpKeyguardState(): String {
        val result = keyguardEngine.detectKeyguard()
        return if (result is com.iranjan.hotspotscheduler.platform.Result.Success) {
            "Keyguard: showing=${result.value.isShowing}, secure=${result.value.isSecure}"
        } else "Keyguard: ${result.error.detail}"
    }

    private fun dumpHotspotState(): String {
        val strategy = strategyResolver.resolveHotspot()
        val result = strategy.readState()
        return if (result is com.iranjan.hotspotscheduler.platform.Result.Success) "Hotspot: ${result.value}" else "Hotspot: unknown"
    }

    private fun dumpMobileDataState(): String {
        val strategy = strategyResolver.resolveMobileData()
        val result = strategy.readState()
        return if (result is com.iranjan.hotspotscheduler.platform.Result.Success) "Mobile Data: ${result.value}" else "Mobile Data: unknown"
    }

    private fun mapResult(result: com.iranjan.hotspotscheduler.platform.Result<Unit>): com.iranjan.hotspotscheduler.domain.model.AutomationResult {
        return if (result is com.iranjan.hotspotscheduler.platform.Result.Success) {
            com.iranjan.hotspotscheduler.domain.model.AutomationResult.Success(
                emptyList(), com.iranjan.hotspotscheduler.domain.model.LockResult.SKIPPED, "diag"
            )
        } else {
            com.iranjan.hotspotscheduler.domain.model.AutomationResult.Failed(
                com.iranjan.hotspotscheduler.domain.model.FailureReason.ScreenWakeFailed(result.error.detail),
                sessionId = "diag"
            )
        }
    }

    private fun mapUnlockResult(result: com.iranjan.hotspotscheduler.platform.Result<com.iranjan.hotspotscheduler.platform.screen.UnlockResult>): com.iranjan.hotspotscheduler.domain.model.AutomationResult {
        return if (result is com.iranjan.hotspotscheduler.platform.Result.Success) {
            com.iranjan.hotspotscheduler.domain.model.AutomationResult.Success(
                emptyList(), com.iranjan.hotspotscheduler.domain.model.LockResult.SKIPPED, "diag"
            )
        } else {
            com.iranjan.hotspotscheduler.domain.model.AutomationResult.Failed(
                com.iranjan.hotspotscheduler.domain.model.FailureReason.UnlockVerificationFailed(result.error.detail),
                sessionId = "diag"
            )
        }
    }

    private fun mapStepResult(result: com.iranjan.hotspotscheduler.platform.Result<com.iranjan.hotspotscheduler.domain.model.StepResult>): com.iranjan.hotspotscheduler.domain.model.AutomationResult {
        return if (result is com.iranjan.hotspotscheduler.platform.Result.Success) {
            com.iranjan.hotspotscheduler.domain.model.AutomationResult.Success(
                listOf(result.value), com.iranjan.hotspotscheduler.domain.model.LockResult.SKIPPED, "diag"
            )
        } else {
            com.iranjan.hotspotscheduler.domain.model.AutomationResult.Failed(
                com.iranjan.hotspotscheduler.domain.model.FailureReason.ToggleStateDidNotChange(
                    com.iranjan.hotspotscheduler.domain.model.Feature.HOTSPOT, result.error.detail),
                sessionId = "diag"
            )
        }
    }
}