package com.swp81x.nrsuite.core.pcap

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Minimal libpcap writer for IEEE 802.11 + radiotap frames.
 *
 * The ESP32 firmware sends PCAP payloads as one complete radiotap + 802.11
 * frame. This writer wraps each payload in a libpcap packet record.
 */
class PcapWriter(
    private val output: OutputStream,
    private val closeOutput: Boolean = true,
    private val snaplen: Int = DEFAULT_SNAPLEN,
) : Closeable {

    /**
     * Convenience constructor for app-private capture files.
     */
    constructor(file: File, snaplen: Int = DEFAULT_SNAPLEN) : this(
        output = BufferedOutputStream(FileOutputStream(file), BUFFER_SIZE),
        closeOutput = true,
        snaplen = snaplen,
    )

    init {
        writeGlobalHeader()
    }

    var packetCount: Long = 0
        private set

    @Synchronized
    fun writePacket(data: ByteArray) {
        val now = System.currentTimeMillis()
        val sec = now / 1000L
        val usec = ((now % 1000L) * 1000L).toInt()

        writeUInt32(sec)
        writeUInt32(usec.toLong())
        writeUInt32(data.size.toLong())
        writeUInt32(data.size.toLong())
        output.write(data)
        packetCount++
    }

    @Synchronized
    override fun close() {
        try {
            output.flush()
        } finally {
            if (closeOutput) {
                output.close()
            }
        }
    }

    private fun writeGlobalHeader() {
        writeUInt32(PCAP_MAGIC)
        writeUInt16(2)
        writeUInt16(4)
        writeUInt32(0)
        writeUInt32(0)
        writeUInt32(snaplen.toLong())
        writeUInt32(LINKTYPE_IEEE802_11_RADIOTAP.toLong())
    }

    private fun writeUInt16(value: Int) {
        output.write(value and 0xFF)
        output.write((value ushr 8) and 0xFF)
    }

    private fun writeUInt32(value: Long) {
        output.write((value and 0xFF).toInt())
        output.write(((value ushr 8) and 0xFF).toInt())
        output.write(((value ushr 16) and 0xFF).toInt())
        output.write(((value ushr 24) and 0xFF).toInt())
    }

    companion object {
        private const val PCAP_MAGIC = 0xA1B2C3D4L
        private const val LINKTYPE_IEEE802_11_RADIOTAP = 127L
        private const val DEFAULT_SNAPLEN = 65535
        private const val BUFFER_SIZE = 256 * 1024
    }
}
