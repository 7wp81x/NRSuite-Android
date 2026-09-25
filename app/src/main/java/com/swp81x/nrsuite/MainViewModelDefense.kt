package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.defense.AlertConfidence
import com.swp81x.nrsuite.core.defense.DeauthAlert
import com.swp81x.nrsuite.core.defense.DeauthChannelMode
import com.swp81x.nrsuite.core.defense.DeauthFeedEntry
import com.swp81x.nrsuite.core.history.HistoryLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

// Deauth detector state and command handling.

internal fun MainViewModel.startDeauthDetectorImpl() {
    val activeSession = session
    if (activeSession == null) {
        appendLog("Connect to a device before starting the deauth detector.")
        return
    }
    if (_deauthDetectorRunning.value) return

    val mode = _deauthDetectorChannelMode.value
    val target = _deauthDetectorSelectedTarget.value
    if (mode == DeauthChannelMode.TARGETED && target == null) {
        _actionError.value = "Select a target AP before starting targeted deauth detection."
        return
    }
    if (!ensureRadioIdle("Deauth Detector")) return

    val channel = _deauthDetectorChannel.value.coerceIn(1, 14)
    val hopIntervalMs = _deauthDetectorHopIntervalMs.value.coerceIn(100, 2_000)

    _deauthDetectorActiveAlert.value = null
    _deauthDetectorCurrentHopChannel.value = null
    _deauthDetectorFeed.value = emptyList()
    _deauthDetectorFramesPerSecond.value = 0
    _deauthDetectorTotalFrames.value = 0
    _deauthDetectorUniqueSourceCount.value = 0
    deauthDetectorSources.clear()
    deauthDetectorFrameTimestamps.clear()
    deauthDetectorWindowTargets.clear()
    deauthDetectorWindowReasons.clear()
    deauthDetectorWindowSources.clear()
    deauthDetectorSourceLatestRssi.clear()
    deauthDetectorAlertStartedAtMs = null
    deauthDetectorLastEventAtMs = 0L
    deauthAlertClearJob?.cancel()
    deauthAlertClearJob = null
    deauthFpsResetJob?.cancel()
    deauthFpsResetJob = null

    _deauthDetectorRunning.value = true
    updateForegroundService()

    scope.launch {
        val args = JSONObject().apply {
            put("mode", if (mode == DeauthChannelMode.HOPPING) "hop" else "fixed")
            put("rssi_min", -127)
            if (mode == DeauthChannelMode.HOPPING) {
                put("interval_ms", hopIntervalMs)
            } else {
                put("channel", channel)
                target?.bssid?.let { put("bssid", it) }
                put("interval_ms", 300)
            }
        }

        val startDescription = if (mode == DeauthChannelMode.HOPPING) {
            "all channels every $hopIntervalMs ms"
        } else {
            "channel $channel (target ${target?.ssid ?: "selected AP"})"
        }
        appendLog("Starting deauth detector on $startDescription...")

        val response = activeSession.sendCommand("DEAUTH_DETECT_START", args, timeoutMs = 10_000)
        if (response?.optBoolean("ok") == true) {
            _deauthDetectorCurrentHopChannel.value =
                if (mode == DeauthChannelMode.HOPPING) response.optInt("channel", 1) else null
            appendLog("Deauth detector started.")
            addHistory(
                "deauth_detector",
                "Deauth detector started ($startDescription)",
                HistoryLevel.SUCCESS,
            )
        } else {
            _deauthDetectorRunning.value = false
            _deauthDetectorFramesPerSecond.value = 0
            updateForegroundService()
            appendLog("Failed to start deauth detector: ${response?.optString("msg") ?: "timeout"}")
        }
    }
}

