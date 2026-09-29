package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.defense.HiddenApObservation
import com.swp81x.nrsuite.core.defense.HiddenSsidCandidate
import com.swp81x.nrsuite.core.history.HistoryLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

// Hidden AP Revealer: passive detection of zero-length SSID beacons/probe
// responses, probe-request SSID candidates, and client association mappings.

internal fun MainViewModel.startHiddenApImpl(fixed: Boolean, channel: Int) {
    val activeSession = session
    if (activeSession == null) {
        appendLog("Connect to a device before starting hidden AP detection.")
        return
    }
    if (_hiddenApRunning.value) return
    if (!ensureRadioIdle("Hidden AP Revealer")) return

    val safeChannel = channel.coerceIn(1, 14)
    _hiddenApFixed.value = fixed
    _hiddenApChannel.value = safeChannel
    hiddenApSavedChannel = safeChannel
    _hiddenApCurrentHopChannel.value = if (fixed) null else 1
    _hiddenApObservations.value = emptyList()
    _hiddenApCandidates.value = emptyList()
    _hiddenApEventCount.value = 0

    scope.launch {
        // One-time baseline scan. This is best-effort: if it fails or the user
        // moves, unknown BSSIDs still pass through to passive detection.
        hiddenApBaselineBssids.clear()
        appendLog("Running baseline WiFi scan for Hidden AP Revealer...")
        val baselineCount = activeSession.scanWifi(timeoutMs = 30_000)
        if (baselineCount != null && baselineCount >= 0) {
            delay(150)
            val visibleBssids = _networks.value.mapNotNull { network ->
                val bssid = network.optString("bssid").uppercase()
                val ssid = network.optString("ssid")
                if (bssid.isNotBlank() && ssid.isNotBlank()) bssid else null
            }.toSet()
            hiddenApBaselineBssids.addAll(visibleBssids)
            appendLog("Revealer baseline: ${visibleBssids.size} visible AP(s) from $baselineCount scan result(s).")
        } else {
            appendLog("Revealer baseline scan failed; continuing without visible-AP filtering.")
        }

        val args = JSONObject().apply {
            put("mode", if (fixed) "fixed" else "hop")
            put("channel", safeChannel)
            put("interval_ms", 300)
        }
        val response = activeSession.sendCommand("START_HIDDEN_AP", args, timeoutMs = 10_000)
        if (response?.optBoolean("ok") == true) {
            _hiddenApRunning.value = true
            updateForegroundService()
            appendLog("Hidden AP Revealer started (${if (fixed) "fixed" else "hopping"} channel).")
            addHistory("hidden_ap", "Hidden AP Revealer started", HistoryLevel.SUCCESS)
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
            appendLog("Hidden AP Revealer stopped.")
            addHistory("hidden_ap", "Hidden AP Revealer stopped", HistoryLevel.INFO)
        } else {
            appendLog("Hidden AP Revealer stop request sent without confirmation.")
        }
    }
}

internal fun MainViewModel.recordHiddenApObservation(event: JSONObject) {
    val bssid = event.optString("bssid").uppercase().takeIf { it.isNotBlank() } ?: return
    if (bssid in hiddenApBaselineBssids) return
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

    resolvedHiddenSsidByBssid[bssid] = ssid
    applyResolvedSsidToScanTargetsImpl(bssid, ssid)

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


internal fun MainViewModel.forceHiddenApReconnectImpl(observation: HiddenApObservation) {
    if (!_hiddenApRunning.value) {
        appendLog("Start hidden AP detection before forcing a reconnect.")
        return
    }
    if (!_hiddenApDeauthEnabled.value) {
        appendLog("Enable deauth reconnect in Hidden AP settings first.")
        return
    }
    val activeSession = session ?: return

    scope.launch {
        val response = activeSession.sendCommand(
            "HIDDEN_AP_FORCE_RECONNECT",
            JSONObject().apply {
                put("bssid", observation.bssid)
                put("channel", observation.channel)
                put("client", "FF:FF:FF:FF:FF:FF")
                put("count", 5)
                put("interval_ms", 60)
                put("reason", 7)
            },
            timeoutMs = 8_000,
        )
        if (response?.optBoolean("ok") == true) {
            appendLog("Reconnect burst sent to ${observation.bssid}; watching for hidden SSID resolution.")
            addHistory("hidden_ap", "Forced reconnect for ${observation.bssid}", HistoryLevel.INFO)
        } else {
            appendLog("Failed to force reconnect for ${observation.bssid}.")
        }
    }
}


internal fun MainViewModel.applyResolvedSsidToScanTargetsImpl(bssid: String, ssid: String) {
    if (bssid.isBlank() || ssid.isBlank()) return

    _networks.update { current ->
        current.map { network ->
            if (network.optString("bssid").equals(bssid, ignoreCase = true)) {
                network.put("ssid", ssid)
                network.put("hidden_resolved", true)
            }
            network
        }
    }

    _deauthDetectorTargets.update { current ->
        current.map { target ->
            if (target.bssid.equals(bssid, ignoreCase = true)) target.copy(ssid = ssid) else target
        }
    }
    _deauthDetectorSelectedTarget.update { selected ->
        if (selected?.bssid.equals(bssid, ignoreCase = true)) selected?.copy(ssid = ssid) else selected
    }
}
