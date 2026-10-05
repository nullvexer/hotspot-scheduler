package com.iranjan.hotspotscheduler.util

/**
 * WPA2-PSK passphrase rules.
 *
 * The editor, the toggle controller and the tests all validate against this single definition.
 * They previously disagreed: the editor only rejected passwords shorter than 8 characters, so a
 * 100-character or non-ASCII password was accepted, encrypted, stored, and then silently ignored
 * when the app tried to use it.
 *
 * WPA2 requires 8..63 characters of printable ASCII.
 */
object PassphraseRules {

    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 63

    fun isValid(password: String?): Boolean {
        if (password == null) return false
        if (password.length < MIN_LENGTH || password.length > MAX_LENGTH) return false
        return password.all { it.code in 32..126 }
    }

    /** Human-readable reason a password was rejected, for inline field errors. */
    fun rejectionReason(password: String?): String? = when {
        password == null || password.isEmpty() -> null
        password.length < MIN_LENGTH -> "too short (min $MIN_LENGTH characters)"
        password.length > MAX_LENGTH -> "too long (max $MAX_LENGTH characters)"
        password.any { it.code < 32 || it.code > 126 } -> "must be printable ASCII letters, digits or symbols"
        else -> null
    }
}