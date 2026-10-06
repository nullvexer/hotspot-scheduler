package com.iranjan.hotspotscheduler.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object PasswordCrypto {

    private const val KEY_ALIAS = "hotspot_pw_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128
    private const val VERSION_PREFIX = "1"

    private val key: SecretKey by lazy { getOrCreateKey() }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val cipherText = cipher.doFinal(plain.toByteArray(charset()))
            VERSION_PREFIX + ":" + Base64.encodeToString(cipher.iv + cipherText, Base64.NO_WRAP)
        } catch (t: Throwable) {
            Log.e("PasswordCrypto", "encrypt failed", t)
            ""
        }
    }

    fun decrypt(stored: String?): String? {
        if (stored.isNullOrEmpty()) return null
        val separator = stored.indexOf(':')
        if (separator <= 0) {
            Log.w("PasswordCrypto", "stored value is not in the expected format")
            return null
        }
        val version = stored.substring(0, separator)
        if (version != VERSION_PREFIX) {
            Log.w("PasswordCrypto", "unsupported ciphertext version '$version'")
            return null
        }
        return try {
            val data = Base64.decode(stored.substring(separator + 1), Base64.NO_WRAP)
            if (data.size <= IV_LENGTH) {
                Log.w("PasswordCrypto", "ciphertext too short (${data.size} bytes)")
                return null
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(TAG_LENGTH_BITS, data, 0, IV_LENGTH)
            )
            String(cipher.doFinal(data, IV_LENGTH, data.size - IV_LENGTH), charset())
        } catch (t: Throwable) {
            Log.w("PasswordCrypto", "decrypt failed", t)
            null
        }
    }

    private fun charset() = java.nio.charset.StandardCharsets.UTF_8
}