package com.swp81x.nrsuite.core.geo

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class BssidGeolocationHistoryStore(
    val root: File,
) {
    private val historyFile = File(root, "bssid_geolocation_history.json")

    @Synchronized
    fun load(): List<BssidGeolocationHistoryEntry> {
        root.mkdirs()
        if (!historyFile.exists()) return emptyList()
        val raw = runCatching { historyFile.readText() }.getOrNull() ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val entries = mutableListOf<BssidGeolocationHistoryEntry>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val entry = runCatching {
                val bssid = obj.optString("bssid").uppercase().takeIf { it.isNotBlank() }
                    ?: return@runCatching null
                BssidGeolocationHistoryEntry(
                    bssid = bssid,
                    vendor = obj.optString("vendor").takeIf { it.isNotBlank() },
                    latitude = obj.getDouble("latitude"),
                    longitude = obj.getDouble("longitude"),
                    accuracyMeters = obj.optDouble("accuracy_m", Double.NaN)
                        .takeUnless { it.isNaN() },
                    source = obj.optString("source", "unknown"),
                    firstLookedUpAtMs = obj.optLong("first_looked_up_at_ms", 0L),
                    lastLookedUpAtMs = obj.optLong("last_looked_up_at_ms", 0L),
                    lookupCount = obj.optInt("lookup_count", 1).coerceAtLeast(1),
                )
            }.getOrNull()
            if (entry != null) entries += entry
        }
        return entries.sortedByDescending { it.lastLookedUpAtMs }
    }

    @Synchronized
    fun save(entries: List<BssidGeolocationHistoryEntry>) {
        root.mkdirs()
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().apply {
                put("bssid", entry.bssid)
                put("vendor", entry.vendor ?: "")
                put("latitude", entry.latitude)
                put("longitude", entry.longitude)
                put("accuracy_m", entry.accuracyMeters ?: JSONObject.NULL)
                put("source", entry.source)
                put("first_looked_up_at_ms", entry.firstLookedUpAtMs)
                put("last_looked_up_at_ms", entry.lastLookedUpAtMs)
                put("lookup_count", entry.lookupCount)
            })
        }
        val temp = File(root, "${historyFile.name}.tmp")
        temp.writeText(array.toString(2))
        if (historyFile.exists() && !historyFile.delete()) {
            temp.delete()
            return
        }
        if (!temp.renameTo(historyFile)) {
            temp.delete()
        }
    }
}
