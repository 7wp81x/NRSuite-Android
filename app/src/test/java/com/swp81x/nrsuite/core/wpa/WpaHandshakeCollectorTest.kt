package com.swp81x.nrsuite.core.wpa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WpaHandshakeCollectorTest {

    @Test
    fun `collects multiple complete handshakes from interleaved sessions`() {
        val collector = WpaHandshakeCollector()
        val bssidA = mac(0x10)
        val stationA = mac(0x20)
        val bssidB = mac(0x30)
        val stationB = mac(0x40)

        collector.addFrame(m1Frame(bssidA, stationA, replay = 7, nonce(0xA1)))
        collector.addFrame(m1Frame(bssidB, stationB, replay = 8, nonce(0xB1)))
        collector.addFrame(m2Frame(bssidB, stationB, replay = 8, nonce(0xB2)))
        collector.addFrame(m2Frame(bssidA, stationA, replay = 7, nonce(0xA2)))

        val handshakes = collector.finish()
        assertEquals(2, handshakes.size)
        assertTrue(handshakes.all { it.isComplete })
    }

    @Test
    fun `does not pair M1 and M2 with different replay counters`() {
        val collector = WpaHandshakeCollector()
        val bssid = mac(0x10)
        val station = mac(0x20)

        collector.addFrame(m1Frame(bssid, station, replay = 7, nonce(0xA1)))
        collector.addFrame(m2Frame(bssid, station, replay = 8, nonce(0xA2)))

        assertTrue(collector.finish().isEmpty())
    }

    private fun mac(firstByte: Int): ByteArray = byteArrayOf(
        firstByte.toByte(), 0x01, 0x02, 0x03, 0x04, 0x05,
    )

    private fun nonce(firstByte: Int): ByteArray = ByteArray(32) { index ->
        if (index == 0) firstByte.toByte() else index.toByte()
    }

    private fun m1Frame(
        bssid: ByteArray,
        station: ByteArray,
        replay: Long,
        nonce: ByteArray,
    ): ByteArray = dataFrame(
        address1 = station,
        address2 = bssid,
        address3 = bssid,
        keyInfo = byteArrayOf(0x00, 0x88.toByte()),
        replay = replay,
        nonce = nonce,
    )

    private fun m2Frame(
        bssid: ByteArray,
        station: ByteArray,
        replay: Long,
        nonce: ByteArray,
    ): ByteArray = dataFrame(
        address1 = bssid,
        address2 = station,
        address3 = bssid,
        keyInfo = byteArrayOf(0x01, 0x0A),
        replay = replay,
        nonce = nonce,
    )

    private fun dataFrame(
        address1: ByteArray,
        address2: ByteArray,
        address3: ByteArray,
        keyInfo: ByteArray,
        replay: Long,
        nonce: ByteArray,
    ): ByteArray {
        val radiotap = ByteArray(8).apply { this[2] = 8 }
        val macHeader = ByteArray(24).apply {
            this[0] = 0x08
            address1.copyInto(this, 4)
            address2.copyInto(this, 10)
            address3.copyInto(this, 16)
        }
        val llcSnap = byteArrayOf(
            0xAA.toByte(), 0xAA.toByte(), 0x03, 0x00,
            0x00, 0x00, 0x88.toByte(), 0x8E.toByte(),
        )
        val eapol = ByteArray(99).apply {
            this[0] = 1
            this[1] = 3
            this[2] = 0
            this[3] = 95
            this[4] = 2
            keyInfo.copyInto(this, 5)
            writeU64Le(this, 9, replay)
            nonce.copyInto(this, 17)
        }
        return radiotap + macHeader + llcSnap + eapol
    }

    private fun writeU64Le(target: ByteArray, offset: Int, value: Long) {
        for (index in 0 until 8) {
            target[offset + index] = ((value ushr (index * 8)) and 0xFF).toByte()
        }
    }
}
