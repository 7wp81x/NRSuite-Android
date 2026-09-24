package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.history.HistoryLevel
import kotlinx.coroutines.launch
import org.json.JSONObject

// Deauth detector state and command handling.

internal fun MainViewModel.startDeauthDetectorImpl(
    hop: Boolean,
    channel: Int,
    bssid: String,
    client: String,
    rssiMin: Int,
    intervalMs: Int,
) {
    val activeSession = session
    if (activeSession == null) {
        appendLog("Connect to a device before starting the deauth detector.")
        return
    }
    if (_deauthDetectorRunning.value) return
    if (!ensureRadioIdle("Deauth Detector")) return

    val cleanBssid = bssid.trim().uppercase()
    val cleanClient = client.trim().uppercase()
    val hasBssid = cleanBssid.isNotBlank()
    val hasClient = cleanClient.isNotBlank()

    if (hasBssid && !MainViewModel.MAC_PATTERN.matches(cleanBssid)) {
        appendLog("Invalid BSSID filter: $cleanBssid")
        return
    }
    if (hasClient && !MainViewModel.MAC_PATTERN.matches(cleanClient)) {
        appendLog("Invalid client filter: $cleanClient")
        return
    }

    val safeChannel = channel.coerceIn(1, 13)
    val safeIntervalMs = intervalMs.coerceIn(50, 5_000)
    val safeRssiMin = rssiMin.coerceIn(-127, 0)

    _deauthDetectorAlerts.value = emptyList()
    _deauthDetectorHopping.value = hop
    _deauthDetectorChannel.value = if (hop) 1 else safeChannel
    _deauthDetectorIntervalMs.value = safeIntervalMs
    _deauthDetectorRunning.value = true
    updateForegroundService()

    scope.launch {
        appendLog(
            "Starting deauth detector (" +
                (if (hop) "channel hop" else "channel $safeChannel") +
                ", RSSI >= $safeRssiMin dBm)..."
        )
        val args = JSONObject().apply {
            put("mode", if (hop) "hop" else "fixed")
            put("channel", safeChannel)
            put("interval_ms", safeIntervalMs)
            put("rssi_min", safeRssiMin)
            if (hasBssid) put("bssid", cleanBssid)
            if (hasClient) put("client", cleanClient)
        }

        val response = activeSession.sendCommand("DEAUTH_DETECT_START", args, timeoutMs = 10_000)
        if (response?.optBoolean("ok") == true) {
            _deauthDetectorHopping.value = response.optBoolean("hopping", hop)
            _deauthDetectorChannel.value = response.optInt("channel", if (hop) 1 else safeChannel)
            appendLog("Deauth detector started.")
            addHistory(
                "deauth_detector",
                "Deauth detector started (${if (hop) "channel hop" else "channel $safeChannel"})",
                HistoryLevel.SUCCESS,
            )
        } else {
            _deauthDetectorRunning.value = false
            updateForegroundService()
            appendLog("Failed to start deauth detector: ${response?.optString("msg") ?: "timeout"}")
        }
    }
}

internal fun MainViewModel.stopDeauthDetectorImpl() {
    if (!_deauthDetectorRunning.value) return
    _deauthDetectorRunning.value = false
    updateForegroundService()

    val activeSession = session
    scope.launch {
        val response = activeSession?.sendCommand("DEAUTH_DETECT_STOP", timeoutMs = 6_000)
        if (response?.optBoolean("ok") == true) {
            val detected = response.optInt("detected", _deauthDetectorAlerts.value.size)
            val sent = response.optInt("sent", detected)
            val dropped = response.optInt("dropped", 0)
            appendLog("Deauth detector stopped: detected=$detected, sent=$sent, dropped=$dropped.")
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
