package com.iranjan.hotspotscheduler.automation.strategies

import com.iranjan.hotspotscheduler.domain.model.StepResult
import com.iranjan.hotspotscheduler.platform.Result
import com.iranjan.hotspotscheduler.platform.accessibility.AccessibilityRuntime
import com.iranjan.hotspotscheduler.automation.strategies.QuickSettingsNavigator
import com.iranjan.hotspotscheduler.util.AttemptLog
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SamsungQuickSettingsDataStrategy @Inject constructor(
    private val accessibility: AccessibilityRuntime,
    private val qsNavigator: QuickSettingsNavigator
) : NetworkOperationStrategy {

    override suspend fun readState(): Result<Boolean> {
        val openResult = qsNavigator.openQuickSettings()
        if (openResult is Result.Failure) return openResult
        val tileResult = qsNavigator.findTile("mobile data")
        qsNavigator.collapseQuickSettings()
        if (tileResult is Result.Failure) return Result.failure(tileResult.error)
        val state = qsNavigator.getTileState(tileResult.value)
        return if (state == null) Result.failure(Result.Error.NotAvailable("mobile data tile state unknown"))
        else Result.success(state)
    }

    override suspend fun setState(targetOn: Boolean, password: String?): Result<StepResult> {
        AttemptLog.add("Quick Settings mobile data: target=$targetOn")
        val openResult = qsNavigator.openQuickSettings()
        if (openResult is Result.Failure) return openResult

        val tileResult = qsNavigator.findTile("mobile data")
        if (tileResult is Result.Failure) {
            qsNavigator.collapseQuickSettings()
            return Result.failure(tileResult.error)
        }

        val tile = tileResult.value
        val currentState = qsNavigator.getTileState(tile)
        if (currentState == targetOn) {
            qsNavigator.collapseQuickSettings()
            return Result.success(StepResult.ALREADY_OK)
        }

        val clickResult = qsNavigator.clickTile(tile)
        qsNavigator.collapseQuickSettings()
        if (clickResult is Result.Failure) return clickResult

        delay(1000)
        val verifyOpen = qsNavigator.openQuickSettings()
        if (verifyOpen is Result.Failure) return Result.success(StepResult.CHANGED)
        val verifyTile = qsNavigator.findTile("mobile data")
        qsNavigator.collapseQuickSettings()
        if (verifyTile is Result.Success) {
            val newState = qsNavigator.getTileState(verifyTile.value)
            if (newState == targetOn) return Result.success(StepResult.CHANGED)
        }
        return Result.success(StepResult.FAILED)
    }

    override suspend fun verifyState(targetOn: Boolean): Result<Boolean> {
        val openResult = qsNavigator.openQuickSettings()
        if (openResult is Result.Failure) return openResult
        val tileResult = qsNavigator.findTile("mobile data")
        qsNavigator.collapseQuickSettings()
        if (tileResult is Result.Failure) return Result.failure(tileResult.error)
        val state = qsNavigator.getTileState(tileResult.value)
        return if (state == null) Result.failure(Result.Error.NotAvailable("state unknown"))
        else Result.success(state == targetOn)
    }
}