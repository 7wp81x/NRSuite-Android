package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.defense.ClientObservation
import com.swp81x.nrsuite.core.defense.ClientPresenceMode
import com.swp81x.nrsuite.core.history.HistoryLevel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

// Client / Presence detector: passive management-frame monitoring plus an
// optional active deauth-trigger mode to force clients to re-announce.

internal fun MainViewModel.startClientPresenceImpl(
    mode: ClientPresenceMode,
    fixed: Boolean,
    channel: Int,
    targetBssid: String,
    intervalMs: Int,
) {
    val activeSession = session
    if (activeSession == null) {
        appendLog("Connect to a device before starting client detection.")
        return
    }
    if (_clientPresenceRunning.value) return
    if (!ensureRadioIdle("Client/Presence Detector")) return

    val cleanTarget = targetBssid.trim().uppercase()
    if (mode == ClientPresenceMode.ACTIVE) {
        if (cleanTarget.isBlank() || !MainViewModel.MAC_PATTERN.matches(cleanTarget)) {
            _actionError.value = "Select a valid target AP before starting active client detection."
            return
        }
    }

    val safeChannel = channel.coerceIn(1, 14)
    _clientPresenceMode.value = mode
    _clientPresenceFixed.value = fixed
    _clientPresenceChannel.value = safeChannel
    _clientPresenceTargetBssid.value = cleanTarget
    clientPresenceSavedChannel = safeChannel
    clientPresenceSavedTarget = cleanTarget
    _clientPresenceClients.value = emptyList()
    _clientPresenceFrameCount.value = 0L

    scope.launch {
        if (mode == ClientPresenceMode.ACTIVE) {
            appendLog("Sending reconnect burst to $cleanTarget before client detection...")
            val deauth = activeSession.sendCommand(
                "DEAUTH",
                JSONObject().apply {
                    put("bssid", cleanTarget)
                    put("client", "FF:FF:FF:FF:FF:FF")
                    put("channel", safeChannel)
                    put("count", 10)
                    put("deauth_interval_ms", 100)
                    put("reason", 7)
                },
                timeoutMs = 20_000,
            )
            _clientPresenceLastTriggerAt.value = timeHmNow()
            if (deauth?.optBoolean("ok") != true) {
                appendLog("Reconnect burst failed or timed out; continuing with passive detection.")
            }
        }

        val args = JSONObject().apply {
            put("mode", if (fixed) "fixed" else "hop")
            put("channel", safeChannel)
            put("interval_ms", intervalMs.coerceIn(50, 2_000))
        }
        val response = activeSession.sendCommand("START_CLIENT_DETECT", args, timeoutMs = 10_000)
        if (response?.optBoolean("ok") == true) {
            _clientPresenceRunning.value = true
            updateForegroundService()
            appendLog(
                "Client detection started (" +
                    if (mode == ClientPresenceMode.ACTIVE) "active" else "passive" +
                    " mode)."
            )
            addHistory(
                "client_presence",
                "Client detection started (${if (mode == ClientPresenceMode.ACTIVE) "active" else "passive"})",
                HistoryLevel.SUCCESS,
            )
        } else {
            appendLog("Failed to start client detection: ${response?.optString("msg") ?: "timeout"}")
        }
    }
}

internal fun MainViewModel.stopClientPresenceImpl() {
    if (!_clientPresenceRunning.value) return
    _clientPresenceRunning.value = false
    updateForegroundService()

    val activeSession = session
    scope.launch {
        val response = activeSession?.sendCommand("STOP_CLIENT_DETECT", timeoutMs = 6_000)
        if (response?.optBoolean("ok") == true) {
            appendLog("Client detection stopped.")
            addHistory("client_presence", "Client detection stopped", HistoryLevel.INFO)
        } else {
            appendLog("Client detection stop request sent without confirmation.")
        }
    }
}

internal fun MainViewModel.triggerClientReconnectBurstImpl() {
    if (!_clientPresenceRunning.value ||
        _clientPresenceMode.value != ClientPresenceMode.ACTIVE) return

    val target = _clientPresenceTargetBssid.value
    if (target.isBlank() || !MainViewModel.MAC_PATTERN.matches(target)) return

    val activeSession = session ?: return
    scope.launch {
        appendLog("Forcing reconnect burst to $target...")
        runCatching { activeSession.sendCommand("STOP_CLIENT_DETECT", timeoutMs = 4_000) }
        val deauth = activeSession.sendCommand(
            "DEAUTH",
            JSONObject().apply {
                put("bssid", target)
                put("client", "FF:FF:FF:FF:FF:FF")
                put("channel", _clientPresenceChannel.value)
                put("count", 10)
                put("deauth_interval_ms", 100)
                put("reason", 7)
            },
            timeoutMs = 20_000,
        )
        _clientPresenceLastTriggerAt.value = timeHmNow()
        if (deauth?.optBoolean("ok") != true) {
            appendLog("Reconnect burst failed; restarting passive detection.")
        }

        val args = JSONObject().apply {
            put("mode", if (_clientPresenceFixed.value) "fixed" else "hop")
            put("channel", _clientPresenceChannel.value)
            put("interval_ms", 300)
        }
        val response = activeSession.sendCommand("START_CLIENT_DETECT", args, timeoutMs = 10_000)
        if (response?.optBoolean("ok") != true) {
            _clientPresenceRunning.value = false
            updateForegroundService()
            appendLog("Failed to restart client detection after reconnect burst.")
        }
    }
}

internal fun MainViewModel.recordClientPresenceEvent(event: JSONObject) {
    val mac = event.optString("client").uppercase()
    if (mac.isBlank()) return

    val now = timeHmNow()
    val rssi = event.optInt("rssi", -127)
    val channel = event.optInt("channel", _clientPresenceChannel.value)
    val subtype = event.optString("subtype", "client")
    val bssid = event.optString("bssid").ifBlank { null }
    val ssid = event.optString("ssid").ifBlank { null }
    val vendor = ouiDatabaseRepository.lookup(mac)?.vendor

    val existing = _clientPresenceClients.value.firstOrNull { it.mac == mac }
    val updated = if (existing == null) {
        appendLog("Client detected: $mac ($subtype, $rssi dBm).")
        ClientObservation(
            mac = mac,
            bssid = bssid,
            ssid = ssid,
            subtype = subtype,
            channel = channel,
            rssi = rssi,
            firstSeen = now,
            lastSeen = now,
            sightings = 1,
            vendor = vendor,
        )
    } else {
        existing.copy(
            bssid = bssid ?: existing.bssid,
            ssid = ssid ?: existing.ssid,
            subtype = subtype,
            channel = channel,
            rssi = rssi,
            lastSeen = now,
            sightings = existing.sightings + 1,
            vendor = vendor ?: existing.vendor,
        )
    }

    _clientPresenceClients.update { current ->
        (listOf(updated) + current.filterNot { it.mac == mac }).take(300)
    }
    _clientPresenceFrameCount.update { it + 1 }
}
