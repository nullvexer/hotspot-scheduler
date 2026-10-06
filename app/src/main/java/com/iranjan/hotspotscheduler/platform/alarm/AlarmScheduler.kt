package com.iranjan.hotspotscheduler.platform.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.iranjan.hotspotscheduler.domain.scheduler.RoutineEvaluator
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    private val context: Context
) {
    suspend fun rescheduleAll(
        routines: List<com.iranjan.hotspotscheduler.domain.model.Routine>,
        masterEnabled: Boolean
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val zone = java.time.ZoneId.systemDefault()

        cancelAll(alarmManager, routines)

        if (masterEnabled) {
            val midnight = RoutineEvaluator.nextMidnight(now, zone)
            schedule(alarmManager, RC_MIDNIGHT, midnight, ACTION_MIDNIGHT, null)

            val next = RoutineEvaluator.nextBoundary(routines, now, zone)
            if (next != null) {
                schedule(alarmManager, requestCodeFor(next), next.atMillis, ACTION_BOUNDARY, next)
            }
        }
    }

    suspend fun cancelAll(routines: List<com.iranjan.hotspotscheduler.domain.model.Routine>) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        cancelAll(alarmManager, routines)
    }

    private fun cancelAll(alarmManager: AlarmManager, routines: List<com.iranjan.hotspotscheduler.domain.model.Routine>) {
        alarmManager.cancel(pendingIntent(RC_MIDNIGHT, ACTION_MIDNIGHT, null))
        for (routine in routines) {
            for (isStart in listOf(true, false)) {
                val boundary = RoutineEvaluator.Boundary(routine.id, isStart, 0)
                alarmManager.cancel(pendingIntent(requestCodeFor(boundary), ACTION_BOUNDARY, null))
            }
        }
    }

    private fun schedule(
        alarmManager: AlarmManager,
        requestCode: Int,
        atMillis: Long,
        action: String,
        boundary: RoutineEvaluator.Boundary?
    ) {
        val pi = pendingIntent(requestCode, action, boundary)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            when {
                canExact -> alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(atMillis, pi),
                    pi
                )
                else -> {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
                }
            }
        } catch (e: Exception) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    private fun pendingIntent(requestCode: Int, action: String, boundary: RoutineEvaluator.Boundary?): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            buildIntent(action, boundary),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun buildIntent(action: String, boundary: RoutineEvaluator.Boundary?): Intent =
        Intent(context, AlarmReceiver::class.java).setAction(action).apply {
            boundary?.let {
                putExtra(EXTRA_ROUTINE_ID, it.routineId)
                putExtra(EXTRA_IS_START, it.isStart)
                putExtra(EXTRA_AT_MILLIS, it.atMillis)
            }
        }

    private fun requestCodeFor(boundary: RoutineEvaluator.Boundary): Int {
        val id = (boundary.routineId + 1).coerceAtLeast(1L)
        return if (boundary.isStart) RC_BOUNDARY_START + (id % RC_SPAN).toInt()
        else RC_BOUNDARY_END + (id % RC_SPAN).toInt()
    }

    companion object {
        const val ACTION_BOUNDARY = "com.iranjan.hotspotscheduler.ACTION_BOUNDARY"
        const val ACTION_MIDNIGHT = "com.iranjan.hotspotscheduler.ACTION_MIDNIGHT"
        const val EXTRA_ROUTINE_ID = "routine_id"
        const val EXTRA_IS_START = "is_start"
        const val EXTRA_AT_MILLIS = "at_millis"
        private const val RC_MIDNIGHT = 7
        private const val RC_BOUNDARY_START = 1_000_000
        private const val RC_BOUNDARY_END = 2_000_000
        private const val RC_SPAN = 900_000L
    }
}