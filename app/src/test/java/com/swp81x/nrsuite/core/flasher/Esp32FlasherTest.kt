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
    fun `esp32 stub path uploads runs and switches baud`() {
        val transport = FakeFlasherTransport()
        val flasher = Esp32Flasher(
            transport,
            supportsEncryptedFlash = false,
            useStub = true,
        )

        flasher.flash(
            firmware = ByteArray(8) { it.toByte() },
            offset = 0,
            resetMode = Esp32Flasher.ResetMode.NONE,
        )

        assertEquals(
            listOf(0x08, 0x05, 0x07, 0x05, 0x07, 0x06, 0x0F, 0x0D, 0x02, 0x03, 0x04),
            transport.writtenOps(),
        )
        assertEquals(460_800, transport.currentBaudRate)
    }

    @Test
    fun `esp32 stub command payloads match stub protocol`() {
        val transport = FakeFlasherTransport()
        val flasher = Esp32Flasher(
            transport,
            supportsEncryptedFlash = false,
            useStub = true,
        )

        flasher.flash(
            firmware = ByteArray(0x4001) { (it and 0xFF).toByte() },
            offset = 0,
            resetMode = Esp32Flasher.ResetMode.NONE,
        )

        val commands = transport.writtenCommands()
        val memBegins = commands.filter { it.op == 0x05 }
        assertEquals(2, memBegins.size)
        assertEquals(Esp32StubFlasher.text.size, readIntLe(memBegins[0].data, 0))
        assertEquals(0x1800, readIntLe(memBegins[0].data, 8))
        assertEquals(Esp32StubFlasher.TEXT_START.toInt(), readIntLe(memBegins[0].data, 12))
        assertEquals(Esp32StubFlasher.data.size, readIntLe(memBegins[1].data, 0))
        assertEquals(Esp32StubFlasher.DATA_START.toInt(), readIntLe(memBegins[1].data, 12))

        val memEnd = commands.single { it.op == 0x06 }
        assertEquals(8, memEnd.data.size)
        assertEquals(0, readIntLe(memEnd.data, 0))
        assertEquals(Esp32StubFlasher.ENTRY.toInt(), readIntLe(memEnd.data, 4))

        val baudChange = commands.single { it.op == 0x0F }
        assertEquals(8, baudChange.data.size)
        assertEquals(460_800, readIntLe(baudChange.data, 0))
        assertEquals(115_200, readIntLe(baudChange.data, 4))

        // The ROM consumes a 4-byte hspi_arg plus a 4-byte legacy flag, while
        // the ESP32 stub consumes only hspi_arg. Sending 8 bytes in stub mode
        // produced SPI_ATTACH status=0x01 on hardware.
        val spiAttach = commands.single { it.op == 0x0D }
        assertEquals(4, spiAttach.data.size)

        val flashBegin = commands.single { it.op == 0x02 }
        assertEquals(20, flashBegin.data.size)
        assertEquals(0x4001, readIntLe(flashBegin.data, 0))
        assertEquals(2, readIntLe(flashBegin.data, 4))
        assertEquals(0x4000, readIntLe(flashBegin.data, 8))
        assertEquals(0, readIntLe(flashBegin.data, 12))
        assertEquals(0, readIntLe(flashBegin.data, 16))

        val flashBlocks = commands.filter { it.op == 0x03 }
        assertEquals(2, flashBlocks.size)
        assertEquals(0x4000, readIntLe(flashBlocks[0].data, 0))
        assertEquals(0x0001, readIntLe(flashBlocks[1].data, 0))
    }

    @Test
    fun `no-stub command payloads retain rom protocol sizes`() {
        val transport = FakeFlasherTransport()
        val flasher = Esp32Flasher(transport, supportsEncryptedFlash = false)

        flasher.flash(
            firmware = ByteArray(8) { it.toByte() },
            offset = 0,
            resetMode = Esp32Flasher.ResetMode.NONE,
        )

        val commands = transport.writtenCommands()
        assertEquals(8, commands.single { it.op == 0x0D }.data.size)
        val flashBegin = commands.single { it.op == 0x02 }
        assertEquals(16, flashBegin.data.size)
        assertEquals(0x400, readIntLe(flashBegin.data, 8))
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
        override var currentBaudRate: Int = 115_200
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

        override fun setBaudRate(baudRate: Int) {
            currentBaudRate = baudRate
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
            if (op == 0x06) {
                // MEM_END starts the stub, which emits a raw OHAI greeting.
                input = input + "OHAI".toByteArray(Charsets.US_ASCII)
            }
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

        fun writtenCommands(): List<DecodedCommand> = writtenPackets.map { encoded ->
            val frame = SlipDecoder().feed(encoded).first()
            val payloadLength = (frame[2].toInt() and 0xFF) or
                ((frame[3].toInt() and 0xFF) shl 8)
            DecodedCommand(
                op = frame[1].toInt() and 0xFF,
                data = frame.copyOfRange(8, 8 + payloadLength),
            )
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

    private data class DecodedCommand(
        val op: Int,
        val data: ByteArray,
    )

    private fun readIntLe(source: ByteArray, offset: Int): Int =
        (source[offset].toInt() and 0xFF) or
            ((source[offset + 1].toInt() and 0xFF) shl 8) or
            ((source[offset + 2].toInt() and 0xFF) shl 16) or
            ((source[offset + 3].toInt() and 0xFF) shl 24)
}
