package com.swp81x.nrsuite

import android.net.Uri
import com.swp81x.nrsuite.core.credentials.CapturedCredential
import com.swp81x.nrsuite.core.credentials.CredentialSession
import com.swp81x.nrsuite.core.credentials.CredentialSource
import com.swp81x.nrsuite.core.credentials.CredentialStatus
import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.pcap.PcapReader
import com.swp81x.nrsuite.core.wifi.DetectedSsid
import com.swp81x.nrsuite.core.wifi.PcapSsidParser
import com.swp81x.nrsuite.core.wpa.WpaCracker
import com.swp81x.nrsuite.core.wpa.WpaHandshake
import com.swp81x.nrsuite.core.wpa.WpaHandshakeParser
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class CustomPcapAnalysis(
    val handshake: WpaHandshake,
    val detectedSsids: List<DetectedSsid>,
)

private suspend fun MainViewModel.analyzeCustomPcapImpl(uri: Uri): CustomPcapAnalysis =
    withContext(Dispatchers.IO) {
        val input = app.contentResolver.openInputStream(uri)
            ?: throw IOException("Could not open the selected PCAP.")
        val parsed = WpaHandshake()
        val detected = LinkedHashMap<String, DetectedSsid>()
        PcapReader(input).forEachPacket { frame ->
            WpaHandshakeParser.parse(frame, parsed)
            PcapSsidParser.parse(frame)?.let { ssid ->
                detected["${ssid.ssid}|${ssid.bssid}"] = ssid
            }
            true
        }
        CustomPcapAnalysis(
            handshake = parsed.copyHandshake(),
            detectedSsids = detected.values.toList(),
        )
    }

private suspend fun MainViewModel.parseHandshakeFromUriImpl(uri: Uri): WpaHandshake =
    withContext(Dispatchers.IO) {
        val input = app.contentResolver.openInputStream(uri)
            ?: throw IOException("Could not open the selected PCAP.")
        val parsed = WpaHandshake()
        PcapReader(input).forEachPacket { frame ->
            WpaHandshakeParser.parse(frame, parsed)
            !parsed.isComplete
        }
        parsed.copyHandshake()
    }

private fun ByteArray.toMacString(): String =
    joinToString(":") { value -> String.format("%02X", value.toInt() and 0xFF) }

internal fun MainViewModel.loadCredentialSessionsImpl() {
    scope.launch(Dispatchers.IO) {
        val loaded = runCatching { credentialStore.loadSessions() }
            .getOrDefault(emptyList())
            .sortedByDescending { it.startedAt }
        _credentialSessions.value = loaded
    }
}

internal fun MainViewModel.persistCrackedCredentialImpl(sessionId: String, password: String) {
    synchronized(credentialLock) {
        val session = _credentialSessions.value.firstOrNull { it.id == sessionId } ?: return
        if (session.source != CredentialSource.EVIL_TWIN) return
        if (session.credentials.any { it.status == CredentialStatus.CORRECT }) return

        val updated = session.copy(
            credentials = listOf(
                CapturedCredential(
                    value = password,
                    capturedAt = timeHmNow(),
                    status = CredentialStatus.CORRECT,
                    source = CredentialSource.EVIL_TWIN,
                ),
            ),
        )
        if (_crackerSelectedSession.value?.id == sessionId) {
            _crackerSelectedSession.value = updated
        }
        upsertCredentialSessionLocked(updated)
    }
}

internal suspend fun MainViewModel.saveCrackedCustomSessionImpl(sourceUri: Uri, displayName: String, ssid: String, bssid: ByteArray?, password: String) {
    val copiedFile = withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(app.filesDir, "Credentials")
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException("Could not create credential capture directory")
            }
            val destination = File(directory, "wpa_cracker_${System.currentTimeMillis()}.pcap")
            val input = app.contentResolver.openInputStream(sourceUri)
                ?: throw IOException("Could not open custom PCAP")
            input.use { source ->
                destination.outputStream().use { sink ->
                    source.copyTo(sink)
                }
            }
            destination
        }.onFailure { error ->
            appendLog("Could not save cracked custom PCAP: ${error.message}", level = LogLevel.ERROR)
        }.getOrNull()
    }

    val session = CredentialSession(
        id = System.currentTimeMillis().toString(),
        source = CredentialSource.WPA_CRACKER,
        ssid = ssid.ifBlank { "(hidden)" },
        bssid = bssid?.toMacString().orEmpty(),
        channel = null,
        startedAt = timestampNow(),
        endedAt = timestampNow(),
        pcapPath = copiedFile?.absolutePath,
        credentials = listOf(
            CapturedCredential(
                value = password,
                capturedAt = timeHmNow(),
                status = CredentialStatus.CORRECT,
                source = CredentialSource.WPA_CRACKER,
            ),
        ),
    )
    synchronized(credentialLock) {
        upsertCredentialSessionLocked(session)
    }
    appendLog("Saved WPA Cracker result for ${ssid.ifBlank { displayName }}.")
}

