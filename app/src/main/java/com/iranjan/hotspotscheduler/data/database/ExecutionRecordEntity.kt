package com.iranjan.hotspotscheduler.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "execution_records")
data class ExecutionRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val trigger: String,
    val startedAt: Long,
    val endedAt: Long,
    val desiredHotspot: Int,
    val desiredMobileData: Int,
    val actualHotspotBefore: Int?,
    val actualMobileDataBefore: Int?,
    val actualHotspotAfter: Int?,
    val actualMobileDataAfter: Int?,
    val outcome: String,
    val failureReason: String?,
    val durationMs: Long
)