package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.rfid.RfidTagObservation
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private const val PREF_RFID_HISTORY = "rfid_scan_history"
private const val RFID_HISTORY_LIMIT = 100
private const val RFID_DUPLICATE_WINDOW_MS = 15_000L

internal fun MainViewModel.loadRfidHistoryImpl() {
    val raw = preferences.getString(PREF_RFID_HISTORY, "").orEmpty()
    if (raw.isBlank()) return
    val entries = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val uid = item.optString("uid").trim()
                if (uid.isBlank()) continue
                add(
                    RfidTagObservation(
                        uid = uid,
                        atqa = item.optString("atqa", ""),
                        sak = item.optString("sak", ""),
                        type = item.optString("type", "Unknown ISO14443A"),
                        observedAtMs = item.optLong("observed_at_ms", 0L),
                        firmwareTimestampMs = if (
                            item.has("firmware_timestamp_ms") &&
                            !item.isNull("firmware_timestamp_ms")
                        ) {
                            item.optLong("firmware_timestamp_ms")
                        } else {
                            null
                        },
                        readOnly = item.optBoolean("read_only", true),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())
    if (entries.isNotEmpty()) {
        _rfid.update { it.copy(tags = entries.take(RFID_HISTORY_LIMIT)) }
    }
}

internal fun MainViewModel.refreshRfidStatusImpl() {
    val activeSession = session ?: run {
        _rfid.update {
            it.copy(
                connected = false,
                scanning = false,
                statusMessage = "Connect an NRSuite ESP32 to use PN532 RFID.",
            )
        }
        return
    }
    scope.launch {
        try {
            val response = activeSession.sendCommand("RFID_STATUS", timeoutMs = 12_000)
            if (response == null) {
                _rfid.update {
                    it.copy(
                        connected = false,
                        scanning = false,
                        errorMessage = "RFID status timed out.",
                    )
                }
                return@launch
            }
            applyRfidStatus(response)
        } catch (t: Throwable) {
            _rfid.update {
                it.copy(
                    connected = false,
                    scanning = false,
                    errorMessage = t.message ?: "Could not read RFID status.",
                )
            }
            appendLog(
                "RFID status failed: ${t.message ?: t.javaClass.simpleName}",
                level = LogLevel.ERROR,
                tag = "rfid",
            )
        }
    }
}

internal fun MainViewModel.saveRfidConfigImpl(sdaGpio: Int, sclGpio: Int) {
    val activeSession = session ?: run {
        _rfid.update { it.copy(errorMessage = "Connect an NRSuite ESP32 first.") }
        return
    }
    if (_rfid.value.configSaving) return
    _rfid.update { it.copy(configSaving = true, errorMessage = null, statusMessage = null) }
    scope.launch {
        try {
            val args = JSONObject()
                .put("interface", "i2c")
                .put("sda_gpio", sdaGpio)
                .put("scl_gpio", sclGpio)
            val response = activeSession.sendCommand(
                "RFID_SET_CONFIG",
                args,
                timeoutMs = 12_000,
            )
            if (response == null) {
                _rfid.update { it.copy(errorMessage = "RFID configuration timed out.") }
                return@launch
            }
            applyRfidStatus(response)
            val configSaved = response.optBoolean(
                "config_saved",
                response.optBoolean("ok"),
            )
            val detectionOk = response.optBoolean(
                "detection_ok",
                response.optBoolean("connected"),
            )
            val message = response.optString("msg").ifBlank {
                when {
                    !configSaved -> "PN532 configuration failed."
                    detectionOk -> "PN532 configured."
                    else -> "Configuration saved, but PN532 was not detected."
                }
            }
            _rfid.update {
                it.copy(
                    statusMessage = if (configSaved) message else it.statusMessage,
                    errorMessage = if (configSaved) null else message,
                )
            }
            appendLog(
                message,
                level = if (configSaved) LogLevel.INFO else LogLevel.ERROR,
                tag = "rfid",
            )
            if (configSaved) {
                addHistory(
                    "rfid",
                    "PN532 GPIOs saved on SDA $sdaGpio / SCL $sclGpio" +
                        if (detectionOk) "" else " (reader not detected)",
                    HistoryLevel.SUCCESS,
                )
            }
        } catch (t: Throwable) {
            _rfid.update {
                it.copy(errorMessage = t.message ?: "Could not save RFID configuration.")
            }
            appendLog(
                "RFID configuration failed: ${t.message ?: t.javaClass.simpleName}",
                level = LogLevel.ERROR,
                tag = "rfid",
            )
        } finally {
            _rfid.update { it.copy(configSaving = false) }
        }
    }
}

internal fun MainViewModel.startRfidScanImpl() {
    val activeSession = session ?: run {
        _rfid.update { it.copy(errorMessage = "Connect an NRSuite ESP32 first.") }
        return
    }
    if (_rfid.value.starting || _rfid.value.scanning) return
    _rfid.update {
        it.copy(starting = true, stopping = false, errorMessage = null, statusMessage = null)
    }
    scope.launch {
        try {
            val response = activeSession.sendCommand("RFID_SCAN_START", timeoutMs = 15_000)
            if (response == null) {
                _rfid.update {
                    it.copy(
                        starting = false,
                        scanning = false,
                        errorMessage = "RFID scan start timed out.",
                    )
                }
                return@launch
            }
            applyRfidStatus(response)
            if (response.optBoolean("ok")) {
                _rfid.update { it.copy(statusMessage = "Scanning for ISO14443A tags.") }
                appendLog("PN532 RFID scan started.", tag = "rfid")
                addHistory("rfid", "RFID scan started", HistoryLevel.INFO)
                updateForegroundService()
            } else {
                val message = response.optString("msg", "Could not start RFID scan.")
                _rfid.update { it.copy(scanning = false, errorMessage = message) }
                appendLog("RFID scan start failed: $message", level = LogLevel.ERROR, tag = "rfid")
            }
        } catch (t: Throwable) {
            _rfid.update {
                it.copy(
                    starting = false,
                    scanning = false,
                    errorMessage = t.message ?: "Could not start RFID scan.",
                )
            }
            appendLog(
                "RFID scan start failed: ${t.message ?: t.javaClass.simpleName}",
                level = LogLevel.ERROR,
                tag = "rfid",
            )
        } finally {
            _rfid.update { it.copy(starting = false) }
        }
    }
}

internal fun MainViewModel.stopRfidScanImpl(showStatus: Boolean = true) {
    val activeSession = session
    val shouldStop = _rfid.value.scanning || _rfid.value.starting || _rfid.value.stopping
    _rfid.update { it.copy(scanning = false, starting = false) }
    if (activeSession == null || !shouldStop) {
        updateForegroundService()
        return
    }
    if (_rfid.value.stopping) return
    _rfid.update { it.copy(stopping = true) }
    scope.launch {
        try {
            val response = activeSession.sendCommand("RFID_SCAN_STOP", timeoutMs = 5_000)
            if (response != null) applyRfidStatus(response)
            if (showStatus) {
                _rfid.update { it.copy(statusMessage = "RFID scan stopped.") }
                appendLog("PN532 RFID scan stopped.", tag = "rfid")
            }
        } catch (t: Throwable) {
            if (showStatus) {
                appendLog(
                    "RFID stop failed: ${t.message ?: t.javaClass.simpleName}",
                    level = LogLevel.ERROR,
                    tag = "rfid",
                )
            }
        } finally {
            _rfid.update { it.copy(stopping = false, scanning = false, starting = false) }
            updateForegroundService()
        }
    }
}

internal fun MainViewModel.clearRfidHistoryImpl() {
    _rfid.update { it.copy(tags = emptyList(), statusMessage = "RFID history cleared.") }
    preferences.edit().remove(PREF_RFID_HISTORY).apply()
}

internal fun MainViewModel.handleRfidDisconnectedImpl() {
    val wasScanning = _rfid.value.scanning || _rfid.value.starting
    _rfid.update {
        it.copy(
            connected = false,
            scanning = false,
            starting = false,
            stopping = false,
            configSaving = false,
            errorMessage = if (wasScanning) "ESP32 disconnected. RFID scanning stopped." else it.errorMessage,
        )
    }
    if (wasScanning) {
        appendLog("ESP32 disconnected; RFID scan stopped.", level = LogLevel.ERROR, tag = "rfid")
        updateForegroundService()
    }
}

internal fun MainViewModel.handleRfidStatusEvent(event: JSONObject) {
    applyRfidStatus(event)
}

internal fun MainViewModel.handleRfidTagEvent(event: JSONObject) {
    val uid = event.optString("uid").trim().uppercase()
    if (uid.isBlank()) return
    val now = System.currentTimeMillis()
    val observation = RfidTagObservation(
        uid = uid,
        atqa = event.optString("atqa", "").uppercase(),
        sak = event.optString("sak", "").uppercase(),
        type = event.optString("type", "ISO14443A"),
        observedAtMs = now,
        firmwareTimestampMs = event.optLong("timestamp_ms").takeIf { it > 0L },
        readOnly = event.optBoolean("read_only", true),
    )

    val previous = _rfid.value.tags.firstOrNull()
    val duplicate = previous != null &&
        previous.uid == observation.uid &&
        now - previous.observedAtMs < RFID_DUPLICATE_WINDOW_MS
    if (!duplicate) {
        val updated = (listOf(observation) + _rfid.value.tags).take(RFID_HISTORY_LIMIT)
        _rfid.update {
            it.copy(
                tags = updated,
                connected = true,
                scanning = true,
                statusMessage = "Tag detected: ${observation.type}.",
                errorMessage = null,
                lastUpdatedAtMs = now,
            )
        }
        persistRfidHistory(updated)
        appendLog("RFID tag ${observation.uid} · ${observation.type}", tag = "rfid")
        addHistory("rfid", "RFID tag ${observation.uid} · ${observation.type}", HistoryLevel.SUCCESS)
    } else {
        _rfid.update { it.copy(connected = true, scanning = true, lastUpdatedAtMs = now) }
    }
}

internal fun MainViewModel.handleRfidErrorEvent(event: JSONObject) {
    val message = event.optString("msg").ifBlank { "PN532 RFID error." }
    _rfid.update {
        it.copy(
            connected = event.optBoolean("connected", it.connected),
            scanning = event.optBoolean("scanning", it.scanning),
            errorMessage = message,
            lastUpdatedAtMs = System.currentTimeMillis(),
        )
    }
    appendLog("RFID error: $message", level = LogLevel.ERROR, tag = "rfid")
}

private fun MainViewModel.applyRfidStatus(json: JSONObject) {
    val availableGpios = buildList {
        val array = json.optJSONArray("rfid_gpios") ?: return@buildList
        for (index in 0 until array.length()) {
            val gpio = array.optInt(index, -1)
            if (gpio >= 0) add(gpio)
        }
    }
    val configured = json.optBoolean("configured", _rfid.value.configured)
    val connected = json.optBoolean("connected", _rfid.value.connected)
    val responseOk = json.optBoolean("ok", true)
    val configSaved = json.optBoolean("config_saved", false)
    val message = json.optString("msg").ifBlank { json.optString("detail") }
    val hasSda = json.has("sda_gpio") && !json.isNull("sda_gpio")
    val hasScl = json.has("scl_gpio") && !json.isNull("scl_gpio")
    val hasFirmwareVersion = json.has("firmware_version") && !json.isNull("firmware_version")
    val statusMessage = when {
        configSaved && !connected && message.isNotBlank() -> message
        connected && json.optBoolean("scanning", _rfid.value.scanning) ->
            "Scanning for ISO14443A tags."
        connected -> "PN532 ready."
        configured && message.isNotBlank() -> message
        !configured -> "Configure PN532 I2C SDA/SCL GPIOs from RFID Config."
        else -> _rfid.value.statusMessage
    }
    _rfid.update {
        it.copy(
            connected = connected,
            configured = configured,
            interfaceName = json.optString("interface", it.interfaceName),
            sdaGpio = if (hasSda) json.optionalGpio("sda_gpio") else it.sdaGpio,
            sclGpio = if (hasScl) json.optionalGpio("scl_gpio") else it.sclGpio,
            firmwareVersion = if (hasFirmwareVersion) {
                json.optString("firmware_version").takeIf { version -> version.isNotBlank() }
            } else {
                it.firmwareVersion
            },
            scanning = json.optBoolean("scanning", it.scanning),
            availableGpios = availableGpios.ifEmpty { it.availableGpios },
            statusMessage = statusMessage,
            errorMessage = when {
                !responseOk && message.isNotBlank() -> message
                connected -> null
                else -> it.errorMessage
            },
            lastUpdatedAtMs = System.currentTimeMillis(),
        )
    }
}

private fun MainViewModel.persistRfidHistory(tags: List<RfidTagObservation>) {
    val array = JSONArray()
    tags.forEach { tag ->
        array.put(
            JSONObject()
                .put("uid", tag.uid)
                .put("atqa", tag.atqa)
                .put("sak", tag.sak)
                .put("type", tag.type)
                .put("observed_at_ms", tag.observedAtMs)
                .put("firmware_timestamp_ms", tag.firmwareTimestampMs ?: JSONObject.NULL)
                .put("read_only", tag.readOnly),
        )
    }
    preferences.edit().putString(PREF_RFID_HISTORY, array.toString()).apply()
}

private fun JSONObject.optionalGpio(name: String): Int? {
    if (!has(name) || isNull(name)) return null
    return optInt(name, -1).takeIf { it >= 0 }
}
