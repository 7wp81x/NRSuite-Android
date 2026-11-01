package com.swp81x.nrsuite.core.wpa

data class WpaHandshakeCandidate(
    val handshake: WpaHandshake,
    val ssid: String?,
)

/**
 * Pick the most useful complete M1/M2 exchange from a capture. Preferred SSID
 * and BSSID matches rank above generic completeness, while exact duplicates
 * are removed.
 */
fun selectBestWpaHandshake(
    candidates: List<WpaHandshakeCandidate>,
    preferredSsid: String? = null,
    preferredBssid: String? = null,
): WpaHandshake? {
    val cleanSsid = preferredSsid?.trim()?.takeIf { it.isNotEmpty() }
    val cleanBssid = preferredBssid?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
    val unique = linkedMapOf<String, WpaHandshakeCandidate>()
    candidates.forEach { candidate ->
        val handshake = candidate.handshake
        if (!handshake.isComplete) return@forEach
        val bssid = handshake.bssid ?: return@forEach
        val station = handshake.station ?: return@forEach
        val mic = handshake.mic ?: return@forEach
        val key = buildString {
            append(bssid.toHex())
            append('|')
            append(station.toHex())
            append('|')
            append(handshake.m1ReplayCounter ?: handshake.m2ReplayCounter ?: -1L)
            append('|')
            append(mic.toHex())
        }
        unique.putIfAbsent(key, candidate)
    }

    return unique.values.maxByOrNull { candidate ->
        var score = 1_000
        val handshake = candidate.handshake
        if (cleanSsid != null && candidate.ssid == cleanSsid) score += 500
        if (cleanBssid != null && handshake.bssid?.toMacString() == cleanBssid) score += 250
        if (handshake.aNonce?.any { it.toInt() != 0 } == true) score += 20
        if (handshake.sNonce?.any { it.toInt() != 0 } == true) score += 20
        if (handshake.mic?.any { it.toInt() != 0 } == true) score += 20
        if (handshake.keyDescriptorVersion == 2) score += 10
        score
    }?.handshake
}

private fun ByteArray.toMacString(): String =
    joinToString(":") { value -> "%02X".format(value.toInt() and 0xFF) }

private fun ByteArray.toHex(): String =
    joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
