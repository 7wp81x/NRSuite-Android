package com.swp81x.nrsuite.core.flasher

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.ArrayDeque
import kotlin.math.max

/**
 * Minimal ESP32 ROM-bootloader flasher.
 *
 * This does not use the NRSuite bridge protocol or any OTA mechanism. It talks
 * directly to the ESP32 ROM serial bootloader, writes a complete image at the
 * chosen flash offset, and reboots the chip.
 *
 * Only the no-stub command subset is implemented, so it works with ROM
 * bootloaders across ESP32, ESP32-S2, ESP32-S3, ESP32-C3, and ESP32-C5.
 */
class Esp32Flasher(
    private val transport: FlasherTransport,
    private val supportsEncryptedFlash: Boolean,
) {
    enum class ResetMode {
        CLASSIC,
        USB_JTAG,
        NONE,
    }

    private data class Response(
        val op: Int,
        val value: Long,
        val data: ByteArray,
    )

    private val decoder = SlipDecoder()

    // A single USB read can contain more than one SLIP frame. Keep decoded
    // frames queued instead of dropping every frame after the first one. Some
    // ROM bootloaders send multiple SYNC replies, and losing a later frame can
    // otherwise make the next command (notably SPI_ATTACH) time out.
    private val pendingResponses = ArrayDeque<Response>()

    private var diagnostic: (String) -> Unit = {}

    fun flash(
        firmware: ByteArray,
        offset: Int = 0,
        resetMode: ResetMode = ResetMode.CLASSIC,
        fallbackResetMode: ResetMode? = null,
        eraseBeforeFlash: Boolean = false,
        onStage: (String) -> Unit = {},
        onProgress: (Int) -> Unit = {},
        onLog: (String) -> Unit = {},
    ) {
        require(firmware.isNotEmpty()) { "Firmware image is empty" }
        require(offset >= 0) { "Flash offset must be >= 0" }

        diagnostic = onLog
        val openStarted = System.nanoTime()
        transport.open()
        try {
            log("Serial port opened in ${elapsedMs(openStarted)} ms; settling ${PORT_SETTLE_MS} ms.")
            Thread.sleep(PORT_SETTLE_MS)
            resetInput()
            log("Serial input flushed; connecting to ROM bootloader (mode=$resetMode).")

            connectToRomBootloader(resetMode, fallbackResetMode)
            Thread.sleep(POST_SYNC_SETTLE_MS)

            onStage("Configuring flash access...")
            flashSpiAttach()

            if (eraseBeforeFlash) {
                // Region erase (ROM op 0xD1), scoped to [offset, offset+size),
                // not a full-chip erase. The ROM aligns this to sector
                // boundaries internally, same as esptool.py's erase_region /
                // the implicit erase FLASH_BEGIN already does. This is
                // normally redundant with FLASH_BEGIN's own erase-as-you-go
                // behavior, but some setups want an explicit erase pass
                // first (e.g. to confirm a clean region before a verify
                // step); unlike the old full-chip erase, its duration scales
                // with the firmware size being written, not total flash size.
                onStage("Erasing flash...")
                eraseRegion(offset, firmware.size, onStage)

                // A long erase is still possible for big images on slow
                // flash. On native-USB chips (S2/S3/C3) the ROM doesn't
                // service the USB peripheral while blocked on an erase, so
                // re-sync instead of assuming the link survived — this fails
                // fast with a clear error if the connection dropped, rather
                // than hanging silently on the first post-erase command.
                onStage("Reconnecting after erase...")
                sync()
                Thread.sleep(POST_SYNC_SETTLE_MS)
            }
            onStage("Writing firmware...")
            flashBegin(firmware.size, offset)

            var written = 0
            var sequence = 0
            while (written < firmware.size) {
                val blockLength = minOf(FLASH_WRITE_SIZE, firmware.size - written)
                val block = firmware.copyOfRange(written, written + blockLength)
                flashBlock(block, sequence)
                written += blockLength
                sequence++
                onProgress(((written * 100L) / firmware.size).toInt().coerceIn(0, 100))
            }

            flashFinish(reboot = true)
            onProgress(100)
        } finally {
            runCatching { transport.close() }
        }
    }

    private data class ResetAttempt(
        val description: String,
        val enterReset: () -> Unit,
    )

    private fun connectToRomBootloader(mode: ResetMode, fallbackResetMode: ResetMode?) {
        var effectiveMode = mode
        if (mode == ResetMode.NONE) {
            log("Manual bootloader mode: skipping DTR/RTS reset and syncing as-is.")
            try {
                sync(attempts = MANUAL_SYNC_ATTEMPTS)
                return
            } catch (t: Throwable) {
                if (fallbackResetMode == null || fallbackResetMode == ResetMode.NONE) throw t
                log(
                    "Manual sync failed (${t.message ?: t.javaClass.simpleName}); " +
                        "trying automatic $fallbackResetMode reset fallback.",
                )
                effectiveMode = fallbackResetMode
            }
        }

        // Match esptool's connect strategy: try seven reset/connect attempts,
        // cycling through the short and long classic reset delays. The long
        // release delay is esptool's extra-delay fallback for USB-UART bridges
        // whose auto-reset circuit needs more time after EN is released.
        val resetAttempts = when (effectiveMode) {
            ResetMode.CLASSIC -> listOf(
                ResetAttempt("classic reset, esptool 50 ms release delay") {
                    classicReset(Esp32ResetSequences.ESPTOOL_CLASSIC_RELEASE_DELAY_MS)
                },
                ResetAttempt("classic reset, esptool extended 550 ms release delay") {
                    classicReset(Esp32ResetSequences.ESPTOOL_CLASSIC_EXTRA_RELEASE_DELAY_MS)
                },
            )
            ResetMode.USB_JTAG -> listOf(
                ResetAttempt("USB-JTAG-Serial reset, esptool timing") {
                    usbJtagSerialReset()
                },
            )
            ResetMode.NONE -> emptyList()
        }
        if (resetAttempts.isEmpty()) {
            throw IOException("No automatic reset strategy is available.")
        }

        var lastError: Throwable? = null
        repeat(BOOTLOADER_CONNECT_ATTEMPTS) { attempt ->
            val strategy = resetAttempts[attempt % resetAttempts.size]
            resetInput()
            log(
                "Bootloader connection attempt ${attempt + 1}/$BOOTLOADER_CONNECT_ATTEMPTS " +
                    "(${strategy.description}).",
            )
            strategy.enterReset()
            // Give the ROM time to emit its boot log and settle the USB
            // endpoint before the first SYNC packet.
            Thread.sleep(POST_RESET_DELAY_MS)
            resetInput()

            try {
                sync(attempts = SYNC_ATTEMPTS_PER_RESET)
                return
            } catch (t: Throwable) {
                lastError = t
                log(
                    "Connection attempt ${attempt + 1} failed after reset: " +
                        (t.message ?: t.javaClass.simpleName),
                )
                Thread.sleep(RESET_RETRY_DELAY_MS)
            }
        }

        throw IOException(
            "Could not enter the ROM bootloader after $BOOTLOADER_CONNECT_ATTEMPTS reset attempts. " +
                "Hold BOOT, tap RESET, then retry or use manual bootloader recovery. " +
                (lastError?.message ?: "No response from the device."),
        )
    }

    private fun classicReset(releaseDelayMs: Long) {
        applyResetSequence(
            name = "classic DTR/RTS",
            steps = Esp32ResetSequences.classic(releaseDelayMs),
        )
    }

    private fun usbJtagSerialReset() {
        applyResetSequence(
            name = "USB-JTAG-Serial DTR/RTS",
            steps = Esp32ResetSequences.usbJtagSerial(),
        )
    }

    private fun applyResetSequence(name: String, steps: List<ResetControlStep>) {
        log("Applying $name reset sequence (${steps.size} line operations)...")
        for (step in steps) {
            step.dtr?.let { transport.setDtr(it) }
            step.rts?.let { transport.setRts(it) }
            if (step.delayAfterMs > 0) {
                Thread.sleep(step.delayAfterMs)
            }
        }
    }

    private fun resetInput() {
        runCatching { transport.flushInput() }
        // esptool flushes both serial directions before each sync/command.
        // A stale host-to-device byte would otherwise corrupt the next SLIP
        // frame and make the ROM silently ignore it.
        runCatching { transport.flushOutput() }
        decoder.reset()
        pendingResponses.clear()
    }

    private fun sync(attempts: Int = SYNC_ATTEMPTS) {
        var lastError: Throwable? = null
        repeat(attempts) { attempt ->
            log("ROM SYNC attempt ${attempt + 1}/$attempts...")
            try {
                val response = sendCommand(
                    op = OP_SYNC,
                    data = SYNC_PAYLOAD,
                    checksum = 0,
                    timeoutMs = SYNC_TIMEOUT_MS,
                )
                log(
                    "ROM SYNC ACK received (value=0x%08x, data=%d bytes)."
                        .format(response.value, response.data.size),
                )

                // ESP ROM bootloaders can emit a burst of additional SYNC
                // replies after the first ACK. esptool reads up to seven of
                // them before issuing the next command. On ESP32-C5 a delayed
                // extra reply can otherwise arrive while SPI_ATTACH is in
                // flight, so drain the burst explicitly instead of relying on
                // a fixed sleep followed by a flush.
                drainExtraSyncResponses()
                resetInput()
                return
            } catch (t: Throwable) {
                lastError = t
                log("ROM SYNC attempt ${attempt + 1} failed: ${t.message ?: t.javaClass.simpleName}")
                Thread.sleep(SYNC_RETRY_DELAY_MS)
            }
        }
        throw IOException(
            "Could not synchronize with the ROM bootloader. " +
                "Hold BOOT, tap RESET, then retry. (${lastError?.message ?: "no response"})",
        )
    }

    /**
     * Consume the extra ROM SYNC replies that follow a successful SYNC.
     *
     * esptool's sync path reads up to seven additional packets before issuing
     * the next ROM command. The C5 ROM is especially sensitive here: a later
     * SYNC reply can arrive after the first SPI_ATTACH write and leave the
     * command unanswered. Use a bounded total drain window so normal boards
     * do not pay a long delay when they only send one reply.
     */
    private fun drainExtraSyncResponses() {
        val deadline = nowMs() + SYNC_DRAIN_TIMEOUT_MS
        var drained = 0
        while (drained < MAX_EXTRA_SYNC_RESPONSES && nowMs() < deadline) {
            val remaining = (deadline - nowMs()).toInt().coerceAtLeast(1)
            val response = readResponse(remaining) ?: break
            if (response.op == OP_SYNC) {
                drained++
                log(
                    "Drained extra ROM SYNC reply $drained/$MAX_EXTRA_SYNC_RESPONSES " +
                        "(data=${response.data.size} bytes).",
                )
            } else {
                log(
                    "Discarding non-SYNC ROM response op=0x%02x during SYNC drain."
                        .format(response.op),
                )
            }
        }
        log("ROM SYNC drain complete after $drained extra reply/replies.")
    }

    private fun flashSpiAttach() {
        var lastError: Throwable? = null
        for (attempt in 0 until SPI_ATTACH_ATTEMPTS) {
            log(
                "SPI attach attempt ${attempt + 1}/$SPI_ATTACH_ATTEMPTS " +
                    "(timeout=${SPI_ATTACH_TIMEOUT_MS} ms)...",
            )
            try {
                // hspi_arg = 0, followed by the ROM-only "is legacy" byte field.
                checkCommand(
                    description = "enable SPI flash",
                    op = OP_SPI_ATTACH,
                    data = ByteArray(8),
                    timeoutMs = SPI_ATTACH_TIMEOUT_MS,
                )
                log("SPI flash access enabled.")
                return
            } catch (t: Throwable) {
                lastError = t
                log("SPI attach attempt ${attempt + 1} failed: ${t.message ?: t.javaClass.simpleName}")
                if (attempt + 1 >= SPI_ATTACH_ATTEMPTS) break
                Thread.sleep(SPI_ATTACH_RETRY_DELAY_MS)

                // A lost SPI_ATTACH reply can leave the ROM and host out of
                // step. Re-run SYNC before retrying instead of blindly sending
                // another 0x0D packet into a link that may already be stale.
                log("Resynchronizing before SPI attach retry...")
                try {
                    sync(attempts = SYNC_ATTEMPTS_AFTER_COMMAND_TIMEOUT)
                    Thread.sleep(POST_SYNC_SETTLE_MS)
                } catch (syncError: Throwable) {
                    log(
                        "Re-sync before SPI attach retry failed: " +
                            (syncError.message ?: syncError.javaClass.simpleName),
                    )
                }
            }
        }
        throw IOException(
            "Could not enable SPI flash after $SPI_ATTACH_ATTEMPTS attempts. " +
                (lastError?.message ?: "No response from the ROM bootloader."),
        )
    }

    private fun eraseRegion(offset: Int, size: Int, onStage: (String) -> Unit) {
        val params = ByteArray(8)
        writeIntLe(params, 0, offset)
        writeIntLe(params, 4, size)

        // Mirrors esptool.py's ERASE_REGION_TIMEOUT_PER_MB scaling: budget
        // time proportional to the region being erased, with a sane floor
        // for small images.
        val timeoutMs = max(
            ERASE_REGION_MIN_TIMEOUT_MS,
            ((size.toLong() * ERASE_REGION_TIMEOUT_PER_MB_MS) / (1024 * 1024)).toInt(),
        )

        val progressThread = Thread {
            var elapsedSeconds = 0L
            try {
                while (!Thread.currentThread().isInterrupted) {
                    Thread.sleep(5_000)
                    elapsedSeconds += 5
                    onStage("Erasing flash... (${elapsedSeconds}s elapsed)")
                }
            } catch (_: InterruptedException) {
                // Erase finished or failed; stop ticking.
            }
        }.apply {
            isDaemon = true
            start()
        }

        try {
            checkCommand(
                description = "erase flash region",
                op = OP_ERASE_REGION,
                data = params,
                timeoutMs = timeoutMs,
            )
        } finally {
            progressThread.interrupt()
        }
    }

    private fun flashBegin(size: Int, offset: Int) {
        val numBlocks = (size + FLASH_WRITE_SIZE - 1) / FLASH_WRITE_SIZE
        val params = ByteArray(if (supportsEncryptedFlash) 20 else 16)
        writeIntLe(params, 0, size)
        writeIntLe(params, 4, numBlocks)
        writeIntLe(params, 8, FLASH_WRITE_SIZE)
        writeIntLe(params, 12, offset)
        if (supportsEncryptedFlash) {
            writeIntLe(params, 16, 0)
        }

        val timeoutMs = max(DEFAULT_BEGIN_TIMEOUT_MS, (size / (1024 * 1024) + 1) * 30_000)
        checkCommand(
            description = "start flash write",
            op = OP_FLASH_BEGIN,
            data = params,
            timeoutMs = timeoutMs,
        )
    }

    private fun flashBlock(block: ByteArray, sequence: Int) {
        val params = ByteArray(16 + block.size)
        writeIntLe(params, 0, block.size)
        writeIntLe(params, 4, sequence)
        block.copyInto(params, destinationOffset = 16)

        var lastError: Throwable? = null
        repeat(FLASH_BLOCK_ATTEMPTS) {
            try {
                checkCommand(
                    description = "write flash block $sequence",
                    op = OP_FLASH_DATA,
                    data = params,
                    checksum = checksum(block),
                    timeoutMs = FLASH_BLOCK_TIMEOUT_MS,
                )
                return
            } catch (t: Throwable) {
                lastError = t
                Thread.sleep(100)
            }
        }
        throw IOException("Flash block $sequence failed: ${lastError?.message ?: "unknown error"}")
    }

    private fun flashFinish(reboot: Boolean) {
        try {
            val rebootFlag = if (reboot) 0 else 1
            val finishData = ByteArray(4)
            writeIntLe(finishData, 0, rebootFlag)
            checkCommand(
                description = "finish flash write",
                op = OP_FLASH_END,
                data = finishData,
                timeoutMs = 2_000,
            )
        } catch (_: Throwable) {
            // A reboot can drop the final response, so this is best-effort.
        }
    }

    private fun checkCommand(
        description: String,
        op: Int,
        data: ByteArray,
        checksum: Int = 0,
        timeoutMs: Int = DEFAULT_COMMAND_TIMEOUT_MS,
    ): Response {
        val response = sendCommand(op, data, checksum, timeoutMs)
        if (response.data.size < STATUS_BYTES_LENGTH) {
            throw IOException("$description: short status response")
        }
        val statusOffset = response.data.size - STATUS_BYTES_LENGTH
        val code = response.data[statusOffset].toInt() and 0xFF
        if (code != 0) {
            val reason = response.data[statusOffset + 1].toInt() and 0xFF
            throw IOException("$description failed (status=0x%02x reason=0x%02x)".format(code, reason))
        }
        return response
    }

    private fun sendCommand(
        op: Int,
        data: ByteArray,
        checksum: Int,
        timeoutMs: Int,
    ): Response {
        // Start each command from a clean RX state. This discards any late
        // response from a previous retry and clears partially decoded SLIP.
        resetInput()

        val packet = ByteArrayOutputStream(8 + data.size).apply {
            write(0x00)
            write(op)
            writeShortLe(data.size)
            writeIntLe(checksum)
            write(data)
        }.toByteArray()

        if (op != OP_FLASH_DATA) {
            log("TX ROM op=0x%02x payload=%d bytes timeout=%d ms.".format(op, data.size, timeoutMs))
        }
        transport.write(SlipCodec.encode(packet), WRITE_TIMEOUT_MS)

        val deadline = nowMs() + timeoutMs
        while (nowMs() < deadline) {
            val remaining = (deadline - nowMs()).toInt().coerceAtLeast(1)
            val response = readResponse(remaining) ?: continue
            if (response.op == op) {
                if (op != OP_FLASH_DATA) {
                    log("RX ROM op=0x%02x data=%d bytes.".format(response.op, response.data.size))
                }
                return response
            }
            log(
                "Ignoring out-of-order ROM response op=0x%02x while waiting for op=0x%02x."
                    .format(response.op, op),
            )
        }
        throw IOException("Timed out waiting for ROM response to op 0x%02x".format(op))
    }

    private fun readResponse(timeoutMs: Int): Response? {
        pendingResponses.pollFirst()?.let { return it }

        val deadline = nowMs() + timeoutMs
        val buffer = ByteArray(READ_BUFFER_SIZE)
        while (nowMs() < deadline) {
            val remaining = (deadline - nowMs()).toInt().coerceAtLeast(1)
            val readTimeout = remaining.coerceAtMost(MAX_READ_TIMEOUT_MS)
            val count = transport.read(buffer, readTimeout)
            if (count <= 0) {
                continue
            }

            // Queue every complete frame from this read. Returning from
            // inside this loop would silently discard any additional frames
            // that arrived in the same USB transfer.
            for (frame in decoder.feed(buffer.copyOf(count))) {
                parseResponse(frame)?.let { pendingResponses.addLast(it) }
            }
            if (pendingResponses.isNotEmpty()) {
                return pendingResponses.removeFirst()
            }
        }
        return null
    }

    private fun parseResponse(frame: ByteArray): Response? {
        if (frame.size < HEADER_LENGTH) return null
        val direction = frame[0].toInt() and 0xFF
        if (direction != RESPONSE_DIRECTION) return null
        val op = frame[1].toInt() and 0xFF
        val length = readU16Le(frame, 2)
        if (frame.size < HEADER_LENGTH + length) return null
        val value = readU32Le(frame, 4)
        val data = frame.copyOfRange(HEADER_LENGTH, HEADER_LENGTH + length)
        return Response(op = op, value = value, data = data)
    }

    private fun checksum(data: ByteArray, initialState: Int = CHECKSUM_MAGIC): Int {
        var value = initialState
        for (byte in data) {
            value = value xor (byte.toInt() and 0xFF)
        }
        return value
    }

    private fun ByteArrayOutputStream.writeShortLe(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeIntLe(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 24) and 0xFF)
    }

    private fun writeIntLe(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value and 0xFF).toByte()
        target[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        target[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        target[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun readU16Le(source: ByteArray, offset: Int): Int {
        return (source[offset].toInt() and 0xFF) or
            ((source[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readU32Le(source: ByteArray, offset: Int): Long {
        var value = 0L
        for (index in 0 until 4) {
            value = value or ((source[offset + index].toLong() and 0xFFL) shl (index * 8))
        }
        return value
    }

    private fun nowMs(): Long = System.nanoTime() / 1_000_000L

    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000L

    private fun log(message: String) {
        runCatching { diagnostic(message) }
    }

    companion object {
        private const val OP_FLASH_BEGIN = 0x02
        private const val OP_FLASH_DATA = 0x03
        private const val OP_FLASH_END = 0x04
        private const val OP_SYNC = 0x08
        private const val OP_SPI_ATTACH = 0x0D
        private const val OP_ERASE_REGION = 0xD1

        private const val RESPONSE_DIRECTION = 0x01
        private const val HEADER_LENGTH = 8
        private const val STATUS_BYTES_LENGTH = 2
        private const val CHECKSUM_MAGIC = 0xEF

        private const val FLASH_WRITE_SIZE = 0x400
        private const val FLASH_BLOCK_ATTEMPTS = 3
        private const val WRITE_TIMEOUT_MS = 5_000
        private const val DEFAULT_COMMAND_TIMEOUT_MS = 5_000
        private const val DEFAULT_BEGIN_TIMEOUT_MS = 40_000
        private const val FLASH_BLOCK_TIMEOUT_MS = 10_000
        private const val ERASE_REGION_TIMEOUT_PER_MB_MS = 30_000L
        private const val ERASE_REGION_MIN_TIMEOUT_MS = 10_000
        private const val READ_BUFFER_SIZE = 4096
        private const val MAX_READ_TIMEOUT_MS = 250

        // Android's USB stack and the CP210x/CH34x bridge need more settle
        // time after enumeration than a desktop serial port. Keep the actual
        // reset sequence timings aligned with esptool; this delay only covers
        // opening/configuration of the already-enumerated USB endpoint.
        private const val PORT_SETTLE_MS = 750L
        private const val POST_RESET_DELAY_MS = 120L
        private const val POST_SYNC_SETTLE_MS = 150L
        private const val BOOTLOADER_CONNECT_ATTEMPTS = 7
        private const val MANUAL_SYNC_ATTEMPTS = 6
        private const val SYNC_ATTEMPTS = 5
        private const val SYNC_ATTEMPTS_PER_RESET = 2
        private const val SYNC_ATTEMPTS_AFTER_COMMAND_TIMEOUT = 2
        private const val RESET_RETRY_DELAY_MS = 300L
        private const val SYNC_TIMEOUT_MS = 1_200
        private const val SYNC_RETRY_DELAY_MS = 80L
        private const val SYNC_DRAIN_TIMEOUT_MS = 1_000L
        private const val MAX_EXTRA_SYNC_RESPONSES = 7
        private const val SPI_ATTACH_ATTEMPTS = 3
        private const val SPI_ATTACH_TIMEOUT_MS = 8_000
        private const val SPI_ATTACH_RETRY_DELAY_MS = 250L

        private val SYNC_PAYLOAD = byteArrayOf(0x07, 0x07, 0x12, 0x20) + ByteArray(32) { 0x55 }
    }
}