internal fun MainViewModel.deleteCredentialSessionImpl(id: String) {
    val removed = synchronized(credentialLock) {
        val session = _credentialSessions.value.firstOrNull { it.id == id } ?: return@synchronized null
        _credentialSessions.value = _credentialSessions.value.filterNot { it.id == id }
        session
    } ?: return

    if (_crackerSelectedSession.value?.id == removed.id) {
        _crackerSelectedSession.value = null
    }

    scope.launch(Dispatchers.IO) {
        runCatching {
            credentialStore.deleteCaptureFile(removed)
            credentialStore.saveSessions(_credentialSessions.value)
        }
    }
}

internal fun MainViewModel.clearCredentialSessionsImpl() {
    val removed = synchronized(credentialLock) {
        val current = _credentialSessions.value
        _credentialSessions.value = emptyList()
        current
    }
    _crackerSelectedSession.value = null
    scope.launch(Dispatchers.IO) {
        runCatching {
            removed.forEach { credentialStore.deleteCaptureFile(it) }
            credentialStore.saveSessions(emptyList())
        }
    }
}

internal fun MainViewModel.selectCrackerSessionImpl(session: CredentialSession?) {
    if (_crackerRunning.value) return
    if (session != null) {
        resetCrackerCustomPcapStateImpl()
    }
    _crackerSelectedSession.value = session
    _crackerResult.value = null
    _crackerTested.value = 0
    _crackerSpeed.value = 0.0
    _crackerStatus.value = when {
        session == null -> "Select a saved handshake, or choose a custom PCAP."
        session.pcapPath.isNullOrBlank() -> "Selected session has no PCAP capture."
        else -> "Ready: ${session.ssid.ifBlank { "target" }}"
    }
}

internal fun MainViewModel.setCrackerCustomPcapImpl(uri: Uri, name: String?) {
    if (_crackerRunning.value) return
    _crackerSelectedSession.value = null
    _crackerCustomPcapUri.value = uri
    _crackerCustomPcapName.value = name ?: uri.lastPathSegment ?: "capture.pcap"
    resetCrackerCustomPcapStateImpl()
    _crackerCustomPcapUri.value = uri
    _crackerCustomPcapName.value = name ?: uri.lastPathSegment ?: "capture.pcap"
    _crackerCustomPcapMessage.value = "Validating EAPOL handshake..."
    _crackerResult.value = null
    _crackerTested.value = 0
    _crackerSpeed.value = 0.0
    _crackerStatus.value = "Validating custom PCAP..."

    scope.launch(Dispatchers.IO) {
        _crackerCustomPcapValidating.value = true
        try {
            val analysis = analyzeCustomPcapImpl(uri)
            val parsed = analysis.handshake
            val sawM1 = parsed.m1Bssid != null || parsed.aNonce != null
            val sawM2 = parsed.m2Bssid != null || parsed.mic != null
            val detected = analysis.detectedSsids
            val preferred = detected.firstOrNull {
                parsed.bssid?.let { bytes -> it.bssid.equals(bytes.toMacString(), ignoreCase = true) } == true
            } ?: detected.singleOrNull()

            _crackerCustomSsidOptions.value = detected
            if (preferred != null) {
                _crackerCustomSsidSelected.value = preferred.ssid
                _crackerCustomSsid.value = preferred.ssid
                _crackerCustomSsidManual.value = false
            } else {
                _crackerCustomSsidSelected.value = null
                _crackerCustomSsid.value = ""
                _crackerCustomSsidManual.value = true
            }

            _crackerCustomPcapValid.value = parsed.isComplete
            _crackerCustomPcapMessage.value = when {
                parsed.isComplete && detected.isNotEmpty() ->
                    "Valid M1/M2 EAPOL handshake detected. ${detected.size} SSID(s) found in PCAP."
                parsed.isComplete ->
                    "Valid M1/M2 EAPOL handshake detected. No SSID found; enter it manually."
                sawM1 || sawM2 ->
                    "EAPOL frames found, but the M1+M2 handshake is incomplete."
                else ->
                    "No EAPOL handshake found in this PCAP."
            }
            _crackerStatus.value = when {
                !parsed.isComplete -> _crackerCustomPcapMessage.value
                detected.isNotEmpty() -> "Custom PCAP validated. Select the target SSID and choose a wordlist."
                else -> "Custom PCAP validated. Enter the target SSID and choose a wordlist."
            }
        } catch (t: Throwable) {
            _crackerCustomPcapValid.value = false
            _crackerCustomPcapValidating.value = false
            _crackerCustomPcapMessage.value = "PCAP invalid: ${t.message ?: t.javaClass.simpleName}"
            _crackerStatus.value = _crackerCustomPcapMessage.value
        } finally {
            _crackerCustomPcapValidating.value = false
        }
    }
}

