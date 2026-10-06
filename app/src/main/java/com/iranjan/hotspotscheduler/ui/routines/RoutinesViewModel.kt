package com.iranjan.hotspotscheduler.ui.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iranjan.hotspotscheduler.data.repository.RoutineRepository
import com.iranjan.hotspotscheduler.domain.model.Routine
import com.iranjan.hotspotscheduler.domain.model.Target
import com.iranjan.hotspotscheduler.domain.scheduler.RoutineEvaluator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutinesViewModel @Inject constructor(
    private val routineRepository: RoutineRepository
) : ViewModel() {

    private val _routines = MutableStateFlow<List<Routine>>(emptyList())
    val routines: kotlinx.coroutines.flow.StateFlow<List<Routine>> = _routines

    init {
        loadRoutines()
    }

    fun loadRoutines() {
        viewModelScope.launch {
            _routines.value = routineRepository.getAllRoutines()
        }
    }

    fun deleteRoutine(id: Long) {
        viewModelScope.launch {
            routineRepository.deleteRoutine(id)
            loadRoutines()
        }
    }

    fun toggleEnabled(routine: Routine) {
        viewModelScope.launch {
            routineRepository.updateRoutine(routine.copy(enabled = !routine.enabled))
            loadRoutines()
        }
    }

    fun formatTime(time: java.time.LocalTime): String = RoutineEvaluator.formatTime(time)
}