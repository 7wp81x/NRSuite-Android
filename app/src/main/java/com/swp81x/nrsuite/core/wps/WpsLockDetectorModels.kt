package com.swp81x.nrsuite.core.wps

enum class WpsLockState {
    LOCKED,
    LIKELY_UNLOCKED,
    UNKNOWN,
}

data class WpsLockAccessPoint(
    val ssid: String,
    val bssid: String,
    val channel: Int,
    val rssi: Int,
    val security: String,
    val wpsParsed: Boolean,
    val lockReported: Boolean,
    val apSetupLocked: Boolean,
    val wpsVersion: Int,
    val configMethods: Int,
    val selectedRegistrar: Boolean,
    val lastSeenMs: Long,
    val vendor: String? = null,
    val ouiWhitelisted: Boolean = false,
    val ouiBlacklisted: Boolean = false,
) {
    val lockState: WpsLockState
        get() = when {
            lockReported && apSetupLocked -> WpsLockState.LOCKED
            wpsParsed -> WpsLockState.LIKELY_UNLOCKED
            else -> WpsLockState.UNKNOWN
        }
}

data class WpsLockDetectorUiState(
    val scanning: Boolean = false,
    val accessPoints: List<WpsLockAccessPoint> = emptyList(),
    val error: String? = null,
    val lastScanAtMs: Long? = null,
)
