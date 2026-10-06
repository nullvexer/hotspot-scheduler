package com.iranjan.hotspotscheduler.ui.editor

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
class RoutineEditorViewModel @Inject constructor(
    private val routineRepository: RoutineRepository
) : ViewModel() {

    private val _name = MutableStateFlow("")
    private val _startHour = MutableStateFlow(2)
    private val _startMinute = MutableStateFlow(0)
    private val _endHour = MutableStateFlow(3)
    private val _endMinute = MutableStateFlow(0)
    private val _days = MutableStateFlow<Set<java.time.DayOfWeek>>(java.time.DayOfWeek.values().toSet())
    private val _hotspotTarget = MutableStateFlow<Target>(Target.Set(true))
    private val _dataTarget = MutableStateFlow<Target>(Target.Set(true))
    private val _hotspotPassword = MutableStateFlow("")
    private val _priority = MutableStateFlow(0)
    private val _isEditing = MutableStateFlow(false)
    private val _routineId = MutableStateFlow<Long?>(null)
    private val _error = MutableStateFlow<String?>(null)

    val name: StateFlow<String> = _name
    val startHour: StateFlow<Int> = _startHour
    val startMinute: StateFlow<Int> = _startMinute
    val endHour: StateFlow<Int> = _endHour
    val endMinute: StateFlow<Int> = _endMinute
    val days: StateFlow<Set<java.time.DayOfWeek>> = _days
    val hotspotTarget: StateFlow<Target> = _hotspotTarget
    val dataTarget: StateFlow<Target> = _dataTarget
    val hotspotPassword: StateFlow<String> = _hotspotPassword
    val priority: StateFlow<Int> = _priority
    val isEditing: StateFlow<Boolean> = _isEditing
    val routineId: StateFlow<Long?> = _routineId
    val error: StateFlow<String?> = _error

    // Derived values are recomputed in the setters rather than with derivedStateOf: these must be
// plain Flows so the editor screen can collect them without a Compose runtime dependency.
    private val _durationMinutes = MutableStateFlow(60)
    val durationMinutes: StateFlow<Int> = _durationMinutes

    private val _effectiveStatePreview = MutableStateFlow("")
    val effectiveStatePreview: StateFlow<String> = _effectiveStatePreview

    private val _validationErrors = MutableStateFlow<String?>(null)
    val validationErrors: StateFlow<String?> = _validationErrors

    private fun recomputeDerived() {
        val start = _startHour.value * 60 + _startMinute.value
        val end = _endHour.value * 60 + _endMinute.value
        _durationMinutes.value = if (end > start) end - start else (1440 - start + end)

        fun render(target: Target): String = when (target) {
            is Target.Set -> if (target.on) "ON" else "OFF"
            Target.LeaveAlone -> "UNCHANGED"
        }
        _effectiveStatePreview.value =
            "Hotspot: ${render(_hotspotTarget.value)}  •  Mobile Data: ${render(_dataTarget.value)}"

        _validationErrors.value = when {
            _name.value.isBlank() -> "Name required"
            _days.value.isEmpty() -> "Select at least one day"
            start == end -> "Start and end must differ"
            else -> null
        }
    }

    fun loadRoutine(id: Long) {
        viewModelScope.launch {
            val routine = routineRepository.getRoutine(id)
            routine?.let {
                _isEditing.value = true
                _routineId.value = id
                _name.value = it.name
                _startHour.value = it.startTime.hour
                _startMinute.value = it.startTime.minute
                _endHour.value = it.endTime.hour
                _endMinute.value = it.endTime.minute
                _days.value = it.daysOfWeek
                _hotspotTarget.value = it.hotspotTarget
                _dataTarget.value = it.mobileDataTarget
                _hotspotPassword.value = it.hotspotPassword ?: ""
                _priority.value = it.priority
            }
        }
    }

    fun clearForm() {
        _isEditing.value = false
        _routineId.value = null
        _name.value = ""
        _startHour.value = 2
        _startMinute.value = 0
        _endHour.value = 3
        _endMinute.value = 0
        _days.value = java.time.DayOfWeek.values().toSet()
        _hotspotTarget.value = Target.Set(true)
        _dataTarget.value = Target.Set(true)
        _hotspotPassword.value = ""
        _priority.value = 0
        _error.value = null
    }

    fun onNameChange(name: String) { _name.value = name; recomputeDerived() }
    fun onStartTimeChange(hour: Int, minute: Int) {
        _startHour.value = hour; _startMinute.value = minute; recomputeDerived()
    }
    fun onEndTimeChange(hour: Int, minute: Int) {
        _endHour.value = hour; _endMinute.value = minute; recomputeDerived()
    }
    fun onDayToggle(day: java.time.DayOfWeek) {
        val current = _days.value.toMutableSet()
        if (current.contains(day)) current.remove(day) else current.add(day)
        _days.value = current
        recomputeDerived()
    }
    fun onHotspotTargetChange(target: Target) { _hotspotTarget.value = target; recomputeDerived() }
    fun onDataTargetChange(target: Target) { _dataTarget.value = target; recomputeDerived() }
    fun onPasswordChange(password: String) { _hotspotPassword.value = password }
    fun onPriorityChange(priority: Int) { _priority.value = priority }

    fun save() {
        _error.value = null

        if (_name.value.isBlank()) {
            _error.value = "Name is required"
            return
        }
        if (_days.value.isEmpty()) {
            _error.value = "At least one day must be selected"
            return
        }

        val startTime = java.time.LocalTime.of(_startHour.value, _startMinute.value)
        val endTime = java.time.LocalTime.of(_endHour.value, _endMinute.value)
        if (startTime == endTime) {
            _error.value = "Start and end time cannot be equal (zero-length window)"
            return
        }

        val password = if (_hotspotPassword.value.isBlank()) null else _hotspotPassword.value

        val routine = Routine(
            id = _routineId.value ?: 0,
            name = _name.value,
            enabled = true,
            daysOfWeek = _days.value,
            startTime = startTime,
            endTime = endTime,
            hotspotTarget = _hotspotTarget.value,
            mobileDataTarget = _dataTarget.value,
            hotspotPassword = password,
            priority = _priority.value
        )

        viewModelScope.launch {
            if (_isEditing.value) {
                routineRepository.updateRoutine(routine)
            } else {
                routineRepository.insertRoutine(routine)
            }
        }
    }
}