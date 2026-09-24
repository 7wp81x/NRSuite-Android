package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.eapol.EapolHandshake
import com.swp81x.nrsuite.core.eapol.EapolParser
import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.pcap.PcapWriter
import com.swp81x.nrsuite.core.session.NrSession
import com.swp81x.nrsuite.core.sniff.SniffRequest
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.update
import org.json.JSONObject

private const val PREF_BEACON_LISTS = "beacon_lists"

// WiFi scan, deauth, beacon, sniff capture, and related helpers.

internal fun MainViewModel.saveBeaconListImpl(name: String, ssids: List<String>) {
    val cleanName = name.trim()
    if (cleanName.isBlank()) return
    val cleanSsids = ssids.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    _beaconListMap.update { it + (cleanName to cleanSsids) }
    persistBeaconListsImpl()
    appendLog("Saved beacon SSID list '$cleanName' (${cleanSsids.size} SSIDs).")
}

internal fun MainViewModel.deleteBeaconListImpl(name: String) {
    _beaconListMap.update { it - name }
    persistBeaconListsImpl()
    appendLog("Deleted beacon SSID list '$name'.")
}

internal fun MainViewModel.loadBeaconListsImpl() {
    val raw = preferences.getString(PREF_BEACON_LISTS, null) ?: return
    runCatching {
        val json = JSONObject(raw)
        val map = mutableMapOf<String, List<String>>()
        json.keys().forEach { key ->
            val array = json.optJSONArray(key) ?: return@forEach
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val value = array.optString(i)
                if (value.isNotBlank()) list += value
            }
            map[key] = list
        }
        _beaconListMap.value = map
    }
}

internal fun MainViewModel.persistBeaconListsImpl() {
    val json = JSONObject()
    _beaconListMap.value.forEach { (name, ssids) ->
        val array = org.json.JSONArray()
        ssids.forEach { array.put(it) }
        json.put(name, array)
    }
    preferences.edit().putString(PREF_BEACON_LISTS, json.toString()).apply()
}

internal fun MainViewModel.scanWifiImpl() {
    val activeSession = session
    if (activeSession == null) {
        appendLog("Connect to a device before scanning.")
        return
    }
    if (_scanning.value) return
    if (!ensureRadioIdle("WiFi Scan")) return

    _networks.value = emptyList()
    _deauthDetectorTargets.value = emptyList()
    _scanning.value = true
    scope.launch {
        appendLog("Starting WiFi scan...")
        val count = activeSession.scanWifi()
        _scanning.value = false
        when {
            count == null -> appendLog("WiFi scan timed out.")
            count < 0 -> appendLog("WiFi scan failed.")
            else -> {
                appendLog("WiFi scan complete: $count network(s).")
                addHistory("scan", "WiFi scan complete: $count network(s)", HistoryLevel.SUCCESS)
            }
        }
    }
}

