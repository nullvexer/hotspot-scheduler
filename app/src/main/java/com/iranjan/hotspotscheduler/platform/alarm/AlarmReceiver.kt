package com.iranjan.hotspotscheduler.platform.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.iranjan.hotspotscheduler.HotspotApp

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val app = context.applicationContext as HotspotApp

        when (action) {
            AlarmScheduler.ACTION_BOUNDARY -> {
                val routineId = intent.getLongExtra(AlarmScheduler.EXTRA_ROUTINE_ID, -1)
                val isStart = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_START, false)
                val atMillis = intent.getLongExtra(AlarmScheduler.EXTRA_AT_MILLIS, 0)
                val boundary = com.iranjan.hotspotscheduler.domain.scheduler.RoutineEvaluator.Boundary(routineId, isStart, atMillis)
                app.automationCoordinator.executeBoundary(boundary)
            }
            AlarmScheduler.ACTION_MIDNIGHT -> {
                // Trigger cap reset, day rollover, reschedule
                app.boundaryScheduler.rescheduleAll()
            }
        }
    }
}