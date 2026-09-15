package com.swp81x.nrsuite.core.pcap

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class PcapWriterTest {

    @Test
    fun `writes libpcap global header and one packet`() {
        val file = File.createTempFile("nrsuite-capture", ".pcap")
        try {
            val payload = byteArrayOf(0x00, 0x01, 0x02, 0x03)
            PcapWriter(file).use { writer ->
                writer.writePacket(payload)
            }

            val bytes = file.readBytes()
            assertEquals(0xD4, bytes[0].toInt() and 0xFF)
            assertEquals(0xC3, bytes[1].toInt() and 0xFF)
            assertEquals(0xB2, bytes[2].toInt() and 0xFF)
            assertEquals(0xA1, bytes[3].toInt() and 0xFF)

            // Global header is 24 bytes, record header is 16 bytes.
            assertEquals(24 + 16 + payload.size, bytes.size)

            // Record header: ts_sec(4), ts_usec(4), incl_len(4), orig_len(4)
            val declaredLength = (bytes[32].toInt() and 0xFF) or
                ((bytes[33].toInt() and 0xFF) shl 8) or
                ((bytes[34].toInt() and 0xFF) shl 16) or
                ((bytes[35].toInt() and 0xFF) shl 24)
            assertEquals(payload.size, declaredLength)
        } finally {
            file.delete()
        }
    }
}
