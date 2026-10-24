package com.swp81x.nrsuite.core.wpa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WpaHandshakeTrackerTest {

    @Test
    fun `completes m1 and m2 for same bssid and station`() {
        val tracker = WpaHandshakeTracker()
        val bssid = mac(0x10)
        val station = mac(0x20)

        tracker.onFrame(
            dataFrame(
                address1 = station,
                address2 = bssid,
                address3 = bssid,
                keyInfo = byteArrayOf(0x00, 0x88.toByte()),
                replayCounter = 42,
                nonce = nonce(0xA1),
            ),
        )
        val update = tracker.onFrame(
            dataFrame(
                address1 = bssid,
                address2 = station,
                address3 = bssid,
                keyInfo = byteArrayOf(0x01, 0x0A),
                replayCounter = 42,
                nonce = nonce(0xB2),
            ),
        )

        assertTrue(update.m1Found)
        assertTrue(update.m2Found)
        assertTrue(update.valid)
        assertNotNull(update.completeHandshake)
    }

    @Test
    fun `keeps interleaved clients separate`() {
        val tracker = WpaHandshakeTracker()
        val bssid = mac(0x10)
        val stationA = mac(0x20)
        val stationB = mac(0x30)

        tracker.onFrame(
            dataFrame(
                address1 = stationA,
                address2 = bssid,
                address3 = bssid,
                keyInfo = byteArrayOf(0x00, 0x88.toByte()),
                replayCounter = 1,
                nonce = nonce(0xA1),
            ),
        )
        tracker.onFrame(
            dataFrame(
                address1 = bssid,
                address2 = stationB,
                address3 = bssid,
                keyInfo = byteArrayOf(0x01, 0x0A),
                replayCounter = 99,
                nonce = nonce(0xB2),
            ),
        )
        val updateA = tracker.onFrame(
            dataFrame(
                address1 = bssid,
                address2 = stationA,
                address3 = bssid,
                keyInfo = byteArrayOf(0x01, 0x0A),
                replayCounter = 1,
                nonce = nonce(0xB3),
            ),
        )

        assertTrue(updateA.valid)
        assertEquals(2, tracker.snapshot().size)
    }

    @Test
    fun `rejects mismatched replay counters`() {
        val tracker = WpaHandshakeTracker()
        val bssid = mac(0x10)
        val station = mac(0x20)

        tracker.onFrame(
            dataFrame(
                address1 = station,
                address2 = bssid,
                address3 = bssid,
                keyInfo = byteArrayOf(0x00, 0x88.toByte()),
                replayCounter = 7,
                nonce = nonce(0xA1),
            ),
        )
        val update = tracker.onFrame(
            dataFrame(
                address1 = bssid,
                address2 = station,
                address3 = bssid,
                keyInfo = byteArrayOf(0x01, 0x0A),
                replayCounter = 8,
                nonce = nonce(0xB2),
            ),
        )

        assertFalse(update.valid)
    }

    private fun mac(firstByte: Int): ByteArray = byteArrayOf(
        firstByte.toByte(), 0x01, 0x02, 0x03, 0x04, 0x05,
    )

    private fun nonce(firstByte: Int): ByteArray = ByteArray(32) { index ->
        if (index == 0) firstByte.toByte() else index.toByte()
    }

    private fun dataFrame(
        address1: ByteArray,
        address2: ByteArray,
        address3: ByteArray,
        keyInfo: ByteArray,
        replayCounter: Long,
        nonce: ByteArray,
    ): ByteArray {
        val radiotap = ByteArray(8).apply {
            this[2] = 8
        }
        val macHeader = ByteArray(24).apply {
            this[0] = 0x08
            address1.copyInto(this, 4)
            address2.copyInto(this, 10)
            address3.copyInto(this, 16)
        }
        val llcSnap = byteArrayOf(
            0xAA.toByte(), 0xAA.toByte(), 0x03, 0x00, 0x00, 0x00, 0x88.toByte(), 0x8E.toByte(),
        )
        val eapol = ByteArray(99).apply {
            this[0] = 1
            this[1] = 3
            this[2] = 0
            this[3] = 95
            this[4] = 2
            keyInfo.copyInto(this, 5)
            writeU64Le(this, 9, replayCounter)
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
