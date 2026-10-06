package com.iranjan.hotspotscheduler.util

object Formatters {

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0)
        if (bytes < 1024 * 1024 * 1024) return "%.1f MB".format(bytes / (1024.0 * 1024))
        return "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    }

    fun formatCapMb(capMb: Long?): String =
        capMb?.let { "${it} MB" } ?: "unlimited"
}