package com.iranjan.hotspotscheduler.accessibility

import android.view.accessibility.AccessibilityNodeInfo

object NodeDumper {

    fun dump(root: AccessibilityNodeInfo?): List<String> {
        val result = mutableListOf<String>()
        root?.let { dumpNode(it, 0, result) }
        return result
    }

    private fun dumpNode(node: AccessibilityNodeInfo, depth: Int, result: MutableList<String>) {
        if (depth > 10 || result.size > 200) return
        val indent = "  ".repeat(depth)
        val pkg = node.packageName ?: ""
        val cls = node.className ?: ""
        val id = node.viewIdResourceName ?: ""
        val text = node.text ?: ""
        val desc = node.contentDescription ?: ""
        val bounds = node.boundsInScreen
        val b = if (bounds != null) " [$bounds]" else ""
        result.add("$indent$pkg/$cls$id b$text$desc$b")
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { dumpNode(it, depth + 1, result) }
        }
    }

    fun dumpCompact(root: AccessibilityNodeInfo?, maxLines: Int = 50): List<String> {
        val result = mutableListOf<String>()
        root?.let { dumpNodeCompact(it, 0, result, maxLines) }
        return result
    }

    private fun dumpNodeCompact(node: AccessibilityNodeInfo, depth: Int, result: MutableList<String>, maxLines: Int) {
        if (depth > 10 || result.size >= maxLines) return
        val indent = "  ".repeat(depth)
        val id = node.viewIdResourceName ?: ""
        val cls = node.className?.toString().substringAfterLast(".") ?: ""
        val text = node.text?.takeIf { it.isNotBlank() } ?: ""
        val clickable = if (node.isClickable) " [C]" else ""
        val checkable = if (node.isCheckable) " [Ch]" else ""
        val checked = if (node.isChecked) " [✓]" else ""
        result.add("$indent$id $cls$text$clickable$checkable$checked")
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { dumpNodeCompact(it, depth + 1, result, maxLines) }
        }
    }
}