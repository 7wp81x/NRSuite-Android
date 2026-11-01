package com.swp81x.nrsuite.core.wpa

import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CrackerSessionStoreTest {
    @Test
    fun `round trips a checkpointed session with handshake material`() {
        val directory = Files.createTempDirectory("cracker-session").toFile()
        val store = CrackerSessionStore(directory)
        val handshake = WpaHandshake(
            m1Bssid = byteArrayOf(1, 2, 3, 4, 5, 6),
            m1Station = byteArrayOf(7, 8, 9, 10, 11, 12),
            aNonce = ByteArray(32) { 1 },
            m1ReplayCounter = 9L,
            m2Bssid = byteArrayOf(1, 2, 3, 4, 5, 6),
            m2Station = byteArrayOf(7, 8, 9, 10, 11, 12),
            sNonce = ByteArray(32) { 2 },
            eapolM2 = ByteArray(121) { 3 },
            mic = ByteArray(16) { 4 },
            m2ReplayCounter = 9L,
            keyDescriptorVersion = 2,
        )
        val session = CrackerSessionSnapshot(
            id = "job-1",
            sourceType = CrackerSourceType.CUSTOM_PCAP,
            sourceLabel = "capture.pcap",
            sourceUri = "content://capture",
            savedSessionId = null,
            wordlistUri = "content://wordlist",
            wordlistName = "words.txt",
            targets = listOf(
                CrackerTargetSnapshot(
                    id = "target-1",
                    ssid = "TestNetwork",
                    bssid = "AA:BB:CC:DD:EE:FF",
                    station = "11:22:33:44:55:66",
                    handshake = handshake,
                    status = CrackerTargetStatus.PENDING,
                    tested = 1234L,
                ),
            ),
            currentTargetIndex = 0,
            updatedAt = 123456L,
            status = CrackerSessionStatus.PAUSED,
        )

        store.saveSessions(listOf(session))
        val loaded = store.loadSessions().single()

        assertEquals(session.id, loaded.id)
        assertEquals(session.status, loaded.status)
        assertEquals(session.targets.single().tested, loaded.targets.single().tested)
        assertArrayEquals(handshake.eapolM2, loaded.targets.single().handshake.eapolM2)
        assertArrayEquals(handshake.mic, loaded.targets.single().handshake.mic)
        directory.deleteRecursively()
    }
}
