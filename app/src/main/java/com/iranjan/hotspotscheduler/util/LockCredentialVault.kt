package com.iranjan.hotspotscheduler.util

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.iranjan.hotspotscheduler.accessibility.AttemptLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage for the screen-lock credential used by unattended unlock.
 *
 * Two decisions that matter here:
 *
 * 1. **Device-protected (DE) storage.** Normal app storage is credential-encrypted and is
 *    unavailable until the device has been unlocked once since boot. A 5am routine that runs
 *    before anyone has touched the phone could not read its own credential. DE storage *is*
 *    available that early, so the vault lives there.
 *
 * 2. **Encrypted at rest.** The credential is sealed with a Keystore AES-GCM key, the same
 *    primitive used for hotspot passwords. A DE file is readable by anything running as this UID
 *    (including a forensic dump of /data), so plaintext on disk is not acceptable.
 *
 * The stored value is versioned; unknown versions and decryption failures both read back as
 * "no credential configured" rather than leaking ciphertext.
 */
@Singleton
class LockCredentialVault @Inject constructor(@ApplicationContext private val context: Context) {

    private val store: DataStore<Preferences> by lazy {
        val deContext = context.createDeviceProtectedStorageContext()
        // ReplaceFileCorruptionHandler is a SAM interface, so the parameter type must be explicit.
        val handler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler<Preferences> {
                throwable: java.io.IOException ->
            Log.e(TAG, "lock credential store corrupted; starting empty", throwable)
            AttemptLog.add("lock credential store was corrupted and has been reset")
            emptyPreferences()
        }
        PreferenceDataStoreFactory.create(
            // A distinct file from the main prefs: different directory, different lifetime.
            produceFile = { deContext.preferencesDataStoreFile(FILE_NAME) },
            corruptionHandler = handler
        )
    }

    private object Keys {
        val ENCRYPTED_PIN = stringPreferencesKey("encrypted_lock_pin")
        val ENABLED = booleanPreferencesKey("auto_unlock_enabled")
        val FAILED_ATTEMPT_AT = stringPreferencesKey("last_failed_attempt_at")
    }

    val enabled: Flow<Boolean> = safeData().map { it[Keys.ENABLED] ?: false }

    val configured: Flow<Boolean> = safeData().map { !it[Keys.ENCRYPTED_PIN].isNullOrEmpty() }

    suspend fun isEnabled(): Boolean = enabled.first()

    suspend fun isConfigured(): Boolean = configured.first()

    /**
     * The plaintext credential, or null when nothing usable is stored. Never returns ciphertext.
     */
    suspend fun credential(): String? {
        val stored = safeData().first()[Keys.ENCRYPTED_PIN] ?: return null
        return PasswordCrypto.decrypt(stored)
    }

    /** Returns false when the credential could not be persisted; the caller must report it. */
    suspend fun store(pin: String): Boolean {
        if (pin.isEmpty()) {
            clear()
            return true
        }
        val sealed = PasswordCrypto.encrypt(pin)
        if (sealed.isEmpty()) {
            AttemptLog.add("could not encrypt the lock credential; it was not stored")
            return false
        }
        return try {
            store.edit {
                it[Keys.ENCRYPTED_PIN] = sealed
                it[Keys.ENABLED] = true
            }
            AttemptLog.add("lock credential stored (${pin.length} characters, encrypted)")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "failed to persist the lock credential", t)
            AttemptLog.add("failed to store the lock credential: ${t.message}")
            false
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        store.edit { it[Keys.ENABLED] = enabled }
    }

    /** Records a failed attempt so the engine can refuse to try again and avoid a lockout. */
    suspend fun markFailedAttempt(atMillis: Long) {
        runCatching { store.edit { it[Keys.FAILED_ATTEMPT_AT] = atMillis.toString() } }
    }

    suspend fun lastFailedAttemptAt(): Long =
        safeData().first()[Keys.FAILED_ATTEMPT_AT]?.toLongOrNull() ?: 0L

    /** Wipes the credential. Used when the user turns the feature off. */
    suspend fun clear() {
        runCatching {
            store.edit {
                it.remove(Keys.ENCRYPTED_PIN)
                it[Keys.ENABLED] = false
                it.remove(Keys.FAILED_ATTEMPT_AT)
            }
            AttemptLog.add("lock credential cleared")
        }
    }

    private fun safeData(): Flow<Preferences> = store.data.catch { throwable ->
        // A read failure must not take down the automation loop every two minutes.
        if (throwable is IOException) {
            Log.w(TAG, "lock credential read failed", throwable)
            AttemptLog.add("lock credential read failed: ${throwable.message}")
            emit(emptyPreferences())
        } else {
            throw throwable
        }
    }

    companion object {
        private const val TAG = "HSVault"
        private const val FILE_NAME = "lock_credential"
    }
}