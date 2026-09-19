package com.swp81x.nrsuite.core.pcap

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Minimal libpcap reader for the radiotap + 802.11 captures written by
 * [PcapWriter]. It streams packet payloads so a capture does not need to be
 * loaded into memory all at once.
 *
 * The [InputStream] constructor is intended for Android SAF/ContentResolver
 * sources; it consumes and closes the supplied stream.
 */
class PcapReader private constructor(
    private val file: File?,
    private val suppliedInput: InputStream?,
) {
    constructor(file: File) : this(file = file, suppliedInput = null)
    constructor(inputStream: InputStream) : this(file = null, suppliedInput = inputStream)

    @Throws(IOException::class)
    fun forEachPacket(action: (ByteArray) -> Boolean) {
        val rawInput = suppliedInput ?: FileInputStream(requireNotNull(file))
        rawInput.buffered(BUFFER_SIZE).use { input ->
            val globalHeader = ByteArray(GLOBAL_HEADER_SIZE)
            if (!readFully(input, globalHeader, globalHeader.size)) return
            val magic = u32(globalHeader, 0)
            if (magic != PCAP_MAGIC) {
                throw IOException("Unsupported PCAP magic: 0x${magic.toString(16)}")
            }

            val recordHeader = ByteArray(RECORD_HEADER_SIZE)
            while (readFully(input, recordHeader, recordHeader.size)) {
                val capturedLength = u32(recordHeader, 8).toInt()
                if (capturedLength < 0 || capturedLength > MAX_PACKET_SIZE) {
                    throw IOException("Invalid PCAP packet length: $capturedLength")
                }
                val payload = ByteArray(capturedLength)
                if (!readFully(input, payload, payload.size)) return
                if (!action(payload)) return
            }
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray, length: Int): Boolean {
        var offset = 0
        while (offset < length) {
            val read = input.read(buffer, offset, length - offset)
            if (read < 0) return false
            offset += read
        }
        return true
    }

    private fun u32(bytes: ByteArray, offset: Int): Long {
        return (bytes[offset].toLong() and 0xFFL) or
            ((bytes[offset + 1].toLong() and 0xFFL) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFFL) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFFL) shl 24)
    }

    companion object {
        private const val PCAP_MAGIC = 0xA1B2C3D4L
        private const val GLOBAL_HEADER_SIZE = 24
        private const val RECORD_HEADER_SIZE = 16
        private const val MAX_PACKET_SIZE = 1024 * 1024
        private const val BUFFER_SIZE = 256 * 1024
    }
}
