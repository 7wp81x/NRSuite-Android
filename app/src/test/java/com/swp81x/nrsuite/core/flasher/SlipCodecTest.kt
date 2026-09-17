package com.swp81x.nrsuite.core.flasher

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SlipCodecTest {

    @Test
    fun `round trips reserved bytes`() {
        val packet = byteArrayOf(0x01, 0xC0.toByte(), 0x02, 0xDB.toByte(), 0x03)
        val encoded = SlipCodec.encode(packet)
        val decoded = SlipDecoder().feed(encoded)

        assertArrayEquals(packet, decoded.single())
    }

    @Test
    fun `decodes fragmented writes`() {
        val packet = byteArrayOf(0x10, 0xC0.toByte(), 0x20, 0xDB.toByte(), 0x30)
        val encoded = SlipCodec.encode(packet)
        val decoder = SlipDecoder()
        var decoded: ByteArray? = null

        for (byte in encoded) {
            val frames = decoder.feed(byteArrayOf(byte))
            if (frames.isNotEmpty()) decoded = frames.first()
        }

        assertArrayEquals(packet, decoded)
    }

    @Test
    fun `ignores bytes before first frame delimiter`() {
        val packet = byteArrayOf(0x44, 0x55)
        val encoded = byteArrayOf(0x11, 0x22) + SlipCodec.encode(packet)
        val decoded = SlipDecoder().feed(encoded)

        assertArrayEquals(packet, decoded.single())
    }
}
