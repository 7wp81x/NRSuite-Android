package com.swp81x.nrsuite.core.wps

import com.swp81x.nrsuite.core.wifi.NetworkTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WpsPixieTargetFilterTest {
    private fun target(
        bssid: String,
        wps: Boolean,
        security: String = "WPA2-PSK",
    ): NetworkTarget = NetworkTarget(
        ssid = bssid,
        bssid = bssid,
        channel = 6,
        rssi = -60,
        security = security,
        wps = wps,
    )

    @Test
    fun `wps only hides non-wps and non-actionable WPS candidates`() {
        val targets = listOf(
            target("AA:AA:AA:AA:AA:01", wps = true, security = "WPA2-PSK"),
            target("AA:AA:AA:AA:AA:02", wps = false, security = "OPEN"),
            target("AA:AA:AA:AA:AA:03", wps = true, security = "OPEN"),
            target("AA:AA:AA:AA:AA:04", wps = true, security = "WPA3-PSK"),
            target("AA:AA:AA:AA:AA:05", wps = true, security = "WPA2-ENTERPRISE"),
            target("AA:AA:AA:AA:AA:06", wps = true, security = "WPA2/WPA3-PSK"),
        )

        val filtered = filterWpsPixieTargets(targets, wpsOnly = true)
        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.wps })
        assertEquals(6, filterWpsPixieTargets(targets, wpsOnly = false).size)
    }

    @Test
    fun `actionable WPS target requires WPA or WPA2 PSK security`() {
        assertTrue(isActionableWpsPixieTarget(target("01", true, "WPA/WPA2-PSK")))
        assertTrue(isActionableWpsPixieTarget(target("02", true, "WPA-PSK")))
        assertTrue(isActionableWpsPixieTarget(target("03", true, "WPA2/WPA3-PSK")))
        assertFalse(isActionableWpsPixieTarget(target("04", true, "OPEN")))
        assertFalse(isActionableWpsPixieTarget(target("05", true, "WEP")))
        assertFalse(isActionableWpsPixieTarget(target("06", true, "WPA3-PSK")))
        assertFalse(isActionableWpsPixieTarget(target("07", true, "WPA2-ENTERPRISE")))
        assertFalse(isActionableWpsPixieTarget(target("08", false, "WPA2-PSK")))
    }
}
