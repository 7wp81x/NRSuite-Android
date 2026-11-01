package com.swp81x.nrsuite.core.wpa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Wpa22000ImporterTest {
    @Test
    fun `parses a valid WPA02 EAPOL line`() {
        val handshake = handshake()
        val line = Wpa22000Exporter.buildLine(handshake, "TestNetwork")
        assertNotNull(line)

        val result = parseWpa22000(sequenceOf(line!!))
        val record = result.crackable.single()

        assertEquals("TestNetwork", record.ssid)
        assertEquals("10:01:02:03:04:05", record.bssid)
        assertEquals("20:01:02:03:04:05", record.station)
        assertTrue(record.handshake!!.isComplete)
        assertEquals(2, record.handshake!!.keyDescriptorVersion)
    }

    @Test
    fun `recognizes PMKID lines without treating them as crackable`() {
        val line = "WPA*01*00112233445566778899AABBCCDDEEFF*" +
            "100102030405*200102030405*546573744e6574***"

        val result = parseWpa22000(sequenceOf(line))

        assertEquals(1, result.pmkidCount)
        assertTrue(result.crackable.isEmpty())
        assertEquals("PMKID verification is not supported yet", result.records.single().error)
    }

    @Test
    fun `rejects malformed lines`() {
        val result = parseWpa22000(
            sequenceOf(
                "not-a-wpa-line",
                "WPA*02*abc*bad*fields",
                "",
            ),
        )

        assertEquals(2, result.malformedCount)
        assertTrue(result.crackable.isEmpty())
    }

    private fun handshake(): WpaHandshake {
        val bssid = byteArrayOf(0x10, 1, 2, 3, 4, 5)
        val station = byteArrayOf(0x20, 1, 2, 3, 4, 5)
        val nonce = ByteArray(32) { 1 }
        val sNonce = ByteArray(32) { 2 }
        val eapol = ByteArray(121).apply {
            // EAPOL key-info descriptor version 2, replay counter 5, SNonce.
            this[5] = 0
            this[6] = 2
            this[9] = 0
            this[10] = 0
            this[11] = 0
            this[12] = 0
            this[13] = 0
            this[14] = 0
            this[15] = 0
            this[16] = 5
            sNonce.copyInto(this, destinationOffset = 17)
        }
        return WpaHandshake(
            m1Bssid = bssid,
            m1Station = station,
            aNonce = nonce,
            m1ReplayCounter = 5L,
            m2Bssid = bssid,
            m2Station = station,
            sNonce = sNonce,
            eapolM2 = eapol,
            mic = ByteArray(16) { 9 },
            m2ReplayCounter = 5L,
            keyDescriptorVersion = 2,
        )
    }
}
