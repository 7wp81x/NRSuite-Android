package com.swp81x.nrsuite.core.handshake

import com.swp81x.nrsuite.core.wifi.NetworkTarget
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * App-private persistence for Auto Handshake sessions, attempt metadata, final
 * PCAP files, and aggregate capture stats.
 *
 * Final PCAP files are intentionally stored as HS_<BSSID>.pcap in a flat
 * captures directory. A later capture of the same BSSID overwrites the previous
 * successful file, matching the operator's requested naming scheme.
 */
class AutoHandshakeSessionStore(
    val root: File,
) {
    private val sessionsFile = File(root, "sessions.json")
    private val statsFile = File(root, "stats.json")
    val capturesDir: File = File(root, "captures")

    fun ensureDirs() {
        root.mkdirs()
        capturesDir.mkdirs()
    }

    fun captureFile(bssid: String): File {
        ensureDirs()
        return File(capturesDir, "HS_${bssidKey(bssid)}.pcap")
    }

    fun partialCaptureFile(bssid: String): File {
        ensureDirs()
        return File(capturesDir, "HS_${bssidKey(bssid)}.pcap.part")
    }

    fun deleteCaptureFile(bssid: String) {
        runCatching { captureFile(bssid).delete() }
        runCatching { partialCaptureFile(bssid).delete() }
    }

    @Synchronized
    fun loadSessions(): List<AutoHandshakeSession> {
        ensureDirs()
        if (!sessionsFile.exists()) return emptyList()
        val raw = runCatching { sessionsFile.readText() }.getOrNull() ?: return emptyList()
        val rootObj = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val array = rootObj.optJSONArray("sessions") ?: return emptyList()
        val sessions = mutableListOf<AutoHandshakeSession>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            runCatching { obj.toSession() }.getOrNull()?.let { sessions += it }
        }
        return sessions
    }

    @Synchronized
    fun saveSessions(sessions: List<AutoHandshakeSession>) {
        ensureDirs()
        val rootObj = JSONObject().apply {
            put("sessions", JSONArray().apply {
                sessions.forEach { put(it.toJson()) }
            })
        }
        writeAtomically(sessionsFile, rootObj.toString(2))
    }

    @Synchronized
    fun loadStats(): AutoHandshakeStats {
        ensureDirs()
        if (!statsFile.exists()) return AutoHandshakeStats()
        val raw = runCatching { statsFile.readText() }.getOrNull() ?: return AutoHandshakeStats()
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return AutoHandshakeStats()
        return AutoHandshakeStats(
            totalCaptures = obj.optInt("totalCaptures", 0).coerceAtLeast(0),
            todayCaptures = obj.optInt("todayCaptures", 0).coerceAtLeast(0),
            statsDate = obj.optString("statsDate"),
            lastCapturedAt = obj.optString("lastCapturedAt")
                .takeIf { it.isNotBlank() && it != "null" },
            lastBssid = obj.optString("lastBssid")
                .takeIf { it.isNotBlank() && it != "null" },
        )
    }

    @Synchronized
    fun saveStats(stats: AutoHandshakeStats) {
        ensureDirs()
        val obj = JSONObject().apply {
            put("totalCaptures", stats.totalCaptures)
            put("todayCaptures", stats.todayCaptures)
            put("statsDate", stats.statsDate)
            put("lastCapturedAt", stats.lastCapturedAt ?: JSONObject.NULL)
            put("lastBssid", stats.lastBssid ?: JSONObject.NULL)
        }
        writeAtomically(statsFile, obj.toString(2))
    }

    private fun writeAtomically(file: File, text: String) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(text)
        if (file.exists()) file.delete()
        if (!temp.renameTo(file)) {
            file.writeText(text)
            temp.delete()
        }
    }

    private fun bssidKey(bssid: String): String =
        bssid.uppercase().replace(":", "").replace("-", "").replace(".", "")

    private fun AutoHandshakeSession.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("deviceId", deviceId ?: JSONObject.NULL)
        put("state", state.name)
        put("config", config.toJson())
        put("targets", JSONArray().apply {
            targets.forEach { put(it.toJson()) }
        })
        put("lastError", lastError ?: JSONObject.NULL)
    }

    private fun AutoHandshakeConfig.toJson(): JSONObject = JSONObject().apply {
        put("allChannels", allChannels)
        put("scanTimeoutMs", scanTimeoutMs)
        put("deauthAssist", deauthAssist)
        put("skipCapturedMacs", skipCapturedMacs)
        put("maxApAttemptsPerCycle", maxApAttemptsPerCycle)
        put("deauthFramesPerAp", deauthFramesPerAp)
        put("deauthIntervalMs", deauthIntervalMs)
        put("channelWaitMs", channelWaitMs)
        put("channelWaitNoDeauthMs", channelWaitNoDeauthMs)
        put("rescanDelayMs", rescanDelayMs)
        put("failedTargetCooldownMs", failedTargetCooldownMs)
    }

    private fun AutoHandshakeTarget.toJson(): JSONObject = JSONObject().apply {
        put("target", target.toJson())
        put("observedClients", JSONArray().apply {
            observedClients.forEach { put(it) }
        })
        put("deauthClient", deauthClient ?: JSONObject.NULL)
        put("score", score)
        put("status", status.name)
        put("inRange", inRange)
        put("lastSeenAtMs", lastSeenAtMs)
        put("attempts", JSONArray().apply {
            attempts.forEach { put(it.toJson()) }
        })
    }

    private fun NetworkTarget.toJson(): JSONObject = JSONObject().apply {
        put("ssid", ssid)
        put("bssid", bssid)
        put("channel", channel)
        put("rssi", rssi)
        put("security", security)
        put("wps", wps)
        put("vendor", vendor ?: JSONObject.NULL)
        put("ouiWhitelisted", ouiWhitelisted)
        put("ouiBlacklisted", ouiBlacklisted)
    }

    private fun AutoHandshakeAttempt.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("startedAtMs", startedAtMs)
        put("endedAtMs", endedAtMs ?: JSONObject.NULL)
        put("channel", channel)
        put("bssid", bssid)
        put("client", client ?: JSONObject.NULL)
        put("pcapPath", pcapPath ?: JSONObject.NULL)
        put("packetCount", packetCount)
        put("deauthBursts", deauthBursts)
        put("m1", m1)
        put("m2", m2)
        put("valid", valid)
        put("interrupted", interrupted)
        put("error", error ?: JSONObject.NULL)
    }

    private fun JSONObject.toSession(): AutoHandshakeSession {
        val id = optString("id")
        require(id.isNotBlank()) { "missing session id" }
        val configObj = optJSONObject("config") ?: JSONObject()
        val targetsArray = optJSONArray("targets") ?: JSONArray()
        val targets = mutableListOf<AutoHandshakeTarget>()
        for (index in 0 until targetsArray.length()) {
            val obj = targetsArray.optJSONObject(index) ?: continue
            runCatching { obj.toTarget() }.getOrNull()?.let { targets += it }
        }
        return AutoHandshakeSession(
            id = id,
            createdAt = optString("createdAt"),
            updatedAt = optString("updatedAt"),
            deviceId = optString("deviceId").takeIf { it.isNotBlank() && it != "null" },
            state = enumValueOrDefault(optString("state"), AutoHandshakePhase.IDLE),
            config = configObj.toConfig(),
            targets = targets,
            lastError = optString("lastError").takeIf { it.isNotBlank() && it != "null" },
        )
    }

    private fun JSONObject.toConfig(): AutoHandshakeConfig = AutoHandshakeConfig(
        allChannels = optBoolean("allChannels", true),
        scanTimeoutMs = optLong("scanTimeoutMs", 15_000L).coerceIn(5_000L, 60_000L),
        deauthAssist = optBoolean("deauthAssist", true),
        skipCapturedMacs = optBoolean("skipCapturedMacs", true),
        maxApAttemptsPerCycle = optInt("maxApAttemptsPerCycle", 4).coerceIn(1, 20),
        deauthFramesPerAp = optInt("deauthFramesPerAp", 8).coerceIn(1, 20),
        deauthIntervalMs = optInt("deauthIntervalMs", 50).coerceIn(5, 10_000),
        channelWaitMs = optLong("channelWaitMs", 6_000L).coerceIn(500L, 60_000L),
        channelWaitNoDeauthMs = optLong("channelWaitNoDeauthMs", 1_500L)
            .coerceIn(250L, 30_000L),
        rescanDelayMs = optLong("rescanDelayMs", 1_500L).coerceIn(250L, 60_000L),
        failedTargetCooldownMs = optLong("failedTargetCooldownMs", 0L)
            .coerceIn(0L, 3_600_000L),
    )

    private fun JSONObject.toTarget(): AutoHandshakeTarget {
        val targetObj = optJSONObject("target") ?: error("missing target")
        val observed = mutableListOf<String>()
        optJSONArray("observedClients")?.let { array ->
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let { observed += it }
            }
        }
        val attempts = mutableListOf<AutoHandshakeAttempt>()
        optJSONArray("attempts")?.let { array ->
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                runCatching { obj.toAttempt() }.getOrNull()?.let { attempts += it }
            }
        }
        return AutoHandshakeTarget(
            target = targetObj.toNetworkTarget(),
            observedClients = observed,
            deauthClient = optString("deauthClient").takeIf { it.isNotBlank() && it != "null" },
            score = optDouble("score", 0.0),
            status = enumValueOrDefault(optString("status"), AutoHandshakeTargetStatus.PENDING),
            attempts = attempts,
            inRange = optBoolean("inRange", true),
            lastSeenAtMs = optLong("lastSeenAtMs", 0L),
        )
    }

    private fun JSONObject.toNetworkTarget(): NetworkTarget = NetworkTarget(
        ssid = optString("ssid"),
        bssid = optString("bssid"),
        channel = optInt("channel", 1).coerceIn(1, 14),
        rssi = optInt("rssi", -100),
        security = optString("security", "UNKNOWN"),
        wps = optBoolean("wps", false),
        vendor = optString("vendor").takeIf { it.isNotBlank() && it != "null" },
        ouiWhitelisted = optBoolean("ouiWhitelisted", false),
        ouiBlacklisted = optBoolean("ouiBlacklisted", false),
    )

    private fun JSONObject.toAttempt(): AutoHandshakeAttempt = AutoHandshakeAttempt(
        id = optString("id"),
        startedAtMs = optLong("startedAtMs", 0L),
        endedAtMs = if (isNull("endedAtMs")) null else optLong("endedAtMs", 0L),
        channel = optInt("channel", 1).coerceIn(1, 14),
        bssid = optString("bssid"),
        client = optString("client").takeIf { it.isNotBlank() && it != "null" },
        pcapPath = optString("pcapPath").takeIf { it.isNotBlank() && it != "null" },
        packetCount = optLong("packetCount", 0L),
        deauthBursts = optInt("deauthBursts", 0),
        m1 = optBoolean("m1", false),
        m2 = optBoolean("m2", false),
        valid = optBoolean("valid", false),
        interrupted = optBoolean("interrupted", false),
        error = optString("error").takeIf { it.isNotBlank() && it != "null" },
    )

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(default)
}
