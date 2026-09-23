package com.swp81x.nrsuite.core.flasher

import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max

/**
 * Minimal ESP32 ROM-bootloader flasher.
 *
 * This does not use the NRSuite bridge protocol or any OTA mechanism. It talks
 * directly to the ESP32 ROM serial bootloader, writes a complete image at the
 * chosen flash offset, and reboots the chip.
 *
 * Only the no-stub command subset is implemented, so it works with ROM
 * bootloaders across ESP32, ESP32-S2, ESP32-S3, and ESP32-C3.
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

    fun flash(
        firmware: ByteArray,
        offset: Int = 0,
        resetMode: ResetMode = ResetMode.CLASSIC,
        eraseBeforeFlash: Boolean = true,
        onStage: (String) -> Unit = {},
        onProgress: (Int) -> Unit = {},
    ) {
        require(firmware.isNotEmpty()) { "Firmware image is empty" }
        require(offset >= 0) { "Flash offset must be >= 0" }

        transport.open()
        try {
            enterBootloader(resetMode)
            resetInput()
            sync()
            flashSpiAttach()

            if (eraseBeforeFlash) {
                onStage("Erasing flash...")
                eraseFlash()
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

    private fun enterBootloader(mode: ResetMode) {
        when (mode) {
            ResetMode.CLASSIC -> classicReset()
            ResetMode.USB_JTAG -> usbJtagSerialReset()
            ResetMode.NONE -> Unit
        }
        Thread.sleep(120)
    }

    private fun classicReset() {
        transport.setDtr(false)
        transport.setRts(true)
        Thread.sleep(100)
        transport.setDtr(true)
        transport.setRts(false)
        Thread.sleep(50)
        transport.setDtr(false)
    }

    private fun usbJtagSerialReset() {
        transport.setRts(false)
        transport.setDtr(false)
        Thread.sleep(100)
        transport.setDtr(true)
        transport.setRts(false)
        Thread.sleep(100)
        transport.setRts(true)
        transport.setDtr(false)
        transport.setRts(true)
        Thread.sleep(100)
        transport.setDtr(false)
        transport.setRts(false)
    }

    private fun resetInput() {
        transport.flushInput()
        decoder.reset()
    }

    private fun sync() {
        var lastError: Throwable? = null
        repeat(5) {
            resetInput()
            try {
                sendCommand(
                    op = OP_SYNC,
                    data = SYNC_PAYLOAD,
                    checksum = 0,
                    timeoutMs = 1_200,
                )
                return
            } catch (t: Throwable) {
                lastError = t
                Thread.sleep(100)
            }
        }
        throw IOException(
            "Could not synchronize with the ROM bootloader. " +
                "Hold BOOT, tap RESET, then retry. (${lastError?.message ?: "no response"})",
        )
    }

    private fun flashSpiAttach() {
        // hspi_arg = 0, followed by the ROM-only "is legacy" byte field.
        checkCommand(
            description = "enable SPI flash",
            op = OP_SPI_ATTACH,
            data = ByteArray(8),
        )
    }

    private fun eraseFlash() {
        checkCommand(
            description = "erase flash",
            op = OP_ERASE_FLASH,
            data = ByteArray(0),
            timeoutMs = ERASE_TIMEOUT_MS,
        )
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
        val packet = ByteArrayOutputStream(8 + data.size).apply {
            write(0x00)
            write(op)
            writeShortLe(data.size)
            writeIntLe(checksum)
            write(data)
        }.toByteArray()

        transport.write(SlipCodec.encode(packet), WRITE_TIMEOUT_MS)

        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val remaining = (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(1)
            val response = readResponse(remaining)
            if (response == null) {
                continue
            }
            if (response.op == op) {
                return response
            }
        }
        throw IOException("Timed out waiting for ROM response to op 0x%02x".format(op))
    }

    private fun readResponse(timeoutMs: Int): Response? {
        val deadline = System.currentTimeMillis() + timeoutMs
        val buffer = ByteArray(READ_BUFFER_SIZE)
        while (System.currentTimeMillis() < deadline) {
            val remaining = (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(1)
            val readTimeout = remaining.coerceAtMost(MAX_READ_TIMEOUT_MS)
            val count = transport.read(buffer, readTimeout)
            if (count <= 0) {
                continue
            }
            for (frame in decoder.feed(buffer.copyOf(count))) {
                parseResponse(frame)?.let { return it }
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

    companion object {
        private const val OP_FLASH_BEGIN = 0x02
        private const val OP_FLASH_DATA = 0x03
        private const val OP_FLASH_END = 0x04
        private const val OP_SYNC = 0x08
        private const val OP_SPI_ATTACH = 0x0D
        private const val OP_ERASE_FLASH = 0xD0

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
        private const val ERASE_TIMEOUT_MS = 120_000
        private const val READ_BUFFER_SIZE = 4096
        private const val MAX_READ_TIMEOUT_MS = 250

        private val SYNC_PAYLOAD = byteArrayOf(0x07, 0x07, 0x12, 0x20) + ByteArray(32) { 0x55 }
    }
}
