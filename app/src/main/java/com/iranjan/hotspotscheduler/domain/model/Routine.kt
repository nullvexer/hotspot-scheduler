package com.iranjan.hotspotscheduler.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

data class Routine(
    val id: Long,
    val name: String,
    val enabled: Boolean,
    val daysOfWeek: Set<DayOfWeek>,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val hotspotTarget: Target,
    val mobileDataTarget: Target,
    val hotspotPassword: String? = null,
    val priority: Int = 0
) {
    fun isActiveAt(instant: java.time.Instant, zone: java.time.ZoneId): Boolean {
        if (!enabled) return false
        val zdt = instant.atZone(zone)
        if (zdt.dayOfWeek !in daysOfWeek) return false
        val start = zdt.toLocalDate().atTime(startTime).atZone(zone).toInstant()
        val endDate = if (endTime.isAfter(startTime)) zdt.toLocalDate() else zdt.toLocalDate().plusDays(1)
        val end = endDate.atTime(endTime).atZone(zone).toInstant()
        return instant >= start && instant < end
    }

    val isOvernight: Boolean
        get() = endTime.isBefore(startTime) || endTime == startTime
}