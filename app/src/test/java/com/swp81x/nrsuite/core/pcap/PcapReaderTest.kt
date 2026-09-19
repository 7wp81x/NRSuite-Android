package com.swp81x.nrsuite.core.pcap

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PcapReaderTest {

    @Test
    fun `reads packets written by PcapWriter`() {
        val file = File.createTempFile("nrsuite-reader", ".pcap")
        val first = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        val second = byteArrayOf(0x10, 0x11, 0x12)
        try {
            PcapWriter(file).use { writer ->
                writer.writePacket(first)
                writer.writePacket(second)
            }

            val packets = mutableListOf<ByteArray>()
            PcapReader(file).forEachPacket { packet ->
                packets += packet
                true
            }

            assertEquals(2, packets.size)
            assertArrayEquals(first, packets[0])
            assertArrayEquals(second, packets[1])
        } finally {
            file.delete()
        }
    }

    @Test
    fun `stops when callback returns false`() {
        val file = File.createTempFile("nrsuite-reader-stop", ".pcap")
        try {
            PcapWriter(file).use { writer ->
                writer.writePacket(byteArrayOf(1))
                writer.writePacket(byteArrayOf(2))
            }

            var count = 0
            PcapReader(file).forEachPacket {
                count++
                false
            }

            assertEquals(1, count)
        } finally {
            file.delete()
        }
    }
}
