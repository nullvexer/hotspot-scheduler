package com.iranjan.hotspotscheduler.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationPreferencesImpl @Inject constructor(private val dataStore: DataStore<Preferences>) : AutomationPreferences {

    private object Keys {
        val MASTER_ENABLED = booleanPreferencesKey("master_enabled")
        val PAUSED_UNTIL_MS = longPreferencesKey("paused_until_ms")
        val SUPPRESSED = booleanPreferencesKey("suppressed_until_next_window")
        val CAP_HIT_DAY = longPreferencesKey("cap_hit_epoch_day")
        val LAST_BOUNDARY = stringPreferencesKey("last_applied_boundary")
        val HOTSPOT_KNOWN = intPreferencesKey("hotspot_known_state")
        val ACC_ALERT_MS = longPreferencesKey("last_acc_alert_ms")
        val CALIB_TYPE = stringPreferencesKey("calib_type")
        val CALIB_VALUE = stringPreferencesKey("calib_value")
        val ALARMS_DIRTY = booleanPreferencesKey("alarms_dirty")
    }

    override val masterEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.MASTER_ENABLED] ?: true }
    override val pausedUntilMs: Flow<Long> = dataStore.data.map { it[Keys.PAUSED_UNTIL_MS] ?: 0L }
    override val suppressedUntilNextWindow: Flow<Boolean> = dataStore.data.map { it[Keys.SUPPRESSED] ?: false }
    override val capHitEpochDay: Flow<Long> = dataStore.data.map { it[Keys.CAP_HIT_DAY] ?: 0L }
    override val lastAppliedBoundary: Flow<String> = dataStore.data.map { it[Keys.LAST_BOUNDARY] ?: "" }
    override val lastKnownHotspotOn: Flow<Boolean?> =
        dataStore.data.map { v -> when (v[Keys.HOTSPOT_KNOWN] ?: -1) { 0 -> false; 1 -> true; else -> null } }
    override val lastAccAlertMs: Flow<Long> = dataStore.data.map { it[Keys.ACC_ALERT_MS] ?: 0L }

    override suspend fun setMasterEnabled(value: Boolean) = dataStore.edit { it[Keys.MASTER_ENABLED] = value }
    override suspend fun setPausedUntilMs(value: Long) = dataStore.edit { it[Keys.PAUSED_UNTIL_MS] = value }
    override suspend fun setSuppressedUntilNextWindow(value: Boolean) = dataStore.edit { it[Keys.SUPPRESSED] = value }
    override suspend fun setCapHitEpochDay(value: Long) = dataStore.edit { it[Keys.CAP_HIT_DAY] = value }
    override suspend fun setLastAppliedBoundary(value: String) = dataStore.edit { it[Keys.LAST_BOUNDARY] = value }
    override suspend fun setLastKnownHotspotOn(value: Boolean?) =
        dataStore.edit { it[Keys.HOTSPOT_KNOWN] = when (value) { true -> 1; false -> 0; null -> -1 } }
    override suspend fun setLastAccAlertMs(value: Long) = dataStore.edit { it[Keys.ACC_ALERT_MS] = value }

    override suspend fun apConfig(): ApConfig = ApConfig(null, null, false)
    override suspend fun setApConfig(ssid: String?, passphrase: String?, open: Boolean) {}

    override suspend fun setAlarmsDirty(value: Boolean) = dataStore.edit { it[Keys.ALARMS_DIRTY] = value }
    override suspend fun alarmsDirty(): Boolean = dataStore.data.map { it[Keys.ALARMS_DIRTY] ?: true }.first()

    override suspend fun calibration(): CalibrationSignature? {
        val type = dataStore.data.map { it[Keys.CALIB_TYPE] ?: "" }.first()
        val value = dataStore.data.map { it[Keys.CALIB_VALUE] ?: "" }.first()
        if (type.isBlank() || value.isBlank()) return null
        return CalibrationSignature(type, value)
    }

    override suspend fun setCalibration(signature: CalibrationSignature) =
        dataStore.edit {
            it[Keys.CALIB_TYPE] = signature.type
            it[Keys.CALIB_VALUE] = signature.value
        }

    override suspend fun clearCalibration() =
        dataStore.edit {
            it[Keys.CALIB_TYPE] = ""
            it[Keys.CALIB_VALUE] = ""
        }
}