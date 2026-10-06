package com.iranjan.hotspotscheduler.platform.accessibility

import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.iranjan.hotspotscheduler.platform.screen.Result
import kotlinx.coroutines.flow.StateFlow

interface AccessibilityRuntime {
    val state: StateFlow<RuntimeState>
    suspend fun getActiveWindows(): Result<List<WindowInfo>>
    suspend fun findNodes(selector: NodeSelector): Result<List<AccessibilityNodeInfo>>
    suspend fun click(node: AccessibilityNodeInfo): Result<Unit>
    suspend fun gesture(path: Path, duration: Long): Result<Unit>
    suspend fun globalAction(action: Int): Result<Unit>
    suspend fun waitForCondition(predicate: suspend () -> Boolean, timeout: java.time.Duration): Result<Boolean>
    suspend fun dumpCurrentUi(): Result<UiDump>
}

enum class RuntimeState { CONNECTED, DISCONNECTED, INTERRUPTED, UNAVAILABLE }
data class WindowInfo(val packageName: String, val windowId: Int, val bounds: Rect)
data class NodeSelector(
    val packageNames: List<String> = emptyList(),
    val resourceIds: List<String> = emptyList(),
    val classes: List<String> = emptyList(),
    val textContains: String? = null,
    val clickable: Boolean? = null,
    val checkable: Boolean? = null,
    val maxDepth: Int = 10
)
data class UiDump(val windows: List<WindowInfo>, val timestamp: Long)