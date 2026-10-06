package com.iranjan.hotspotscheduler.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iranjan.hotspotscheduler.automation.diagnostics.AutomationDiagnostics
import com.iranjan.hotspotscheduler.automation.diagnostics.AutomationDiagnostics.FullDiagnosticReport
import com.iranjan.hotspotscheduler.automation.diagnostics.AutomationDiagnostics.LiveTestResult
import com.iranjan.hotspotscheduler.domain.model.AutomationResult
import com.iranjan.hotspotscheduler.platform.permissions.CapabilityProbe
import com.iranjan.hotspotscheduler.platform.permissions.CapabilityProbe.Report
import com.iranjan.hotspotscheduler.util.AttemptLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosticsViewModel @Inject constructor(
    private val diagnostics: AutomationDiagnostics,
    private val capabilityProbe: CapabilityProbe
) : ViewModel() {

    private val _fullReport = MutableStateFlow<FullDiagnosticReport?>(null)
    private val _liveTestResult = MutableStateFlow<LiveTestResult?>(null)
    private val _capabilityReport = MutableStateFlow<Report?>(null)
    private val _isRunning = MutableStateFlow(false)
    private val _logs = MutableStateFlow<List<String>>(emptyList())

    val fullReport: StateFlow<FullDiagnosticReport?> = _fullReport
    val liveTestResult: StateFlow<LiveTestResult?> = _liveTestResult
    val capabilityReport: StateFlow<Report?> = _capabilityReport
    val isRunning: StateFlow<Boolean> = _isRunning
    val logs: StateFlow<List<String>> = _logs

    init {
        observeLogs()
    }

    private fun observeLogs() {
        viewModelScope.launch {
            while (true) {
                _logs.value = AttemptLog.getAll()
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    fun runFullProbe() {
        _isRunning.value = true
        viewModelScope.launch {
            val report = diagnostics.runFullProbe()
            _fullReport.value = report
            _isRunning.value = false
        }
    }

    fun runCapabilityProbe() {
        _isRunning.value = true
        viewModelScope.launch {
            val report = capabilityProbe.runProbe()
            _capabilityReport.value = report
            _isRunning.value = false
        }
    }

    fun runWakeTest() {
        _isRunning.value = true
        viewModelScope.launch {
            val result = diagnostics.runWakeTest()
            _liveTestResult.value = result
            _isRunning.value = false
        }
    }

    fun runUnlockTest() {
        _isRunning.value = true
        viewModelScope.launch {
            val result = diagnostics.runUnlockTest()
            _liveTestResult.value = result
            _isRunning.value = false
        }
    }

    fun runHotspotTest(on: Boolean) {
        _isRunning.value = true
        viewModelScope.launch {
            val result = diagnostics.runHotspotTest(on)
            _liveTestResult.value = result
            _isRunning.value = false
        }
    }

    fun runDataTest(on: Boolean) {
        _isRunning.value = true
        viewModelScope.launch {
            val result = diagnostics.runMobileDataTest(on)
            _liveTestResult.value = result
            _isRunning.value = false
        }
    }

    fun runFullTransaction(hotspotOn: Boolean, dataOn: Boolean) {
        _isRunning.value = true
        viewModelScope.launch {
            val result = diagnostics.runFullTransaction(hotspotOn, dataOn)
            _liveTestResult.value = result
            _isRunning.value = false
        }
    }

    fun clearLogs() {
        AttemptLog.clear()
    }
}