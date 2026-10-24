package com.swp81x.nrsuite.core.handshake

import com.swp81x.nrsuite.core.wifi.NetworkTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoHandshakeRankerTest {

    private val config = AutoHandshakeConfig()

    @Test
    fun `eligible targets are selected by strongest rssi`() {
        val weak = ap("AA:AA:AA:AA:AA:01", -80)
        val strong = ap("AA:AA:AA:AA:AA:02", -42)

        val history = AutoHandshakeRanker.refresh(
            history = emptyList(),
            aps = listOf(weak, strong),
            clients = emptyList(),
            config = config,
            nowMs = 1_000L,
        )
        val eligible = AutoHandshakeRanker.eligibleTargets(history, 8)

        assertEquals(strong.bssid, eligible.first().target.bssid)
    }

    @Test
    fun `does not repeat an in-range captured target`() {
        val ap = ap("AA:AA:AA:AA:AA:03", -50)
        val captured = AutoHandshakeTarget(
            target = ap,
            status = AutoHandshakeTargetStatus.CAPTURED,
            inRange = true,
        )

        val history = AutoHandshakeRanker.refresh(
            history = listOf(captured),
            aps = listOf(ap),
            clients = emptyList(),
            config = config,
            nowMs = 1_000L,
        )

        assertEquals(AutoHandshakeTargetStatus.CAPTURED, history.single().status)
        assertTrue(AutoHandshakeRanker.eligibleTargets(history, 8).isEmpty())
    }

    @Test
    fun `recaptures a captured target after it reappears`() {
        val ap = ap("AA:AA:AA:AA:AA:04", -50)
        var history = listOf(
            AutoHandshakeTarget(
                target = ap,
                status = AutoHandshakeTargetStatus.CAPTURED,
                inRange = true,
            ),
        )

        history = AutoHandshakeRanker.refresh(
            history = history,
            aps = emptyList(),
            clients = emptyList(),
            config = config,
            nowMs = 2_000L,
        )
        assertTrue(history.single().inRange.not())

        history = AutoHandshakeRanker.refresh(
            history = history,
            aps = listOf(ap),
            clients = emptyList(),
            config = config,
            nowMs = 3_000L,
        )

        assertEquals(AutoHandshakeTargetStatus.PENDING, history.single().status)
        assertEquals(1, AutoHandshakeRanker.eligibleTargets(history, 8).size)
    }

    @Test
    fun `excludes unsupported security`() {
        val open = ap("AA:AA:AA:AA:AA:05", -40, security = "OPEN")
        val wpa3 = ap("AA:AA:AA:AA:AA:06", -41, security = "WPA3-PSK")

        val history = AutoHandshakeRanker.refresh(
            history = emptyList(),
            aps = listOf(open, wpa3),
            clients = emptyList(),
            config = config,
            nowMs = 1_000L,
        )

        assertTrue(history.all { it.status == AutoHandshakeTargetStatus.UNSUPPORTED })
        assertTrue(AutoHandshakeRanker.eligibleTargets(history, 8).isEmpty())
    }

    private fun ap(
        bssid: String,
        rssi: Int,
        security: String = "WPA2-PSK",
    ): NetworkTarget = NetworkTarget(
        ssid = "Lab-$bssid",
        bssid = bssid,
        channel = 6,
        rssi = rssi,
        security = security,
    )
}
