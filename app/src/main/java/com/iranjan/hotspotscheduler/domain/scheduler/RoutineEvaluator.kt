package com.iranjan.hotspotscheduler.domain.scheduler

import com.iranjan.hotspotscheduler.domain.model.Routine
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object RoutineEvaluator {

    private const val DAY_MINUTES = 24L * 60

    data class Boundary(
        val routineId: Long,
        val isStart: Boolean,
        val atMillis: Long
    ) {
        val key: String get() = "$routineId:${if (isStart) "S" else "E"}:$atMillis"
    }

    fun windowOnDay(routine: Routine, date: LocalDate, zone: ZoneId): Pair<Long, Long>? {
        if (!routine.enabled) return null
        if (date.dayOfWeek.value !in routine.daysOfWeek) return null
        if (routine.startTime == routine.endTime) return null

        val start = atLocalTime(date, routine.startTime, zone)
        val endDate = if (routine.endTime.isAfter(routine.startTime)) date else date.plusDays(1)
        val end = atLocalTime(endDate, routine.endTime, zone)
        return start.toInstant().toEpochMilli() to end.toInstant().toEpochMilli()
    }

    private fun atLocalTime(date: LocalDate, time: LocalTime, zone: ZoneId): ZonedDateTime =
        LocalDateTime.of(date, time).atZone(zone)

    private fun collectWindows(routine: Routine, firstDay: LocalDate, dayCount: Int, zone: ZoneId): List<Pair<Long, Long>> =
        (0 until dayCount).mapNotNull { offset ->
            windowOnDay(routine, firstDay.plusDays(offset.toLong()), zone)
        }

    fun activeRoutines(
        routines: List<Routine>,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<Routine> {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        return routines.filter { routine ->
            collectWindows(routine, today.minusDays(1), 2, zone).any { (start, end) -> nowMs >= start && nowMs < end }
        }
    }

    fun strictestCapMb(routines: List<Routine>): Long? =
        routines.filter { it.enabled }.mapNotNull { it.hotspotPassword?.let { 0L } }.minOrNull()

    fun nextBoundary(
        routines: List<Routine>,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boundary? {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        var best: Boundary? = null
        for (routine in routines) {
            if (!routine.enabled) continue
            for (window in collectWindows(routine, today, 8, zone)) {
                if (window.first > nowMs) {
                    val candidate = Boundary(routine.id, true, window.first)
                    if (best == null || candidate.atMillis < best!!.atMillis) best = candidate
                }
                if (window.second > nowMs) {
                    val candidate = Boundary(routine.id, false, window.second)
                    if (best == null || candidate.atMillis < best!!.atMillis) best = candidate
                }
            }
        }
        return best
    }

    fun lastBoundary(
        routines: List<Routine>,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boundary? {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        var best: Boundary? = null
        for (routine in routines) {
            if (!routine.enabled) continue
            for (window in collectWindows(routine, today.minusDays(2), 3, zone)) {
                if (window.first <= nowMs) {
                    val candidate = Boundary(routine.id, true, window.first)
                    if (best == null || candidate.atMillis > best!!.atMillis) best = candidate
                }
                if (window.second <= nowMs) {
                    val candidate = Boundary(routine.id, false, window.second)
                    if (best == null || candidate.atMillis > best!!.atMillis) best = candidate
                }
            }
        }
        return best
    }

    fun nextMidnight(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        return now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun todayEpochDay(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()

    fun isActive(routine: Routine, instant: Instant, zone: ZoneId): Boolean =
        routine.isActiveAt(instant, zone)

    fun formatTime(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

    fun boundaryAtMillis(key: String?): Long? {
        if (key.isNullOrBlank()) return null
        return """(\d+):([SE]):(\d+)""".toRegex().matchEntire(key)?.groupValues?.get(3)?.toLongOrNull()
    }
}