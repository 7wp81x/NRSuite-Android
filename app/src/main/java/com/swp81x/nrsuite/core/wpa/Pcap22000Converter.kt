package com.swp81x.nrsuite.core.wpa

enum class Pcap22000DedupeMode {
    NONE,
    EXACT,
    BEST_PER_AP,
}

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
 * Merge and deduplicate M1/M2 candidates, apply BSSID→ESSID overrides, and
 * produce one Hashcat mode 22000 line per retained handshake.
 */
fun buildPcap22000Export(
    candidates: List<Pcap22000Candidate>,
    dedupeMode: Pcap22000DedupeMode,
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

    val exactSeen = linkedSetOf<String>()
    val bestByAp = linkedMapOf<String, Pcap22000Candidate>()
    var duplicatesRemoved = 0

    overridden.forEach { candidate ->
        when (dedupeMode) {
            Pcap22000DedupeMode.NONE -> Unit
            Pcap22000DedupeMode.EXACT -> {
                val key = candidate.exactKey()
                if (key != null && !exactSeen.add(key)) duplicatesRemoved++
            }
            Pcap22000DedupeMode.BEST_PER_AP -> {
                val apKey = candidate.apKey()
                if (apKey == null) return@forEach
                val previous = bestByAp[apKey]
                if (previous == null || candidate.qualityScore() > previous.qualityScore()) {
                    if (previous != null) duplicatesRemoved++
                    bestByAp[apKey] = candidate
                } else {
                    duplicatesRemoved++
                }
            }
        }
    }

    val selected = when (dedupeMode) {
        Pcap22000DedupeMode.NONE -> overridden
        Pcap22000DedupeMode.EXACT -> overridden.filter { candidate ->
            val key = candidate.exactKey()
            key == null || key in exactSeen
        }.distinctBy { candidate -> candidate.exactKey() }
        Pcap22000DedupeMode.BEST_PER_AP -> bestByAp.values.toList()
    }

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

private fun Pcap22000Candidate.exactKey(): String? {
    val bssid = bssidHex ?: return null
    val station = stationHex ?: return null
    val mic = handshake.mic?.toHex() ?: return null
    val replay = handshake.m1ReplayCounter ?: handshake.m2ReplayCounter ?: -1L
    return "$bssid|$station|$replay|$mic"
}

private fun Pcap22000Candidate.apKey(): String? {
    val bssid = bssidHex ?: return null
    val ssid = effectiveSsid ?: return null
    return "$bssid|$ssid"
}

private fun Pcap22000Candidate.qualityScore(): Int {
    var score = 10
    if (handshake.isComplete) score += 100
    if (handshake.aNonce?.any { it.toInt() != 0 } == true) score += 20
    if (handshake.sNonce?.any { it.toInt() != 0 } == true) score += 20
    if (handshake.mic?.any { it.toInt() != 0 } == true) score += 20
    if (handshake.keyDescriptorVersion == 2) score += 10
    if (handshake.eapolM2?.size in 100..256) score += 10
    return score
}

private fun ByteArray.toHex(): String =
    joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }

private fun ByteArray.toMacString(): String =
    joinToString(":") { byte -> "%02X".format(byte.toInt() and 0xFF) }
