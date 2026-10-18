package com.swp81x.nrsuite.core.handshake

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoHandshakeSessionStoreTest {

    @Test
    fun `capture files use requested BSSID format and sane defaults`() {
        val root = Files.createTempDirectory("nrsuite-auto-handshake").toFile()
        val store = AutoHandshakeSessionStore(root)

        val finalFile = store.captureFile("aa:bb:cc:dd:ee:ff")
        val partialFile = store.partialCaptureFile("aa:bb:cc:dd:ee:ff")

        assertEquals("HS_AABBCCDDEEFF.pcap", finalFile.name)
        assertEquals("HS_AABBCCDDEEFF.pcap.part", partialFile.name)
        assertTrue(store.loadSessions().isEmpty())
        assertEquals(0, store.loadStats().totalCaptures)
    }
}
