package com.swp81x.nrsuite.core.wpa

data class Pcap22000Candidate(
    val sourceName: String,
    val handshake: WpaHandshake,
    val detectedSsid: String? = null,
    val ssidOverride: String? = null,
) {
    val effectiveSsid: String?
        get() = ssidOverride?.trim()?.takeIf { it.isNotEmpty() }
            ?: detectedSsid?.trim()?.takeIf { it.isNotEmpty() }

    val bssidHex: String?
        get() = handshake.bssid?.toMacString()

    val stationHex: String?
        get() = handshake.station?.toMacString()
}

data class Pcap22000Export(
    val lines: List<String>,
    val exportedCandidates: List<Pcap22000Candidate>,
    val duplicatesRemoved: Int,
    val missingSsid: Int,
    val unsupported: Int,
)

/**
 * Keep one highest-quality handshake for each BSSID + station pair. Repeated
 * captures of the same exchange are never exported twice, while different
 * clients of the same AP remain independent targets.
 */
fun dedupeBestWpaCandidates(
    candidates: List<Pcap22000Candidate>,
): Pair<List<Pcap22000Candidate>, Int> {
    val selected = mutableListOf<Pcap22000Candidate>()
    val indexByKey = mutableMapOf<String, Int>()
    var duplicatesRemoved = 0

    candidates.forEach { candidate ->
        val key = candidate.handshakeKey()
        if (key == null) {
            selected += candidate
            return@forEach
        }
        val existingIndex = indexByKey[key]
        if (existingIndex == null) {
            indexByKey[key] = selected.size
            selected += candidate
        } else {
            duplicatesRemoved++
            val existing = selected[existingIndex]
            if (candidate.qualityScore() >= existing.qualityScore()) {
                selected[existingIndex] = candidate
            }
        }
    }
    return selected to duplicatesRemoved
}

/**
 * Merge and deduplicate M1/M2 candidates, apply BSSID→ESSID overrides, and
 * produce one Hashcat mode 22000 line per retained handshake.
 */
fun buildPcap22000Export(
    candidates: List<Pcap22000Candidate>,
    ssidOverrides: Map<String, String> = emptyMap(),
): Pcap22000Export {
    val normalizedOverrides = ssidOverrides.entries.associate { entry ->
        entry.key.trim().uppercase() to entry.value.trim()
    }
    val overridden = candidates.map { candidate ->
        val bssid = candidate.bssidHex?.uppercase()
        val override = bssid?.let { normalizedOverrides[it] }
        if (override.isNullOrBlank()) candidate else candidate.copy(ssidOverride = override)
    }

    val (selected, duplicatesRemoved) = dedupeBestWpaCandidates(overridden)
    val lines = mutableListOf<String>()
    val exported = mutableListOf<Pcap22000Candidate>()
    var missingSsid = 0
    var unsupported = 0
    selected.forEach { candidate ->
        val ssid = candidate.effectiveSsid
        if (ssid.isNullOrBlank()) {
            missingSsid++
            return@forEach
        }
        val line = Wpa22000Exporter.buildLine(candidate.handshake, ssid)
        if (line == null) {
            unsupported++
        } else {
            lines += line
            exported += candidate
        }
    }

    return Pcap22000Export(
        lines = lines,
        exportedCandidates = exported,
        duplicatesRemoved = duplicatesRemoved,
        missingSsid = missingSsid,
        unsupported = unsupported,
    )
}

private fun Pcap22000Candidate.handshakeKey(): String? {
    val bssid = bssidHex ?: return null
    val station = stationHex ?: return null
    return "$bssid|$station"
}

fun Pcap22000Candidate.qualityScore(): Int {
    var score = 10
    if (handshake.isComplete) score += 100
    if (!effectiveSsid.isNullOrBlank()) score += 50
    if (handshake.aNonce?.any { it.toInt() != 0 } == true) score += 20
    if (handshake.sNonce?.any { it.toInt() != 0 } == true) score += 20
    if (handshake.mic?.any { it.toInt() != 0 } == true) score += 20
    if (handshake.keyDescriptorVersion == 2) score += 10
    if (handshake.eapolM2?.size in 100..256) score += 10
    return score
}

private fun ByteArray.toMacString(): String =
    joinToString(":") { byte -> "%02X".format(byte.toInt() and 0xFF) }
