package com.swp81x.nrsuite.core.wps

enum class WpsAttemptRisk {
    LOW,
    MEDIUM,
    HIGH,
}

data class WpsAttemptSession(
    val sessionId: Long,
    val bssid: String,
    val client: String,
    val ssid: String,
    val channel: Int,
    val rssi: Int,
    val stage: String,
    val messageType: Int,
    val frames: Int,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val durationMs: Long,
    val vendor: String? = null,
    val randomizedMac: Boolean = false,
    val risk: WpsAttemptRisk = WpsAttemptRisk.LOW,
    val riskReasons: List<String> = emptyList(),
)

data class WpsAttemptDetectorUiState(
    val starting: Boolean = false,
    val running: Boolean = false,
    val fixedMode: Boolean = true,
    val channel: Int = 6,
    val hopIntervalMs: Int = 300,
    val sessions: List<WpsAttemptSession> = emptyList(),
    val error: String? = null,
    val lastStatusAtMs: Long? = null,
)
