package com.swp81x.nrsuite.core.wifi

/**
 * UI model for a scanned AP/target used by target-selection screens.
 */
data class NetworkTarget(
    val ssid: String,
    val bssid: String,
    val channel: Int,
    val rssi: Int,
    val security: String,
    val vendor: String? = null,
    val ouiWhitelisted: Boolean = false,
    val ouiBlacklisted: Boolean = false,
)