internal fun MainViewModel.selectCrackerCustomSsidImpl(ssid: String) {
    if (_crackerRunning.value) return
    _crackerCustomSsidSelected.value = ssid
    _crackerCustomSsid.value = ssid.take(32)
    _crackerCustomSsidManual.value = false
}

internal fun MainViewModel.useManualCrackerSsidImpl() {
    if (_crackerRunning.value) return
    _crackerCustomSsidSelected.value = null
    _crackerCustomSsidManual.value = true
}

internal fun MainViewModel.setCrackerCustomSsidImpl(value: String) {
    if (!_crackerCustomSsidManual.value) return
    _crackerCustomSsid.value = value.take(32)
}

internal fun MainViewModel.clearCrackerCustomPcapImpl() {
    if (_crackerRunning.value) return
    resetCrackerCustomPcapStateImpl()
    _crackerResult.value = null
    _crackerStatus.value = "Select a saved handshake, or choose a custom PCAP."
}

internal fun MainViewModel.resetCrackerCustomPcapStateImpl() {
    _crackerCustomPcapUri.value = null
    _crackerCustomPcapName.value = null
    _crackerCustomSsid.value = ""
    _crackerCustomSsidOptions.value = emptyList()
    _crackerCustomSsidSelected.value = null
    _crackerCustomSsidManual.value = true
    _crackerCustomPcapValid.value = false
    _crackerCustomPcapValidating.value = false
    _crackerCustomPcapMessage.value = "No custom PCAP selected."
}

internal fun MainViewModel.setCrackerWordlistImpl(uri: Uri, name: String?) {
    if (_crackerRunning.value) return
    _crackerWordlistUri.value = uri
    _crackerWordlistName.value = name ?: uri.lastPathSegment ?: "wordlist.txt"
    _crackerResult.value = null
    _crackerStatus.value = "Wordlist selected: ${_crackerWordlistName.value}"
}

internal fun MainViewModel.clearCrackerWordlistImpl() {
    if (_crackerRunning.value) return
    _crackerWordlistUri.value = null
    _crackerWordlistName.value = null
    _crackerResult.value = null
    _crackerStatus.value = "Select a saved handshake and a wordlist."
}

