package com.iranjan.hotspotscheduler.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.iranjan.hotspotscheduler.accessibility.AttemptLog
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM encryption for hotspot passphrases, backed by a hardware/AndroidKeyStore key.
 *
 * Format: `"<version>:<base64(iv || ciphertext||tag)>"`, currently version [VERSION_PREFIX].
 *
 * Contract that matters for safety: [decrypt] returns `null` for anything it cannot actually
 * decrypt. It must never hand back the stored blob, because callers feed the result straight
 * into `cmd wifi start-softap ... wpa2 <passphrase>` and a Base64 blob is itself a valid
 * 8..63-char printable "passphrase" — that would silently publish a hotspot nobody can join.
 */
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

    /** Returns "" for a blank input, and "" when encryption is impossible. Never throws. */
    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            VERSION_PREFIX + ":" + Base64.encodeToString(cipher.iv + cipherText, Base64.NO_WRAP)
        } catch (t: Throwable) {
            // Returning "" here would persist "no password". Loudly log it instead so the
            // diagnostics log explains why the password appears to have vanished.
            Log.e(TAG, "encrypt failed", t)
            AttemptLog.add("password encrypt failed: ${t.message}")
            ""
        }
    }

    /** Returns the plaintext, or `null` when there is nothing stored or it cannot be read. */
    fun decrypt(stored: String?): String? {
        if (stored.isNullOrEmpty()) return null
        val separator = stored.indexOf(':')
        if (separator <= 0) {
            Log.w(TAG, "stored value is not in the expected format; ignoring it")
            return null
        }
        val version = stored.substring(0, separator)
        if (version != VERSION_PREFIX) {
            Log.w(TAG, "unsupported ciphertext version '$version'; ignoring it")
            return null
        }
        return try {
            val data = Base64.decode(stored.substring(separator + 1), Base64.NO_WRAP)
            if (data.size <= IV_LENGTH) {
                Log.w(TAG, "ciphertext too short (${data.size} bytes); ignoring it")
                return null
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(TAG_LENGTH_BITS, data, 0, IV_LENGTH)
            )
            String(cipher.doFinal(data, IV_LENGTH, data.size - IV_LENGTH), Charsets.UTF_8)
        } catch (t: Throwable) {
            // Wrong key, tampered data, truncated blob: report nothing rather than leaking the
            // ciphertext into a place where it would be used as a password.
            Log.w(TAG, "decrypt failed; treating password as unavailable", t)
            AttemptLog.add("password decrypt failed; stored password ignored: ${t.message}")
            null
        }
    }

    private const val TAG = "PasswordCrypto"
}