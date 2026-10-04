package com.iranjan.hotspotscheduler.core

import com.iranjan.hotspotscheduler.data.model.Routine
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object RoutineEvaluator {

    data class Boundary(val routineId: Long, val isStart: Boolean, val atMillis: Long) {
        val key: String get() = "$routineId:${if (isStart) "S" else "E"}:$atMillis"
    }

    const val DAY_MINUTES = 24L * 60

    /**
     * Inverse of [Boundary.key]. Returns null for an empty/absent key so callers can tell
     * "nothing applied yet" apart from "applied at epoch 0".
     */
    fun boundaryAtMillis(key: String?): Long? {
        if (key.isNullOrBlank()) return null
        return BOUNDARY_KEY_REGEX.matchEntire(key)?.groupValues?.get(3)?.toLongOrNull()
    }

    private val BOUNDARY_KEY_REGEX = Regex("""(\d+):([SE]):(\d+)""")

    fun windowOnDay(routine: Routine, date: LocalDate, zone: ZoneId): Pair<Long, Long>? {
        if (!routine.enabled) return null
        if (date.dayOfWeek.value !in routine.days) return null
        // Equal start/end used to fall into the overnight branch and produce a 24-hour window,
        // i.e. "always on" with the cap permanently applied. A zero-length window is the only
        // reading that cannot silently mean the opposite of what the user asked for.
        if (routine.startMinutes == routine.endMinutes) return null

        // Resolve each endpoint from its wall-clock time rather than adding a duration to the
        // start. Adding minutes is absolute time, so a 01:00-04:00 window became 2 real hours
        // on spring-forward and 4 on fall-back.
        //
        // The end date is chosen from the wall-clock comparison BEFORE resolving the time:
        // an overnight 23:00-02:00 window on a spring-forward date must end at 02:00 on the
        // NEXT day. Resolving 02:00 on the start date first would hit the DST gap, be pushed
        // to 03:00, and then wrongly roll a second time.
        val start = atLocalTime(date, routine.startMinutes, zone)
        val endDate = if (routine.endMinutes > routine.startMinutes) date else date.plusDays(1)
        val end = atLocalTime(endDate, routine.endMinutes, zone)
        return start.toInstant().toEpochMilli() to end.toInstant().toEpochMilli()
    }

    private fun atLocalTime(date: LocalDate, minutes: Int, zone: ZoneId): ZonedDateTime {
        val clamped = minutes.coerceIn(0, DAY_MINUTES.toInt() - 1)
        return LocalDateTime.of(date, LocalTime.of(clamped / 60, clamped % 60)).atZone(zone)
    }

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
        routines.filter { it.enabled }.mapNotNull { it.capMb }.minOrNull()

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

    fun overlapping(
        routine: Routine,
        others: List<Routine>,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<Routine> {
        if (!routine.enabled) return emptyList()
        val today = LocalDate.now(zone)
        return others.filter { other ->
            other.enabled && windowsOverlap(routine, other, today, zone)
        }
    }

    private fun windowsOverlap(a: Routine, b: Routine, today: LocalDate, zone: ZoneId): Boolean {
        val aWindows = collectWindows(a, today, 9, zone)
        val bWindows = collectWindows(b, today, 9, zone)
        return aWindows.any { wa -> bWindows.any { wb -> wa.first < wb.second && wb.first < wa.second } }
    }

    fun nextMidnight(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        return now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun todayEpochDay(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()

    fun summarize(routine: Routine, dayLabels: Map<Int, String>): String {
        val order = listOf(1, 2, 3, 4, 5, 6, 7)
        val days = if (routine.days.toSortedSet() == order.toSet()) {
            "Every day"
        } else {
            order.filter { it in routine.days }.mapNotNull { dayLabels[it] }.joinToString(", ")
        }
        return "$days ${formatMinutes(routine.startMinutes)}–${formatMinutes(routine.endMinutes)}"
    }

    fun formatMinutes(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return "%02d:%02d".format(h, m)
    }

    fun dayLabel(isoDay: Int, labels: Map<Int, String>): String = labels[isoDay] ?: isoDay.toString()

    fun nowZoned(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime =
        Instant.ofEpochMilli(nowMs).atZone(zone)
}
