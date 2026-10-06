package com.iranjan.hotspotscheduler

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class HotspotApp : Application() {
    lateinit var automationCoordinator: com.iranjan.hotspotscheduler.automation.session.AutomationCoordinator
    lateinit var boundaryScheduler: com.iranjan.hotspotscheduler.domain.scheduler.BoundaryScheduler
    lateinit var crashRecovery: com.iranjan.hotspotscheduler.automation.recovery.CrashRecovery

    override fun onCreate() {
        super.onCreate()
        automationCoordinator = com.iranjan.hotspotscheduler.di.EntryPointAccessors.fromApplication(this, com.iranjan.hotspotscheduler.di.HiltEntryPoint::class.java).automationCoordinator()
        boundaryScheduler = com.iranjan.hotspotscheduler.di.EntryPointAccessors.fromApplication(this, com.iranjan.hotspotscheduler.di.HiltEntryPoint::class.java).boundaryScheduler()
        crashRecovery = com.iranjan.hotspotscheduler.di.EntryPointAccessors.fromApplication(this, com.iranjan.hotspotscheduler.di.HiltEntryPoint::class.java).crashRecovery()

        // Run crash recovery on startup
        CoroutineScope(Dispatchers.IO).launch {
            crashRecovery.runIfNeeded()
        }

        // Reschedule alarms on startup
        CoroutineScope(Dispatchers.IO).launch {
            boundaryScheduler.rescheduleAll()
        }
    }
}