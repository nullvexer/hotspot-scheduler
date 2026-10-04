package com.iranjan.hotspotscheduler.toggle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `parseStateProbe` is the only thing standing between the app and a wrong answer about whether
 * the hotspot is on. A null result means "could not read", which is NOT the same as "off" -
 * conflating the two is what made the app claim it had disabled the hotspot after a data cap
 * while it was still broadcasting.
 */
class StateProbeContractTest {

    @Test
    fun `tethered manager with interface up means on`() {
        // SecurityType must be on its own line: the parser matches line prefixes, so appending it
        // to the mCurrentSoftApConfiguration line would leave `open` unknown.
        val out = """
            Dump of ActiveModeWarden
            Dump of SoftApManager
            mRole: ROLE_SOFTAP_TETHERED
            mIfaceIsUp: true
            mCurrentSoftApConfiguration: ssid = "MyAP"
            Passphrase = hunter2
            SecurityType = 2
        """.trimIndent()
        val probe = HotspotCommands.parseStateProbe(out)!!
        assertTrue(probe.on)
        assertEquals("MyAP", probe.ssid)
        assertEquals(false, probe.open)
    }

    @Test
    fun `interface down means off even when the role is tethered`() {
        // Regression guard: mIfaceIsUp: false must not be read as up.
        val out = """
            Dump of ActiveModeWarden
            Dump of SoftApManager
            mRole: ROLE_SOFTAP_TETHERED
            mIfaceIsUp: false
        """.trimIndent()
        val probe = HotspotCommands.parseStateProbe(out)!!
        assertFalse(probe.on)
    }

    @Test
    fun `local only softap is not counted as on`() {
        val out = """
            Dump of ActiveModeWarden
            Dump of SoftApManager
            mRole: ROLE_SOFTAP_LOCAL_ONLY
            mIfaceIsUp: true
        """.trimIndent()
        assertFalse(HotspotCommands.parseStateProbe(out)!!.on)
    }

    @Test
    fun `open network is detected from empty passphrase`() {
        val out = """
            Dump of ActiveModeWarden
            Dump of SoftApManager
            mRole: ROLE_SOFTAP_TETHERED
            mIfaceIsUp: true
            mCurrentSoftApConfiguration: ssid = "OpenAP"
            Passphrase = <empty>
            SecurityType = 0
        """.trimIndent()
        val probe = HotspotCommands.parseStateProbe(out)!!
        assertTrue(probe.on)
        assertEquals(true, probe.open)
    }

    @Test
    fun `no softap manager block means definitively off`() {
        val out = "Dump of ActiveModeWarden\nsome other content"
        val probe = HotspotCommands.parseStateProbe(out)
        assertFalse(probe!!.on)
    }

    @Test
    fun `missing warden header means the probe failed`() {
        assertNull(HotspotCommands.parseStateProbe(""))
        assertNull(HotspotCommands.parseStateProbe("Permission Denial: dump failed"))
        assertNull(HotspotCommands.parseStateProbe("Dump of SoftApManager\nmIfaceIsUp: true"))
    }

    @Test
    fun `null probe is distinguishable from off`() {
        // This distinction is the contract the OFF path depends on.
        val failed = HotspotCommands.parseStateProbe("Permission Denial")
        val off = HotspotCommands.parseStateProbe("Dump of ActiveModeWarden")
        assertNull(failed)
        assertEquals(false, off!!.on)
    }
}