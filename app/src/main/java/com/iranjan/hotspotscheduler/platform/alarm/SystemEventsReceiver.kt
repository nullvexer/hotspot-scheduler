package com.iranjan.hotspotscheduler.platform.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.iranjan.hotspotscheduler.HotspotApp

class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val app = context.applicationContext as HotspotApp

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_SET,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                app.boundaryScheduler.rescheduleAll()
            }
        }
    }
}