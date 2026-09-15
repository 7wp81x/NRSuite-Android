package com.swp81x.nrsuite.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameCodecTest {

    @Test
    fun `encodes a command frame with little endian length`() {
        val payload = """{"cmd":"PING","args":{}}""".toByteArray()

        val encoded = FrameCodec.encode(FrameType.COMMAND, id = 0x2A, payload = payload)

        assertEquals(FrameCodec.HEADER_SIZE + payload.size, encoded.size)
        assertEquals(0xAD, encoded[0].toInt() and 0xFF)
        assertEquals(0xDE, encoded[1].toInt() and 0xFF)
        assertEquals(FrameType.COMMAND.code, encoded[2].toInt() and 0xFF)
        assertEquals(0x2A, encoded[3].toInt() and 0xFF)
        assertEquals(payload.size, encoded[4].toInt() and 0xFF)
        assertEquals(0, encoded[5].toInt() and 0xFF)
    }

    @Test
    fun `round trips a frame`() {
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val encoded = FrameCodec.encode(FrameType.PCAP, id = 7, payload = payload)

        val decoded = FrameDecoder().feed(encoded)

        assertEquals(1, decoded.size)
        assertEquals(FrameType.PCAP, decoded[0].type)
        assertEquals(7, decoded[0].id)
        assertArrayEquals(payload, decoded[0].payload)
    }

    @Test
    fun `decodes multiple frames from one feed`() {
        val first = FrameCodec.encode(FrameType.EVENT, 1, "one".toByteArray())
        val second = FrameCodec.encode(FrameType.RESPONSE, 2, "two".toByteArray())

        val decoded = FrameDecoder().feed(first + second)

        assertEquals(2, decoded.size)
        assertEquals("one", decoded[0].payloadAsString)
        assertEquals("two", decoded[1].payloadAsString)
    }

    @Test
    fun `decodes frames split across feeds`() {
        val encoded = FrameCodec.encode(FrameType.COMMAND, 3, "split".toByteArray())
        val decoder = FrameDecoder()

        assertTrue(decoder.feed(encoded.copyOfRange(0, 3)).isEmpty())
        assertTrue(decoder.feed(encoded.copyOfRange(3, 5)).isEmpty())
        val decoded = decoder.feed(encoded.copyOfRange(5, encoded.size))

        assertEquals(1, decoded.size)
        assertEquals(FrameType.COMMAND, decoded[0].type)
        assertEquals("split", decoded[0].payloadAsString)
    }

    @Test
    fun `resynchronizes after garbage bytes`() {
        val encoded = FrameCodec.encode(FrameType.EVENT, 9, "ok".toByteArray())
        val decoder = FrameDecoder()

        val decoded = decoder.feed(byteArrayOf(0x00, 0x11, 0x22) + encoded)

        assertEquals(1, decoded.size)
        assertEquals(FrameType.EVENT, decoded[0].type)
        assertEquals("ok", decoded[0].payloadAsString)
    }

    @Test
    fun `rejects payloads larger than the protocol maximum`() {
        val tooLarge = ByteArray(FrameCodec.MAX_PAYLOAD_SIZE + 1)
        try {
            FrameCodec.encode(FrameType.PCAP, 1, tooLarge)
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
