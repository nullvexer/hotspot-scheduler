package com.iranjan.hotspotscheduler.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iranjan.hotspotscheduler.automation.diagnostics.AutomationDiagnostics
import com.iranjan.hotspotscheduler.data.datastore.AutomationPreferences
import com.iranjan.hotspotscheduler.data.encrypted.CredentialVault
import com.iranjan.hotspotscheduler.platform.permissions.CapabilityProbe
import com.iranjan.hotspotscheduler.util.AttemptLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetupViewModel @Inject constructor(
    private val preferences: AutomationPreferences,
    private val credentialVault: CredentialVault,
    private val capabilityProbe: CapabilityProbe,
    private val diagnostics: AutomationDiagnostics
) : ViewModel() {

    private val _pin = MutableStateFlow("")
    private val _pinConfirm = MutableStateFlow("")
    private val _pinError = MutableStateFlow<String?>(null)
    private val _pinConfigured = MutableStateFlow(false)
    private val _autoUnlockEnabled = MutableStateFlow(false)

    val pin: StateFlow<String> = _pin
    val pinConfirm: StateFlow<String> = _pinConfirm
    val pinError: StateFlow<String?> = _pinError
    val pinConfigured: StateFlow<Boolean> = _pinConfigured
    val autoUnlockEnabled: StateFlow<Boolean> = _autoUnlockEnabled

    init {
        loadPinStatus()
    }

    private fun loadPinStatus() {
        viewModelScope.launch {
            _pinConfigured.value = credentialVault.isConfigured()
            _autoUnlockEnabled.value = credentialVault.isEnabled()
        }
    }

    fun onPinChange(pin: String) { _pin.value = pin; _pinError.value = null }
    fun onPinConfirmChange(pin: String) { _pinConfirm.value = pin; _pinError.value = null }

    fun savePin() {
        _pinError.value = null
        if (_pin.value.isBlank()) {
            _pinError.value = "PIN cannot be empty"
            return
        }
        if (_pin.value != _pinConfirm.value) {
            _pinError.value = "PINs do not match"
            return
        }
        if (_pin.value.length < 4 || _pin.value.length > 16) {
            _pinError.value = "PIN must be 4-16 digits"
            return
        }
        if (!_pin.value.all { it.isDigit() }) {
            _pinError.value = "PIN must contain only digits"
            return
        }

        viewModelScope.launch {
            val success = credentialVault.store(_pin.value)
            if (success) {
                _pinConfigured.value = true
                _pin.value = ""
                _pinConfirm.value = ""
                AttemptLog.add("PIN saved successfully")
            } else {
                _pinError.value = "Failed to save PIN"
            }
        }
    }

    fun removePin() {
        viewModelScope.launch {
            credentialVault.clear()
            _pinConfigured.value = false
            _autoUnlockEnabled.value = false
            AttemptLog.add("PIN removed")
        }
    }

    fun setAutoUnlockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            credentialVault.setEnabled(enabled)
            _autoUnlockEnabled.value = enabled
        }
    }

    fun runCapabilityProbe() {
        viewModelScope.launch {
            capabilityProbe.runProbe()
        }
    }

    fun openAccessibilitySettings() {
        android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            .also { androidx.core.app.ActivityCompat.startActivityForResult(null, it, 0, null) }
    }

    fun openBatteryOptimizationSettings() {
        android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            .also { androidx.core.app.ActivityCompat.startActivityForResult(null, it, 0, null) }
    }

    fun openExactAlarmSettings() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                .also { androidx.core.app.ActivityCompat.startActivityForResult(null, it, 0, null) }
        }
    }

    fun openOverlaySettings() {
        android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            .also { androidx.core.app.ActivityCompat.startActivityForResult(null, it, 0, null) }
    }

    fun openNotificationSettings() {
        android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, "com.iranjan.hotspotscheduler")
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            .also { androidx.core.app.ActivityCompat.startActivityForResult(null, it, 0, null) }
    }

    fun openSecuritySettings() {
        android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            .also { androidx.core.app.ActivityCompat.startActivityForResult(null, it, 0, null) }
    }
}