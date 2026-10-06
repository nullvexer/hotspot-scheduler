package com.iranjan.hotspotscheduler.automation.strategies

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.iranjan.hotspotscheduler.domain.model.StepResult
import com.iranjan.hotspotscheduler.platform.Result
import com.iranjan.hotspotscheduler.platform.accessibility.AccessibilityRuntime
import com.iranjan.hotspotscheduler.automation.strategies.SwitchFinder
import com.iranjan.hotspotscheduler.automation.strategies.SettingsNavigator
import com.iranjan.hotspotscheduler.platform.screen.ScreenSession
import com.iranjan.hotspotscheduler.util.AttemptLog
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SamsungSettingsDataStrategy @Inject constructor(
    private val accessibility: AccessibilityRuntime,
    private val switchFinder: SwitchFinder,
    private val navigator: SettingsNavigator,
    private val screenSession: ScreenSession
) : NetworkOperationStrategy {

    override suspend fun readState(): Result<Boolean> {
        val matchResult = switchFinder.findMobileDataToggle()
        if (matchResult is Result.Failure) return Result.failure(matchResult.error)
        return readState(matchResult.value)
    }

    override suspend fun setState(targetOn: Boolean, password: String?): Result<StepResult> {
        // Navigate to data usage screen
        val navResult = screenSession.ensureSettingsForeground(
            com.iranjan.hotspotscheduler.platform.screen.SettingsTarget(
                navigator.dataUsageTarget()?.packageName ?: "com.android.settings",
                navigator.dataUsageTarget()?.className ?: "com.android.settings.Settings\$DataUsageSummaryActivity"
            )
        )
        if (navResult is Result.Failure) return Result.failure(navResult.error)

        // Wait for screen and find toggle
        val matchResult = awaitToggle(KEYWORD_MOBILE_DATA)
        if (matchResult is Result.Failure) return Result.failure(matchResult.error)

        val match = matchResult.value

        // Read before
        val before = readState(match) ?: return Result.failure(Result.Error.NotAvailable("state unreadable"))
        if (before == targetOn) return Result.success(StepResult.ALREADY_OK)

        // Click
        AttemptLog.add("clicking mobile data toggle (target=$targetOn)")
        val clickResult = clickToggle(match)
        if (!clickResult) return Result.failure(Result.Error.ActionRejected("click rejected"))

        // Wait for state change
        if (awaitStateChange(before, targetOn)) {
            AttemptLog.add("mobile data state changed OK")
            return Result.success(StepResult.CHANGED)
        }

        // Retry once
        AttemptLog.add("retrying mobile data click once")
        val retryMatch = switchFinder.findMobileDataToggle()
        if (retryMatch is Result.Success) {
            val retryClick = clickToggle(retryMatch.value)
            if (retryClick && awaitStateChange(before, targetOn)) {
                return Result.success(StepResult.CHANGED)
            }
        }

        return Result.success(StepResult.FAILED)
    }

    override suspend fun verifyState(targetOn: Boolean): Result<Boolean> {
        val matchResult = switchFinder.findMobileDataToggle()
        if (matchResult is Result.Failure) return Result.failure(matchResult.error)
        val state = readState(matchResult.value)
        return if (state == null) Result.failure(Result.Error.NotAvailable("state unreadable"))
        else Result.success(state == targetOn)
    }

    private suspend fun awaitToggle(keyword: String): Result<ToggleMatch> {
        val deadline = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < deadline) {
            val matchResult = switchFinder.findMobileDataToggle()
            if (matchResult is Result.Success) return matchResult
            delay(300)
        }
        return Result.failure(Result.Error.Timeout("mobile data toggle not found"))
    }

    private fun readState(match: ToggleMatch): Boolean? {
        val node = match.stateNode
        if (node.isCheckable) return node.isChecked
        var parent = node.parent ?: return null
        repeat(6) {
            for (i in 0 until parent.childCount) {
                val sibling = parent.getChild(i) ?: continue
                if (sibling.viewIdResourceName == "com.android.settings:id/switch_text") {
                    return onOffToBoolean(sibling.text?.toString())
                }
            }
            parent = parent.parent ?: return null
        }
        return null
    }

    private fun onOffToBoolean(text: String?): Boolean? = when (text?.lowercase()) {
        "on" -> true
        "off" -> false
        else -> null
    }

    private fun clickToggle(match: ToggleMatch): Boolean {
        return try {
            match.clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } catch (e: Exception) { false }
    }

    private suspend fun awaitStateChange(before: Boolean, targetOn: Boolean): Boolean {
        val deadline = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < deadline) {
            delay(300)
            val matchResult = switchFinder.findMobileDataToggle()
            if (matchResult is Result.Success) {
                val state = readState(matchResult.value)
                if (state != null && state != before && state == targetOn) return true
            }
        }
        return false
    }
}