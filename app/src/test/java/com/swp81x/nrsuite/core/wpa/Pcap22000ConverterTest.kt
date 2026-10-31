package com.swp81x.nrsuite.core.wpa

import org.junit.Assert.assertEquals
import org.junit.Test

class Pcap22000ConverterTest {

    @Test
    fun `merges candidates and applies exact deduplication`() {
        val bssid = byteArrayOf(0x10, 1, 2, 3, 4, 5)
        val station = byteArrayOf(0x20, 1, 2, 3, 4, 5)
        val first = handshake(bssid, station, micByte = 1)
        val duplicate = handshake(bssid, station, micByte = 1)
        val second = handshake(bssid, station, micByte = 2)

        val result = buildPcap22000Export(
            candidates = listOf(
                Pcap22000Candidate("one.pcap", first, "Target"),
                Pcap22000Candidate("two.pcap", duplicate, "Target"),
                Pcap22000Candidate("three.pcap", second, "Target"),
            ),
            dedupeMode = Pcap22000DedupeMode.EXACT,
        )

        assertEquals(2, result.lines.size)
        assertEquals(1, result.duplicatesRemoved)
    }

    @Test
    fun `best per AP keeps one handshake per BSSID and SSID`() {
        val bssid = byteArrayOf(0x10, 1, 2, 3, 4, 5)
        val station = byteArrayOf(0x20, 1, 2, 3, 4, 5)
        val first = handshake(bssid, station, micByte = 1, keyDescriptorVersion = 1)
        val better = handshake(bssid, station, micByte = 2, keyDescriptorVersion = 2)

        val result = buildPcap22000Export(
            candidates = listOf(
                Pcap22000Candidate("one.pcap", first, "Target"),
                Pcap22000Candidate("two.pcap", better, "Target"),
            ),
            dedupeMode = Pcap22000DedupeMode.BEST_PER_AP,
        )

        assertEquals(1, result.lines.size)
        assertEquals("two.pcap", result.exportedCandidates.single().sourceName)
        assertEquals(1, result.duplicatesRemoved)
    }

    @Test
    fun `advanced SSID override enables export and missing SSIDs are counted`() {
        val bssid = byteArrayOf(0xAA.toByte(), 1, 2, 3, 4, 5)
        val station = byteArrayOf(0x20, 1, 2, 3, 4, 5)
        val handshake = handshake(bssid, station, micByte = 1)

        val withOverride = buildPcap22000Export(
            candidates = listOf(Pcap22000Candidate("hidden.pcap", handshake, null)),
            dedupeMode = Pcap22000DedupeMode.NONE,
            ssidOverrides = mapOf("AA:01:02:03:04:05" to "HiddenNetwork"),
        )
        assertEquals(1, withOverride.lines.size)
        assertEquals(0, withOverride.missingSsid)

        val withoutOverride = buildPcap22000Export(
            candidates = listOf(Pcap22000Candidate("hidden.pcap", handshake, null)),
            dedupeMode = Pcap22000DedupeMode.NONE,
        )
        assertEquals(0, withoutOverride.lines.size)
        assertEquals(1, withoutOverride.missingSsid)
    }

    private fun handshake(
        bssid: ByteArray,
        station: ByteArray,
        micByte: Int,
        eapolSize: Int = 121,
        keyDescriptorVersion: Int = 2,
    ): WpaHandshake {
        val nonce = ByteArray(32) { 1 }
        val mic = ByteArray(16) { micByte.toByte() }
        val eapol = ByteArray(eapolSize) { 3 }.apply {
            if (size >= 97) mic.copyInto(this, destinationOffset = 81)
        }
        return WpaHandshake(
            m1Bssid = bssid,
            m1Station = station,
            aNonce = nonce,
            m1ReplayCounter = 5L,
            m2Bssid = bssid,
            m2Station = station,
            sNonce = nonce,
            eapolM2 = eapol,
            mic = mic,
            m2ReplayCounter = 5L,
            keyDescriptorVersion = keyDescriptorVersion,
        )
    }
}
