package com.iranjan.hotspotscheduler.data.encrypted

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.iranjan.hotspotscheduler.util.PasswordCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialVaultImpl @Inject constructor(@androidx.hilt.android.qualifiers.ApplicationContext private val context: Context) : CredentialVault {

    private val store: DataStore<Preferences> by lazy {
        val deContext = context.createDeviceProtectedStorageContext()
        PreferenceDataStoreFactory.create(
            produceFile = { deContext.preferencesDataStoreFile("credential_vault") }
        )
    }

    private object Keys {
        val ENCRYPTED_PIN = stringPreferencesKey("encrypted_lock_pin")
        val ENABLED = booleanPreferencesKey("auto_unlock_enabled")
        val FAILED_ATTEMPT_AT = longPreferencesKey("last_failed_attempt_at")
    }

    override val enabled: Flow<Boolean> = store.data.map { it[Keys.ENABLED] ?: false }
    override val configured: Flow<Boolean> = store.data.map { !it[Keys.ENCRYPTED_PIN].isNullOrEmpty() }

    override suspend fun isEnabled(): Boolean = enabled.first()
    override suspend fun isConfigured(): Boolean = configured.first()

    override suspend fun credential(): String? {
        val stored = store.data.first()[Keys.ENCRYPTED_PIN] ?: return null
        return PasswordCrypto.decrypt(stored)
    }

    override suspend fun store(pin: String): Boolean {
        if (pin.isEmpty()) {
            clear()
            return true
        }
        val sealed = PasswordCrypto.encrypt(pin)
        if (sealed.isEmpty()) return false
        return try {
            store.edit {
                it[Keys.ENCRYPTED_PIN] = sealed
                it[Keys.ENABLED] = true
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun setEnabled(enabled: Boolean) = store.edit { it[Keys.ENABLED] = enabled }

    override suspend fun markFailedAttempt(atMillis: Long) =
        store.edit { it[Keys.FAILED_ATTEMPT_AT] = atMillis }

    override suspend fun lastFailedAttemptAt(): Long =
        store.data.first()[Keys.FAILED_ATTEMPT_AT] ?: 0L

    override suspend fun clear() =
        store.edit {
            it.remove(Keys.ENCRYPTED_PIN)
            it[Keys.ENABLED] = false
            it.remove(Keys.FAILED_ATTEMPT_AT)
        }
}