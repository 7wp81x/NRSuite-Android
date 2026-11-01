package com.swp81x.nrsuite.core.wpa

enum class CrackerSourceType {
    SAVED_CAPTURE,
    CUSTOM_PCAP,
    IMPORTED_22000,
}

enum class CrackerTargetStatus {
    PENDING,
    RUNNING,
    CRACKED,
    EXHAUSTED,
    STOPPED,
    ERROR,
}

enum class CrackerSessionStatus {
    PAUSED,
    STOPPED,
    COMPLETED,
    INTERRUPTED,
}

data class CrackerTargetSnapshot(
    val id: String,
    val ssid: String,
    val bssid: String?,
    val station: String?,
    val handshake: WpaHandshake,
    val selected: Boolean = true,
    val status: CrackerTargetStatus = CrackerTargetStatus.PENDING,
    val tested: Long = 0L,
    val result: String? = null,
)

data class CrackerSessionSnapshot(
    val id: String,
    val sourceType: CrackerSourceType,
    val sourceLabel: String,
    val sourceUri: String?,
    val savedSessionId: String?,
    val wordlistUri: String,
    val wordlistName: String,
    val targets: List<CrackerTargetSnapshot>,
    val currentTargetIndex: Int,
    val updatedAt: Long,
    val status: CrackerSessionStatus,
)
