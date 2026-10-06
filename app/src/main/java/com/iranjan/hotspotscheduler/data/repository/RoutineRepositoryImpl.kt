package com.iranjan.hotspotscheduler.data.repository

import com.iranjan.hotspotscheduler.data.database.AppDatabase
import com.iranjan.hotspotscheduler.data.database.RoutineEntity
import com.iranjan.hotspotscheduler.domain.model.Routine
import com.iranjan.hotspotscheduler.domain.model.Target
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutineRepositoryImpl @Inject constructor(
    private val database: AppDatabase
) : RoutineRepository {

    override suspend fun getAllRoutines(): List<Routine> =
        database.routineDao().getAll().first().map { it.toDomain() }

    override suspend fun getEnabledRoutines(): List<Routine> =
        database.routineDao().getEnabled().first().map { it.toDomain() }

    override suspend fun getRoutine(id: Long): Routine? =
        database.routineDao().getById(id)?.toDomain()

    override suspend fun insertRoutine(routine: Routine): Long =
        database.routineDao().insert(routine.toEntity())

    override suspend fun updateRoutine(routine: Routine) {
        database.routineDao().update(routine.toEntity())
    }

    override suspend fun deleteRoutine(id: Long) {
        database.routineDao().deleteById(id)
    }

    override fun observeAllRoutines(): Flow<List<Routine>> =
        database.routineDao().getAll().map { list -> list.map { it.toDomain() } }

    override fun observeEnabledRoutines(): Flow<List<Routine>> =
        database.routineDao().getEnabled().map { list -> list.map { it.toDomain() } }
}

private fun RoutineEntity.toDomain(): Routine = Routine(
    id = id,
    name = name,
    enabled = enabled,
    daysOfWeek = daysCsv.split(",")
        .mapNotNull { it.trim().toIntOrNull() }
        .mapNotNull { value -> runCatching { DayOfWeek.of(value) }.getOrNull() }
        .toSet(),
    startTime = LocalTime.of(startMinutes / 60, startMinutes % 60),
    endTime = LocalTime.of(endMinutes / 60, endMinutes % 60),
    hotspotTarget = when (hotspotTarget) {
        0 -> Target.LeaveAlone
        1 -> Target.Set(false)
        else -> Target.Set(true)
    },
    mobileDataTarget = when (mobileDataTarget) {
        0 -> Target.LeaveAlone
        1 -> Target.Set(false)
        else -> Target.Set(true)
    },
    hotspotPassword = hotspotPassword,
    priority = priority
)

private fun Routine.toEntity(): RoutineEntity = RoutineEntity(
    id = id,
    name = name,
    daysCsv = daysOfWeek.sortedBy { it.value }.joinToString(",") { it.value.toString() },
    startMinutes = startTime.hour * 60 + startTime.minute,
    endMinutes = endTime.hour * 60 + endTime.minute,
    hotspotTarget = when (val t = hotspotTarget) {
        Target.LeaveAlone -> 0
        is Target.Set -> if (t.on) 2 else 1
    },
    mobileDataTarget = when (val t = mobileDataTarget) {
        Target.LeaveAlone -> 0
        is Target.Set -> if (t.on) 2 else 1
    },
    hotspotPassword = hotspotPassword,
    priority = priority,
    enabled = enabled
)