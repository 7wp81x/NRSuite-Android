package com.swp81x.nrsuite.core.flasher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

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

        val ops = transport.writtenOps()
        assertEquals(listOf(0x08, 0x0D, 0x02, 0x03, 0x04), ops)
        assertTrue(progress.isNotEmpty())
        assertEquals(100, progress.last())
        assertTrue("output must be flushed before commands", transport.flushOutputCalls > 0)
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

        val ops = transport.writtenOps()
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

        val ops = transport.writtenOps()
        assertEquals(listOf(0x08, 0x0D, 0x02, 0x03, 0x04), ops)
    }

    @Test
    fun `drains multiple sync replies before spi attach`() {
        val transport = FakeFlasherTransport()
        // ESP32-C5 can emit a burst of SYNC replies. Make sure the flasher
        // consumes that burst rather than carrying a delayed reply into the
        // SPI_ATTACH exchange.
        transport.extraResponseOpsOnWrite[0x08] = mutableListOf(0x08, 0x08, 0x08)
        val flasher = Esp32Flasher(transport, supportsEncryptedFlash = false)
        val logs = mutableListOf<String>()

        flasher.flash(
            firmware = ByteArray(8) { it.toByte() },
            offset = 0,
            resetMode = Esp32Flasher.ResetMode.NONE,
            onLog = { logs += it },
        )

        assertEquals(listOf(0x08, 0x0D, 0x02, 0x03, 0x04), transport.writtenOps())
        assertTrue(logs.any { it.contains("Drained extra ROM SYNC reply") })
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
    fun `classic reset sequence matches esptool short timing`() {
        val transport = FakeFlasherTransport()
        val flasher = Esp32Flasher(transport, supportsEncryptedFlash = false)

        flasher.flash(
            firmware = ByteArray(8) { it.toByte() },
            offset = 0,
            resetMode = Esp32Flasher.ResetMode.CLASSIC,
        )

        assertEquals(
            listOf(
                "dtr=false" to "rts=false",
                "dtr=false" to "rts=true",
                "dtr=true" to "rts=true",
                "dtr=true" to "rts=false",
                "dtr=false" to "rts=false",
            ),
            transport.controlLineEvents,
        )
    }

    @Test
    fun `esptool reset timing vectors are preserved`() {
        assertEquals(
            listOf(
                ResetControlStep(dtr = false),
                ResetControlStep(rts = true, delayAfterMs = 100L),
                ResetControlStep(dtr = true),
                ResetControlStep(rts = false, delayAfterMs = 50L),
                ResetControlStep(dtr = false),
            ),
            Esp32ResetSequences.classic(),
        )
        assertEquals(
            listOf(
                ResetControlStep(dtr = false),
                ResetControlStep(rts = true, delayAfterMs = 100L),
                ResetControlStep(dtr = true),
                ResetControlStep(rts = false, delayAfterMs = 550L),
                ResetControlStep(dtr = false),
            ),
            Esp32ResetSequences.classic(
                Esp32ResetSequences.ESPTOOL_CLASSIC_EXTRA_RELEASE_DELAY_MS,
            ),
        )
        assertEquals(
            listOf(
                ResetControlStep(rts = false),
                ResetControlStep(dtr = false, delayAfterMs = 100L),
                ResetControlStep(dtr = true),
                ResetControlStep(rts = false, delayAfterMs = 100L),
                ResetControlStep(rts = true),
                ResetControlStep(dtr = false),
                ResetControlStep(rts = true, delayAfterMs = 100L),
                ResetControlStep(dtr = false),
                ResetControlStep(rts = false),
            ),
            Esp32ResetSequences.usbJtagSerial(),
        )
    }

    @Test
    fun `manual recovery falls back to automatic reset after sync failure`() {
        val transport = FakeFlasherTransport()
        transport.syncWriteFailuresRemaining = 6
        val flasher = Esp32Flasher(transport, supportsEncryptedFlash = false)

        flasher.flash(
            firmware = ByteArray(8) { it.toByte() },
            offset = 0,
            resetMode = Esp32Flasher.ResetMode.NONE,
            fallbackResetMode = Esp32Flasher.ResetMode.CLASSIC,
        )

        assertFalse(transport.controlLineEvents.isEmpty())
        assertEquals(listOf(0x08, 0x0D, 0x02, 0x03, 0x04), transport.writtenOps().takeLast(5))
    }

    @Test
    fun `spi attach failure resynchronizes before retry`() {
        val transport = FakeFlasherTransport()
        transport.throwOnceOnOp[0x0D] = IOException("injected SPI attach write failure")
        val flasher = Esp32Flasher(transport, supportsEncryptedFlash = false)

        flasher.flash(
            firmware = ByteArray(8) { it.toByte() },
            offset = 0,
            resetMode = Esp32Flasher.ResetMode.NONE,
        )

        assertEquals(
            listOf(0x08, 0x0D, 0x08, 0x0D, 0x02, 0x03, 0x04),
            transport.writtenOps(),
        )
    }

    @Test
    fun `reset mode policy distinguishes bridge and native usb`() {
        assertEquals(
            Esp32Flasher.ResetMode.USB_JTAG,
            recommendedEsp32ResetMode(0x303A, 0x1001),
        )
        assertEquals(
            Esp32Flasher.ResetMode.CLASSIC,
            recommendedEsp32ResetMode(0x10C4, 0xEA60),
        )
        assertEquals(
            Esp32Flasher.ResetMode.CLASSIC,
            recommendedEsp32ResetMode(0x1A86, 0x7523),
        )
        assertEquals(
            Esp32Flasher.ResetMode.CLASSIC,
            recommendedEsp32ResetMode(0x1A86, 0x55D4),
        )
    }

    private class FakeFlasherTransport : FlasherTransport {
        override var isOpen: Boolean = false
            private set

        val writtenPackets = mutableListOf<ByteArray>()
        val extraResponseOpsOnWrite = mutableMapOf<Int, MutableList<Int>>()
        val throwOnceOnOp = mutableMapOf<Int, IOException>()
        val controlLineEvents = mutableListOf<Pair<String, String>>()
        var syncWriteFailuresRemaining = 0
        var flushOutputCalls = 0
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

            throwOnceOnOp.remove(op)?.let { throw it }
            if (op == 0x08 && syncWriteFailuresRemaining > 0) {
                syncWriteFailuresRemaining--
                throw IOException("injected SYNC write failure ($syncWriteFailuresRemaining left)")
            }

            extraResponseOpsOnWrite.remove(op)
                ?.forEach { extraOp -> appendResponse(extraOp) }
            appendResponse(op)
        }

        override fun flushInput() {
            input = ByteArray(0)
            inputOffset = 0
        }

        override fun flushOutput() {
            flushOutputCalls++
        }

        override fun setDtr(value: Boolean) {
            lastDtr = value
            controlLineEvents += "dtr=$value" to "rts=$lastRts"
        }

        override fun setRts(value: Boolean) {
            lastRts = value
            controlLineEvents += "dtr=$lastDtr" to "rts=$value"
        }

        fun writtenOps(): List<Int> = writtenPackets.map { encoded ->
            SlipDecoder().feed(encoded).first()[1].toInt() and 0xFF
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
