package com.iranjan.hotspotscheduler.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val daysCsv: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val hotspotTarget: Int, // 0=LeaveAlone, 1=Set(false), 2=Set(true)
    val mobileDataTarget: Int,
    val hotspotPassword: String?,
    val priority: Int = 0,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)