package com.swp81x.nrsuite.core.credentials

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * App-private persistence for reviewed credential sessions.
 *
 * Only correct Evil Twin attempts are written by the current caller. The model
 * already carries a source/status pair so the rogue-AP portal feature can reuse
 * the same store later for unverified portal captures.
 */
class CredentialStore(
    private val directory: File,
) {
    private val sessionsFile = File(directory, SESSIONS_FILE)

    @Synchronized
    fun loadSessions(): List<CredentialSession> {
        if (!sessionsFile.exists()) return emptyList()
        val raw = runCatching { sessionsFile.readText() }.getOrNull() ?: return emptyList()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val array = root.optJSONArray("sessions") ?: return emptyList()
        val sessions = mutableListOf<CredentialSession>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            runCatching { obj.toSession() }.getOrNull()?.let { sessions += it }
        }
        return sessions
    }

    @Synchronized
    fun saveSessions(sessions: List<CredentialSession>) {
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
    fun deleteCaptureFile(session: CredentialSession) {
        session.pcapPath?.let { path ->
            runCatching { File(path).delete() }
        }
    }

    private fun CredentialSession.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("source", source.name)
        put("ssid", ssid)
        put("bssid", bssid)
        put("channel", channel ?: JSONObject.NULL)
        put("startedAt", startedAt)
        put("endedAt", endedAt ?: JSONObject.NULL)
        put("pcapPath", pcapPath ?: JSONObject.NULL)
        put("credentials", JSONArray().apply {
            credentials.forEach { credential ->
                put(JSONObject().apply {
                    put("value", credential.value)
                    put("capturedAt", credential.capturedAt)
                    put("status", credential.status.name)
                    put("source", credential.source.name)
                    put("details", JSONObject().apply {
                        credential.details.forEach { (key, value) -> put(key, value) }
                    })
                })
            }
        })
    }

    private fun JSONObject.toSession(): CredentialSession {
        val source = runCatching {
            CredentialSource.valueOf(optString("source"))
        }.getOrDefault(CredentialSource.EVIL_TWIN)

        val credentialsArray = optJSONArray("credentials") ?: JSONArray()
        val credentials = mutableListOf<CapturedCredential>()
        for (index in 0 until credentialsArray.length()) {
            val obj = credentialsArray.optJSONObject(index) ?: continue
            val status = runCatching {
                CredentialStatus.valueOf(obj.optString("status"))
            }.getOrDefault(CredentialStatus.UNVERIFIED)
            val credSource = runCatching {
                CredentialSource.valueOf(obj.optString("source"))
            }.getOrDefault(source)
            val detailsObject = obj.optJSONObject("details")
            val details = mutableMapOf<String, String>()
            detailsObject?.keys()?.forEach { key ->
                details[key] = detailsObject.optString(key)
            }
            credentials += CapturedCredential(
                value = obj.optString("value"),
                capturedAt = obj.optString("capturedAt"),
                status = status,
                source = credSource,
                details = details,
            )
        }

        return CredentialSession(
            id = optString("id"),
            source = source,
            ssid = optString("ssid").ifBlank { "(hidden)" },
            bssid = optString("bssid"),
            channel = if (isNull("channel")) null else optInt("channel"),
            startedAt = optString("startedAt"),
            endedAt = if (isNull("endedAt")) null else optString("endedAt"),
            pcapPath = if (isNull("pcapPath")) null else optString("pcapPath"),
            credentials = credentials,
        )
    }

    companion object {
        private const val SESSIONS_FILE = "sessions.json"
    }
}