internal fun MainViewModel.startDeauthImpl(bssid: String, channel: Int, client: String, count: Int, duration: Int, intervalMs: Int) {
    val activeSession = session
    if (activeSession == null) {
        appendLog("Connect to a device before sending deauth frames.")
        return
    }
    if (_deauthRunning.value) return
    if (!ensureRadioIdle("Deauthentication")) return
    this.stopLocalPortalImpl()

    val cleanBssid = bssid.trim().uppercase()
    val cleanClient = client.trim().ifBlank { "FF:FF:FF:FF:FF:FF" }.uppercase()
    if (!MainViewModel.MAC_PATTERN.matches(cleanBssid)) {
        appendLog("Invalid target BSSID: $cleanBssid")
        return
    }
    if (!MainViewModel.MAC_PATTERN.matches(cleanClient)) {
        appendLog("Invalid client MAC: $cleanClient")
        return
    }

    // The firmware's DEAUTH handler calls radioIdle(), so stop local
    // companion tasks before issuing the burst.
    beaconStatusJob?.cancel()
    beaconStatusJob = null
    _beaconRunning.value = false
    if (_sniffing.value) {
        _sniffing.value = false
        pcapJob?.cancel()
        pcapJob = null
        scope.launch(Dispatchers.IO) {
            runCatching { pcapWriter?.close() }
            pcapWriter = null
        }
    }

    _deauthSent.value = 0
    _deauthTarget.value = cleanBssid
    _deauthChannel.value = channel.coerceIn(1, 13)
    _deauthRunning.value = true
    updateForegroundService()

    scope.launch {
        appendLog(
            "Starting deauth burst: $cleanBssid on channel $channel " +
                "(client=$cleanClient, count=${if (count <= 0) "firmware default" else count})."
        )
        val args = JSONObject().apply {
            put("bssid", cleanBssid)
            put("client", cleanClient)
            put("channel", channel.coerceIn(1, 13))
            put("count", count.coerceAtLeast(0))
            put("duration", duration.coerceAtLeast(0))
            put("deauth_interval_ms", intervalMs.coerceIn(10, 10_000))
            put("reason", 7)
        }
        val response = activeSession.sendCommand("DEAUTH", args, timeoutMs = 60_000)
        _deauthRunning.value = false
        updateForegroundService()
        if (response?.optBoolean("ok") == true) {
            appendLog("Deauth burst completed.")
            addHistory("deauth", "Deauth burst completed on $cleanBssid", HistoryLevel.SUCCESS)
        } else {
            appendLog("Deauth request failed: ${response?.optString("msg") ?: "timeout"}")
        }
    }
}

internal fun MainViewModel.startBeaconImpl(ssids: List<String>, channel: Int, intervalMs: Int, hidden: Boolean, randomBssid: Boolean) {
    val activeSession = session
    if (activeSession == null) {
        appendLog("Connect to a device before starting beacon broadcast.")
        return
    }
    if (_beaconRunning.value) return
    if (!ensureRadioIdle("Beacon Broadcast")) return
    this.stopLocalPortalImpl()

    val cleanSsids = ssids.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (cleanSsids.isEmpty()) {
        appendLog("At least one SSID is required.")
        return
    }
    if (cleanSsids.size > 32) {
        appendLog("Firmware supports at most 32 SSIDs.")
        return
    }

    scope.launch {
        appendLog("Starting beacon broadcast (${cleanSsids.size} SSID(s), channel $channel)...")
        val args = JSONObject().apply {
            put("ssids", cleanSsids.joinToString("\n"))
            put("channel", channel.coerceIn(1, 13))
            put("interval_ms", intervalMs.coerceIn(10, 2000))
            put("hidden", hidden)
            put("random_bssid", randomBssid)
        }
        val response = activeSession.sendCommand("START_BEACON", args, timeoutMs = 10_000)
        if (response?.optBoolean("ok") == true) {
            _beaconRunning.value = true
            updateForegroundService()
            _beaconSent.value = 0
            _beaconSsidCount.value = response.optInt("ssids", cleanSsids.size)
            _beaconChannel.value = response.optInt("channel", channel)
            appendLog("Beacon broadcast started.")
            addHistory("beacon", "Beacon broadcast started (${cleanSsids.size} SSIDs)", HistoryLevel.SUCCESS)
            startBeaconStatusPollingImpl(activeSession)
        } else {
            appendLog("Failed to start beacon broadcast: ${response?.optString("msg") ?: "timeout"}")
        }
    }
}

internal fun MainViewModel.stopBeaconImpl() {
    if (!_beaconRunning.value) return
    beaconStatusJob?.cancel()
    beaconStatusJob = null
    _beaconRunning.value = false
    updateForegroundService()

    val activeSession = session
    scope.launch {
        val response = activeSession?.sendCommand("STOP_BEACON", timeoutMs = 6_000)
        if (response?.optBoolean("ok") == true) {
            _beaconSent.value = response.optInt("sent", _beaconSent.value)
            _beaconSsidCount.value = response.optInt("ssids", _beaconSsidCount.value)
            appendLog("Beacon stopped. Frames sent: ${_beaconSent.value}.")
            addHistory("beacon", "Beacon stopped; ${_beaconSent.value} frames sent", HistoryLevel.SUCCESS)
        } else {
            appendLog("Beacon stop request sent, but the device did not confirm.")
        }
    }
}

