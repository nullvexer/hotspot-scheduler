package com.iranjan.hotspotscheduler.data.encrypted

import kotlinx.coroutines.flow.Flow

interface CredentialVault {
    val enabled: Flow<Boolean>
    val configured: Flow<Boolean>

    suspend fun isEnabled(): Boolean
    suspend fun isConfigured(): Boolean
    suspend fun credential(): String?
    suspend fun store(pin: String): Boolean
    suspend fun setEnabled(enabled: Boolean)
    suspend fun markFailedAttempt(atMillis: Long)
    suspend fun lastFailedAttemptAt(): Long
    suspend fun clear()
}

interface PasswordVault {
    suspend fun encryptAndStore(password: String): Boolean
    suspend fun decryptAndGet(): String?
    suspend fun clear()
}