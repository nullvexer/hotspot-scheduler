package com.iranjan.hotspotscheduler.domain.scheduler

import com.iranjan.hotspotscheduler.domain.model.Routine
import com.iranjan.hotspotscheduler.data.repository.RoutineRepository
import com.iranjan.hotspotscheduler.data.datastore.AutomationPreferences
import com.iranjan.hotspotscheduler.platform.alarm.AlarmScheduler
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BoundaryScheduler @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val preferences: AutomationPreferences,
    private val alarmScheduler: AlarmScheduler,
    private val desiredStateResolver: DesiredStateResolver
) {
    suspend fun rescheduleAll() {
        val masterEnabled = preferences.masterEnabled.first()
        val routines = routineRepository.getEnabledRoutines()
        alarmScheduler.rescheduleAll(routines, masterEnabled)
    }
}