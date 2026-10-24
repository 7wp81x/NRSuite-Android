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

    @Test
    fun `can opt into region erase when requested`() {
        val transport = FakeFlasherTransport()
        val flasher = Esp32Flasher(transport, supportsEncryptedFlash = false)

        flasher.flash(
            firmware = ByteArray(8) { it.toByte() },
            offset = 0,
            resetMode = Esp32Flasher.ResetMode.NONE,
            eraseBeforeFlash = true,
        )

        val ops = transport.writtenPackets.map { encoded ->
            SlipDecoder().feed(encoded).first()[1].toInt() and 0xFF
        }
        assertEquals(listOf(0x08, 0x0D, 0xD1, 0x08, 0x02, 0x03, 0x04), ops)
    }

    @Test
    fun `keeps a response batched behind an extra sync reply`() {
        val transport = FakeFlasherTransport()
        // A ROM can emit extra SYNC replies. Make the response to SPI_ATTACH
        // arrive in the same USB read as one of those stale frames. The
        // flasher must ignore the stale frame without dropping the real
        // response that followed it in the same decoded chunk.
        transport.extraResponseOpsOnWrite[0x0D] = mutableListOf(0x08)
        val flasher = Esp32Flasher(transport, supportsEncryptedFlash = false)

        flasher.flash(
            firmware = ByteArray(8) { it.toByte() },
            offset = 0,
            resetMode = Esp32Flasher.ResetMode.NONE,
        )

        val ops = transport.writtenPackets.map { encoded ->
            SlipDecoder().feed(encoded).first()[1].toInt() and 0xFF
        }
        assertEquals(listOf(0x08, 0x0D, 0x02, 0x03, 0x04), ops)
    }

    @Test
    fun `manual mode does not toggle reset lines`() {
        val transport = FakeFlasherTransport()
        val flasher = Esp32Flasher(transport, supportsEncryptedFlash = false)

        flasher.flash(
            firmware = ByteArray(8) { it.toByte() },
            offset = 0,
            resetMode = Esp32Flasher.ResetMode.NONE,
        )

        assertTrue(transport.controlLineEvents.isEmpty())
    }

    @Test
    fun `classic reset releases both control lines afterward`() {
        val transport = FakeFlasherTransport()
        val flasher = Esp32Flasher(transport, supportsEncryptedFlash = false)

        flasher.flash(
            firmware = ByteArray(8) { it.toByte() },
            offset = 0,
            resetMode = Esp32Flasher.ResetMode.CLASSIC,
        )

        assertEquals("dtr=false", transport.controlLineEvents.last().first)
        assertEquals("rts=false", transport.controlLineEvents.last().second)
    }

    private class FakeFlasherTransport : FlasherTransport {
        override var isOpen: Boolean = false
            private set

        val writtenPackets = mutableListOf<ByteArray>()
        val extraResponseOpsOnWrite = mutableMapOf<Int, MutableList<Int>>()
        val controlLineEvents = mutableListOf<Pair<String, String>>()
        private var input = ByteArray(0)
        private var inputOffset = 0
        private var lastDtr = false
        private var lastRts = false

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
            extraResponseOpsOnWrite.remove(op)
                ?.forEach { extraOp -> appendResponse(extraOp) }
            appendResponse(op)
        }

        override fun flushInput() {
            input = ByteArray(0)
            inputOffset = 0
        }

        override fun setDtr(value: Boolean) {
            lastDtr = value
            controlLineEvents += "dtr=$value" to "rts=$lastRts"
        }

        override fun setRts(value: Boolean) {
            lastRts = value
            controlLineEvents += "dtr=$lastDtr" to "rts=$value"
        }

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
