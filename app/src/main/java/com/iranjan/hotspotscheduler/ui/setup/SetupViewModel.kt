package com.iranjan.hotspotscheduler.ui.setup

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iranjan.hotspotscheduler.accessibility.AttemptLog
import com.iranjan.hotspotscheduler.accessibility.HotspotController
import com.iranjan.hotspotscheduler.accessibility.KeyguardAutomator
import com.iranjan.hotspotscheduler.accessibility.ScreenControl
import com.iranjan.hotspotscheduler.data.repo.RoutineRepository
import com.iranjan.hotspotscheduler.util.AccessibilityUtils
import com.iranjan.hotspotscheduler.util.LockCredentialVault
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupState(
    val accessibility: Boolean = false,
    val usageAccess: Boolean = false,
    val notifications: Boolean = false,
    val exactAlarms: Boolean = false,
    val battery: Boolean = false,
    val overlay: Boolean = false,
    val secureLock: Boolean = false,
    val autoUnlockEnabled: Boolean = false,
    val pinStored: Boolean = false
) {
    /**
     * True when a scheduled toggle can run with nobody touching the phone: the accessibility
     * service is on, and either there is no credential or auto-unlock is enabled.
     */
    val unattendedReady: Boolean
        get() = accessibility && (!secureLock || autoUnlockEnabled)
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controller: HotspotController,
    private val repo: RoutineRepository,
    private val screen: ScreenControl,
    private val vault: LockCredentialVault
) : ViewModel() {

    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state

    private val _testRunning = MutableStateFlow(false)
    val testRunning: StateFlow<Boolean> = _testRunning

    private val _screenOffAfter = MutableStateFlow(true)
    val screenOffAfter: StateFlow<Boolean> = _screenOffAfter

    init {
        refresh()
    }

    /**
     * Re-reads every permission/state flag. Launched rather than synchronous because reading the
     * stored PIN flags touches disk, and this runs on every ON_RESUME.
     */
    fun refresh() = viewModelScope.launch {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val state = SetupState(
            accessibility = AccessibilityUtils.isServiceEnabled(context),
            usageAccess = hasUsageAccess(),
            notifications = notificationGranted(),
            exactAlarms = Build.VERSION.SDK_INT < 31 || alarmManager?.canScheduleExactAlarms() == true,
            battery = isIgnoringBattery(),
            overlay = Settings.canDrawOverlays(context),
            secureLock = screen.secureLockPresent(),
            autoUnlockEnabled = vault.isEnabled(),
            pinStored = vault.isConfigured()
        )
        _state.value = state
        AttemptLog.add(
            if (state.unattendedReady) {
                "setup: ready for unattended operation; ${screen.capabilities()}"
            } else {
                val missing = buildList {
                    if (!state.accessibility) add("accessibility service")
                    if (state.secureLock && !state.autoUnlockEnabled) add("a stored lock-screen PIN")
                }
                "setup: not unattended yet, missing: ${missing.joinToString()}"
            }
        )
    }

    fun smartLockIntent(): Intent = screen.smartLockIntent()

    /** Stores the lock-screen PIN for unattended unlock. Digits only. */
    fun savePin(pin: String, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = vault.store(pin)
        if (ok) {
            AttemptLog.add("lock-screen PIN saved for unattended unlock")
        }
        refresh()
        onDone(ok)
    }

    fun clearPin() = viewModelScope.launch {
        vault.clear()
        refresh()
    }

    fun setAutoUnlockEnabled(enabled: Boolean) = viewModelScope.launch {
        vault.setEnabled(enabled)
        AttemptLog.add("auto unlock enabled = $enabled")
        refresh()
    }

    /**
     * Reports which keypad buttons the accessibility service can actually see right now. This is
     * how a One UI change gets accommodated: run it on the lock screen, read the log, and adjust
     * [KeyguardIds] if the ids differ. Nothing is entered.
     */
    fun diagnoseKeypad() = viewModelScope.launch {
        val automator = com.iranjan.hotspotscheduler.accessibility.AccessibilityServiceHolder.service?.automator
        if (automator == null) {
            AttemptLog.add("keypad diagnose: the accessibility service is not connected")
            return@launch
        }
        screen.wakeScreen()
        val found = automator.diagnoseKeypad()
        AttemptLog.add("keypad diagnose: $found")
    }

    fun setScreenOffAfter(enabled: Boolean) {
        _screenOffAfter.value = enabled
        com.iranjan.hotspotscheduler.accessibility.AccessibilityHotspotControllerImpl
            .turnScreenOffAfterToggle = enabled
        AttemptLog.add("screen-off after toggle = $enabled")
    }

    fun testHotspot(on: Boolean) = viewModelScope.launch {
        _testRunning.value = true
        try {
            val password = repo.enabledRoutines()
                .firstOrNull { !it.hotspotPassword.isNullOrBlank() }?.hotspotPassword
            if (password != null) {
                AttemptLog.add("live test: using the password from an enabled routine")
            }
            controller.setHotspotState(on, password)
        } finally {
            _testRunning.value = false
        }
    }

    fun testMobileData(on: Boolean) = viewModelScope.launch {
        _testRunning.value = true
        try {
            controller.setMobileData(on)
        } finally {
            _testRunning.value = false
        }
    }

    fun shareText(): String = AttemptLog.snapshot().joinToString("\n")

    private fun hasUsageAccess(): Boolean {
        return try {
            val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            ) == AppOpsManager.MODE_ALLOWED
        } catch (t: Throwable) {
            false
        }
    }

    private fun notificationGranted(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun isIgnoringBattery(): Boolean = try {
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
    } catch (t: Throwable) {
        false
    }

    companion object {
        /** Android's own keyguard UI only accepts a short numeric PIN. */
        const val PIN_MIN = 4
        const val PIN_MAX = 10

        fun isPlausiblePin(pin: String): Boolean =
            pin.length in PIN_MIN..PIN_MAX && pin.all { it.isDigit() }
    }
}