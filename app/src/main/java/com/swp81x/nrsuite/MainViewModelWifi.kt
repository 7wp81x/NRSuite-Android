package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.eapol.EapolHandshake
import com.swp81x.nrsuite.core.eapol.EapolParser
import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.pcap.PcapWriter
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.session.NrSession
import com.swp81x.nrsuite.core.sniff.SniffRequest
import com.swp81x.nrsuite.core.wifi.WIFI_2G_CHANNELS
import com.swp81x.nrsuite.core.wifi.WIFI_5G_CHANNELS
import com.swp81x.nrsuite.core.wifi.WifiBand
import com.swp81x.nrsuite.core.wifi.bandForWifiChannel
import com.swp81x.nrsuite.core.wifi.normalizeWifiChannel
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

internal fun MainViewModel.setWifiBandImpl(band: WifiBand) {
    if (_scanning.value) return
    _wifiBand.value = band
}

internal fun MainViewModel.scanWifiImpl() {
    scanWifiInternalImpl(_wifiBand.value)
}

/**
 * Scans for targets from feature modules. These scans must always use the
 * firmware's AUTO band selection and must not inherit the WiFi Scan screen's
 * user-selected band filter.
 */
internal fun MainViewModel.scanWifiForTargetsImpl() {
    scanWifiInternalImpl(WifiBand.AUTO)
}

private fun MainViewModel.scanWifiInternalImpl(band: WifiBand) {
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
    val wireBand = band.takeIf { it != WifiBand.AUTO }?.wireName
    scope.launch {
        appendLog("Starting WiFi scan (${band.label})...")
        val count = activeSession.scanWifi(band = wireBand)
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

internal fun MainViewModel.setWifiCountryImpl(country: String) {
    val activeSession = session
    if (activeSession == null) {
        appendLog("Connect to a device before changing the regulatory country.")
        return
    }
    val cleanCountry = country.trim().uppercase()
    if (cleanCountry.length != 2) {
        appendLog("Invalid regulatory country code: $cleanCountry")
        return
    }
    scope.launch {
        val response = activeSession.setWifiCountry(cleanCountry)
        if (response?.optBoolean("ok") == true) {
            val five = response.optJSONArray("allowed_5ghz_channels")
                ?.let { array ->
                    (0 until array.length()).map { array.optInt(it) }.filter { it > 0 }
                }
                .orEmpty()
            appendLog(
                "Regulatory country set to $cleanCountry" +
                    (if (five.isNotEmpty()) " (5 GHz: ${five.joinToString(",")})" else "")
            )
            addHistory("wifi", "Regulatory country set to $cleanCountry", HistoryLevel.SUCCESS)
            _toastMessage.value = "Wi-Fi regulatory country set to $cleanCountry"
        } else {
            val message = "Failed to set regulatory country $cleanCountry: " +
                (response?.optString("msg") ?: "timeout")
            appendLog(message, level = LogLevel.ERROR)
            _toastMessage.value = message
        }
    }
}

internal fun MainViewModel.currentAllowedWifiChannels(): List<Int> {
    val connected = connectionState.value as? ConnectionState.Connected ?: return emptyList()
    val allowed2g = connected.allowed2gChannels.ifEmpty { WIFI_2G_CHANNELS }
    val allowed5g = if ("wifi_5ghz" in connected.features) {
        connected.allowed5gChannels.ifEmpty { WIFI_5G_CHANNELS }
    } else {
        emptyList()
    }
    return (allowed2g + allowed5g).distinct()
}

internal fun MainViewModel.ensureWifiChannelAllowed(channel: Int, module: String): Boolean {
    val connected = connectionState.value as? ConnectionState.Connected ?: return true
    val allowed = currentAllowedWifiChannels()
    if (allowed.isEmpty() || channel in allowed) return true
    val country = connected.country ?: "unknown"
    val message = "Channel $channel is not allowed in country $country. " +
        "Change the Wi-Fi regulatory country in Settings."
    appendLog(
        "$module: $message (allowed: ${allowed.joinToString(",")}).",
        level = LogLevel.ERROR,
    )
    _toastMessage.value = message
    return false
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

    val safeChannel = normalizeWifiChannel(channel)
    if (!ensureWifiChannelAllowed(safeChannel, "Deauthentication")) return
    _deauthSent.value = 0
    _deauthTarget.value = cleanBssid
    _deauthChannel.value = safeChannel
    _deauthRunning.value = true
    updateForegroundService()

    scope.launch {
        appendLog(
            "Starting deauth burst: $cleanBssid on channel $safeChannel " +
                "(client=$cleanClient, count=${if (count <= 0) "firmware default" else count})."
        )
        val args = JSONObject().apply {
            put("bssid", cleanBssid)
            put("client", cleanClient)
            put("band", bandForWifiChannel(safeChannel).wireName)
            put("channel", safeChannel)
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

    val safeChannel = normalizeWifiChannel(channel)
    if (!ensureWifiChannelAllowed(safeChannel, "Beacon Broadcast")) return
    scope.launch {
        appendLog("Starting beacon broadcast (${cleanSsids.size} SSID(s), channel $safeChannel)...")
        val args = JSONObject().apply {
            put("ssids", cleanSsids.joinToString("\n"))
            put("band", bandForWifiChannel(safeChannel).wireName)
            put("channel", safeChannel)
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
            _beaconChannel.value = response.optInt("channel", safeChannel)
            appendLog("Beacon broadcast started.")
            addHistory("beacon", "Beacon broadcast started (${cleanSsids.size} SSIDs)", HistoryLevel.SUCCESS)
            startBeaconStatusPollingImpl(activeSession)
        } else {
            val message = "Failed to start beacon broadcast: " +
                (response?.optString("msg") ?: "timeout")
            appendLog(message, level = LogLevel.ERROR)
            _toastMessage.value = message
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
    val safeChannel = normalizeWifiChannel(request.channel)
    if (request.fixedMode && !ensureWifiChannelAllowed(safeChannel, "Packet Sniffer")) return
    val effectiveBand = if (request.fixedMode) {
        bandForWifiChannel(safeChannel).wireName
    } else {
        request.band
    }
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
                        put("channel", safeChannel)
                        put("count", request.deauthCount.coerceAtLeast(0))
                        put("deauth_interval_ms", request.deauthIntervalMs.coerceIn(10, 10_000))
                        put("reason", 7)
                        put("band", effectiveBand)
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
            put("band", effectiveBand)
            put("channel", safeChannel)
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
                "Sniffing started on channel $safeChannel."
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