internal fun MainViewModel.stopDeauthDetectorImpl() {
    if (!_deauthDetectorRunning.value) return
    _deauthDetectorRunning.value = false
    _deauthDetectorFramesPerSecond.value = 0
    _deauthDetectorCurrentHopChannel.value = null
    deauthAlertClearJob?.cancel()
    deauthAlertClearJob = null
    deauthFpsResetJob?.cancel()
    deauthFpsResetJob = null
    _deauthDetectorActiveAlert.value = null
    deauthDetectorWindowTargets.clear()
    deauthDetectorWindowReasons.clear()
    deauthDetectorWindowSources.clear()
    deauthDetectorSourceLatestRssi.clear()
    deauthDetectorAlertStartedAtMs = null
    deauthDetectorLastEventAtMs = 0L
    updateForegroundService()

    val activeSession = session
    scope.launch {
        val response = activeSession?.sendCommand("DEAUTH_DETECT_STOP", timeoutMs = 6_000)
        if (response?.optBoolean("ok") == true) {
            val detected = response.optInt("detected", _deauthDetectorTotalFrames.value)
            appendLog("Deauth detector stopped: detected=$detected frame(s).")
            addHistory(
                "deauth_detector",
                "Deauth detector stopped; detected=$detected",
                HistoryLevel.SUCCESS,
            )
        } else {
            appendLog("Deauth detector stop request sent, but the device did not confirm.")
        }
    }
}

private const val DEAUTH_DETECTOR_WINDOW_SIZE = 20
private const val DEAUTH_DETECTOR_GAP_RESET_MS = 5_000L
private const val DEAUTH_DETECTOR_ALERT_CLEAR_MS = 8_000L

private fun <T> dominantConcentrationPct(values: List<T>): Int {
    if (values.isEmpty()) return 0
    val dominantCount = values.groupingBy { it }.eachCount().values.maxOrNull() ?: 0
    return dominantCount * 100 / values.size
}

private fun confidenceFor(
    sustainedSeconds: Int,
    targetConcentrationPct: Int,
    reasonCodeConsistencyPct: Int,
): AlertConfidence = when {
    sustainedSeconds >= 5 &&
        targetConcentrationPct >= 70 &&
        reasonCodeConsistencyPct >= 70 -> AlertConfidence.HIGH

    sustainedSeconds >= 3 &&
        (targetConcentrationPct >= 50 || reasonCodeConsistencyPct >= 60) -> AlertConfidence.MEDIUM

    else -> AlertConfidence.LOW
}

/**
 * Relay one already-parsed deauth_detected event into UI state.
 *
 * This is still just state aggregation/relaying from the firmware event. The
 * confidence and proximity tiers are either simple windowed counters here or
 * pure presentation mappings on the UI side.
 */
