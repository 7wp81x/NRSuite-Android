package com.swp81x.nrsuite.core.wpa

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Wpa22000ExporterTest {

    @Test
    fun `exports a hashcat 22000 M1M2 line and zeroes the EAPOL MIC`() {
        val handshake = handshake()
        val line = Wpa22000Exporter.buildLine(handshake, "TestNetwork")
        requireNotNull(line)

        val fields = line.split("*")
        assertEquals(9, fields.size)
        assertEquals("WPA", fields[0])
        assertEquals("02", fields[1])
        assertEquals("aabbccddeeff", fields[3])
        assertEquals("112233445566", fields[4])
        assertEquals("546573744e6574776f726b", fields[5])
        assertEquals("00", fields[8])

        val eapol = fields[7].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertArrayEquals(ByteArray(16), eapol.copyOfRange(81, 97))
        assertTrue(eapol.size > 97)
    }

    @Test
    fun `rejects incomplete or invalid export material`() {
        assertNull(Wpa22000Exporter.buildLine(handshake().copy(mic = null), "TestNetwork"))
        assertNull(Wpa22000Exporter.buildLine(handshake(), ""))
        assertNull(Wpa22000Exporter.buildLine(handshake(), "x".repeat(33)))
    }

    private fun handshake(): WpaHandshake {
        val bssid = byteArrayOf(
            0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(),
            0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte(),
        )
        val station = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66)
        val aNonce = ByteArray(32) { (it + 1).toByte() }
        val sNonce = ByteArray(32) { (it + 33).toByte() }
        val mic = ByteArray(16) { (it + 7).toByte() }
        val eapol = ByteArray(117) { (it + 1).toByte() }
        mic.copyInto(eapol, destinationOffset = 81)
        return WpaHandshake(
            m1Bssid = bssid,
            m1Station = station,
            aNonce = aNonce,
            m1ReplayCounter = 1L,
            m2Bssid = bssid,
            m2Station = station,
            sNonce = sNonce,
            eapolM2 = eapol,
            mic = mic,
            m2ReplayCounter = 1L,
            keyDescriptorVersion = 2,
        )
    }
}
