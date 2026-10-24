package com.swp81x.nrsuite.core.handshake

import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.wifi.NetworkTarget

enum class AutoHandshakePhase {
    IDLE,
    RECON,
    INTERACT,
    SCANNING,
    SURVEYING,
    RANKING,
    TARGET_READY,
    CAPTURING,
    DEAUTH_ASSIST,
    HANDSHAKE_FOUND,
    SAVING,
    PAUSED,
    STOPPING,
    COMPLETED,
    FAILED,
    INTERRUPTED,
}

enum class AutoHandshakeTargetStatus {
    PENDING,
    ACTIVE,
    CAPTURED,
    NO_HANDSHAKE,
    UNSUPPORTED,
    SKIPPED,
    INTERRUPTED,
}

data class AutoHandshakeConfig(
    val allChannels: Boolean = true,
    val scanTimeoutMs: Long = 15_000L,
    val deauthAssist: Boolean = true,
    val skipCapturedMacs: Boolean = true,
    val maxApAttemptsPerCycle: Int = 4,
    val deauthFramesPerAp: Int = 8,
    val deauthIntervalMs: Int = 50,
    val channelWaitMs: Long = 6_000L,
    val channelWaitNoDeauthMs: Long = 1_500L,
    val rescanDelayMs: Long = 1_500L,
    val failedTargetCooldownMs: Long = 0L,
)

data class AutoHandshakeAttempt(
    val id: String,
    val startedAtMs: Long,
    val endedAtMs: Long? = null,
    val channel: Int,
    val bssid: String,
    val client: String? = null,
    val pcapPath: String? = null,
    val packetCount: Long = 0L,
    val deauthBursts: Int = 0,
    val m1: Boolean = false,
    val m2: Boolean = false,
    val valid: Boolean = false,
    val interrupted: Boolean = false,
    val error: String? = null,
)

data class AutoHandshakeTarget(
    val target: NetworkTarget,
    val observedClients: List<String> = emptyList(),
    val deauthClient: String? = null,
    val score: Double = 0.0,
    val status: AutoHandshakeTargetStatus = AutoHandshakeTargetStatus.PENDING,
    val attempts: List<AutoHandshakeAttempt> = emptyList(),
    val inRange: Boolean = true,
    val lastSeenAtMs: Long = 0L,
) {
    val bssidKey: String
        get() = target.bssid.uppercase().replace(":", "")

    val captureFileName: String
        get() = "HS_$bssidKey.pcap"

    val lastAttempt: AutoHandshakeAttempt?
        get() = attempts.lastOrNull()
}

data class AutoHandshakeSession(
    val id: String,
    val createdAt: String,
    val updatedAt: String,
    val deviceId: String? = null,
    val state: AutoHandshakePhase = AutoHandshakePhase.IDLE,
    val config: AutoHandshakeConfig = AutoHandshakeConfig(),
    val targets: List<AutoHandshakeTarget> = emptyList(),
    val lastError: String? = null,
)

data class HandshakeProgress(
    val m1Found: Boolean = false,
    val m2Found: Boolean = false,
    val bssid: String? = null,
    val station: String? = null,
    val valid: Boolean = false,
    val packetCount: Long = 0L,
    val eapolCount: Long = 0L,
    val deauthBursts: Int = 0,
    val elapsedMs: Long = 0L,
)

data class AutoHandshakeStats(
    val totalCaptures: Int = 0,
    val todayCaptures: Int = 0,
    val statsDate: String = "",
    val lastCapturedAt: String? = null,
    val lastBssid: String? = null,
)

data class AutoHandshakeFeedEntry(
    val timestampMs: Long,
    val level: LogLevel,
    val message: String,
)

data class AutoHandshakeUiState(
    val running: Boolean = false,
    val phase: AutoHandshakePhase = AutoHandshakePhase.IDLE,
    val reactionFace: String = "-_-",
    val reactionPhrase: String = "Ready.",
    val currentChannel: Int? = null,
    val cycleHandshakeCount: Int = 0,
    val cycleTargetCount: Int = 0,
    val session: AutoHandshakeSession? = null,
    val targets: List<AutoHandshakeTarget> = emptyList(),
    val currentTarget: AutoHandshakeTarget? = null,
    val progress: HandshakeProgress = HandshakeProgress(),
    val stats: AutoHandshakeStats = AutoHandshakeStats(),
    val error: String? = null,
)
