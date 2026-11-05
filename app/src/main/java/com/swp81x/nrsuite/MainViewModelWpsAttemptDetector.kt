package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.defense.matchOuiRule
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.notifications.NrSuiteNotifications
import com.swp81x.nrsuite.core.wifi.NetworkTarget
import com.swp81x.nrsuite.core.wifi.bandForWifiChannel
import com.swp81x.nrsuite.core.wps.BssidLockSnapshot
import com.swp81x.nrsuite.core.wps.WpsAttemptSession
import com.swp81x.nrsuite.core.wps.assessWpsAttemptRisk
import com.swp81x.nrsuite.core.wps.WpsLockConfidence
import com.swp81x.nrsuite.core.wps.WpsLockObservationSource
import com.swp81x.nrsuite.core.wps.WpsLockState
import com.swp81x.nrsuite.core.wps.shouldRetryWpsTargetScan
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

internal fun MainViewModel.startWpsAttemptTargetScanImpl() {
    val activeSession = session
    if (activeSession == null) {
        appendLog("WPS Attempt Detector: connect to a device before scanning.", level = LogLevel.ERROR)
        _toastMessage.value = "Connect a device before scanning."
        return
    }
    val current = _wpsAttemptDetector.value
    if (current.running || current.starting) {
        _toastMessage.value = "Stop WPS attempt monitoring before scanning targets."
        return
    }
    if (current.scanningTargets) return
    if (!ensureRadioIdle("WPS Attempt Detector scan")) return

    wpsAttemptTargetScanJob?.cancel()
    wifiScanDestination = WifiScanDestination.WPS_ATTEMPT_TARGETS
    _wpsAttemptDetector.update {
        it.copy(
            scanningTargets = true,
            scanCandidates = emptyList(),
            lockSnapshots = emptyMap(),
            lockTransitionBssids = emptySet(),
            error = null,
        )
    }
    updateForegroundService()
    wpsAttemptTargetScanJob = scope.launch(Dispatchers.IO) {
        try {
            val scan = runWpsLockScanWithRecovery(activeSession)
            val visibleCount = _wpsAttemptDetector.value.scanCandidates.size
            if (scan.response?.optBoolean("ok") == true) {
                if (shouldRetryWpsTargetScan(scan.reportedCount, scan.receivedCount)) {
                    appendLog(
                        "WPS Attempt Detector: scan returned ${scan.reportedCount} target(s) but only " +
                            "${scan.receivedCount} event(s) arrived; kept $visibleCount partial target(s).",
                        level = LogLevel.ERROR,
                    )
                } else {
                    appendLog("WPS Attempt Detector: found ${scan.reportedCount} WPS target(s).")
                }
            } else {
                val message = scan.response?.optString("msg")?.takeIf { it.isNotBlank() }
                    ?: scan.response?.optString("error")?.takeIf { it.isNotBlank() }
                    ?: "scan timed out"
                _wpsAttemptDetector.update { it.copy(error = message) }
                appendLog("WPS Attempt Detector scan failed: $message", level = LogLevel.ERROR)
                _toastMessage.value = "WPS target scan failed: $message"
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val message = error.message ?: error.javaClass.simpleName
            _wpsAttemptDetector.update { it.copy(error = message) }
            appendLog("WPS Attempt Detector scan failed: $message", level = LogLevel.ERROR)
            _toastMessage.value = "WPS target scan failed: $message"
        } finally {
            if (wifiScanDestination == WifiScanDestination.WPS_ATTEMPT_TARGETS) {
                wifiScanDestination = null
            }
            _wpsAttemptDetector.update { it.copy(scanningTargets = false) }
            wpsAttemptTargetScanJob = null
            updateForegroundService()
        }
    }
}

internal fun MainViewModel.startWpsAttemptDetectorImpl(
    targets: List<NetworkTarget>,
    allChannels: Boolean,
    intervalMs: Int,
) {
    val activeSession = session
    if (activeSession == null) {
        appendLog("WPS Attempt Detector: connect to a device before starting.", level = LogLevel.ERROR)
        _toastMessage.value = "Connect a device before starting."
        return
    }
    if (_wpsAttemptDetector.value.starting || _wpsAttemptDetector.value.running) return
    val cleanTargets = targets.distinctBy { it.bssid.uppercase() }
    if (!allChannels && cleanTargets.isEmpty()) {
        _toastMessage.value = "Scan and select at least one WPS target first."
        return
    }
    val channels = cleanTargets.map { it.channel }.distinct().sorted()
    if (channels.any { it <= 0 }) {
        _toastMessage.value = "Selected targets have invalid channels."
        return
    }
    if (!ensureRadioIdle("WPS Attempt Detector")) return

    val safeInterval = intervalMs.coerceIn(100, 2_000)
    val band = when {
        allChannels -> "auto"
        channels.isNotEmpty() && channels.all { it > 14 } -> "5g"
        else -> "auto"
    }
    _wpsAttemptDetector.update {
        it.copy(
            starting = true,
            running = false,
            allChannels = allChannels,
            selectedTargetCount = cleanTargets.size,
            monitoredChannels = channels,
            hopIntervalMs = safeInterval,
            sessions = emptyList(),
            error = null,
        )
    }
    updateForegroundService()

    wpsAttemptDetectJob?.cancel()
    wpsAttemptDetectJob = scope.launch(Dispatchers.IO) {
        try {
            val args = JSONObject().apply {
                put("mode", if (allChannels || channels.size > 1) "hop" else "fixed")
                put("band", band)
                put("interval_ms", safeInterval)
                if (channels.isNotEmpty()) {
                    put("channels", JSONArray(channels))
                    put("targets", JSONArray(cleanTargets.map { it.bssid }))
                }
            }
            val response = activeSession.sendCommand(
                "WPS_ATTEMPT_DETECT_START",
                args,
                timeoutMs = 12_000,
            )
            if (response?.optBoolean("ok") == true) {
                _wpsAttemptDetector.update {
                    it.copy(
                        starting = false,
                        running = true,
                        error = null,
                    )
                }
                updateForegroundService()
                val targetLabel = if (allChannels) {
                    "all allowed channels"
                } else {
                    "${cleanTargets.size} selected WPS target(s) on " +
                        channels.joinToString(",")
                }
                appendLog("WPS Attempt Detector started: $targetLabel.")
            } else {
                val message = response?.optString("msg")?.takeIf { it.isNotBlank() }
                    ?: response?.optString("error")?.takeIf { it.isNotBlank() }
                    ?: "start timed out"
                _wpsAttemptDetector.update {
                    it.copy(starting = false, running = false, error = message)
                }
                updateForegroundService()
                appendLog("WPS Attempt Detector start failed: $message", level = LogLevel.ERROR)
                _toastMessage.value = "WPS Attempt Detector failed: $message"
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val message = error.message ?: error.javaClass.simpleName
            _wpsAttemptDetector.update {
                it.copy(starting = false, running = false, error = message)
            }
            updateForegroundService()
            appendLog("WPS Attempt Detector start failed: $message", level = LogLevel.ERROR)
            _toastMessage.value = "WPS Attempt Detector failed: $message"
        }
    }
}

internal fun MainViewModel.stopWpsAttemptDetectorImpl() {
    val current = _wpsAttemptDetector.value
    if (!current.starting && !current.running) return
    wpsAttemptDetectJob?.cancel()
    wpsAttemptDetectJob = null
    _wpsAttemptDetector.update {
        it.copy(starting = false, running = false)
    }
    updateForegroundService()
    val activeSession = session
    scope.launch {
        runCatching {
            activeSession?.sendCommand("WPS_ATTEMPT_DETECT_STOP", timeoutMs = 6_000)
        }
    }
    appendLog("WPS Attempt Detector stopped.")
}

internal fun MainViewModel.handleWpsAttemptSessionEventImpl(event: JSONObject) {
    val sessionId = event.optLong("session_id", 0L)
    val bssid = event.optString("bssid").trim().uppercase()
    val client = event.optString("client").trim().uppercase()
    if (sessionId <= 0L || bssid.isBlank() || client.isBlank()) return

    val vendor = ouiDatabaseRepository.lookup(client)?.vendor
    val randomized = client.take(2).toIntOrNull(16)?.let { (it and 0x02) != 0 } ?: false
    val updated = WpsAttemptSession(
        sessionId = sessionId,
        bssid = bssid,
        client = client,
        ssid = event.optString("ssid").ifBlank { "(SSID not observed)" },
        channel = event.optInt("channel", 0),
        rssi = event.optInt("rssi", -127),
        stage = event.optString("stage", "?"),
        messageType = event.optInt("message_type", 0),
        m1Seen = event.optBoolean("m1_seen", false),
        m2Seen = event.optBoolean("m2_seen", false),
        m3Seen = event.optBoolean("m3_seen", false),
        m4Seen = event.optBoolean("m4_seen", false),
        m5Seen = event.optBoolean("m5_seen", false),
        m6Seen = event.optBoolean("m6_seen", false),
        m7Seen = event.optBoolean("m7_seen", false),
        m8Seen = event.optBoolean("m8_seen", false),
        m2dSeen = event.optBoolean("m2d_seen", false),
        nackSeen = event.optBoolean("nack_seen", false),
        frames = event.optInt("frames", 0),
        firstSeenMs = event.optLong("first_seen_ms", 0L),
        lastSeenMs = event.optLong("last_seen_ms", 0L),
        durationMs = event.optLong("duration_ms", 0L),
        vendor = vendor,
        randomizedMac = randomized,
    )

    if (event.optString("state") == "started") {
        NrSuiteNotifications.notifyTaskAlert(
            app,
            "WPS attempt detected",
            "${updated.client} → ${updated.ssid} (${updated.bssid})",
        )
    }
    _wpsAttemptDetector.update { state ->
        val sessions = (
            state.sessions.filterNot { it.sessionId == sessionId } + updated
        )
            .sortedByDescending { it.lastSeenMs }
            .take(200)
        state.copy(
            running = true,
            sessions = scoreWpsAttemptRisk(
                sessions = sessions,
                lockSnapshots = state.lockSnapshots,
                lockTransitionBssids = state.lockTransitionBssids,
            ),
            lastStatusAtMs = System.currentTimeMillis(),
        )
    }
    updateForegroundService()
}

private fun scoreWpsAttemptRisk(
    sessions: List<WpsAttemptSession>,
    lockSnapshots: Map<String, BssidLockSnapshot>,
    lockTransitionBssids: Set<String>,
): List<WpsAttemptSession> {
    val byTarget = sessions.groupBy { it.bssid }
    val now = SystemClock.elapsedRealtime()
    return sessions.map { session ->
        val targetSessions = byTarget[session.bssid].orEmpty()
        val clients = targetSessions.map { it.client }.distinct()
        val snapshot = lockSnapshots[session.bssid]
        val ageMs = snapshot?.let { (now - it.receivedAtElapsedMs).coerceAtLeast(0L) }
        val confidence = when {
            snapshot == null -> WpsLockConfidence.NONE
            snapshot.source == WpsLockObservationSource.LIVE_MONITOR &&
                ageMs != null && ageMs <= 30_000L -> WpsLockConfidence.LIVE
            snapshot.source == WpsLockObservationSource.WPS_LOCK_SCAN &&
                ageMs != null && ageMs <= 60_000L -> WpsLockConfidence.RECENT
            else -> WpsLockConfidence.STALE
        }
        val assessment = assessWpsAttemptRisk(
            session = session,
            targetSessionCount = targetSessions.size,
            distinctClientCount = clients.size,
            lockState = snapshot?.state ?: WpsLockState.UNKNOWN,
            lockConfidence = confidence,
            lockTransitioned = session.bssid in lockTransitionBssids,
        )
        session.copy(
            lockState = snapshot?.state ?: WpsLockState.UNKNOWN,
            lockSource = snapshot?.source,
            lockAgeMs = ageMs,
            lockConfidence = confidence,
            risk = assessment.risk,
            riskReasons = assessment.reasons,
        )
    }
}

internal fun MainViewModel.handleWpsAttemptLockStateEventImpl(event: JSONObject) {
    val bssid = event.optString("bssid").trim().uppercase()
    if (bssid.isBlank()) return
    val wpsParsed = event.optBoolean("wps_parsed", true)
    val lockReported = event.optBoolean("lock_reported", false)
    val locked = event.optBoolean("ap_setup_locked", false)
    val newState = when {
        lockReported && locked -> WpsLockState.LOCKED
        wpsParsed -> WpsLockState.LIKELY_UNLOCKED
        else -> WpsLockState.UNKNOWN
    }
    val now = SystemClock.elapsedRealtime()
    _wpsAttemptDetector.update { state ->
        val previous = state.lockSnapshots[bssid]
        val transitioned = previous?.state == WpsLockState.LIKELY_UNLOCKED &&
            newState == WpsLockState.LOCKED
        val snapshots = state.lockSnapshots + (
            bssid to BssidLockSnapshot(
                state = newState,
                lockReported = lockReported,
                wpsParsed = wpsParsed,
                source = WpsLockObservationSource.LIVE_MONITOR,
                receivedAtElapsedMs = now,
            )
        )
        val transitions = if (transitioned) {
            state.lockTransitionBssids + bssid
        } else {
            state.lockTransitionBssids
        }
        state.copy(
            lockSnapshots = snapshots,
            lockTransitionBssids = transitions,
            sessions = scoreWpsAttemptRisk(
                sessions = state.sessions,
                lockSnapshots = snapshots,
                lockTransitionBssids = transitions,
            ),
        )
    }
}

internal fun MainViewModel.resetWpsAttemptDetectorImpl() {
    wpsAttemptDetectJob?.cancel()
    wpsAttemptDetectJob = null
    wpsAttemptTargetScanJob?.cancel()
    wpsAttemptTargetScanJob = null
    if (wifiScanDestination == WifiScanDestination.WPS_ATTEMPT_TARGETS) {
        wifiScanDestination = null
    }
    val wasActive = _wpsAttemptDetector.value.running || _wpsAttemptDetector.value.starting
    _wpsAttemptDetector.value = com.swp81x.nrsuite.core.wps.WpsAttemptDetectorUiState()
    updateForegroundService()
    if (wasActive) {
        val activeSession = session
        scope.launch {
            runCatching {
                activeSession?.sendCommand("WPS_ATTEMPT_DETECT_STOP", timeoutMs = 4_000)
            }
        }
    }
}
