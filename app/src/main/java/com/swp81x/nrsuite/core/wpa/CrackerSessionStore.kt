package com.swp81x.nrsuite.core.wpa

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * App-private persistence for interrupted/paused WPA cracker jobs.
 *
 * Handshake material is embedded so a session can resume without re-reading
 * the original PCAP/22000 source. Wordlist and source URI permissions remain
 * managed by the Android document picker.
 */
class CrackerSessionStore(
    private val directory: File,
) {
    private val sessionsFile = File(directory, SESSIONS_FILE)

    @Synchronized
    fun loadSessions(): List<CrackerSessionSnapshot> {
        if (!sessionsFile.exists()) return emptyList()
        val raw = runCatching { sessionsFile.readText() }.getOrNull() ?: return emptyList()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val array = root.optJSONArray("sessions") ?: return emptyList()
        val sessions = mutableListOf<CrackerSessionSnapshot>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            runCatching { obj.toSession() }.getOrNull()?.let { sessions += it }
        }
        return sessions.sortedByDescending { it.updatedAt }
    }

    @Synchronized
    fun saveSessions(sessions: List<CrackerSessionSnapshot>) {
        directory.mkdirs()
        val root = JSONObject().apply {
            put("sessions", JSONArray().apply {
                sessions.forEach { put(it.toJson()) }
            })
        }
        val serialized = root.toString(2)
        val temp = File(directory, "$SESSIONS_FILE.tmp")
        temp.writeText(serialized)
        if (!temp.renameTo(sessionsFile)) {
            sessionsFile.writeText(serialized)
            temp.delete()
        }
    }

    @Synchronized
    fun deleteSession(id: String) {
        val remaining = loadSessions().filterNot { it.id == id }
        saveSessions(remaining)
    }

    private fun CrackerSessionSnapshot.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("sourceType", sourceType.name)
        put("sourceLabel", sourceLabel)
        put("sourceUri", sourceUri ?: JSONObject.NULL)
        put("savedSessionId", savedSessionId ?: JSONObject.NULL)
        put("wordlistUri", wordlistUri)
        put("wordlistName", wordlistName)
        put("currentTargetIndex", currentTargetIndex)
        put("updatedAt", updatedAt)
        put("status", status.name)
        put("targets", JSONArray().apply {
            targets.forEach { target ->
                put(JSONObject().apply {
                    put("id", target.id)
                    put("ssid", target.ssid)
                    put("bssid", target.bssid ?: JSONObject.NULL)
                    put("station", target.station ?: JSONObject.NULL)
                    put("selected", target.selected)
                    put("status", target.status.name)
                    put("tested", target.tested)
                    put("result", target.result ?: JSONObject.NULL)
                    put("handshake", target.handshake.toJson())
                })
            }
        })
    }

    private fun JSONObject.toSession(): CrackerSessionSnapshot {
        val targetsArray = optJSONArray("targets") ?: JSONArray()
        val targets = mutableListOf<CrackerTargetSnapshot>()
        for (index in 0 until targetsArray.length()) {
            val targetObject = targetsArray.optJSONObject(index) ?: continue
            val target = runCatching { targetObject.toTarget() }.getOrNull() ?: continue
            targets += target
        }
        return CrackerSessionSnapshot(
            id = optString("id"),
            sourceType = CrackerSourceType.valueOf(optString("sourceType")),
            sourceLabel = optString("sourceLabel"),
            sourceUri = if (isNull("sourceUri")) null else optString("sourceUri"),
            savedSessionId = if (isNull("savedSessionId")) null else optString("savedSessionId"),
            wordlistUri = optString("wordlistUri"),
            wordlistName = optString("wordlistName"),
            targets = targets,
            currentTargetIndex = optInt("currentTargetIndex", 0),
            updatedAt = optLong("updatedAt", 0L),
            status = CrackerSessionStatus.valueOf(optString("status")),
        )
    }

    private fun JSONObject.toTarget(): CrackerTargetSnapshot = CrackerTargetSnapshot(
        id = optString("id"),
        ssid = optString("ssid"),
        bssid = if (isNull("bssid")) null else optString("bssid"),
        station = if (isNull("station")) null else optString("station"),
        handshake = optJSONObject("handshake")?.toHandshake()
            ?: error("Missing handshake"),
        selected = optBoolean("selected", true),
        status = CrackerTargetStatus.valueOf(optString("status")),
        tested = optLong("tested", 0L),
        result = if (isNull("result")) null else optString("result"),
    )

    private fun WpaHandshake.toJson(): JSONObject = JSONObject().apply {
        put("m1Bssid", m1Bssid?.toHex() ?: JSONObject.NULL)
        put("m1Station", m1Station?.toHex() ?: JSONObject.NULL)
        put("aNonce", aNonce?.toHex() ?: JSONObject.NULL)
        put("m1ReplayCounter", m1ReplayCounter ?: JSONObject.NULL)
        put("m2Bssid", m2Bssid?.toHex() ?: JSONObject.NULL)
        put("m2Station", m2Station?.toHex() ?: JSONObject.NULL)
        put("sNonce", sNonce?.toHex() ?: JSONObject.NULL)
        put("eapolM2", eapolM2?.toHex() ?: JSONObject.NULL)
        put("mic", mic?.toHex() ?: JSONObject.NULL)
        put("m2ReplayCounter", m2ReplayCounter ?: JSONObject.NULL)
        put("keyDescriptorVersion", keyDescriptorVersion)
    }

    private fun JSONObject.toHandshake(): WpaHandshake = WpaHandshake(
        m1Bssid = nullableString("m1Bssid")?.fromHex(),
        m1Station = nullableString("m1Station")?.fromHex(),
        aNonce = nullableString("aNonce")?.fromHex(),
        m1ReplayCounter = if (isNull("m1ReplayCounter")) null else optLong("m1ReplayCounter"),
        m2Bssid = nullableString("m2Bssid")?.fromHex(),
        m2Station = nullableString("m2Station")?.fromHex(),
        sNonce = nullableString("sNonce")?.fromHex(),
        eapolM2 = nullableString("eapolM2")?.fromHex(),
        mic = nullableString("mic")?.fromHex(),
        m2ReplayCounter = if (isNull("m2ReplayCounter")) null else optLong("m2ReplayCounter"),
        keyDescriptorVersion = optInt("keyDescriptorVersion", 2),
    )

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun ByteArray.toHex(): String =
        joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }

    private fun String.fromHex(): ByteArray {
        require(length % 2 == 0) { "Odd-length hex value" }
        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private companion object {
        const val SESSIONS_FILE = "cracker_sessions.json"
    }
}
