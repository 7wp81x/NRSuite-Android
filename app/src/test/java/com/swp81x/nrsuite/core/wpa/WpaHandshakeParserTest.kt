package com.swp81x.nrsuite.core.wpa

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WpaHandshakeParserTest {

    @Test
    fun `matches m1 and m2 from the same bssid and station`() {
        val state = WpaHandshake()
        val bssid = mac(0x10)
        val station = mac(0x20)

        WpaHandshakeParser.parse(
            dataFrame(
                address1 = station,
                address2 = bssid,
                address3 = bssid,
                keyInfo = byteArrayOf(0x00, 0x88.toByte()),
                replayCounter = 42,
                nonce = nonce(0xA1),
            ),
            state,
        )
        WpaHandshakeParser.parse(
            dataFrame(
                address1 = bssid,
                address2 = station,
                address3 = bssid,
                keyInfo = byteArrayOf(0x01, 0x0A),
                replayCounter = 42,
                nonce = nonce(0xB2),
            ),
            state,
        )

        assertTrue(state.isComplete)
        assertArrayEquals(bssid, state.bssid!!)
        assertArrayEquals(station, state.station!!)
    }

    @Test
    fun `does not complete when m1 and m2 address sessions differ`() {
        val state = WpaHandshake()
        val m1Bssid = mac(0x10)
        val m2Bssid = mac(0x30)
        val station = mac(0x20)

        WpaHandshakeParser.parse(
            dataFrame(
                address1 = station,
                address2 = m1Bssid,
                address3 = m1Bssid,
                keyInfo = byteArrayOf(0x00, 0x88.toByte()),
                replayCounter = 7,
                nonce = nonce(0xA1),
            ),
            state,
        )
        WpaHandshakeParser.parse(
            dataFrame(
                address1 = m2Bssid,
                address2 = station,
                address3 = m2Bssid,
                keyInfo = byteArrayOf(0x01, 0x0A),
                replayCounter = 7,
                nonce = nonce(0xB2),
            ),
            state,
        )

        assertFalse(state.isComplete)
    }

    @Test
    fun `does not complete when replay counters differ`() {
        val state = WpaHandshake()
        val bssid = mac(0x10)
        val station = mac(0x20)

        WpaHandshakeParser.parse(
            dataFrame(
                address1 = station,
                address2 = bssid,
                address3 = bssid,
                keyInfo = byteArrayOf(0x00, 0x88.toByte()),
                replayCounter = 7,
                nonce = nonce(0xA1),
            ),
            state,
        )
        WpaHandshakeParser.parse(
            dataFrame(
                address1 = bssid,
                address2 = station,
                address3 = bssid,
                keyInfo = byteArrayOf(0x01, 0x0A),
                replayCounter = 8,
                nonce = nonce(0xB2),
            ),
            state,
        )

        assertFalse(state.isComplete)
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
