package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.defense.HiddenApObservation
import com.swp81x.nrsuite.core.defense.HiddenSsidCandidate
import com.swp81x.nrsuite.core.history.HistoryLevel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

// Hidden AP enumerator: passive detection of zero-length SSID beacons/probe
// responses, probe-request SSID candidates, and client association mappings.

internal fun MainViewModel.startHiddenApImpl(fixed: Boolean, channel: Int) {
    val activeSession = session
    if (activeSession == null) {
        appendLog("Connect to a device before starting hidden AP detection.")
        return
    }
    if (_hiddenApRunning.value) return
    if (!ensureRadioIdle("Hidden AP Enumerator")) return

    val safeChannel = channel.coerceIn(1, 14)
    _hiddenApFixed.value = fixed
    _hiddenApChannel.value = safeChannel
    hiddenApSavedChannel = safeChannel
    _hiddenApCurrentHopChannel.value = if (fixed) null else 1
    _hiddenApObservations.value = emptyList()
    _hiddenApCandidates.value = emptyList()
    _hiddenApEventCount.value = 0

    scope.launch {
        val args = JSONObject().apply {
            put("mode", if (fixed) "fixed" else "hop")
            put("channel", safeChannel)
            put("interval_ms", 300)
        }
        val response = activeSession.sendCommand("START_HIDDEN_AP", args, timeoutMs = 10_000)
        if (response?.optBoolean("ok") == true) {
            _hiddenApRunning.value = true
            updateForegroundService()
            appendLog("Hidden AP detection started (${if (fixed) "fixed" else "hopping"} channel).")
            addHistory("hidden_ap", "Hidden AP detection started", HistoryLevel.SUCCESS)
        } else {
            _hiddenApCurrentHopChannel.value = null
            appendLog("Failed to start hidden AP detection: ${response?.optString("msg") ?: "timeout"}")
        }
    }
}

internal fun MainViewModel.stopHiddenApImpl() {
    if (!_hiddenApRunning.value) return
    _hiddenApRunning.value = false
    _hiddenApCurrentHopChannel.value = null
    updateForegroundService()

    val activeSession = session
    scope.launch {
        val response = activeSession?.sendCommand("STOP_HIDDEN_AP", timeoutMs = 6_000)
        if (response?.optBoolean("ok") == true) {
            appendLog("Hidden AP detection stopped.")
            addHistory("hidden_ap", "Hidden AP detection stopped", HistoryLevel.INFO)
        } else {
            appendLog("Hidden AP stop request sent without confirmation.")
        }
    }
}

internal fun MainViewModel.recordHiddenApObservation(event: JSONObject) {
    val bssid = event.optString("bssid").uppercase().takeIf { it.isNotBlank() } ?: return
    val now = timeHmNow()
    val channel = event.optInt("channel", _hiddenApChannel.value)
    val rssi = event.optInt("rssi", -127)
    val vendor = ouiDatabaseRepository.lookup(bssid)?.vendor

    val existing = _hiddenApObservations.value.firstOrNull { it.bssid == bssid }
    val updated = existing?.copy(
        channel = channel,
        rssi = rssi,
        lastSeen = now,
        sightings = existing.sightings + 1,
        vendor = vendor ?: existing.vendor,
    ) ?: HiddenApObservation(
        bssid = bssid,
        channel = channel,
        rssi = rssi,
        resolvedSsid = null,
        resolutionSource = null,
        firstSeen = now,
        lastSeen = now,
        sightings = 1,
        vendor = vendor,
    )

    _hiddenApObservations.update { current ->
        (listOf(updated) + current.filterNot { it.bssid == bssid }).take(300)
    }
    _hiddenApEventCount.update { it + 1 }
}

internal fun MainViewModel.recordHiddenSsidCandidate(event: JSONObject) {
    val ssid = event.optString("ssid").trim().takeIf { it.isNotBlank() } ?: return
    val client = event.optString("client").uppercase().takeIf { it.isNotBlank() }
    val now = timeHmNow()
    val channel = event.optInt("channel", _hiddenApChannel.value)
    val rssi = event.optInt("rssi", -127)

    val existing = _hiddenApCandidates.value.firstOrNull {
        it.ssid.equals(ssid, ignoreCase = true)
    }
    val updated = existing?.copy(
        client = client ?: existing.client,
        channel = channel,
        rssi = rssi,
        lastSeen = now,
        sightings = existing.sightings + 1,
    ) ?: HiddenSsidCandidate(
        ssid = ssid,
        client = client,
        channel = channel,
        rssi = rssi,
        firstSeen = now,
        lastSeen = now,
        sightings = 1,
    )

    _hiddenApCandidates.update { current ->
        (listOf(updated) + current.filterNot { it.ssid.equals(ssid, ignoreCase = true) }).take(200)
    }
    _hiddenApEventCount.update { it + 1 }
}

internal fun MainViewModel.recordHiddenSsidResolved(event: JSONObject) {
    val bssid = event.optString("bssid").uppercase().takeIf { it.isNotBlank() } ?: return
    val ssid = event.optString("ssid").trim().takeIf { it.isNotBlank() } ?: return
    val now = timeHmNow()
    val channel = event.optInt("channel", _hiddenApChannel.value)
    val rssi = event.optInt("rssi", -127)
    val source = when (event.optInt("subtype", 0)) {
        0x02 -> "Resolved by reassociation request"
        else -> "Resolved by association request"
    }

    val existing = _hiddenApObservations.value.firstOrNull { it.bssid == bssid }
    val updated = existing?.copy(
        resolvedSsid = ssid,
        resolutionSource = source,
        channel = channel,
        rssi = rssi,
        lastSeen = now,
        sightings = existing.sightings + 1,
    ) ?: HiddenApObservation(
        bssid = bssid,
        channel = channel,
        rssi = rssi,
        resolvedSsid = ssid,
        resolutionSource = source,
        firstSeen = now,
        lastSeen = now,
        sightings = 1,
        vendor = ouiDatabaseRepository.lookup(bssid)?.vendor,
    )

    _hiddenApObservations.update { current ->
        (listOf(updated) + current.filterNot { it.bssid == bssid }).take(300)
    }
    _hiddenApEventCount.update { it + 1 }
}
