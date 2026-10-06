package com.iranjan.hotspotscheduler

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HotspotApp : Application() {
    lateinit var automationCoordinator: com.iranjan.hotspotscheduler.automation.session.AutomationCoordinator
    lateinit var boundaryScheduler: com.iranjan.hotspotscheduler.domain.scheduler.BoundaryScheduler

    override fun onCreate() {
        super.onCreate()
        automationCoordinator = com.iranjan.hotspotscheduler.di.EntryPointAccessors.fromApplication(this, com.iranjan.hotspotscheduler.di.HiltEntryPoint::class.java).automationCoordinator()
        boundaryScheduler = com.iranjan.hotspotscheduler.di.EntryPointAccessors.fromApplication(this, com.iranjan.hotspotscheduler.di.HiltEntryPoint::class.java).boundaryScheduler()
    }
}