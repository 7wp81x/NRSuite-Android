package com.swp81x.nrsuite.core.eapol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EapolHandshakeTrackerTest {
    @Test
    fun `keeps interleaved stations independent`() {
        val bssid = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte())
        val stationA = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66)
        val stationB = byteArrayOf(0x21, 0x32, 0x43, 0x54, 0x65, 0x76)
        val tracker = EapolHandshakeTracker()
        val latest = mutableMapOf<String, EapolHandshake>()

        listOf(
            dataFrame(stationA, bssid, eapol(0x0088)),
            dataFrame(stationB, bssid, eapol(0x0088)),
            dataFrame(bssid, stationA, eapol(0x010A)),
            dataFrame(bssid, stationA, eapol(0x038A)),
            dataFrame(bssid, stationB, eapol(0x010A)),
            dataFrame(bssid, stationB, eapol(0x038A)),
            dataFrame(stationA, bssid, eapol(0x030A)),
            dataFrame(stationB, bssid, eapol(0x030A)),
        ).forEach { frame ->
            val update = tracker.onFrame(frame)
            val station = update.station ?: error("station missing")
            latest[station] = update.handshake ?: error("handshake missing")
        }

        val expectedA = latest[mac(stationA)]!!
        val expectedB = latest[mac(stationB)]!!
        assertTrue(expectedA.m1 && expectedA.m2 && expectedA.m3 && expectedA.m4)
        assertTrue(expectedB.m1 && expectedB.m2 && expectedB.m3 && expectedB.m4)
        assertEquals("AA:BB:CC:DD:EE:FF", tracker.onFrame(dataFrame(stationA, bssid, eapol(0x0088))).bssid)
    }

    private fun eapol(keyInfo: Int): ByteArray = ByteArray(99).apply {
        this[0] = 1
        this[1] = 3
        this[4] = 2
        this[5] = ((keyInfo shr 8) and 0xFF).toByte()
        this[6] = (keyInfo and 0xFF).toByte()
    }

    private fun dataFrame(destination: ByteArray, source: ByteArray, payload: ByteArray): ByteArray {
        val toDs = source.contentEquals(byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66)) ||
            source.contentEquals(byteArrayOf(0x21, 0x32, 0x43, 0x54, 0x65, 0x76))
        val bssid = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte())
        val radiotap = ByteArray(8).apply { this[2] = 8 }
        val macHeader = ByteArray(24).apply {
            this[0] = 0x08
            this[1] = if (toDs) 0x01 else 0x02
            destination.copyInto(this, 4)
            source.copyInto(this, 10)
            bssid.copyInto(this, 16)
        }
        val llcSnap = byteArrayOf(
            0xAA.toByte(), 0xAA.toByte(), 0x03, 0x00, 0x00, 0x00,
            0x88.toByte(), 0x8E.toByte(),
        )
        return radiotap + macHeader + llcSnap + payload
    }

    private fun mac(bytes: ByteArray): String =
        bytes.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
}
