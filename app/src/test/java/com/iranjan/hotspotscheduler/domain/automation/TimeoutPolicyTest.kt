package com.iranjan.hotspotscheduler.domain.automation

import org.junit.Assert.*
import org.junit.Test
import java.time.Duration

class TimeoutPolicyTest {

    @Test
    fun `default policy has reasonable values`() {
        val policy = TimeoutPolicy.default
        assertEquals(Duration.ofSeconds(8), policy.screenWake)
        assertEquals(Duration.ofSeconds(90), policy.totalTransaction)
    }

    @Test
    fun `samsungA22 policy has longer timeouts`() {
        val policy = TimeoutPolicy.samsungA22
        assertEquals(Duration.ofSeconds(10), policy.screenWake)
        assertEquals(Duration.ofSeconds(8), policy.keypadReveal)
        assertEquals(Duration.ofSeconds(12), policy.settingsLaunch)
        assertEquals(Duration.ofSeconds(6), policy.navigation)
        assertEquals(TimeoutPolicy.default.totalTransaction, policy.totalTransaction)
    }
}