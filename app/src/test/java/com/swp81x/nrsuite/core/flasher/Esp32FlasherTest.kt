package com.swp81x.nrsuite.core.flasher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Esp32FlasherTest {

    @Test
    fun `writes a complete no-stub image sequence`() {
        val transport = FakeFlasherTransport()
        val flasher = Esp32Flasher(transport, supportsEncryptedFlash = false)
        val progress = mutableListOf<Int>()

        flasher.flash(
            firmware = ByteArray(8) { it.toByte() },
            offset = 0,
            resetMode = Esp32Flasher.ResetMode.NONE,
            onProgress = { progress += it },
        )

        val ops = transport.writtenPackets.map { encoded ->
            SlipDecoder().feed(encoded).first()[1].toInt() and 0xFF
        }
        assertEquals(listOf(0x08, 0x0D, 0x02, 0x03, 0x04), ops)
        assertTrue(progress.isNotEmpty())
        assertEquals(100, progress.last())
    }

    private class FakeFlasherTransport : FlasherTransport {
        override var isOpen: Boolean = false
            private set

        val writtenPackets = mutableListOf<ByteArray>()
        private var input = ByteArray(0)
        private var inputOffset = 0

        override fun open() {
            isOpen = true
        }

        override fun close() {
            isOpen = false
        }

        override fun read(buffer: ByteArray, timeoutMs: Int): Int {
            val available = input.size - inputOffset
            if (available <= 0) return 0
            val count = minOf(buffer.size, available)
            input.copyInto(buffer, destinationOffset = 0, startIndex = inputOffset, endIndex = inputOffset + count)
            inputOffset += count
            if (inputOffset >= input.size) {
                input = ByteArray(0)
                inputOffset = 0
            }
            return count
        }

        override fun write(data: ByteArray, timeoutMs: Int) {
            writtenPackets += data.copyOf()
            val packet = SlipDecoder().feed(data).firstOrNull() ?: return
            if (packet.size < 4) return
            val op = packet[1].toInt() and 0xFF
            appendResponse(op)
        }

        override fun flushInput() = Unit

        override fun setDtr(value: Boolean) = Unit

        override fun setRts(value: Boolean) = Unit

        private fun appendResponse(op: Int) {
            val payload = byteArrayOf(0x00, 0x00)
            val frame = byteArrayOf(
                0x01,
                op.toByte(),
                payload.size.toByte(),
                0x00,
                0x00, 0x00, 0x00, 0x00,
            ) + payload
            val encoded = SlipCodec.encode(frame)
            input = input + encoded
        }
    }
}