internal fun MainViewModel.startBeaconStatusPollingImpl(activeSession: NrSession) {
    beaconStatusJob?.cancel()
    beaconStatusJob = scope.launch {
        while (isActive && _beaconRunning.value) {
            delay(2_000)
            val response = activeSession.sendCommand("BEACON_STATUS", timeoutMs = 4_000) ?: continue
            if (response.optBoolean("ok")) {
                _beaconRunning.value = response.optBoolean("active", _beaconRunning.value)
                _beaconSent.value = response.optInt("sent", _beaconSent.value)
                _beaconSsidCount.value = response.optInt("ssids", _beaconSsidCount.value)
                _beaconChannel.value = response.optInt("channel", _beaconChannel.value)
            }
        }
    }
}

internal fun MainViewModel.startSniffImpl(request: SniffRequest) {
    val activeSession = session
    if (activeSession == null) {
        appendLog("Connect to a device before sniffing.")
        return
    }
    if (_sniffing.value) return
    if (!ensureRadioIdle("Packet Sniffer")) return
    beaconStatusJob?.cancel()
    beaconStatusJob = null
    _beaconRunning.value = false
    this.stopLocalPortalImpl()

    val captureName = "capture_${System.currentTimeMillis()}.pcap"
    val exportUri = _exportDirectory.value
    if (exportUri == null) {
        _requiresRootDirectory.value = true
        appendLog("Choose an NRSuite root directory before starting a capture.")
        return
    }
    val writerResult = runCatching {
        val pcapDir = ensureChildDirectory(exportUri, "Pcap")
        val pcapDirUri = pcapDir?.uri ?: exportUri
        val documentUri = createPcapDocumentInDirectory(pcapDirUri, captureName)
        val outputStream = app.contentResolver
            .openOutputStream(documentUri, "wt")
            ?: throw IOException("Could not open export file")
        val displayName = displayNameForTreeUri(exportUri)
        PcapWriter(outputStream, closeOutput = true) to "$displayName/Pcap/$captureName"
    }
    val (writer, captureDisplayPath) = writerResult.getOrElse { error ->
        appendLog("Could not create capture output: ${error.message}")
        return
    }

    pcapWriter = writer
    _capturePath.value = captureDisplayPath
    _sniffPacketCount.value = 0
    _sniffHandshake.value = EapolHandshake()
    _sniffing.value = true
    updateForegroundService()

    appendLog("Capture file: $captureDisplayPath")

    val eapolState = EapolHandshake()
    var eapolStopRequested = false
    pcapJob = scope.launch(Dispatchers.IO) {
        activeSession.pcap.collect { frame ->
            val matchesTarget = !request.targetNetworkOnly ||
                frameMatchesBssidImpl(frame, request.targetBssid)
            if (matchesTarget) {
                try {
                    writer.writePacket(frame)
                    _sniffPacketCount.update { it + 1 }
                } catch (t: Throwable) {
                    appendLog("PCAP write error: ${t.message}")
                }
            }

            if (request.eapolOnly) {
                EapolParser.parse(frame, eapolState)
                _sniffHandshake.value = eapolState.copy()
                val targetMatches = MainViewModel.MAC_PATTERN.matches(request.targetBssid.trim().uppercase())
                if (!eapolStopRequested && targetMatches && eapolState.isComplete) {
                    eapolStopRequested = true
                    appendLog("[+] Valid 4-Way Handshake captured!")
                    stopSniffImpl()
                }
            }
        }
    }

    scope.launch {
        if (request.deauthBeforeCapture) {
            val cleanBssid = request.targetBssid.trim().uppercase()
            if (MainViewModel.MAC_PATTERN.matches(cleanBssid)) {
                val cleanClient = request.client.trim()
                    .ifBlank { "FF:FF:FF:FF:FF:FF" }
                    .uppercase()
                appendLog("Sending deauth burst before capture...")
                val deauthResponse = activeSession.sendCommand(
                    "DEAUTH",
                    JSONObject().apply {
                        put("bssid", cleanBssid)
                        put("client", cleanClient)
                        put("channel", request.channel.coerceIn(1, 13))
                        put("count", request.deauthCount.coerceAtLeast(0))
                        put("deauth_interval_ms", request.deauthIntervalMs.coerceIn(10, 10_000))
                        put("reason", 7)
                    },
                    timeoutMs = 20_000,
                )
                if (deauthResponse?.optBoolean("ok") != true) {
                    appendLog("Deauth burst failed or timed out; continuing with capture.")
                }
            } else {
                appendLog("Skipping deauth trigger: invalid target BSSID.")
            }
        }

        val cleanFilterBssid = request.targetBssid.trim().uppercase()
        val args = JSONObject().apply {
            put("mode", if (request.fixedMode) "fixed" else "hop")
            put("channel", request.channel.coerceIn(1, 13))
            put("interval_ms", request.intervalMs.coerceIn(50, 2_000))
            if (request.eapolOnly) {
                put("eapol_only", true)
            }
            if (MainViewModel.MAC_PATTERN.matches(cleanFilterBssid)) {
                put("bssid", cleanFilterBssid)
            }
        }
        val response = activeSession.sendCommand("START_SNIFF", args, timeoutMs = 12_000)
        if (response?.optBoolean("ok") == true) {
            val message = if (request.fixedMode) {
                "Sniffing started on channel ${request.channel.coerceIn(1, 13)}."
            } else {
                "Channel-hopping sniffing started."
            }
            appendLog(message)
            addHistory("sniff", message, HistoryLevel.SUCCESS)
        } else {
            appendLog("Failed to start sniffing: ${response?.optString("msg") ?: "timeout"}")
            stopSniffImpl()
        }
    }
}

