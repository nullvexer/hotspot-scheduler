package com.iranjan.hotspotscheduler.automation.strategies

import android.view.accessibility.AccessibilityNodeInfo
import com.iranjan.hotspotscheduler.platform.Result
import com.iranjan.hotspotscheduler.platform.accessibility.AccessibilityRuntime
import com.iranjan.hotspotscheduler.platform.accessibility.NodeSelector
import com.iranjan.hotspotscheduler.util.AttemptLog
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuickSettingsNavigator @Inject constructor(
    private val accessibility: AccessibilityRuntime
) {

    private val QS_PACKAGES = listOf("com.android.systemui", "com.samsung.android.systemui")
    private val QS_TILE_CLASSES = listOf("android.service.quicksettings.QuickSettingsTileView")

    suspend fun openQuickSettings(): Result<Unit> {
        AttemptLog.add("opening Quick Settings")
        val globalAction = android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
        val result = accessibility.globalAction(globalAction)
        if (result is Result.Failure) return result
        delay(500)
        return Result.success(Unit)
    }

    suspend fun findTile(keyword: String): Result<AccessibilityNodeInfo> {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            for (pkg in QS_PACKAGES) {
                val selector = NodeSelector(
                    packageNames = listOf(pkg),
                    classes = QS_TILE_CLASSES
                )
                val result = accessibility.findNodes(selector)
                if (result is Result.Success) {
                    for (node in result.value) {
                        val text = node.contentDescription?.toString().lowercase() ?: ""
                        val label = node.text?.toString().lowercase() ?: ""
                        if (text.contains(keyword) || label.contains(keyword)) {
                            if (node.isClickable || node.isEnabled) {
                                return Result.success(node)
                            }
                        }
                    }
                }
            }
            delay(300)
        }
        return Result.failure(Result.Error.NotAvailable("Quick Settings tile '$keyword' not found"))
    }

    suspend fun getTileState(tile: AccessibilityNodeInfo): Boolean? {
        // QuickSettingsTileView typically has checked state or contentDescription indicating state
        if (tile.isCheckable) return tile.isChecked
        val desc = tile.contentDescription?.toString().lowercase() ?: ""
        if (desc.contains("on") || desc.contains("enabled") || desc.contains("active")) return true
        if (desc.contains("off") || desc.contains("disabled") || desc.contains("inactive")) return false
        return null
    }

    suspend fun clickTile(tile: AccessibilityNodeInfo): Result<Unit> {
        AttemptLog.add("clicking Quick Settings tile")
        return accessibility.click(tile)
    }

    suspend fun collapseQuickSettings(): Result<Unit> {
        val globalAction = android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
        val result = accessibility.globalAction(globalAction)
        if (result is Result.Failure) {
            // Try home as fallback
            return accessibility.globalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
        }
        return result
    }
}