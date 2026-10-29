package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.defense.matchOuiRule
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.wifi.NetworkTarget
import com.swp81x.nrsuite.core.wifi.bandForWifiChannel
import com.swp81x.nrsuite.core.wps.WpsAttemptRisk
import com.swp81x.nrsuite.core.wps.WpsAttemptSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

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
        frames = event.optInt("frames", 0),
        firstSeenMs = event.optLong("first_seen_ms", 0L),
        lastSeenMs = event.optLong("last_seen_ms", 0L),
        durationMs = event.optLong("duration_ms", 0L),
        vendor = vendor,
        randomizedMac = randomized,
    )

    _wpsAttemptDetector.update { state ->
        val sessions = (
            state.sessions.filterNot { it.sessionId == sessionId } + updated
        )
            .sortedByDescending { it.lastSeenMs }
            .take(200)
        state.copy(
            running = true,
            sessions = scoreWpsAttemptRisk(sessions),
            lastStatusAtMs = System.currentTimeMillis(),
        )
    }
}

private fun scoreWpsAttemptRisk(sessions: List<WpsAttemptSession>): List<WpsAttemptSession> {
    val byTarget = sessions.groupBy { it.bssid }
    return sessions.map { session ->
        val targetSessions = byTarget[session.bssid].orEmpty()
        val clients = targetSessions.map { it.client }.distinct()
        val reasons = mutableListOf<String>()
        var risk = WpsAttemptRisk.LOW

        if (targetSessions.size >= 2) {
            reasons += "${targetSessions.size} observed attempts against this AP"
            risk = WpsAttemptRisk.MEDIUM
        }
        if (clients.size >= 2) {
            reasons += "${clients.size} different source MACs"
            risk = WpsAttemptRisk.MEDIUM
        }
        if (targetSessions.size >= 5 || clients.size >= 3) {
            risk = WpsAttemptRisk.HIGH
            reasons += "Sustained WPS attempt activity"
        }
        if (session.stage == "NACK" || session.stage == "M2D") {
            reasons += "WPS ${session.stage} response observed"
            if (risk == WpsAttemptRisk.LOW) risk = WpsAttemptRisk.MEDIUM
        }
        if (session.frames <= 1) {
            reasons += "Incomplete WPS message sequence"
        }
        if (reasons.isEmpty()) {
            reasons += "Single observed WPS attempt"
        }
        session.copy(
            risk = risk,
            riskReasons = reasons.distinct(),
        )
    }
}

internal fun MainViewModel.resetWpsAttemptDetectorImpl() {
    wpsAttemptDetectJob?.cancel()
    wpsAttemptDetectJob = null
    val wasActive = _wpsAttemptDetector.value.running || _wpsAttemptDetector.value.starting
    _wpsAttemptDetector.value = com.swp81x.nrsuite.core.wps.WpsAttemptDetectorUiState()
    if (wasActive) {
        val activeSession = session
        scope.launch {
            runCatching {
                activeSession?.sendCommand("WPS_ATTEMPT_DETECT_STOP", timeoutMs = 4_000)
            }
        }
    }
}
