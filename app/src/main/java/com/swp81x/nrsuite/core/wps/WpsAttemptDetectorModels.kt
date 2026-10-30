package com.swp81x.nrsuite.core.wps

enum class WpsAttemptRisk {
    LOW,
    MEDIUM,
    HIGH,
}

enum class WpsLockObservationSource {
    WPS_LOCK_SCAN,
    LIVE_MONITOR,
}

enum class WpsLockConfidence {
    LIVE,
    RECENT,
    STALE,
    NONE,
}

data class BssidLockSnapshot(
    val state: WpsLockState,
    val lockReported: Boolean,
    val wpsParsed: Boolean,
    val source: WpsLockObservationSource,
    val receivedAtElapsedMs: Long,
)

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
    val lockState: WpsLockState = WpsLockState.UNKNOWN,
    val lockSource: WpsLockObservationSource? = null,
    val lockAgeMs: Long? = null,
    val lockConfidence: WpsLockConfidence = WpsLockConfidence.NONE,
    val risk: WpsAttemptRisk = WpsAttemptRisk.LOW,
    val riskReasons: List<String> = emptyList(),
)

data class WpsAttemptDetectorUiState(
    val starting: Boolean = false,
    val running: Boolean = false,
    val allChannels: Boolean = false,
    val selectedTargetCount: Int = 0,
    val monitoredChannels: List<Int> = emptyList(),
    val hopIntervalMs: Int = 300,
    val sessions: List<WpsAttemptSession> = emptyList(),
    val lockSnapshots: Map<String, BssidLockSnapshot> = emptyMap(),
    val lockTransitionBssids: Set<String> = emptySet(),
    val scanCandidates: List<com.swp81x.nrsuite.core.wifi.NetworkTarget> = emptyList(),
    val scanningTargets: Boolean = false,
    val error: String? = null,
    val lastStatusAtMs: Long? = null,
)
