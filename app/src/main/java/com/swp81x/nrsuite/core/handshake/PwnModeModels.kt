package com.swp81x.nrsuite.core.handshake

enum class HandshakeCrackStatus {
    UNCLEARED,
    CRACKED,
    FAILED,
}

enum class CrackSource {
    PWN_MODE,
    WPA_CRACKER,
}

data class PwnModeState(
    val enabled: Boolean = false,
    val passwords: List<String> = emptyList(),
    val wordlistName: String? = null,
    val sourceLineCount: Int = 0,
    val rejectedLineCount: Int = 0,
    val duplicateLineCount: Int = 0,
    val truncated: Boolean = false,
)

data class PwnVerificationRequest(
    val handshake: com.swp81x.nrsuite.core.wpa.WpaHandshake,
    val ssid: String,
    val entry: CapturedHandshakeEntry,
)

data class PwnModeQueueUiState(
    val active: Boolean = false,
    val queued: Int = 0,
    val currentCandidate: Int = 0,
    val totalCandidates: Int = 0,
    val currentBssid: String? = null,
    val queuedBssids: Set<String> = emptySet(),
)

data class CapturedHandshakeEntry(
    val bssid: String,
    val ssid: String,
    val channel: Int,
    val pcapPath: String,
    val capturedAt: String,
    val status: HandshakeCrackStatus = HandshakeCrackStatus.UNCLEARED,
    val password: String? = null,
    val crackSource: CrackSource? = null,
)
