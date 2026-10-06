package com.iranjan.hotspotscheduler.domain.scheduler

import com.iranjan.hotspotscheduler.domain.model.DesiredNetworkState
import com.iranjan.hotspotscheduler.domain.model.Routine
import com.iranjan.hotspotscheduler.domain.model.Target
import com.iranjan.hotspotscheduler.data.repository.RoutineRepository
import com.iranjan.hotspotscheduler.data.datastore.AutomationPreferences
import java.time.Instant
import java.time.ZoneId

class DesiredStateResolver @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val preferences: AutomationPreferences
) {
    data class Inputs(
        val now: Instant,
        val zone: ZoneId,
        val masterEnabled: Boolean,
        val paused: Boolean,
        val capReached: Boolean,
        val hotspotSuppressed: Boolean
    )

    suspend fun resolve(inputs: Inputs): DesiredNetworkState {
        if (!inputs.masterEnabled || inputs.paused) return DesiredNetworkState.untouched()

        val routines = routineRepository.getEnabledRoutines()
        val active = routines.filter { RoutineEvaluator.isActive(it, inputs.now, inputs.zone) }
        if (active.isEmpty()) return DesiredNetworkState.allOff()

        val wantsData = active.any { it.mobileDataTarget == Target.Set(true) }
        val wantsHotspot = active.any { it.hotspotTarget == Target.Set(true) } || wantsData

        val dataTarget = if (wantsData) Target.Set(true) else Target.Set(false)
        val hotspotTarget = when {
            inputs.capReached || inputs.hotspotSuppressed -> Target.Set(false)
            wantsHotspot -> Target.Set(true)
            else -> Target.Set(false)
        }

        return DesiredNetworkState(hotspotTarget, dataTarget)
    }

    suspend fun nextBoundaryChange(zone: ZoneId = ZoneId.systemDefault()): RoutineEvaluator.Boundary? {
        val now = Instant.now()
        val routines = routineRepository.getEnabledRoutines()
        return RoutineEvaluator.nextBoundary(routines, now.toEpochMilli(), zone)
    }
}