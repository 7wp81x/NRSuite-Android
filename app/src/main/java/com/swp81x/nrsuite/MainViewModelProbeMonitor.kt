package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.wifi.WifiBand
import com.swp81x.nrsuite.core.wifi.bandForWifiChannel
import com.swp81x.nrsuite.core.wifi.normalizeWifiChannel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

data class ProbeObservation(
    val mac: String,
    val ssid: String,
    val vendor: String?,
    val channel: Int,
    val rssi: Int,
    val firstSeen: String,
    val lastSeen: String,
    val sightings: Int,
    val randomizedMac: Boolean,
)

internal fun MainViewModel.startProbeMonitorImpl(fixed: Boolean, channel: Int) {
    val activeSession = session
    if (activeSession == null) {
        appendLog("Connect to a device before starting probe monitoring.")
        return
    }
    if (_probeMonitorRunning.value) return
    if (!ensureRadioIdle("Probe Request Monitor")) return

    val safeChannel = normalizeWifiChannel(channel, fallback = 1)
    if (fixed && !ensureWifiChannelAllowed(safeChannel, "Probe Request Monitor")) return
    val wireBand = if (fixed) bandForWifiChannel(safeChannel).wireName else WifiBand.AUTO.wireName

    _probeMonitorFixed.value = fixed
    _probeMonitorChannel.value = safeChannel
    _probeMonitorEntries.value = emptyList()
    _probeMonitorFrameCount.value = 0L
    _probeMonitorRunning.value = true
    updateForegroundService()

    scope.launch {
        val args = JSONObject().apply {
            put("mode", if (fixed) "fixed" else "hop")
            put("band", wireBand)
            put("channel", safeChannel)
            put("interval_ms", if (fixed) 300 else 500)
        }
        val response = activeSession.sendCommand("START_CLIENT_DETECT", args, timeoutMs = 10_000)
        if (response?.optBoolean("ok") == true) {
            appendLog(
                "Probe Request Monitor started (" +
                    (if (fixed) "fixed channel $safeChannel" else "channel hopping") + ")."
            )
            addHistory(
                "probe_monitor",
                "Probe Request Monitor started",
                HistoryLevel.SUCCESS,
            )
        } else {
            _probeMonitorRunning.value = false
            updateForegroundService()
            appendLog(
                "Failed to start Probe Request Monitor: " +
                    (response?.optString("msg") ?: response?.optString("error") ?: "timeout")
            )
        }
    }
}

internal fun MainViewModel.stopProbeMonitorImpl() {
    if (!_probeMonitorRunning.value) return
    _probeMonitorRunning.value = false
    updateForegroundService()
    val activeSession = session
    scope.launch {
        val response = activeSession?.sendCommand("STOP_CLIENT_DETECT", timeoutMs = 6_000)
        if (response?.optBoolean("ok") == true) {
            appendLog("Probe Request Monitor stopped; ${_probeMonitorFrameCount.value} probe frame(s) observed.")
            addHistory(
                "probe_monitor",
                "Probe Request Monitor stopped",
                HistoryLevel.INFO,
            )
        } else {
            appendLog("Probe Request Monitor stop request sent without confirmation.")
        }
    }
}

internal fun MainViewModel.recordProbeMonitorEvent(event: JSONObject) {
    if (!_probeMonitorRunning.value) return
    if (!event.optString("subtype").equals("probe_request", ignoreCase = true)) return

    val mac = event.optString("client").uppercase().ifBlank { return }
    val ssid = event.optString("ssid").trim()
    val displaySsid = ssid.ifBlank { "(broadcast)" }
    val channel = event.optInt("channel", 0)
    val rssi = event.optInt("rssi", -127)
    val vendor = ouiDatabaseRepository.lookup(mac)?.vendor
    val randomized = mac.take(2).toIntOrNull(16)?.let { (it and 0x02) != 0 } ?: false
    val now = timeHmNow()
    val key = "$mac|$displaySsid"

    val existing = _probeMonitorEntries.value.firstOrNull {
        "${it.mac}|${it.ssid}" == key
    }
    val updated = if (existing == null) {
        ProbeObservation(
            mac = mac,
            ssid = displaySsid,
            vendor = vendor,
            channel = channel,
            rssi = rssi,
            firstSeen = now,
            lastSeen = now,
            sightings = 1,
            randomizedMac = randomized,
        )
    } else {
        existing.copy(
            vendor = vendor ?: existing.vendor,
            channel = if (channel > 0) channel else existing.channel,
            rssi = rssi,
            lastSeen = now,
            sightings = existing.sightings + 1,
            randomizedMac = randomized,
        )
    }
    _probeMonitorEntries.update { current ->
        (listOf(updated) + current.filterNot { "${it.mac}|${it.ssid}" == key })
            .sortedWith(compareByDescending<ProbeObservation> { it.rssi }.thenBy { it.ssid })
            .take(500)
    }
    _probeMonitorFrameCount.update { it + 1L }
}


internal fun MainViewModel.resetProbeMonitorImpl() {
    if (_probeMonitorRunning.value) {
        stopProbeMonitorImpl()
    }
    _probeMonitorEntries.value = emptyList()
    _probeMonitorFrameCount.value = 0L
    _probeMonitorFixed.value = false
    _probeMonitorChannel.value = 6
}
