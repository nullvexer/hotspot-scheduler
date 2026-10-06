package com.iranjan.hotspotscheduler.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AttemptLog {

    private val MAX_ENTRIES = 200
    private val entries = MutableStateFlow<List<String>>(emptyList())

    val log: StateFlow<List<String>> = entries

    fun add(message: String) {
        val timestamped = "[${java.time.Instant.now()}] $message"
        entries.value = (entries.value + timestamped).takeLast(MAX_ENTRIES)
    }

    fun clear() {
        entries.value = emptyList()
    }

    fun getAll(): List<String> = entries.value
}