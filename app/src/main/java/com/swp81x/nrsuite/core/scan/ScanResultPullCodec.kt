package com.swp81x.nrsuite.core.scan

import java.util.zip.CRC32
import org.json.JSONObject

/** Feature flag advertised by firmware that supports `SCAN_RESULTS`. */
const val FEATURE_SCAN_RESULTS_PULL = "scan_results_pull"

data class ScanResultPullChunk(
    val scanId: Int,
    val kind: String,
    val offset: Int,
    val nextOffset: Int,
    val total: Int,
    val crc32: Long,
    val results: List<JSONObject>,
)

class ScanResultChunkException(message: String) : Exception(message)

/**
 * Validates and decodes one pull-based scan result chunk.
 *
 * The CRC is computed over a deterministic canonical representation shared
 * with firmware (`scan_results.cpp`). It covers every visible result field,
 * so a truncated-but-still-parseable JSON payload is rejected as well.
 */
object ScanResultPullCodec {
    fun decode(
        response: JSONObject,
        expectedScanId: Int,
        expectedKind: String,
        expectedOffset: Int,
    ): ScanResultPullChunk {
        if (!response.optBoolean("ok")) {
            throw ScanResultChunkException(
                response.optString("msg").ifBlank { "scan results request failed" },
            )
        }

        val scanId = response.optInt("scan_id", -1)
        if (scanId != expectedScanId) {
            throw ScanResultChunkException(
                "scan_id mismatch: expected $expectedScanId, got $scanId",
            )
        }

        val kind = response.optString("kind").lowercase()
        if (kind != expectedKind.lowercase()) {
            throw ScanResultChunkException(
                "scan kind mismatch: expected $expectedKind, got $kind",
            )
        }

        val offset = response.optInt("offset", -1)
        if (offset != expectedOffset) {
            throw ScanResultChunkException(
                "scan offset mismatch: expected $expectedOffset, got $offset",
            )
        }

        val total = response.optInt("total", -1)
        if (total < 0) {
            throw ScanResultChunkException("invalid scan total: $total")
        }

        val array = response.optJSONArray("results")
            ?: throw ScanResultChunkException("scan results array missing")
        val results = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)
                    ?: throw ScanResultChunkException("scan result $index is not an object")
                add(item)
            }
        }

        val reportedCount = response.optInt("count", results.size)
        if (reportedCount != results.size) {
            throw ScanResultChunkException(
                "scan result count mismatch: field=$reportedCount parsed=${results.size}",
            )
        }

        val nextOffset = response.optInt("next_offset", -1)
        if (nextOffset < offset || nextOffset > total) {
            throw ScanResultChunkException(
                "invalid next_offset=$nextOffset (offset=$offset total=$total)",
            )
        }
        if (offset < total && nextOffset == offset) {
            throw ScanResultChunkException("scan chunk made no progress at offset $offset")
        }
        if (results.isEmpty() && offset < total) {
            throw ScanResultChunkException("empty scan chunk at offset $offset of $total")
        }

        val expectedCrc = response.optLong("crc32", -1L) and 0xFFFFFFFFL
        val actualCrc = canonicalCrc32(kind, results)
        if (actualCrc != expectedCrc) {
            throw ScanResultChunkException(
                "scan chunk CRC mismatch at offset $offset: " +
                    "expected=${expectedCrc.toString(16)} actual=${actualCrc.toString(16)}",
            )
        }

        return ScanResultPullChunk(
            scanId = scanId,
            kind = kind,
            offset = offset,
            nextOffset = nextOffset,
            total = total,
            crc32 = expectedCrc,
            results = results,
        )
    }

    /**
     * Canonical form mirrored by firmware:
     *
     *   wifi|<ssid-byte-len>:<ssid>|<BSSID>|<channel>|<rssi>|
     *     <security-byte-len>:<security>|<wps 0/1>|<band>\n
     *
     *   wps|<ssid-byte-len>:<ssid>|<BSSID>|<channel>|<rssi>|
     *     <security-byte-len>:<security>|<wps 0/1>|<wps_parsed 0/1>|
     *     <lock_reported 0/1>|<ap_setup_locked 0/1>|<wps_version>|
     *     <config_methods>|<selected_registrar 0/1>|<last_seen_ms>\n
     */
    fun canonicalCrc32(kind: String, results: List<JSONObject>): Long {
        val canonical = buildString {
            results.forEach { result ->
                append(kind.lowercase())
                append('|')
                appendSizedField(result.optString("ssid"))
                append(result.optString("bssid"))
                append('|')
                append(result.optInt("channel"))
                append('|')
                append(result.optInt("rssi"))
                append('|')
                appendSizedField(result.optString("security"))
                append(if (result.optBoolean("wps")) '1' else '0')
                append('|')
                if (kind.equals("wifi", ignoreCase = true)) {
                    append(result.optString("band", "2g"))
                    append('\n')
                } else {
                    append(if (result.optBoolean("wps_parsed")) '1' else '0')
                    append('|')
                    append(if (result.optBoolean("lock_reported")) '1' else '0')
                    append('|')
                    append(if (result.optBoolean("ap_setup_locked")) '1' else '0')
                    append('|')
                    append(result.optInt("wps_version"))
                    append('|')
                    append(result.optInt("config_methods"))
                    append('|')
                    append(if (result.optBoolean("selected_registrar")) '1' else '0')
                    append('|')
                    append(result.optLong("last_seen_ms"))
                    append('\n')
                }
            }
        }
        return crc32(canonical.toByteArray(Charsets.UTF_8))
    }

    private fun StringBuilder.appendSizedField(value: String) {
        append(value.toByteArray(Charsets.UTF_8).size)
        append(':')
        append(value)
        append('|')
    }

    private fun crc32(data: ByteArray): Long {
        val crc = CRC32()
        crc.update(data)
        return crc.value and 0xFFFFFFFFL
    }
}
