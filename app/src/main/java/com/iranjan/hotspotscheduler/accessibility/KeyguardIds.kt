package com.iranjan.hotspotscheduler.accessibility

/**
 * Resource-id candidates for the lock screen's PIN keypad.
 *
 * Kept as pure data with no Android dependency so it can be unit tested, because the exact ids
 * differ between ROMs and this is the part that silently breaks on a new One UI build.
 *
 * Known variation, all observed in the wild on Android 13:
 *  - AOSP / One UI usually expose `com.android.keyguard:id/key1`
 *  - some Android 13 builds (and most custom ROMs) expose `com.android.systemui:id/key1`
 *  - Samsung additionally uses `com.android.systemui:id/numpad_key{N}` in some builds
 *
 * The digits are deliberately NOT matched by visible text: AOSP attaches
 * `ObscureSpeechDelegate` to the num-pad keys so the spoken digit is suppressed, which is exactly
 * why the id is the only reliable handle.
 */
object KeyguardIds {

    const val KEYGUARD_PREFIX = "com.android.keyguard"
    const val SYSTEM_UI_PREFIX = "com.android.systemui"
    const val SAMSUNG_PREFIX = "com.android.samsung"

    /** Ordered by likelihood; first hit wins. */
    fun digitCandidates(digit: Int): List<String> {
        require(digit in 0..9) { "not a digit: $digit" }
        return listOf(
            "$KEYGUARD_PREFIX:id/key$digit",
            "$SYSTEM_UI_PREFIX:id/key$digit",
            "$SYSTEM_UI_PREFIX:id/numpad_key$digit",
            "$SAMSUNG_PREFIX:id/key$digit"
        )
    }

    /**
     * Submit/confirm button. AOSP may verify automatically after the last digit, so this is only
     * needed on devices that do not.
     */
    fun enterCandidates(): List<String> = listOf(
        "$KEYGUARD_PREFIX:id/key_enter",
        "$SYSTEM_UI_PREFIX:id/key_enter",
        "$KEYGUARD_PREFIX:id/key_enter_button",
        "$SYSTEM_UI_PREFIX:id/enter",
        "$SAMSUNG_PREFIX:id/key_enter"
    )

    /** Backspace, used to correct a mistyped digit before submitting. */
    fun deleteCandidates(): List<String> = listOf(
        "$KEYGUARD_PREFIX:id/delete_button",
        "$SYSTEM_UI_PREFIX:id/delete_button",
        "$KEYGUARD_PREFIX:id/iv_delete",
        "$SYSTEM_UI_PREFIX:id/iv_delete"
    )

    /**
     * The PIN/pattern/password bouncer is up. Any one of these implies the keypad is reachable.
     * Used to avoid a pointless swipe when the keypad is already showing.
     */
    fun bouncerMarkers(): List<String> = listOf(
        "$KEYGUARD_PREFIX:id/key1",
        "$SYSTEM_UI_PREFIX:id/key1",
        "$SYSTEM_UI_PREFIX:id/numpad_key1",
        "$KEYGUARD_PREFIX:id/pattern_view",
        "$SYSTEM_UI_PREFIX:id/pattern_view"
    )

    fun confirmCandidates(): List<String> = listOf(
        "$KEYGUARD_PREFIX:id/key_enter",
        "$SYSTEM_UI_PREFIX:id/key_confirm",
        "$SYSTEM_UI_PREFIX:id/confirm_button",
        "$SAMSUNG_PREFIX:id/key_confirm"
    )

    /**
     * Rows to try when a swipe does not reveal the keypad (some ROMs need a tap first).
     *
     * Note there is deliberately NO digit position table here any more. Key coordinates are derived
     * from the detected keypad by KeypadGeometryValidator; a hard-coded fraction is a guess, and a
     * guessed key on a lock screen is a failed credential attempt.
     */
    fun swipeFraction(): Triple<Float, Float, Float> = Triple(0.5f, 0.85f, 0.15f)
}
