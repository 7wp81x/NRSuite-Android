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
import com.swp81x.nrsuite.core.wifi.NetworkTarget
import com.swp81x.nrsuite.core.wifi.WifiBand
import com.swp81x.nrsuite.core.wifi.bandForWifiChannel
import com.swp81x.nrsuite.core.wifi.deauthMultiCommandTimeoutMs
import com.swp81x.nrsuite.core.wifi.normalizeWifiChannel
import com.swp81x.nrsuite.core.wifi.shouldKeepPartialScanResults
import com.swp81x.nrsuite.core.wifi.shouldRetryWifiScan
import com.swp81x.nrsuite.core.wifi.shouldUseNativeDeauthMulti
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.update
import org.json.JSONArray
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

internal fun MainViewModel.renameBeaconListImpl(oldName: String, newName: String) {
    val cleanNewName = newName.trim()
    val ssids = _beaconListMap.value[oldName] ?: return
    if (cleanNewName.isBlank() || cleanNewName == oldName) return
    if (cleanNewName in _beaconListMap.value) {
        appendLog("A beacon SSID list named '$cleanNewName' already exists.", level = LogLevel.ERROR)
        return
    }
    val renamed = linkedMapOf<String, List<String>>()
    _beaconListMap.value.forEach { (name, values) ->
        renamed[if (name == oldName) cleanNewName else name] = values
    }
    _beaconListMap.value = renamed
    persistBeaconListsImpl()
    appendLog("Renamed beacon SSID list '$oldName' to '$cleanNewName' (${ssids.size} SSIDs).")
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
    scanWifiInternalImpl(_wifiBand.value, WifiScanDestination.WIFI)
}

/**
 * Scans for targets from feature modules. These scans must always use the
 * firmware's AUTO band selection and must not inherit the WiFi Scan screen's
 * user-selected band filter.
 */
internal fun MainViewModel.scanWifiForTargetsImpl() {
    scanWifiInternalImpl(WifiBand.AUTO, WifiScanDestination.TARGETS)
}

internal fun MainViewModel.scanChannelAnalyzerImpl() {
    scanWifiInternalImpl(WifiBand.AUTO, WifiScanDestination.CHANNEL_ANALYZER)
}

