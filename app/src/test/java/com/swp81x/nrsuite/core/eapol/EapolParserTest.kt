package com.swp81x.nrsuite.core.eapol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EapolParserTest {

    @Test
    fun `detects M1 through M4 in order`() {
        val state = EapolHandshake()

        EapolParser.parse(dataFrame(byteArrayOf(0x00, 0x88.toByte())), state)
        assertTrue(state.m1)

        EapolParser.parse(dataFrame(byteArrayOf(0x01, 0x08)), state)
        assertTrue(state.m2)

        EapolParser.parse(dataFrame(byteArrayOf(0x03, 0x88.toByte())), state)
        assertTrue(state.m3)

        EapolParser.parse(dataFrame(byteArrayOf(0x03, 0x08)), state)
        assertTrue(state.m4)
        assertTrue(state.isComplete)
    }

    @Test
    fun `ignores M2 before M1`() {
        val state = EapolHandshake()
        EapolParser.parse(dataFrame(byteArrayOf(0x01, 0x08)), state)
        assertFalse(state.m2)
    }

    @Test
    fun `ignores non data frames`() {
        val state = EapolHandshake()
        val frame = dataFrame(byteArrayOf(0x00, 0x88.toByte()))
        frame[8] = 0x80.toByte()
        EapolParser.parse(frame, state)
        assertFalse(state.m1 || state.m2 || state.m3 || state.m4)
    }

    @Test
    fun `ignores short frames`() {
        val state = EapolHandshake()
        EapolParser.parse(byteArrayOf(0x00, 0x00), state)
        assertFalse(state.m1 || state.m2 || state.m3 || state.m4)
    }

    private fun dataFrame(keyInfo: ByteArray, packetType: Int = 3): ByteArray {
        val radiotap = ByteArray(8)
        radiotap[2] = 8
        val macHeader = ByteArray(24)
        macHeader[0] = 0x08
        val llcSnap = byteArrayOf(
            0xAA.toByte(), 0xAA.toByte(), 0x03, 0x00, 0x00, 0x00, 0x88.toByte(), 0x8E.toByte()
        )
        val eapol = byteArrayOf(1, packetType.toByte(), 0, 0, 0) + keyInfo + byteArrayOf(0)
        return radiotap + macHeader + llcSnap + eapol
    }
}
