package com.iranjan.hotspotscheduler.data.database

import androidx.room.TypeConverter
import java.time.DayOfWeek

class Converters {
    @TypeConverter
    fun fromDayOfWeekSet(set: Set<DayOfWeek>?): String? =
        set?.joinToString(",") { it.value.toString() }

    @TypeConverter
    fun toDayOfWeekSet(csv: String?): Set<DayOfWeek>? =
        csv?.split(",")?.mapNotNull { it.toIntOrNull()?.let { DayOfWeek.of(it) } }?.toSet()
}