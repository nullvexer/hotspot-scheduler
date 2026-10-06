package com.iranjan.hotspotscheduler.data.datastore

import kotlinx.coroutines.flow.Flow

interface AutomationPreferences {
    val masterEnabled: Flow<Boolean>
    val pausedUntilMs: Flow<Long>
    val suppressedUntilNextWindow: Flow<Boolean>
    val capHitEpochDay: Flow<Long>
    val lastAppliedBoundary: Flow<String>
    val lastKnownHotspotOn: Flow<Boolean?>
    val lastAccAlertMs: Flow<Long>

    suspend fun setMasterEnabled(value: Boolean)
    suspend fun setPausedUntilMs(value: Long)
    suspend fun setSuppressedUntilNextWindow(value: Boolean)
    suspend fun setCapHitEpochDay(value: Long)
    suspend fun setLastAppliedBoundary(value: String)
    suspend fun setLastKnownHotspotOn(value: Boolean?)
    suspend fun setLastAccAlertMs(value: Long)

    data class ApConfig(val ssid: String?, val passphrase: String?, val open: Boolean)
    suspend fun apConfig(): ApConfig
    suspend fun setApConfig(ssid: String?, passphrase: String?, open: Boolean)

    suspend fun setAlarmsDirty(value: Boolean)
    suspend fun alarmsDirty(): Boolean

    suspend fun calibration(): CalibrationSignature?
    suspend fun setCalibration(signature: CalibrationSignature)
    suspend fun clearCalibration()
}

data class CalibrationSignature(
    val type: String,
    val value: String
) {
    companion object {
        const val TYPE_RID = "RID"
        const val TYPE_CLASS_SIG = "CLASS_SIG"
    }
}