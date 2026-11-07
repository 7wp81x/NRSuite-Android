package com.swp81x.nrsuite.core.session

import org.json.JSONObject

/**
 * Result of a scan command plus any pull-based result records retrieved with
 * `SCAN_RESULTS`. When [pulled] is false the caller must use the legacy
 * asynchronous events because the connected firmware predates the capability.
 */
data class ScanSnapshotResult(
    val response: JSONObject?,
    val results: List<JSONObject>,
    val pulled: Boolean,
    val expectedCount: Int,
    val receivedCount: Int,
    val error: String? = null,
)
