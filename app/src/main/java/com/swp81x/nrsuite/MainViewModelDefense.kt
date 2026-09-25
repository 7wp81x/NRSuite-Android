package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.defense.DeauthChannelMode
import com.swp81x.nrsuite.core.history.HistoryLevel
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
