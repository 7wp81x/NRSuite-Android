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
