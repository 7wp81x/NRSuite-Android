package com.swp81x.nrsuite.core.scan

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ScanResultPullCodecTest {
    private fun wifiResult(): JSONObject = JSONObject()
        .put("type", "scan_ap")
        .put("ssid", "Test")
        .put("bssid", "C8:3A:35:CA:75:48")
        .put("channel", 6)
        .put("rssi", -55)
        .put("security", "WPA/WPA2-PSK")
        .put("wps", false)
        .put("band", "2g")

    private fun response(
        results: List<JSONObject>,
        crc32: Long,
        count: Int = results.size,
    ): JSONObject = JSONObject()
        .put("ok", true)
        .put("scan_id", 42)
        .put("kind", "wifi")
        .put("offset", 0)
        .put("next_offset", results.size)
        .put("total", count)
        .put("count", results.size)
        .put("crc32", crc32)
        .put("results", JSONArray(results))

    @Test
    fun `wifi canonical CRC matches shared zlib polynomial sample`() {
        assertEquals(0x7efeb06fL, ScanResultPullCodec.canonicalCrc32("wifi", listOf(wifiResult())))
    }

    private fun wpsResult(): JSONObject = JSONObject()
        .put("type", "wps_lock_ap")
        .put("ssid", "Test")
        .put("bssid", "C8:3A:35:CA:75:48")
        .put("channel", 6)
        .put("rssi", -55)
        .put("security", "WPA/WPA2-PSK")
        .put("wps", true)
        .put("wps_parsed", true)
        .put("lock_reported", false)
        .put("ap_setup_locked", false)
        .put("wps_version", 16)
        .put("config_methods", 0)
        .put("selected_registrar", false)
        .put("last_seen_ms", 123456)

    @Test
    fun `wps canonical CRC matches shared zlib polynomial sample`() {
        assertEquals(0x2514ae1fL, ScanResultPullCodec.canonicalCrc32("wps", listOf(wpsResult())))
    }

    @Test
    fun `decodes a valid WPS chunk`() {
        val result = wpsResult()
        val response = response(listOf(result), 0x2514ae1fL)
        response.put("kind", "wps")
        val chunk = ScanResultPullCodec.decode(
            response = response,
            expectedScanId = 42,
            expectedKind = "wps",
            expectedOffset = 0,
        )
        assertEquals("wps_lock_ap", chunk.results.single().optString("type"))
        assertEquals(16, chunk.results.single().optInt("wps_version"))
    }

    @Test
    fun `decodes a valid chunk`() {
        val result = wifiResult()
        val chunk = ScanResultPullCodec.decode(
            response = response(listOf(result), 0x7efeb06fL),
            expectedScanId = 42,
            expectedKind = "wifi",
            expectedOffset = 0,
        )

        assertEquals(1, chunk.results.size)
        assertEquals("C8:3A:35:CA:75:48", chunk.results.single().optString("bssid"))
        assertEquals(1, chunk.nextOffset)
        assertEquals(1, chunk.total)
    }

    @Test
    fun `rejects a corrupt chunk with matching offset and count`() {
        val result = wifiResult()
        val corrupted = response(listOf(result), 0x7efeb06fL)
        corrupted.getJSONArray("results").getJSONObject(0)
            .put("security", "OPEN")

        assertThrows(ScanResultChunkException::class.java) {
            ScanResultPullCodec.decode(corrupted, 42, "wifi", 0)
        }
    }

    @Test
    fun `rejects a result count mismatch`() {
        val result = wifiResult()
        val bad = response(listOf(result), 0x7efeb06fL).put("count", 2)
        assertThrows(ScanResultChunkException::class.java) {
            ScanResultPullCodec.decode(bad, 42, "wifi", 0)
        }
    }
}