internal fun MainViewModel.stopSniffImpl() {
    if (!_sniffing.value) return
    _sniffing.value = false
    updateForegroundService()

    pcapJob?.cancel()
    pcapJob = null

    val activeSession = session
    scope.launch {
        val response = activeSession?.sendCommand("STOP_SNIFF", timeoutMs = 6_000)
        if (response != null) {
            appendLog(
                "Capture stopped: captured=${response.optInt("captured")}, " +
                    "sent=${response.optInt("sent")}, dropped=${response.optInt("dropped")}"
            )
        }
        withContext(Dispatchers.IO) {
            runCatching { pcapWriter?.close() }
        }
        pcapWriter = null
        _capturePath.value?.let {
            appendLog("Capture saved: $it")
            addHistory("sniff", "Capture saved: $it", HistoryLevel.SUCCESS)
        }
    }
}

internal fun MainViewModel.frameMatchesBssidImpl(frame: ByteArray, bssid: String): Boolean {
    val parts = bssid.trim().uppercase().split(":")
    if (parts.size != 6) return false
    val target = ByteArray(6) { index ->
        parts[index].toIntOrNull(16)?.toByte() ?: return false
    }
    if (frame.size < 8) return false
    val radiotapLength = (frame[2].toInt() and 0xFF) or
        ((frame[3].toInt() and 0xFF) shl 8)
    val macBase = radiotapLength
    if (frame.size < macBase + 22) return false
    for (offset in intArrayOf(4, 10, 16)) {
        var matches = true
        for (i in 0 until 6) {
            if (frame[macBase + offset + i] != target[i]) {
                matches = false
                break
            }
        }
        if (matches) return true
    }
    return false
}
