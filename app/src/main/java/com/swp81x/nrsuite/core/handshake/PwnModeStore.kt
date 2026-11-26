package com.swp81x.nrsuite.core.handshake

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * App-private persistence for Pwn Mode settings and captured-handshake metadata.
 *
 * Pwn Mode is intentionally limited to a small user-managed password list. The
 * actual verification is done by [com.swp81x.nrsuite.core.wpa.WpaHandshakeVerifier].
 */
data class PwnWordlistImport(
    val passwords: List<String>,
    val sourceLines: Int,
    val rejectedLines: Int,
    val duplicateLines: Int,
    val truncated: Boolean,
)

/**
 * Pwn Mode keeps only a bounded slice of an imported wordlist because every
 * captured handshake is checked against every saved password on-device.
 */
fun slicePwnWordlist(
    lines: Sequence<String>,
    limit: Int = PwnModeStore.MAX_PASSWORDS,
): PwnWordlistImport {
    val passwords = mutableListOf<String>()
    var sourceLines = 0
    var rejectedLines = 0
    var duplicateLines = 0
    var truncated = false
    for (line in lines) {
        val clean = line.trim()
        if (clean.isEmpty()) continue
        sourceLines++
        if (clean.length !in 8..63) {
            rejectedLines++
            continue
        }
        if (clean in passwords) {
            duplicateLines++
            continue
        }
        if (passwords.size >= limit) {
            truncated = true
            continue
        }
        passwords += clean
    }
    return PwnWordlistImport(
        passwords = passwords,
        sourceLines = sourceLines,
        rejectedLines = rejectedLines,
        duplicateLines = duplicateLines,
        truncated = truncated,
    )
}

class PwnModeStore(
    val root: File,
) {
    private val settingsFile = File(root, "pwn_mode.json")
    private val capturesFile = File(root, "captures.json")

    fun ensureDirs() {
        root.mkdirs()
    }

    @Synchronized
    fun loadState(): PwnModeState {
        ensureDirs()
        if (!settingsFile.exists()) return PwnModeState()
        val rootObj = runCatching {
            JSONObject(settingsFile.readText())
        }.getOrNull() ?: return PwnModeState()

        val passwords = mutableListOf<String>()
        rootObj.optJSONArray("passwords")?.let { array ->
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotEmpty()) passwords += value
            }
        }
        return PwnModeState(
            enabled = rootObj.optBoolean("enabled", false),
            passwords = passwords.distinct().take(MAX_PASSWORDS),
            wordlistName = rootObj.optString("wordlist_name").takeIf { it.isNotBlank() },
            sourceLineCount = rootObj.optInt("source_line_count", 0),
            rejectedLineCount = rootObj.optInt("rejected_line_count", 0),
            duplicateLineCount = rootObj.optInt("duplicate_line_count", 0),
            truncated = rootObj.optBoolean("truncated", false),
        )
    }

    @Synchronized
    fun saveState(state: PwnModeState) {
        ensureDirs()
        val obj = JSONObject().apply {
            put("enabled", state.enabled)
            put("wordlist_name", state.wordlistName ?: "")
            put("source_line_count", state.sourceLineCount)
            put("rejected_line_count", state.rejectedLineCount)
            put("duplicate_line_count", state.duplicateLineCount)
            put("truncated", state.truncated)
            put("passwords", JSONArray().apply {
                state.passwords.take(MAX_PASSWORDS).forEach { put(it) }
            })
        }
        writeAtomically(settingsFile, obj.toString(2))
    }

    @Synchronized
    fun loadCaptures(): List<CapturedHandshakeEntry> {
        ensureDirs()
        if (!capturesFile.exists()) return emptyList()
        val rootObj = runCatching { JSONObject(capturesFile.readText()) }.getOrNull() ?: return emptyList()
        val array = rootObj.optJSONArray("captures") ?: return emptyList()
        val captures = mutableListOf<CapturedHandshakeEntry>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            runCatching { obj.toCaptureEntry() }.getOrNull()?.let { captures += it }
        }
        return captures.sortedByDescending { it.capturedAt }
    }

    @Synchronized
    fun saveCaptures(captures: List<CapturedHandshakeEntry>) {
        ensureDirs()
        val obj = JSONObject().apply {
            put("captures", JSONArray().apply {
                captures.forEach { put(it.toJson()) }
            })
        }
        writeAtomically(capturesFile, obj.toString(2))
    }

    @Synchronized
    fun upsertCapture(entry: CapturedHandshakeEntry) {
        val current = loadCaptures().toMutableList()
        val index = current.indexOfFirst { it.bssid.equals(entry.bssid, ignoreCase = true) }
        if (index >= 0) {
            current[index] = entry
        } else {
            current.add(0, entry)
        }
        saveCaptures(current)
    }

    @Synchronized
    fun deleteCapture(entry: CapturedHandshakeEntry) {
        runCatching { File(entry.pcapPath).delete() }
        val current = loadCaptures().filterNot {
            it.bssid.equals(entry.bssid, ignoreCase = true)
        }
        saveCaptures(current)
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

    private fun CapturedHandshakeEntry.toJson(): JSONObject = JSONObject().apply {
        put("bssid", bssid)
        put("ssid", ssid)
        put("channel", channel)
        put("pcapPath", pcapPath)
        put("capturedAt", capturedAt)
        put("status", status.name)
        put("password", password ?: JSONObject.NULL)
        put("crackSource", crackSource?.name ?: JSONObject.NULL)
    }

    private fun JSONObject.toCaptureEntry(): CapturedHandshakeEntry {
        val status = runCatching {
            HandshakeCrackStatus.valueOf(optString("status"))
        }.getOrDefault(HandshakeCrackStatus.UNCLEARED)
        val source = optString("crackSource")
            .takeIf { it.isNotBlank() && it != "null" }
            ?.let { runCatching { CrackSource.valueOf(it) }.getOrNull() }
        return CapturedHandshakeEntry(
            bssid = optString("bssid"),
            ssid = optString("ssid"),
            channel = optInt("channel", 1).coerceIn(1, 14),
            pcapPath = optString("pcapPath"),
            capturedAt = optString("capturedAt"),
            status = status,
            password = optString("password").takeIf { it.isNotBlank() && it != "null" },
            crackSource = source,
        )
    }

    companion object {
        const val MAX_PASSWORDS = 1000
    }
}
