package com.iranjan.hotspotscheduler.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iranjan.hotspotscheduler.automation.diagnostics.AutomationDiagnostics
import com.iranjan.hotspotscheduler.automation.session.AutomationCoordinator
import com.iranjan.hotspotscheduler.automation.session.AutomationSession
import com.iranjan.hotspotscheduler.data.repository.RoutineRepository
import com.iranjan.hotspotscheduler.data.datastore.AutomationPreferences
import com.iranjan.hotspotscheduler.domain.model.DesiredNetworkState
import com.iranjan.hotspotscheduler.domain.model.Target
import com.iranjan.hotspotscheduler.domain.scheduler.BoundaryScheduler
import com.iranjan.hotspotscheduler.domain.scheduler.RoutineEvaluator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DashboardViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val preferences: AutomationPreferences,
    private val automationCoordinator: AutomationCoordinator,
    private val boundaryScheduler: BoundaryScheduler,
    private val diagnostics: AutomationDiagnostics
) : ViewModel() {

    private val _hotspotState = MutableStateFlow<Boolean?>(null)
    private val _dataState = MutableStateFlow<Boolean?>(null)
    private val _masterEnabled = MutableStateFlow(true)
    private val _paused = MutableStateFlow(false)
    private val _suppressed = MutableStateFlow(false)
    private val _capHit = MutableStateFlow(false)
    private val _nextBoundary = MutableStateFlow<RoutineEvaluator.Boundary?>(null)
    private val _nextRoutineName = MutableStateFlow<String?>(null)
    private val _routines = MutableStateFlow<List<com.iranjan.hotspotscheduler.domain.model.Routine>>(emptyList())
    private val _isRunning = MutableStateFlow(false)

    val hotspotState: StateFlow<Boolean?> = _hotspotState
    val dataState: StateFlow<Boolean?> = _dataState
    val masterEnabled: StateFlow<Boolean> = _masterEnabled
    val paused: StateFlow<Boolean> = _paused
    val suppressed: StateFlow<Boolean> = _suppressed
    val capHit: StateFlow<Boolean> = _capHit
    val nextBoundary: StateFlow<RoutineEvaluator.Boundary?> = _nextBoundary
    val nextRoutineName: StateFlow<String?> = _nextRoutineName
    val routines: StateFlow<List<com.iranjan.hotspotscheduler.domain.model.Routine>> = _routines
    val isRunning: StateFlow<Boolean> = _isRunning

    val dashboardState = combine(
        _hotspotState, _dataState, _masterEnabled, _paused, _suppressed, _capHit,
        _nextBoundary, _nextRoutineName, _routines, _isRunning
    ) { hs, ds, me, p, s, ch, nb, nrn, r, ir ->
        DashboardState(hs, ds, me, p, s, ch, nb, nrn, r, ir)
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(), DashboardState(null, null, true, false, false, false, null, null, emptyList(), false))

    init {
        loadInitialData()
        observePreferences()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _routines.value = routineRepository.getEnabledRoutines()
            updateNextBoundary()
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferences.masterEnabled.collect { _masterEnabled.value = it }
        }
        viewModelScope.launch {
            preferences.pausedUntilMs.map { it > System.currentTimeMillis() }.collect { _paused.value = it }
        }
        viewModelScope.launch {
            preferences.suppressedUntilNextWindow.collect { _suppressed.value = it }
        }
        viewModelScope.launch {
            preferences.capHitEpochDay.map { it == RoutineEvaluator.todayEpochDay(System.currentTimeMillis()) }.collect { _capHit.value = it }
        }
    }

    private fun updateNextBoundary() {
        viewModelScope.launch {
            val routines = routineRepository.getEnabledRoutines()
            val now = System.currentTimeMillis()
            val next = RoutineEvaluator.nextBoundary(routines, now)
            _nextBoundary.value = next
            if (next != null) {
                val routine = routineRepository.getRoutine(next.routineId)
                _nextRoutineName.value = routine?.name
            } else {
                _nextRoutineName.value = null
            }
        }
    }

    fun refresh() {
        loadInitialData()
        boundaryScheduler.rescheduleAll()
    }

    fun runWakeTest() {
        _isRunning.value = true
        viewModelScope.launch {
            val result = diagnostics.runWakeTest()
            _isRunning.value = false
        }
    }

    fun runUnlockTest() {
        _isRunning.value = true
        viewModelScope.launch {
            val result = diagnostics.runUnlockTest()
            _isRunning.value = false
        }
    }

    fun runHotspotTest(on: Boolean) {
        _isRunning.value = true
        viewModelScope.launch {
            val result = diagnostics.runHotspotTest(on)
            _isRunning.value = false
        }
    }

    fun runDataTest(on: Boolean) {
        _isRunning.value = true
        viewModelScope.launch {
            val result = diagnostics.runMobileDataTest(on)
            _isRunning.value = false
        }
    }

    fun runFullTest(hotspotOn: Boolean, dataOn: Boolean) {
        _isRunning.value = true
        viewModelScope.launch {
            val result = diagnostics.runFullTransaction(hotspotOn, dataOn)
            _isRunning.value = false
        }
    }

    fun runFullDiagnostic() {
        _isRunning.value = true
        viewModelScope.launch {
            val result = diagnostics.runFullProbe()
            _isRunning.value = false
        }
    }

    fun onBoundaryExecuted() {
        updateNextBoundary()
    }
}

data class DashboardState(
    val hotspotState: Boolean?,
    val dataState: Boolean?,
    val masterEnabled: Boolean,
    val paused: Boolean,
    val suppressed: Boolean,
    val capHit: Boolean,
    val nextBoundary: RoutineEvaluator.Boundary?,
    val nextRoutineName: String?,
    val routines: List<com.iranjan.hotspotscheduler.domain.model.Routine>,
    val isRunning: Boolean
)