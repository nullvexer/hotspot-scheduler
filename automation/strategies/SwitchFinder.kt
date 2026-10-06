package com.iranjan.hotspotscheduler.automation.strategies

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.iranjan.hotspotscheduler.data.model.CalibrationSignature
import com.iranjan.hotspotscheduler.platform.Result
import com.iranjan.hotspotscheduler.platform.accessibility.AccessibilityRuntime
import com.iranjan.hotspotscheduler.platform.accessibility.NodeSelector
import com.iranjan.hotspotscheduler.data.datastore.AutomationPreferences
import com.iranjan.hotspotscheduler.util.AttemptLog
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

const val KEYWORD_HOTSPOT = "mobile hotspot"
const val KEYWORD_MOBILE_DATA = "mobile data"

@Singleton
class SwitchFinder @Inject constructor(
    private val accessibility: AccessibilityRuntime,
    private val prefs: AutomationPreferences
) {

    private const val SWITCH_WIDGET_ID = "com.android.settings:id/switch_widget"
    private const val SWITCH_TEXT_ID = "com.android.settings:id/switch_text"
    private const val SWITCH_CLASS = "android.widget.Switch"
    private const val COMPOUND_BUTTON_CLASS = "android.widget.CompoundButton"
    private const val EDIT_TEXT_CLASS = "android.widget.EditText"
    private const val MAX_NODES = 1500
    private const val MAX_ANCESTOR_HOPS = 6

    suspend fun findHotspotToggle(): Result<ToggleMatch> = findToggle(KEYWORD_HOTSPOT, useCalibration = true)
    suspend fun findMobileDataToggle(): Result<ToggleMatch> = findToggle(KEYWORD_MOBILE_DATA, useCalibration = false)

    private suspend fun findToggle(keyword: String, useCalibration: Boolean): Result<ToggleMatch> {
        val dumpResult = accessibility.dumpCurrentUi()
        if (dumpResult is Result.Failure) return Result.failure(dumpResult.error)

        val root = accessibility.findNodes(NodeSelector())
        if (root is Result.Failure) return Result.failure(root.error)
        val rootNode = root.value.firstOrNull()?.let { it }

        if (rootNode == null) return Result.failure(Result.Error.NotAvailable("no root node"))

        if (!isSettingsPackage(rootNode)) {
            return Result.failure(Result.Error.NotAvailable("not in Settings package: ${rootNode.packageName}"))
        }

        // Strategy 1: Calibration (hotspot only)
        if (keyword == KEYWORD_HOTSPOT && useCalibration) {
            val calibration = prefs.calibration()
            calibration?.let { sig ->
                val match = findCalibrated(rootNode, sig, keyword)
                if (match != null) return Result.success(match)
            }
        }

        // Strategy 2: Resource ID
        findByResourceId(rootNode, keyword)?.let { return Result.success(it) }

        // Strategy 3: Class-based
        findByClass(rootNode, keyword)?.let { return Result.success(it) }

        // Strategy 4: Text proximity
        findByTextProximity(rootNode, keyword)?.let { return Result.success(it) }

        // Strategy 5: Any checkable
        findByAnyCheckable(rootNode, keyword)?.let { return Result.success(it) }

        Result.failure(Result.Error.NotAvailable("toggle not found for '$keyword'"))
    }

    private fun isSettingsPackage(node: AccessibilityNodeInfo): Boolean {
        val pkg = node.packageName?.toString() ?: return false
        return pkg == "com.android.settings" || pkg == "com.samsung.android.settings"
    }

    private fun findCalibrated(root: AccessibilityNodeInfo, sig: CalibrationSignature, keyword: String): ToggleMatch? {
        return when (sig.type) {
            CalibrationSignature.TYPE_RID -> {
                val candidates = root.findAccessibilityNodeInfosByViewId(sig.value)
                bestRanked(candidates.mapNotNull { wrap(it, "calib:RID") }, keyword)
            }
            CalibrationSignature.TYPE_CLASS_SIG -> {
                findByClassSignature(root, sig.value)?.let { wrap(it, "calib:CLASS") }?.takeIf { rowScore(rowTextOf(it.stateNode), keyword) > 0 }
            }
            else -> null
        }
    }

    private fun findByClassSignature(root: AccessibilityNodeInfo, value: String): AccessibilityNodeInfo? {
        val parts = value.split("|", limit = 3)
        if (parts.size < 3) return null
        val className = parts[0]
        val label = parts[1]
        val index = parts[2].toIntOrNull() ?: 0
        var sameClassSeen = 0
        var labeledMatch: AccessibilityNodeInfo? = null
        var indexedMatch: AccessibilityNodeInfo? = null
        forEachNode(root) { node ->
            if (node.className?.toString() != className) return@forEachNode
            sameClassSeen++
            if (indexedMatch == null && sameClassSeen - 1 == index) indexedMatch = node
            if (labeledMatch == null && label.isNotBlank()) {
                if (node.text?.toString() == label || node.contentDescription?.toString() == label) {
                    labeledMatch = node
                }
            }
        }
        return labeledMatch ?: indexedMatch
    }

    private fun findByResourceId(root: AccessibilityNodeInfo, keyword: String): ToggleMatch? {
        val nodes = root.findAccessibilityNodeInfosByViewId(SWITCH_WIDGET_ID)
        if (nodes.isEmpty()) return null
        return bestRanked(nodes.mapNotNull { wrap(it, "switch_widget") }, keyword)
    }

    private fun findByClass(root: AccessibilityNodeInfo, keyword: String): ToggleMatch? {
        val switches = mutableListOf<AccessibilityNodeInfo>()
        forEachNode(root) { node ->
            val cls = node.className?.toString() ?: return@forEachNode
            if (cls == SWITCH_CLASS || cls == COMPOUND_BUTTON_CLASS) switches.add(node)
        }
        return bestRanked(switches.mapNotNull { wrap(it, "class-switch") }, keyword)
    }

    private fun bestRanked(matches: List<ToggleMatch>, keyword: String): ToggleMatch? {
        if (matches.isEmpty()) return null
        val scored = matches.map { it to rowScore(rowTextOf(it.stateNode), keyword) }
        val best = scored.maxByOrNull { it.second } ?: return null
        return when {
            best.second > 0 -> best.first
            scored.size == 1 && best.second == 0 -> best.first
            else -> null
        }
    }

    private fun findByAnyCheckable(root: AccessibilityNodeInfo, keyword: String): ToggleMatch? {
        val checkables = mutableListOf<AccessibilityNodeInfo>()
        forEachNode(root) { node ->
            if (node.isCheckable) checkables.add(node)
        }
        return bestRanked(checkables.mapNotNull { wrap(it, "checkable") }, keyword)
    }

    private fun findByTextProximity(root: AccessibilityNodeInfo, keyword: String): ToggleMatch? {
        val anchors = mutableListOf<Pair<AccessibilityNodeInfo, Int>>()
        forEachNode(root) { node ->
            val combined = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
            val lower = combined.lowercase()
            if (lower.contains(keyword)) {
                val bonus = if (lower.trim().startsWith(keyword)) 4 else 0
                anchors.add(node to rowScore(lower, keyword) + bonus)
            }
        }
        val sorted = anchors.sortedByDescending { it.second }
        for ((anchor, _) in sorted) {
            var subtreeMatch: ToggleMatch? = null
            forEachNode(anchor) { node ->
                if (subtreeMatch == null && isSwitchLike(node)) subtreeMatch = wrap(node, "text-prox:desc")
            }
            if (subtreeMatch != null) return subtreeMatch

            var current = anchor.parent
            repeat(MAX_ANCESTOR_HOPS) {
                val parent = current ?: return@repeat
                for (i in 0 until parent.childCount) {
                    val sibling = parent.getChild(i) ?: continue
                    if (isSwitchLike(sibling)) wrap(sibling, "text-prox:sib")?.let { return it }
                    var nested: ToggleMatch? = null
                    forEachNode(sibling) { node ->
                        if (nested == null && isSwitchLike(node)) nested = wrap(node, "text-prox:sib-sub")
                    }
                    if (nested != null) return nested
                }
                current = parent.parent
            }
        }
        return null
    }

    private fun isSwitchLike(node: AccessibilityNodeInfo): Boolean {
        val cls = node.className?.toString() ?: return false
        return (cls == SWITCH_CLASS || cls == COMPOUND_BUTTON_CLASS) &&
            (node.isCheckable || node.isClickable)
    }

    private fun rowScore(rowText: String, keyword: String): Int {
        var score = 0
        if (rowText.contains(keyword)) score += 4
        val negativeWords = listOf("bluetooth", "usb", "ethernet", "saver", "roaming", "vpn", "wi-fi sharing", "wifi sharing", "tethering")
        for (word in negativeWords) if (rowText.contains(word)) score -= 6
        return score
    }

    private fun rowTextOf(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        var current: AccessibilityNodeInfo? = node
        var hops = 0
        while (current != null && hops < 4 && sb.length < 400) {
            collectText(current, sb, 0)
            current = current.parent
            hops++
        }
        return sb.toString().lowercase()
    }

    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (sb.length >= 400 || depth > 3) return
        node.text?.let { if (it.isNotBlank()) sb.append(it).append(' ') }
        node.contentDescription?.let { if (it.isNotBlank()) sb.append(it).append(' ') }
        if (sb.length >= 400) return
        for (i in 0 until node.childCount) {
            try { node.getChild(i)?.let { collectText(it, sb, depth + 1) } } catch (e: Exception) {}
        }
    }

    private fun wrap(stateNode: AccessibilityNodeInfo, source: String): ToggleMatch? {
        val target = if (stateNode.isClickable) stateNode else {
            var current: AccessibilityNodeInfo? = stateNode.parent
            var hops = 0
            var clickable: AccessibilityNodeInfo? = null
            while (current != null && hops < MAX_ANCESTOR_HOPS) {
                if (current.isClickable) { clickable = current; break }
                current = current.parent; hops++
            }
            clickable
        } ?: return null
        return ToggleMatch(stateNode, target, source)
    }

    private inline fun forEachNode(root: AccessibilityNodeInfo, maxNodes: Int = MAX_NODES, action: (AccessibilityNodeInfo) -> Unit) {
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        while (queue.isNotEmpty() && count < maxNodes) {
            val node = queue.removeFirst()
            count++
            try { action(node) } catch (e: Exception) {}
            for (i in 0 until node.childCount) {
                try { node.getChild(i)?.let { queue.add(it) } } catch (e: Exception) {}
            }
        }
    }
}

data class ToggleMatch(
    val stateNode: AccessibilityNodeInfo,
    val clickTarget: AccessibilityNodeInfo,
    val source: String
)