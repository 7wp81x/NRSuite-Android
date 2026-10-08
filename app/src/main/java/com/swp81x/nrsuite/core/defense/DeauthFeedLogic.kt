package com.swp81x.nrsuite.core.defense

/**
 * Shared, unit-testable deauth feed helpers used by the Android detector UI.
 */
fun filterDeauthFeedBySource(
    entries: List<DeauthFeedEntry>,
    filter: DeauthSourceFilter,
): List<DeauthFeedEntry> = when (filter) {
    DeauthSourceFilter.ALL -> entries
    DeauthSourceFilter.LOCAL -> entries.filter { it.origin != "mesh" }
    DeauthSourceFilter.MESH -> entries.filter { it.origin == "mesh" }
}

/**
 * Bounded, 10-second dedupe window keyed by (node_id, seq).
 *
 * The same field observation may be retried across mesh windows with the same
 * stable seq; this accepts it once and drops retransmissions.
 */
class DeauthMeshDeduplicator(
    private val retentionMs: Long = 10_000L,
) {
    private val seenAt = mutableMapOf<String, Long>()

    @Synchronized
    fun accept(nodeId: String, seq: Long, nowMs: Long): Boolean {
        val cutoff = nowMs - retentionMs
        seenAt.entries.removeAll { it.value < cutoff }
        val key = "$nodeId:$seq"
        val previous = seenAt[key]
        if (previous != null && nowMs - previous <= retentionMs) {
            return false
        }
        seenAt[key] = nowMs
        return true
    }

    @Synchronized
    fun clear() {
        seenAt.clear()
    }

    @Synchronized
    fun size(): Int = seenAt.size
}
