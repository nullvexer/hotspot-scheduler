package com.iranjan.hotspotscheduler.data.repository

import com.iranjan.hotspotscheduler.domain.model.Routine
import kotlinx.coroutines.flow.Flow

interface RoutineRepository {
    suspend fun getAllRoutines(): List<Routine>
    suspend fun getEnabledRoutines(): List<Routine>
    suspend fun getRoutine(id: Long): Routine?
    suspend fun insertRoutine(routine: Routine): Long
    suspend fun updateRoutine(routine: Routine)
    suspend fun deleteRoutine(id: Long)
    fun observeAllRoutines(): Flow<List<Routine>>
    fun observeEnabledRoutines(): Flow<List<Routine>>
}