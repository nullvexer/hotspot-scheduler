package com.iranjan.hotspotscheduler.domain.automation

import java.time.Duration

data class TimeoutPolicy(
    val screenWake: Duration = Duration.ofSeconds(8),
    val keypadReveal: Duration = Duration.ofSeconds(6),
    val keypadDetection: Duration = Duration.ofSeconds(3),
    val digitAction: Duration = Duration.ofMillis(500),
    val unlockVerification: Duration = Duration.ofSeconds(3),
    val settingsLaunch: Duration = Duration.ofSeconds(10),
    val navigation: Duration = Duration.ofSeconds(5),
    val switchTransition: Duration = Duration.ofSeconds(3),
    val finalVerification: Duration = Duration.ofSeconds(5),
    val finalLock: Duration = Duration.ofSeconds(2),
    val totalTransaction: Duration = Duration.ofSeconds(90)
) {
    companion object {
        val default = TimeoutPolicy()
        val samsungA22 = TimeoutPolicy(
            screenWake = Duration.ofSeconds(10),
            keypadReveal = Duration.ofSeconds(8),
            settingsLaunch = Duration.ofSeconds(12),
            navigation = Duration.ofSeconds(6)
        )
    }
}