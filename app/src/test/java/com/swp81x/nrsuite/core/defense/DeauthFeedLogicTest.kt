package com.swp81x.nrsuite.core.defense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeauthFeedLogicTest {

    private fun entry(
        source: String,
        origin: String,
        seq: Long? = null,
    ) = DeauthFeedEntry(
        timestamp = "12:00:00",
        sourceMac = source,
        targetMac = null,
        reasonCode = 7,
        rssi = -55,
        origin = origin,
        nodeId = if (origin == "mesh") "NR12345678" else null,
        seq = seq,
        channel = 6,
    )

    @Test
    fun sourceFilter_keepsRemoteRowsWhenLocalSelected() {
        val feed = listOf(
            entry("AA:AA:AA:AA:AA:AA", "local"),
            entry("BB:BB:BB:BB:BB:BB", "mesh", seq = 42L),
        )

        val local = filterDeauthFeedBySource(feed, DeauthSourceFilter.LOCAL)
        val mesh = filterDeauthFeedBySource(feed, DeauthSourceFilter.MESH)
        val all = filterDeauthFeedBySource(feed, DeauthSourceFilter.ALL)

        assertEquals(1, local.size)
        assertEquals("local", local.single().origin)
        assertEquals(1, mesh.size)
        assertEquals("mesh", mesh.single().origin)
        assertEquals(2, all.size)
    }

    @Test
    fun dedupeWindow_acceptsOnceThenDropsRetry() {
        val dedupe = DeauthMeshDeduplicator(retentionMs = 10_000L)

        assertTrue(dedupe.accept("NR12345678", 42L, 1_000L))
        assertFalse(dedupe.accept("NR12345678", 42L, 1_500L))
        assertTrue(dedupe.accept("NR12345678", 42L, 12_000L))
        assertTrue(dedupe.accept("NR87654321", 42L, 12_100L))
    }
}