private fun MainViewModel.scanWifiInternalImpl(
    band: WifiBand,
    destination: WifiScanDestination,
) {
    val activeSession = session
    if (activeSession == null) {
        appendLog("Connect to a device before scanning.")
        return
    }
    if (_scanning.value) return
    if (!ensureRadioIdle("WiFi Scan")) return

    val previousNetworks = _networks.value
    val previousChannelAnalyzerNetworks = _channelAnalyzerNetworks.value
    val previousDetectorTargets = _deauthDetectorTargets.value

    when (destination) {
        WifiScanDestination.WIFI,
        WifiScanDestination.TARGETS -> _networks.value = emptyList()
        WifiScanDestination.CHANNEL_ANALYZER -> _channelAnalyzerNetworks.value = emptyList()
        WifiScanDestination.WPS,
        WifiScanDestination.WPS_ATTEMPT_TARGETS -> Unit
    }
    _deauthDetectorTargets.value = emptyList()
    wifiScanDestination = destination
    _scanning.value = true
    val wireBand = band.takeIf { it != WifiBand.AUTO }?.wireName

    fun restorePreviousResults() {
        when (destination) {
            WifiScanDestination.WIFI,
            WifiScanDestination.TARGETS -> _networks.value = previousNetworks
            WifiScanDestination.CHANNEL_ANALYZER ->
                _channelAnalyzerNetworks.value = previousChannelAnalyzerNetworks
            WifiScanDestination.WPS,
            WifiScanDestination.WPS_ATTEMPT_TARGETS -> Unit
        }
        _deauthDetectorTargets.value = previousDetectorTargets
    }

    fun currentResultCount(): Int = when (destination) {
        WifiScanDestination.WIFI,
        WifiScanDestination.TARGETS -> _networks.value.size
        WifiScanDestination.CHANNEL_ANALYZER -> _channelAnalyzerNetworks.value.size
        WifiScanDestination.WPS,
        WifiScanDestination.WPS_ATTEMPT_TARGETS -> 0
    }

    scope.launch {
        try {
            appendLog("Starting WiFi scan (${band.label})...")
            val startedAt = System.currentTimeMillis()
            var count: Int? = null
            var eventsReceived = 0
            var attempts = 0
            val maxAttempts = 2

            while (attempts < maxAttempts) {
                attempts++
                wifiScanEventsReceived.set(0)
                count = activeSession.scanWifi(
                    band = wireBand,
                    timeoutMs = 45_000,
                    settleMs = if (attempts == 1) 1_000 else 1_200,
                )

                // Event delivery and command-response completion are separate
                // coroutine paths. Give already-received scan_ap events a short
                // drain window before deciding that events were lost.
                if (count != null && count > 0) {
                    val drainDeadlineMs = System.currentTimeMillis() + 750
                    while (wifiScanEventsReceived.get() < count &&
                        System.currentTimeMillis() < drainDeadlineMs
                    ) {
                        delay(25)
                    }
                }
                eventsReceived = wifiScanEventsReceived.get()
                val shouldRetry = shouldRetryWifiScan(eventsReceived)
                if (!shouldRetry || attempts >= maxAttempts) break

                appendLog(
                    "WiFi scan attempt $attempts returned " +
                        "${count?.toString() ?: "timeout"} result(s), received " +
                        "$eventsReceived event(s); retrying once after settle.",
                    level = LogLevel.INFO,
                )
                delay(1_200)
            }

            val elapsedMs = System.currentTimeMillis() - startedAt
            val visibleResultCount = currentResultCount()
            val keepPartialResults = shouldKeepPartialScanResults(
                eventsReceived = eventsReceived,
                currentResultCount = visibleResultCount,
            )
            when {
                count == null -> {
                    if (!keepPartialResults) restorePreviousResults()
                    appendLog(
                        if (keepPartialResults) {
                            "WiFi scan timed out after ${elapsedMs} ms, but kept " +
                                "$visibleResultCount partial result(s) already received."
                        } else {
                            "WiFi scan timed out after ${elapsedMs} ms; " +
                                "kept ${previousNetworks.size} previous result(s)."
                        },
                        level = if (keepPartialResults) LogLevel.INFO else LogLevel.ERROR,
                    )
                }
                count < 0 -> {
                    if (!keepPartialResults) restorePreviousResults()
                    appendLog(
                        if (keepPartialResults) {
                            "WiFi scan failed after ${elapsedMs} ms, but kept " +
                                "$visibleResultCount partial result(s) already received."
                        } else {
                            "WiFi scan failed after ${elapsedMs} ms; " +
                                "kept ${previousNetworks.size} previous result(s)."
                        },
                        level = if (keepPartialResults) LogLevel.INFO else LogLevel.ERROR,
                    )
                }
                count == 0 -> {
                    if (!keepPartialResults) restorePreviousResults()
                    appendLog(
                        if (keepPartialResults) {
                            "WiFi scan returned no count after ${elapsedMs} ms, but kept " +
                                "$visibleResultCount partial result(s) already received."
                        } else {
                            "WiFi scan completed with no results after ${elapsedMs} ms; " +
                                "kept ${previousNetworks.size} previous result(s)."
                        },
                    )
                }
                count > eventsReceived -> {
                    if (!keepPartialResults) restorePreviousResults()
                    appendLog(
                        if (keepPartialResults) {
                            "WiFi scan returned $count result(s) but only $eventsReceived " +
                                "scan event(s) arrived; kept $visibleResultCount partial result(s). " +
                                "This points to a transport/framing issue, not RF."
                        } else {
                            "WiFi scan returned $count result(s), but only $eventsReceived " +
                                "scan event(s) arrived; kept the previous list. " +
                                "This points to a transport/framing issue, not RF."
                        },
                        level = LogLevel.ERROR,
                    )
                }
                else -> {
                    appendLog(
                        "WiFi scan complete: $count network(s) in ${elapsedMs} ms " +
                            "(${band.label}); received $eventsReceived scan event(s)."
                    )
                    addHistory(
                        "scan",
                        "WiFi scan complete: $count network(s)",
                        HistoryLevel.SUCCESS,
                    )
                }
            }
        } finally {
            _scanning.value = false
            wifiScanDestination = null
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
            val two = response.optJSONArray("allowed_2ghz_channels")
                ?.let { array ->
                    (0 until array.length()).map { array.optInt(it) }.filter { it > 0 }
                }
                .orEmpty()
            appendLog(
                "Regulatory country set to $cleanCountry " +
                    "(2.4 GHz: ${two.joinToString(",").ifBlank { "none" }}; " +
                    "5 GHz: ${five.joinToString(",").ifBlank { "none" }})"
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

internal fun MainViewModel.startDeauthImpl(
    targets: List<NetworkTarget>,
    client: String,
    count: Int,
    intervalMs: Int,
    loopMode: Boolean = false,
) {
    val activeSession = session
    if (activeSession == null) {
        appendLog("Connect to a device before sending deauth frames.")
        return
    }
    if (_deauthRunning.value) return
    if (!ensureRadioIdle("Deauthentication")) return
    this.stopLocalPortalImpl()

    val cleanClient = client.trim().ifBlank { "FF:FF:FF:FF:FF:FF" }.uppercase()
    if (!MainViewModel.MAC_PATTERN.matches(cleanClient)) {
        appendLog("Invalid client MAC: $cleanClient")
        return
    }
    val cleanTargets = targets
        .mapNotNull { target ->
            val bssid = target.bssid.trim().uppercase()
            if (!MainViewModel.MAC_PATTERN.matches(bssid)) {
                appendLog("Skipping invalid target BSSID: ${target.bssid}")
                null
            } else {
                target.copy(
                    bssid = bssid,
                    channel = normalizeWifiChannel(target.channel),
                )
            }
        }
        .distinctBy { it.bssid }
    if (cleanTargets.isEmpty()) {
        appendLog("Select at least one valid target before starting deauth.", level = LogLevel.ERROR)
        _toastMessage.value = "Select at least one target."
        return
    }
    for (target in cleanTargets) {
        if (!ensureWifiChannelAllowed(target.channel, "Deauthentication")) return
    }

    beaconStatusJob?.cancel()
    beaconStatusJob = null
    _beaconRunning.value = false
    sniffDeauthJob?.cancel()
    sniffDeauthJob = null
    if (_sniffing.value) {
        _sniffing.value = false
        pcapJob?.cancel()
        pcapJob = null
        scope.launch(Dispatchers.IO) {
            runCatching { pcapWriter?.close() }
            pcapWriter = null
        }
    }

    val safeInterval = intervalMs.coerceIn(10, 10_000)
    val safeCount = count.coerceIn(1, 2_000)
    val multiTarget = cleanTargets.size > 1

    _deauthSent.value = 0
    _deauthFailed.value = 0
    _deauthElapsedMs.value = 0L
    _deauthPps.value = 0.0
    _deauthTarget.value = if (cleanTargets.size == 1) {
        cleanTargets.first().bssid
    } else {
        "${cleanTargets.size} targets"
    }
    _deauthChannel.value = if (cleanTargets.size == 1) cleanTargets.first().channel else 0
    deauthNativeMultiActive = false
    _deauthRunning.value = true
    updateForegroundService()

    if (!multiTarget) {
        val target = cleanTargets.first()
        scope.launch {
            val modeDetail = if (loopMode) "until stopped" else "count=$safeCount"
            appendLog(
                "Starting deauth ${if (loopMode) "loop" else "burst"}: ${target.bssid} " +
                    "on channel ${target.channel} (client=$cleanClient, $modeDetail, " +
                    "interval=${safeInterval}ms)."
            )
            val args = JSONObject().apply {
                put("bssid", target.bssid)
                put("client", cleanClient)
                put("band", bandForWifiChannel(target.channel).wireName)
                put("channel", target.channel)
                put("mode", if (loopMode) "loop" else "burst")
                if (loopMode) {
                    put("duration", 0)
                } else {
                    put("count", safeCount)
                }
                put("deauth_interval_ms", safeInterval)
                put("reason", 7)
            }
            val response = activeSession.sendCommand("DEAUTH", args, timeoutMs = 60_000)
            if (response?.optBoolean("ok") == true) {
                val sent = response.optInt("sent_frames", 0)
                val failed = response.optInt("failed_frames", 0)
                _deauthSent.value = sent
                _deauthFailed.value = failed
                if (loopMode && response.optBoolean("active", true)) {
                    appendLog(
                        "Deauth loop started. " +
                            (if (failed > 0) "$failed initial frame(s) failed." else "Initial frames sent: $sent.")
                    )
                    addHistory(
                        "deauth",
                        "Deauth loop started on ${target.bssid} (until stopped)",
                        HistoryLevel.SUCCESS,
                    )
                } else {
                    _deauthRunning.value = false
                    updateForegroundService()
                    appendLog("Deauth burst completed: $sent sent, $failed failed.")
                    addHistory(
                        "deauth",
                        "Deauth burst completed on ${target.bssid}",
                        HistoryLevel.SUCCESS,
                    )
                }
            } else {
                _deauthRunning.value = false
                updateForegroundService()
                val message = "Deauth request failed: ${response?.optString("msg") ?: "timeout"}"
                appendLog(message, level = LogLevel.ERROR)
                _toastMessage.value = message
            }
        }
        return
    }

    val connected = connectionState.value as? ConnectionState.Connected
    if (shouldUseNativeDeauthMulti(connected?.features.orEmpty(), cleanTargets.size)) {
        startNativeDeauthMultiImpl(
            activeSession = activeSession,
            targets = cleanTargets,
            client = cleanClient,
            count = safeCount,
            intervalMs = safeInterval,
            loopMode = loopMode,
        )
        return
    }

    appendLog(
        "DEAUTH_MULTI is unavailable on this firmware; using app-side " +
            "repeated DEAUTH_BURST fallback."
    )

    deauthMultiJob?.cancel()
    deauthMultiJob = scope.launch {
        val startedAt = System.currentTimeMillis()
        var totalSent = 0
        var totalFailed = 0
        appendLog(
            "Starting channel-hopping deauth ${if (loopMode) "loop" else "burst"} across " +
                "${cleanTargets.size} targets " +
                "(burst=$safeCount/target, interval=${safeInterval}ms)."
        )
        try {
            do {
                for (target in cleanTargets) {
                    if (!_deauthRunning.value || !isActive) break
                    val args = JSONObject().apply {
                        put("bssid", target.bssid)
                        put("client", cleanClient)
                        put("band", bandForWifiChannel(target.channel).wireName)
                        put("channel", target.channel)
                        put("count", safeCount)
                        put("deauth_interval_ms", safeInterval)
                        put("reason", 7)
                    }
                    val response = activeSession.sendCommand("DEAUTH_BURST", args, timeoutMs = 30_000)
                    if (response?.optBoolean("ok") == true) {
                        totalSent += response.optInt("sent_frames", 0)
                        totalFailed += response.optInt("failed_frames", 0)
                        _deauthChannel.value = target.channel
                    } else {
                        totalFailed += safeCount
                        appendLog(
                            "Deauth burst failed for ${target.bssid}: " +
                                (response?.optString("msg") ?: "timeout"),
                            level = LogLevel.ERROR,
                        )
                    }
                    val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
                    _deauthSent.value = totalSent
                    _deauthFailed.value = totalFailed
                    _deauthElapsedMs.value = elapsed
                    _deauthPps.value = totalSent * 1000.0 / elapsed
                }
            } while (loopMode && _deauthRunning.value && isActive)

            if (_deauthRunning.value) {
                _deauthRunning.value = false
                updateForegroundService()
                appendLog(
                    "Deauth ${if (loopMode) "multi-target loop" else "multi-target burst"} " +
                        "completed: $totalSent sent, $totalFailed failed."
                )
                addHistory(
                    "deauth",
                    if (loopMode) {
                        "Multi-target deauth loop stopped"
                    } else {
                        "Multi-target deauth burst completed"
                    },
                    HistoryLevel.SUCCESS,
                )
            }
        } finally {
            deauthMultiJob = null
        }
    }
}

internal fun MainViewModel.stopDeauthImpl() {
    if (!_deauthRunning.value) return
    val activeSession = session
    _deauthRunning.value = false
    updateForegroundService()

    if (deauthNativeMultiActive) {
        deauthNativeMultiActive = false
        deauthMultiJob?.cancel()
        deauthMultiJob = null
        scope.launch {
            val response = activeSession?.sendCommand("DEAUTH_STOP", timeoutMs = 8_000)
            if (response?.optBoolean("ok") == true) {
                val sent = response.optInt("sent_frames", _deauthSent.value)
                val failed = response.optInt("failed_frames", _deauthFailed.value)
                _deauthSent.value = sent
                _deauthFailed.value = failed
                appendLog("Firmware-native multi-target deauth stopped: $sent sent, $failed failed.")
                addHistory("deauth", "Firmware-native multi-target deauth stopped", HistoryLevel.INFO)
            } else {
                appendLog("Native multi-target deauth stop request sent without confirmation.")
            }
        }
        return
    }

    val multiJob = deauthMultiJob
    if (multiJob != null) {
        deauthMultiJob = null
        multiJob.cancel()
        appendLog(
            "Channel-hopping deauth stopped: ${_deauthSent.value} sent, " +
                "${_deauthFailed.value} failed."
        )
        addHistory("deauth", "Multi-target deauth stopped", HistoryLevel.INFO)
        return
    }
    scope.launch {
        val response = activeSession?.sendCommand("DEAUTH_STOP", timeoutMs = 6_000)
        if (response?.optBoolean("ok") == true) {
            val sent = response.optInt("sent_frames", _deauthSent.value)
            val failed = response.optInt("failed_frames", 0)
            _deauthSent.value = sent
            _deauthFailed.value = failed
            appendLog("Deauth loop stopped: $sent sent, $failed failed.")
            addHistory("deauth", "Deauth loop stopped", HistoryLevel.INFO)
        } else {
            appendLog("Deauth stop request sent without confirmation.")
        }
    }
}

private fun MainViewModel.startNativeDeauthMultiImpl(
    activeSession: NrSession,
    targets: List<NetworkTarget>,
    client: String,
    count: Int,
    intervalMs: Int,
    loopMode: Boolean,
) {
    deauthMultiJob?.cancel()
    deauthNativeMultiActive = true
    deauthMultiJob = scope.launch {
        val startedAt = System.currentTimeMillis()
        val targetsJson = JSONArray().apply {
            targets.forEach { target ->
                put(
                    JSONObject().apply {
                        put("bssid", target.bssid)
                        put("channel", target.channel)
                    },
                )
            }
        }
        val args = JSONObject().apply {
            put("targets", targetsJson)
            put("client", client)
            put("mode", if (loopMode) "loop" else "burst")
            put("count", count)
            put("interval_ms", intervalMs)
            put("reason", 7)
            if (loopMode) put("duration", 0)
        }
        appendLog(
            "Starting firmware-native multi-target deauth " +
                "${if (loopMode) "loop" else "burst"} across ${targets.size} targets " +
                "(count=$count/target, interval=${intervalMs}ms)."
        )
        try {
            val response = activeSession.sendCommand(
                "DEAUTH_MULTI",
                args,
                timeoutMs = deauthMultiCommandTimeoutMs(
                    targetCount = targets.size,
                    countPerTarget = count,
                    intervalMs = intervalMs,
                    loopMode = loopMode,
                ),
            )
            if (response?.optBoolean("ok") == true) {
                val sent = response.optInt("sent_frames", 0)
                val failed = response.optInt("failed_frames", 0)
                val elapsed = response.optLong(
                    "elapsed_ms",
                    System.currentTimeMillis() - startedAt,
                )
                _deauthSent.value = sent
                _deauthFailed.value = failed
                _deauthElapsedMs.value = elapsed
                _deauthPps.value = response.optDouble(
                    "pps",
                    if (elapsed > 0) sent * 1000.0 / elapsed else 0.0,
                )
                if (loopMode && response.optBoolean("active", true)) {
                    appendLog(
                        "Firmware-native multi-target loop started " +
                            "(${targets.size} targets, $count frame(s)/target per cycle)."
                    )
                    addHistory(
                        "deauth",
                        "Firmware-native multi-target deauth loop started",
                        HistoryLevel.SUCCESS,
                    )
                } else {
                    deauthNativeMultiActive = false
                    _deauthRunning.value = false
                    updateForegroundService()
                    appendLog(
                        "Firmware-native multi-target burst completed: " +
                            "$sent sent, $failed failed."
                    )
                    addHistory(
                        "deauth",
                        "Firmware-native multi-target deauth burst completed",
                        HistoryLevel.SUCCESS,
                    )
                }
            } else {
                deauthNativeMultiActive = false
                _deauthRunning.value = false
                updateForegroundService()
                val message = "DEAUTH_MULTI failed: " +
                    (response?.optString("msg") ?: "timeout")
                appendLog(message, level = LogLevel.ERROR)
                _toastMessage.value = message
            }
        } catch (t: Throwable) {
            deauthNativeMultiActive = false
            _deauthRunning.value = false
            updateForegroundService()
            val message = "DEAUTH_MULTI failed: ${t.message ?: t.javaClass.simpleName}"
            appendLog(message, level = LogLevel.ERROR)
            _toastMessage.value = message
        } finally {
            deauthMultiJob = null
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
    val tooLong = cleanSsids.firstOrNull { it.toByteArray(Charsets.UTF_8).size > 32 }
    if (tooLong != null) {
        val message = "SSID '${tooLong.take(40)}' exceeds the firmware 32-byte limit."
        appendLog(message, level = LogLevel.ERROR)
        _toastMessage.value = message
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
    sniffDeauthJob?.cancel()
    sniffDeauthJob = null
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
    val exportUri = _wifiCaptureDirectory.value ?: _exportDirectory.value
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
    _sniffCaptureBytes.value = 0
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
                    _sniffCaptureBytes.value = writer.bytesWritten
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
            if (request.deauthBeforeCapture) {
                startSniffDeauthRetry(activeSession, request, safeChannel, effectiveBand)
            }
        } else {
            appendLog("Failed to start sniffing: ${response?.optString("msg") ?: "timeout"}")
            stopSniffImpl()
        }
    }
}

private fun MainViewModel.startSniffDeauthRetry(
    activeSession: NrSession,
    request: SniffRequest,
    channel: Int,
    band: String,
) {
    val cleanBssid = request.targetBssid.trim().uppercase()
    if (!MainViewModel.MAC_PATTERN.matches(cleanBssid)) {
        appendLog("Skipping capture deauth retries: select a valid target BSSID.")
        return
    }
    if (!request.fixedMode) {
        appendLog("Skipping capture deauth retries: deauth assist requires fixed-channel capture.")
        return
    }

    val cleanClient = request.client.trim()
        .ifBlank { "FF:FF:FF:FF:FF:FF" }
        .uppercase()
    val burstCount = request.deauthCount.coerceIn(1, 200)
    val burstInterval = request.deauthIntervalMs.coerceIn(10, 10_000)
    val retryInterval = request.deauthRetryIntervalMs.coerceIn(1_000, 60_000)

    sniffDeauthJob?.cancel()
    sniffDeauthJob = scope.launch {
        var retry = 0
        delay(100)
        while (isActive && _sniffing.value) {
            retry++
            val response = runCatching {
                activeSession.sendCommand(
                    "DEAUTH_BURST",
                    JSONObject().apply {
                        put("bssid", cleanBssid)
                        put("client", cleanClient)
                        put("band", band)
                        put("channel", channel)
                        put("count", burstCount)
                        put("deauth_interval_ms", burstInterval)
                        put("reason", 7)
                    },
                    timeoutMs = 12_000,
                )
            }.getOrNull()

            if (!_sniffing.value) break
            if (response?.optBoolean("ok") == true) {
                val sent = response.optInt("sent_frames", 0)
                val failed = response.optInt("failed_frames", 0)
                appendLog(
                    "Capture deauth retry #$retry: $sent sent, $failed failed " +
                        "(burst=$burstCount every ${retryInterval / 1000}s)."
                )
            } else {
                appendLog(
                    "Capture deauth retry #$retry failed: " +
                        (response?.optString("msg") ?: "timeout")
                )
            }

            if (!request.deauthRetryDuringCapture) break
            delay(retryInterval.toLong())
        }
    }
}

internal fun MainViewModel.stopSniffImpl() {
    if (!_sniffing.value) return
    _sniffing.value = false
    updateForegroundService()

    pcapJob?.cancel()
    pcapJob = null
    sniffDeauthJob?.cancel()
    sniffDeauthJob = null

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
            runCatching {
                _sniffCaptureBytes.value = pcapWriter?.bytesWritten ?: _sniffCaptureBytes.value
                pcapWriter?.close()
            }
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
