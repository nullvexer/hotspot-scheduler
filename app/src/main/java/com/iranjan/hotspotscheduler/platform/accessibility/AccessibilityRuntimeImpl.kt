package com.iranjan.hotspotscheduler.platform.accessibility

import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.iranjan.hotspotscheduler.accessibility.AccessibilityServiceHolder
import com.iranjan.hotspotscheduler.platform.Result
import kotlinx.coroutines.MutableStateFlow
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessibilityRuntimeImpl @Inject constructor() : AccessibilityRuntime {

    override val state: MutableStateFlow<RuntimeState> = MutableStateFlow(RuntimeState.UNAVAILABLE)

    private val service: AccessibilityServiceHolder.ServiceWrapper by lazy {
        AccessibilityServiceHolder.service?.let { AccessibilityServiceHolder.ServiceWrapper(it) }
    }

    override suspend fun getActiveWindows(): Result<List<WindowInfo>> {
        val svc = service ?: return Result.failure(Result.Error.ServiceUnavailable("accessibility service not connected"))
        val windows = svc.getWindows()
        return Result.success(windows.map { WindowInfo(it.packageName.toString(), it.id, it.bounds) })
    }

    override suspend fun findNodes(selector: NodeSelector): Result<List<AccessibilityNodeInfo>> {
        val svc = service ?: return Result.failure(Result.Error.ServiceUnavailable("accessibility service not connected"))
        val roots = mutableListOf<AccessibilityNodeInfo>()
        svc.rootInActiveWindow?.let { roots.add(it) }
        svc.getWindows().forEach { roots.add(it.root) }

        val results = mutableListOf<AccessibilityNodeInfo>()
        for (root in roots) {
            if (root == null) continue
            collectMatching(root, selector, results)
            if (results.size >= 50) break
        }
        return Result.success(results)
    }

    private fun collectMatching(node: AccessibilityNodeInfo, selector: NodeSelector, results: MutableList<AccessibilityNodeInfo>) {
        if (matches(node, selector)) results.add(node)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectMatching(it, selector, results) }
        }
    }

    private fun matches(node: AccessibilityNodeInfo, selector: NodeSelector): Boolean {
        if (selector.packageNames.isNotEmpty() && node.packageName?.toString()?.let { it in selector.packageNames } != true) return false
        if (selector.resourceIds.isNotEmpty() && node.viewIdResourceName?.let { it in selector.resourceIds } != true) return false
        if (selector.classes.isNotEmpty() && node.className?.toString()?.let { it in selector.classes } != true) return false
        if (selector.textContains != null) {
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            if (!text.contains(selector.textContains!!) && !desc.contains(selector.textContains!!)) return false
        }
        if (selector.clickable != null && node.isClickable != selector.clickable) return false
        if (selector.checkable != null && node.isCheckable != selector.checkable) return false
        return true
    }

    override suspend fun click(node: AccessibilityNodeInfo): Result<Unit> {
        return try {
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) Result.success(Unit)
            else Result.failure(Result.Error.ActionRejected("click rejected"))
        } catch (e: Exception) {
            Result.failure(Result.Error.ActionRejected(e.message ?: "click failed"))
        }
    }

    override suspend fun gesture(path: Path, duration: Long): Result<Unit> {
        val svc = service ?: return Result.failure(Result.Error.ServiceUnavailable("accessibility service not connected"))
        return try {
            svc.dispatchGesture(
                android.accessibilityservice.GestureDescription.Builder()
                    .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, duration))
                    .build()
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Result.Error.ActionRejected(e.message ?: "gesture failed"))
        }
    }

    override suspend fun globalAction(action: Int): Result<Unit> {
        val svc = service ?: return Result.failure(Result.Error.ServiceUnavailable("accessibility service not connected"))
        return try {
            if (svc.performGlobalAction(action)) Result.success(Unit)
            else Result.failure(Result.Error.ActionRejected("global action rejected"))
        } catch (e: Exception) {
            Result.failure(Result.Error.ActionRejected(e.message ?: "global action failed"))
        }
    }

    override suspend fun waitForCondition(predicate: suspend () -> Boolean, timeout: java.time.Duration): Result<Boolean> {
        val deadline = System.currentTimeMillis() + timeout.toMillis()
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return Result.success(true)
            delay(100)
        }
        return Result.success(false)
    }

    override suspend fun dumpCurrentUi(): Result<UiDump> {
        val svc = service ?: return Result.failure(Result.Error.ServiceUnavailable("accessibility service not connected"))
        val windows = svc.getWindows().map { WindowInfo(it.packageName.toString(), it.id, it.bounds) }
        return Result.success(UiDump(windows, System.currentTimeMillis()))
    }

    internal class ServiceWrapper(private val service: android.accessibilityservice.AccessibilityService) {
fun rootInActiveWindow(): AccessibilityNodeInfo? = service.rootInActiveWindow
        fun getWindows(): List<android.view.accessibility.AccessibilityWindowInfo> = service.windows ?: emptyList()
        fun dispatchGesture(desc: android.accessibilityservice.GestureDescription): Boolean =
            service.dispatchGesture(desc, null, null)
        fun performGlobalAction(action: Int): Boolean = service.performGlobalAction(action)
    }
}