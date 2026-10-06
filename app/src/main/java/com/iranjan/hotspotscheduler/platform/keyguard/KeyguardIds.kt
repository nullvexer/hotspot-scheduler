package com.iranjan.hotspotscheduler.platform.keyguard

object KeyguardIds {

    const val KEYGUARD_PREFIX = "com.android.keyguard"
    const val SYSTEM_UI_PREFIX = "com.android.systemui"
    const val SAMSUNG_PREFIX = "com.android.samsung"

    fun digitCandidates(digit: Int): List<String> {
        require(digit in 0..9) { "not a digit: $digit" }
        return listOf(
            "$KEYGUARD_PREFIX:id/key$digit",
            "$SYSTEM_UI_PREFIX:id/key$digit",
            "$SYSTEM_UI_PREFIX:id/numpad_key$digit",
            "$SAMSUNG_PREFIX:id/key$digit"
        )
    }

    fun enterCandidates(): List<String> = listOf(
        "$KEYGUARD_PREFIX:id/key_enter",
        "$SYSTEM_UI_PREFIX:id/key_enter",
        "$KEYGUARD_PREFIX:id/key_enter_button",
        "$SYSTEM_UI_PREFIX:id/enter",
        "$SAMSUNG_PREFIX:id/key_enter"
    )

    fun deleteCandidates(): List<String> = listOf(
        "$KEYGUARD_PREFIX:id/delete_button",
        "$SYSTEM_UI_PREFIX:id/delete_button",
        "$KEYGUARD_PREFIX:id/iv_delete",
        "$SYSTEM_UI_PREFIX:id/iv_delete"
    )

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

    fun swipeFraction(): Triple<Float, Float, Float> = Triple(0.5f, 0.85f, 0.15f)
}