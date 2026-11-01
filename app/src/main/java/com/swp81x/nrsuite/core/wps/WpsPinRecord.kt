package com.swp81x.nrsuite.core.wps

enum class WpsPinSource {
    STORED,
    GENERATED,
    PIXIE,
    MANUAL,
}

enum class WpsPinAttemptResult {
    UNKNOWN,
    OK,
    WRONG_PIN,
    LOCKED,
    ERROR,
}

/**
 * App-private BSSID -> WPS PIN database entry.
 *
 * A PIN is marked verified only after the full M1-M8 exchange produced a PSK.
 * Failed entries are retained so the next attempt can skip known-bad values.
 */
data class WpsPinRecord(
    val bssid: String,
    val ssid: String,
    val pin: String,
    val source: WpsPinSource,
    val verified: Boolean = false,
    val lastResult: WpsPinAttemptResult = WpsPinAttemptResult.UNKNOWN,
    val updatedAt: String,
)