internal fun MainViewModel.startCrackerImpl() {
    if (_crackerRunning.value) return

    val wordlistUri = _crackerWordlistUri.value
    if (wordlistUri == null) {
        appendLog("WPA cracker: select a wordlist first.", level = LogLevel.ERROR)
        return
    }

    val customUri = _crackerCustomPcapUri.value
    val session = _crackerSelectedSession.value
    val ssid: String
    val pcapLabel: String
    val savedSessionId: String?
    val parseHandshake: suspend () -> WpaHandshake

    when {
        customUri != null -> {
            if (_crackerCustomPcapValidating.value) {
                appendLog("WPA cracker: custom PCAP is still being validated.", level = LogLevel.ERROR)
                return
            }
            val customSsid = _crackerCustomSsid.value.trim()
            if (!_crackerCustomPcapValid.value) {
                appendLog("WPA cracker: custom PCAP has no validated M1/M2 EAPOL handshake.", level = LogLevel.ERROR)
                return
            }
            if (customSsid.isBlank() || customSsid.length > 32) {
                appendLog("WPA cracker: enter the target SSID (1-32 characters) for the custom PCAP.", level = LogLevel.ERROR)
                return
            }
            ssid = customSsid
            pcapLabel = _crackerCustomPcapName.value ?: "custom PCAP"
            savedSessionId = null
            parseHandshake = { parseHandshakeFromUriImpl(customUri) }
        }

        session != null -> {
            val pcapPath = session.pcapPath
            if (pcapPath.isNullOrBlank()) {
                appendLog("WPA cracker: selected session has no PCAP capture.", level = LogLevel.ERROR)
                return
            }
            val pcapFile = File(pcapPath)
            if (!pcapFile.exists()) {
                appendLog("WPA cracker: PCAP file no longer exists.", level = LogLevel.ERROR)
                return
            }
            if (session.ssid.isBlank() || session.ssid == "(hidden)") {
                appendLog("WPA cracker: the saved session has no usable SSID.", level = LogLevel.ERROR)
                return
            }
            ssid = session.ssid
            pcapLabel = pcapFile.name
            savedSessionId = session.id
            parseHandshake = {
                withContext(Dispatchers.IO) {
                    val parsed = WpaHandshake()
                    PcapReader(pcapFile).forEachPacket { frame ->
                        WpaHandshakeParser.parse(frame, parsed)
                        !parsed.isComplete
                    }
                    parsed.copyHandshake()
                }
            }
        }

        else -> {
            appendLog("WPA cracker: select a saved session or choose a custom PCAP.", level = LogLevel.ERROR)
            return
        }
    }

    _crackerRunning.value = true
    _crackerResult.value = null
    _crackerTested.value = 0
    _crackerSpeed.value = 0.0
    _crackerStatus.value = "Parsing handshake..."
    appendLog("WPA cracker: parsing $pcapLabel...")

    crackerJob = scope.launch(Dispatchers.Default) {
        try {
            val handshake = parseHandshake()
            if (!handshake.isComplete) {
                _crackerStatus.value = "No complete M1/M2 WPA2 handshake found in the capture."
                appendLog("WPA cracker: no complete M1/M2 handshake found.", level = LogLevel.ERROR)
                return@launch
            }

            _crackerStatus.value = "Testing wordlist..."
            appendLog("WPA cracker: handshake ready; testing wordlist...")

            val reader = withContext(Dispatchers.IO) {
                app.contentResolver.openInputStream(wordlistUri)?.bufferedReader()
            } ?: throw IOException("Could not open the selected wordlist.")

            val runningJob = coroutineContext[Job]
            val found = WpaCracker.crack(
                handshake = handshake,
                ssid = ssid,
                wordlist = reader,
                shouldStop = { runningJob?.isActive != true },
                onProgress = { progress ->
                    _crackerTested.update { maxOf(it, progress.tested) }
                    _crackerSpeed.value = progress.candidatesPerSecond
                },
            )

            if (runningJob?.isActive != true) {
                _crackerStatus.value = "Cracker stopped."
                return@launch
            }

            if (found != null) {
                _crackerResult.value = found
                _crackerStatus.value = "Password found: $found"
                if (savedSessionId != null) {
                    persistCrackedCredentialImpl(savedSessionId, found)
                } else if (customUri != null) {
                    saveCrackedCustomSessionImpl(
                        sourceUri = customUri,
                        displayName = pcapLabel,
                        ssid = ssid,
                        bssid = handshake.bssid,
                        password = found,
                    )
                }
                appendLog("WPA cracker: password found.")
                addHistory(
                    "wpa_cracker",
                    "WPA2 password recovered for ${ssid.ifBlank { pcapLabel }}",
                    HistoryLevel.SUCCESS,
                )
            } else {
                _crackerStatus.value = "Password not found in the selected wordlist."
                appendLog("WPA cracker: password not found.")
            }
        } catch (e: CancellationException) {
            _crackerStatus.value = "Cracker stopped."
            throw e
        } catch (t: Throwable) {
            _crackerStatus.value = "Cracker failed: ${t.message ?: t.javaClass.simpleName}"
            appendLog(
                "WPA cracker failed: ${t.message ?: t.javaClass.simpleName}",
                level = LogLevel.ERROR,
            )
        } finally {
            _crackerRunning.value = false
            crackerJob = null
        }
    }
}

internal fun MainViewModel.stopCrackerImpl() {
    if (!_crackerRunning.value) return
    _crackerStatus.value = "Stopping..."
    crackerJob?.cancel()
}
