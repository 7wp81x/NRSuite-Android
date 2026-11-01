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
    val m1Seen: Boolean = false,
    val m2Seen: Boolean = false,
    val m3Seen: Boolean = false,
    val m4Seen: Boolean = false,
    val m5Seen: Boolean = false,
    val m6Seen: Boolean = false,
    val m7Seen: Boolean = false,
    val m8Seen: Boolean = false,
    val m2dSeen: Boolean = false,
    val nackSeen: Boolean = false,
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
) {
    /**
     * Pixie Dust/offline WPS PIN recovery requires material from the same
     * session's M1, M2, and M3 exchange. A NACK alone is not enough.
     */
    val hasCrackableM1M3Material: Boolean
        get() = m1Seen && m2Seen && m3Seen

    /**
     * M7 carries the encrypted WPS credential payload and M8 acknowledges it.
     * Seeing both in the same session is strong evidence that the WPA PSK was
     * transferred.
     */
    val hasCredentialTransferEvidence: Boolean
        get() = m7Seen && m8Seen
}

data class WpsAttemptRiskAssessment(
    val risk: WpsAttemptRisk,
    val reasons: List<String>,
)

/**
 * Pure risk rules shared by the live WPS attempt detector and host tests.
 *
 * High risk is raised when the same session captured M1+M2+M3, even if a NACK
 * or M2D followed. NACK-only activity remains Medium and explicitly states
 * that no M1-M3 material was observed.
 */
fun assessWpsAttemptRisk(
    session: WpsAttemptSession,
    targetSessionCount: Int,
    distinctClientCount: Int,
    lockState: WpsLockState,
    lockConfidence: WpsLockConfidence,
    lockTransitioned: Boolean,
): WpsAttemptRiskAssessment {
    val reasons = mutableListOf<String>()
    var risk = WpsAttemptRisk.LOW

    if (targetSessionCount >= 2) {
        reasons += "$targetSessionCount observed attempts against this AP"
        risk = WpsAttemptRisk.MEDIUM
    }
    if (distinctClientCount >= 2) {
        reasons += "$distinctClientCount different source MACs"
        risk = WpsAttemptRisk.MEDIUM
    }
    if (targetSessionCount >= 5 || distinctClientCount >= 3) {
        risk = WpsAttemptRisk.HIGH
        reasons += "Sustained WPS attempt activity"
    }

    if (session.hasCrackableM1M3Material) {
        risk = WpsAttemptRisk.HIGH
        reasons += "WPS M1/M2/M3 exchange observed — offline PIN cracking material"
    }
    if (session.hasCredentialTransferEvidence) {
        risk = WpsAttemptRisk.HIGH
        reasons += "WPS M7/M8 exchange observed — WPA PSK likely transferred"
    } else if (session.m7Seen) {
        if (risk == WpsAttemptRisk.LOW) risk = WpsAttemptRisk.MEDIUM
        reasons += "WPS M7 observed without M8 acknowledgement"
    }
    if (!session.hasCrackableM1M3Material) {
        val partialEvidence = listOf(
            "M1" to session.m1Seen,
            "M2" to session.m2Seen,
            "M3" to session.m3Seen,
        ).filter { it.second }.map { it.first }
        if (partialEvidence.size >= 2) {
            if (risk == WpsAttemptRisk.LOW) risk = WpsAttemptRisk.MEDIUM
            reasons += "Partial WPS M1-M3 evidence: ${partialEvidence.joinToString("/")}"
        }
    }

    val nackOrM2d = session.nackSeen || session.m2dSeen ||
        session.stage == "NACK" || session.stage == "M2D"
    if (nackOrM2d) {
        reasons += if (session.hasCrackableM1M3Material) {
            "WPS ${session.stage} observed after M1/M2/M3 material"
        } else {
            "WPS ${session.stage} observed; no M1-M3 material captured"
        }
        if (risk == WpsAttemptRisk.LOW) risk = WpsAttemptRisk.MEDIUM
    }

    if (lockState == WpsLockState.LOCKED) {
        when (lockConfidence) {
            WpsLockConfidence.LIVE -> {
                risk = WpsAttemptRisk.HIGH
                reasons += "Attempt observed against a live WPS-locked AP"
            }
            WpsLockConfidence.RECENT -> {
                if (risk == WpsAttemptRisk.LOW) risk = WpsAttemptRisk.MEDIUM
                reasons += "Target reported WPS Locked in a recent scan"
            }
            WpsLockConfidence.STALE -> {
                reasons += "Saved WPS lock state is stale and was not used for risk"
            }
            WpsLockConfidence.NONE -> Unit
        }
    }
    if (lockTransitioned && lockConfidence == WpsLockConfidence.LIVE) {
        risk = WpsAttemptRisk.HIGH
        reasons += "AP transitioned to WPS Locked during live monitoring"
    }
    if (session.frames <= 1) {
        reasons += "Incomplete WPS message sequence"
    }
    if (reasons.isEmpty()) {
        reasons += "Single observed WPS attempt"
    }
    return WpsAttemptRiskAssessment(
        risk = risk,
        reasons = reasons.distinct(),
    )
}

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
