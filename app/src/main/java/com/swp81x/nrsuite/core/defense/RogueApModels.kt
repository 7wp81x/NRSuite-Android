package com.swp81x.nrsuite.core.defense

/**
 * Classification for an observed access point that does not match the trusted
 * baseline or otherwise looks suspicious.
 */
enum class RogueApCategory { EVIL_TWIN, FAKE_PORTAL, UNKNOWN_ROGUE }

/**
 * One user-approved AP observation. A trusted network is keyed by the pair of
 * SSID + BSSID; multiple BSSIDs with the same SSID are allowed.
 */
data class TrustedNetwork(
    val ssid: String,
    val bssid: String,
    val channel: Int,
    val security: String,
    val addedAt: String,
)

/**
 * A suspicious AP observation produced by the Rogue AP detector.
 */
data class RogueApAlert(
    val id: String,
    val ssid: String,
    val bssid: String,
    val channel: Int,
    val rssi: Int,
    val category: RogueApCategory,
    val reasons: List<String>,
    val confidence: AlertConfidence,
    val vendor: String?,
    val detectedAt: String,
)

/**
 * Security ranking used to decide whether an observation is downgraded
 * compared to the trusted baseline for the same AP.
 */
internal fun securityRank(security: String): Int {
    val normalized = security.uppercase()
    return when {
        "WPA3" in normalized -> 4
        "WPA2" in normalized -> 3
        "WPA" in normalized -> 2
        "WEP" in normalized -> 1
        "OPEN" in normalized -> 0
        else -> -1
    }
}
