package com.swp81x.nrsuite.core.wifi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PcapSsidParserTest {

    @Test
    fun `extracts SSID and BSSID from a beacon frame`() {
        val frame = buildBeaconFrame("TestNetwork", bssid)
        val detected = PcapSsidParser.parse(frame)

        assertEquals("TestNetwork", detected?.ssid)
        assertEquals("AA:BB:CC:DD:EE:FF", detected?.bssid)
    }

    @Test
    fun `returns null for non-management frames`() {
        val frame = buildBeaconFrame("TestNetwork", bssid)
        frame[RADIOTAP_LENGTH] = 0x08 // data frame, not management
        assertNull(PcapSsidParser.parse(frame))
    }

    private fun buildBeaconFrame(ssid: String, bssid: ByteArray): ByteArray {
        val ssidBytes = ssid.toByteArray(Charsets.UTF_8)
        val frame = ByteArray(RADIOTAP_LENGTH + 24 + 12 + 2 + ssidBytes.size)
        var offset = 0

        // Radiotap: version 0, pad 0, length 8, present 0
        frame[2] = RADIOTAP_LENGTH.toByte()
        offset += RADIOTAP_LENGTH

        // 802.11 header
        frame[offset] = 0x80.toByte() // beacon
        frame[offset + 1] = 0x00
        bssid.copyInto(frame, offset + 10) // addr2, transmitter/BSSID
        bssid.copyInto(frame, offset + 16) // addr3, BSSID
        offset += 24

        // Beacon fixed parameters: timestamp, interval, capability
        offset += 12

        // SSID information element
        frame[offset] = 0x00
        frame[offset + 1] = ssidBytes.size.toByte()
        ssidBytes.copyInto(frame, offset + 2)
        return frame
    }

    companion object {
        private const val RADIOTAP_LENGTH = 8
        private val bssid = byteArrayOf(
            0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(),
            0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte(),
        )
    }
}