internal fun MainViewModel.recordDeauthDetectorFrame(event: JSONObject) {
    val bssid = event.optString("bssid", "?").uppercase().ifBlank { "?" }
    val sourceMac = event.optString("source", bssid).uppercase().ifBlank { bssid }
    val rawClient = event.optString(
        "client",
        event.optString("destination", ""),
    ).uppercase()
    val targetMac = rawClient.takeIf {
        it.isNotBlank() &&
            it != "FF:FF:FF:FF:FF:FF" &&
            it != "00:00:00:00:00:00"
    }
    val channel = event.optInt("channel", _deauthDetectorChannel.value)
    if (_deauthDetectorChannelMode.value == DeauthChannelMode.HOPPING) {
        _deauthDetectorCurrentHopChannel.value = channel
    }
    val rssi = event.optInt("rssi", -127)
    val reasonCode = event.optInt("reason", 0)
    val resolvedSsid = _deauthDetectorTargets.value
        .firstOrNull { it.bssid.equals(bssid, ignoreCase = true) }
        ?.ssid
        ?: _deauthDetectorSelectedTarget.value
            ?.takeIf { it.bssid.equals(bssid, ignoreCase = true) }
            ?.ssid
        ?: bssid

    val nowMs = System.currentTimeMillis()
    if (deauthDetectorLastEventAtMs > 0 &&
        nowMs - deauthDetectorLastEventAtMs > DEAUTH_DETECTOR_GAP_RESET_MS
    ) {
        deauthDetectorWindowTargets.clear()
        deauthDetectorWindowReasons.clear()
        deauthDetectorWindowSources.clear()
        deauthDetectorSourceLatestRssi.clear()
        deauthDetectorAlertStartedAtMs = null
    }
    deauthDetectorLastEventAtMs = nowMs
    if (deauthDetectorAlertStartedAtMs == null) {
        deauthDetectorAlertStartedAtMs = nowMs
    }

    _deauthDetectorFeed.update { current ->
        (current + DeauthFeedEntry(
            timestamp = timeHmNow(),
            sourceMac = sourceMac,
            targetMac = targetMac,
            reasonCode = reasonCode,
            rssi = rssi,
        )).takeLast(500)
    }
    _deauthDetectorTotalFrames.update { it + 1 }
    deauthDetectorSources += sourceMac
    _deauthDetectorUniqueSourceCount.value = deauthDetectorSources.size

    deauthDetectorFrameTimestamps.addLast(nowMs)
    while (deauthDetectorFrameTimestamps.isNotEmpty() &&
        nowMs - deauthDetectorFrameTimestamps.first() > 1_000
    ) {
        deauthDetectorFrameTimestamps.removeFirst()
    }
    _deauthDetectorFramesPerSecond.value = deauthDetectorFrameTimestamps.size
    deauthFpsResetJob?.cancel()
    deauthFpsResetJob = scope.launch {
        delay(1_000)
        _deauthDetectorFramesPerSecond.value = 0
    }

    deauthDetectorWindowTargets.addLast(targetMac ?: "broadcast")
    while (deauthDetectorWindowTargets.size > DEAUTH_DETECTOR_WINDOW_SIZE) {
        deauthDetectorWindowTargets.removeFirst()
    }
    deauthDetectorWindowReasons.addLast(reasonCode)
    while (deauthDetectorWindowReasons.size > DEAUTH_DETECTOR_WINDOW_SIZE) {
        deauthDetectorWindowReasons.removeFirst()
    }
    deauthDetectorWindowSources.addLast(sourceMac)
    while (deauthDetectorWindowSources.size > DEAUTH_DETECTOR_WINDOW_SIZE) {
        deauthDetectorWindowSources.removeFirst()
    }
    deauthDetectorSourceLatestRssi[sourceMac] = rssi

    val targetConcentrationPct =
        dominantConcentrationPct(deauthDetectorWindowTargets.toList())
    val reasonCodeConsistencyPct =
        dominantConcentrationPct(deauthDetectorWindowReasons.toList())
    val dominantSource = deauthDetectorWindowSources
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
        ?: sourceMac
    val dominantSourceRssi = deauthDetectorSourceLatestRssi[dominantSource] ?: rssi
    val sustainedSeconds = (
        (nowMs - (deauthDetectorAlertStartedAtMs ?: nowMs)) / 1_000
    ).toInt().coerceAtLeast(0)
    val confidence = confidenceFor(
        sustainedSeconds = sustainedSeconds,
        targetConcentrationPct = targetConcentrationPct,
        reasonCodeConsistencyPct = reasonCodeConsistencyPct,
    )

    _deauthDetectorActiveAlert.value = DeauthAlert(
        sourceMac = sourceMac,
        ssid = resolvedSsid,
        channel = channel,
        possiblySpoofed = event.optBoolean("possibly_spoofed", false),
        confidence = confidence,
        sustainedSeconds = sustainedSeconds,
        targetConcentrationPct = targetConcentrationPct,
        reasonCodeConsistencyPct = reasonCodeConsistencyPct,
        dominantSourceRssi = dominantSourceRssi,
    )
    deauthAlertClearJob?.cancel()
    deauthAlertClearJob = scope.launch {
        delay(DEAUTH_DETECTOR_ALERT_CLEAR_MS)
        _deauthDetectorActiveAlert.value = null
    }

    appendLog(
        "Deauth detected: $sourceMac -> ${targetMac ?: "broadcast"} " +
            "on ch $channel ($rssi dBm, reason $reasonCode, ${confidence.name.lowercase()} confidence)"
    )
}
