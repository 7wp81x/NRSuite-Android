package com.swp81x.nrsuite.core.wps

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent BSSID -> PIN database stored alongside Credential Manager data.
 */
class WpsPinStore(private val directory: File) {
    private val pinsFile = File(directory, FILE_NAME)

    @Synchronized
    fun loadPins(): List<WpsPinRecord> {
        if (!pinsFile.exists()) return emptyList()
        val root = runCatching { JSONObject(pinsFile.readText()) }.getOrNull() ?: return emptyList()
        val array = root.optJSONArray("pins") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                runCatching { obj.toRecord() }.getOrNull()?.let(::add)
            }
        }
    }

    @Synchronized
    fun savePins(records: List<WpsPinRecord>) {
        directory.mkdirs()
        val root = JSONObject().apply {
            put("pins", JSONArray().apply {
                records.forEach { put(it.toJson()) }
            })
        }
        val serialized = root.toString(2)
        val temporary = File(directory, "$FILE_NAME.tmp")
        temporary.writeText(serialized)
        if (!temporary.renameTo(pinsFile)) {
            pinsFile.writeText(serialized)
            temporary.delete()
        }
    }

    @Synchronized
    fun upsert(record: WpsPinRecord): List<WpsPinRecord> {
        val existing = loadPins().toMutableList()
        val index = existing.indexOfFirst { it.bssid.equals(record.bssid, ignoreCase = true) }
        if (index >= 0) existing[index] = record else existing += record
        savePins(existing)
        return existing
    }

    @Synchronized
    fun delete(bssid: String): List<WpsPinRecord> {
        val updated = loadPins().filterNot { it.bssid.equals(bssid, ignoreCase = true) }
        savePins(updated)
        return updated
    }

    private fun WpsPinRecord.toJson(): JSONObject = JSONObject().apply {
        put("bssid", bssid)
        put("ssid", ssid)
        put("pin", pin)
        put("source", source.name)
        put("verified", verified)
        put("lastResult", lastResult.name)
        put("updatedAt", updatedAt)
    }

    private fun JSONObject.toRecord(): WpsPinRecord = WpsPinRecord(
        bssid = optString("bssid").uppercase(),
        ssid = optString("ssid"),
        pin = optString("pin"),
        source = runCatching { WpsPinSource.valueOf(optString("source")) }
            .getOrDefault(WpsPinSource.MANUAL),
        verified = optBoolean("verified", false),
        lastResult = runCatching { WpsPinAttemptResult.valueOf(optString("lastResult")) }
            .getOrDefault(WpsPinAttemptResult.UNKNOWN),
        updatedAt = optString("updatedAt"),
    )

    companion object {
        private const val FILE_NAME = "wps_pins.json"
    }
}
