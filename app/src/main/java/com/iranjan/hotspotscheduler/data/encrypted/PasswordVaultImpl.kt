package com.iranjan.hotspotscheduler.data.encrypted

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.iranjan.hotspotscheduler.util.PasswordCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PasswordVaultImpl @Inject constructor(@androidx.hilt.android.qualifiers.ApplicationContext private val context: Context) : PasswordVault {

    private val store: DataStore<Preferences> by lazy {
        val deContext = context.createDeviceProtectedStorageContext()
        PreferenceDataStoreFactory.create(
            produceFile = { deContext.preferencesDataStoreFile("password_vault") }
        )
    }

    private val KEY_PASSWORD = stringPreferencesKey("encrypted_hotspot_password")

    override suspend fun encryptAndStore(password: String): Boolean {
        val sealed = PasswordCrypto.encrypt(password)
        if (sealed.isEmpty()) return false
        return try {
            store.edit { it[KEY_PASSWORD] = sealed }
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun decryptAndGet(): String? {
        val stored = store.data.first()[KEY_PASSWORD] ?: return null
        return PasswordCrypto.decrypt(stored)
    }

    override suspend fun clear() = store.edit { it.remove(KEY_PASSWORD) }
}