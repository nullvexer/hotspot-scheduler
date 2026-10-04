package com.iranjan.hotspotscheduler.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.iranjan.hotspotscheduler.data.prefs.AutomationPrefs
import com.iranjan.hotspotscheduler.data.repo.RoutineRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: RoutineRepository,
    private val prefs: AutomationPrefs
) {
    suspend fun rescheduleAll() {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val master = prefs.masterEnabled.first()
        val routines = repo.enabledRoutines()
        val next = if (master) RoutineEvaluator.nextBoundary(routines, now, zone) else null

        // Cancel first. rescheduleAll used to only ever ADD, so an alarm armed for a routine
        // that was later disabled or deleted kept firing, and switching to a different next
        // boundary left the previous one armed alongside it.
        cancelBoundaryAlarms(alarmManager, routines)

        if (master) {
            schedule(alarmManager, RC_MIDNIGHT, RoutineEvaluator.nextMidnight(now, zone), ACTION_MIDNIGHT, null)
            if (next != null) {
                schedule(alarmManager, requestCodeFor(next), next.atMillis, ACTION_BOUNDARY, next)
            } else {
                Log.i(TAG, "no upcoming boundary in horizon; midnight alarm only")
            }
        } else {
            alarmManager.cancel(pendingIntent(RC_MIDNIGHT, ACTION_MIDNIGHT, null))
        }
        prefs.setAlarmsDirty(false)
    }

    suspend fun cancelAll() {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        cancelBoundaryAlarms(alarmManager, repo.enabledRoutines())
        alarmManager.cancel(pendingIntent(RC_MIDNIGHT, ACTION_MIDNIGHT, null))
    }

    private fun cancelBoundaryAlarms(
        alarmManager: AlarmManager,
        routines: List<com.iranjan.hotspotscheduler.data.model.Routine>
    ) {
        for (routine in routines) {
            for (isStart in listOf(true, false)) {
                val rc = requestCodeFor(RoutineEvaluator.Boundary(routine.id, isStart, 0))
                alarmManager.cancel(pendingIntent(rc, ACTION_BOUNDARY, null))
            }
        }
    }

    /**
     * Routines that were deleted between reschedules no longer appear in [routines], so their
     * alarms cannot be cancelled by id. Request codes are derived from the routine id, so a
     * bounded sweep is not possible in general; callers that delete routines must therefore go
     * through [cancelAll] before deleting. Kept explicit here so the coupling is documented.
     */
    private fun requestCodeFor(boundary: RoutineEvaluator.Boundary): Int {
        // Offset the id so an unsaved routine (id 0, Room autoGenerate) cannot collide with
        // RC_MIDNIGHT, and keep the start/end halves in disjoint ranges.
        val id = (boundary.routineId + 1).coerceAtLeast(1L)
        return if (boundary.isStart) {
            RC_BOUNDARY_START + (id % RC_SPAN).toInt()
        } else {
            RC_BOUNDARY_END + (id % RC_SPAN).toInt()
        }
    }

    private fun pendingIntent(
        requestCode: Int,
        action: String,
        boundary: RoutineEvaluator.Boundary?
    ): PendingIntent = PendingIntent.getBroadcast(
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
                // setAlarmClock is exempt from both SCHEDULE_EXACT_ALARM and Doze deferral, and
                // it is the correct API for a user-visible schedule. It shows the alarm icon in
                // the status bar, which is honest for an app that is about to toggle a hotspot.
                canExact -> alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(atMillis, pi),
                    pi
                )
                // No exact-alarm permission: the previous code called setWindow(), which starts
                // the window AT atMillis and so can fire up to 60s late, and is additionally
                // deferred to the next Doze maintenance window. An inexact alarm set at the
                // boundary is no better, but it is honest about when it will actually run.
                else -> {
                    Log.w(TAG, "exact alarms not permitted; scheduling $action inexactly")
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
                }
            }
        } catch (t: Throwable) {
            // An OEM that misreports canScheduleExactAlarms() would otherwise throw here and
            // kill the whole tick via the caller's blanket catch.
            Log.e(TAG, "failed to schedule $action", t)
            runCatching { alarmManager.set(AlarmManager.RTC_WAKEUP, atMillis, pi) }
        }
    }

    companion object {
        private const val TAG = "HSAlarm"
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