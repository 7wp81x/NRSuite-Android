package com.swp81x.nrsuite.core.wpa

import org.junit.Assert.assertSame
import org.junit.Test

class WpaHandshakeSelectorTest {

    @Test
    fun `prefers matching SSID and BSSID then removes duplicate exchanges`() {
        val bssidA = byteArrayOf(0x10, 1, 2, 3, 4, 5)
        val bssidB = byteArrayOf(0x20, 1, 2, 3, 4, 5)
        val station = byteArrayOf(0x30, 1, 2, 3, 4, 5)
        val first = handshake(bssidA, station, mic = 0x01)
        val preferred = handshake(bssidB, station, mic = 0x02)
        val duplicate = handshake(bssidB, station, mic = 0x02)

        val selected = selectBestWpaHandshake(
            candidates = listOf(
                WpaHandshakeCandidate(first, "Other"),
                WpaHandshakeCandidate(preferred, "Target"),
                WpaHandshakeCandidate(duplicate, "Target"),
            ),
            preferredSsid = "Target",
            preferredBssid = "20:01:02:03:04:05",
        )

        assertSame(preferred, selected)
    }

    private fun handshake(bssid: ByteArray, station: ByteArray, mic: Int): WpaHandshake =
        WpaHandshake(
            m1Bssid = bssid,
            m1Station = station,
            aNonce = ByteArray(32) { 1 },
            m1ReplayCounter = 5L,
            m2Bssid = bssid,
            m2Station = station,
            sNonce = ByteArray(32) { 2 },
            eapolM2 = ByteArray(117) { 3 }.apply {
                for (index in 81 until 97) this[index] = mic.toByte()
            },
            mic = ByteArray(16) { mic.toByte() },
            m2ReplayCounter = 5L,
            keyDescriptorVersion = 2,
        )
}
