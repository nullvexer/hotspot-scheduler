package com.iranjan.hotspotscheduler.di

import com.iranjan.hotspotscheduler.automation.recovery.CrashRecovery
import com.iranjan.hotspotscheduler.automation.session.AutomationCoordinator
import com.iranjan.hotspotscheduler.domain.scheduler.BoundaryScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface HiltEntryPoint {
    fun automationCoordinator(): AutomationCoordinator
    fun boundaryScheduler(): BoundaryScheduler
    fun crashRecovery(): CrashRecovery
}