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
import com.swp81x.nrsuite.core.wpa.Wpa22000Exporter
import com.swp81x.nrsuite.core.wpa.WpaCracker
import com.swp81x.nrsuite.core.wpa.WpaHandshake
import com.swp81x.nrsuite.core.wpa.WpaPcapAnalysis
import com.swp81x.nrsuite.core.wpa.analyzeWpaPcap
import com.swp81x.nrsuite.core.wpa.selectBestWpaHandshake
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

data class CrackerWordlistOption(
    val uri: Uri,
    val name: String,
)

private data class CrackerSource(
    val ssid: String,
    val preferredBssid: String?,
    val pcapLabel: String,
    val savedSessionId: String?,
    val sourceUri: Uri?,
    val analysisProvider: suspend () -> WpaPcapAnalysis,
)

private suspend fun MainViewModel.analyzeCustomPcapImpl(uri: Uri): WpaPcapAnalysis =
    withContext(Dispatchers.IO) {
        val input = app.contentResolver.openInputStream(uri)
            ?: throw IOException("Could not open the selected PCAP.")
        analyzeWpaPcap(PcapReader(input))
    }

private suspend fun MainViewModel.analyzePcapFileImpl(file: File): WpaPcapAnalysis =
    withContext(Dispatchers.IO) {
        analyzeWpaPcap(PcapReader(file))
    }

private fun MainViewModel.resolveCrackerSourceImpl(): CrackerSource? {
    val customUri = _crackerCustomPcapUri.value
    val session = _crackerSelectedSession.value
    return when {
        customUri != null -> {
            if (_crackerCustomPcapValidating.value) {
                appendLog("WPA cracker: custom PCAP is still being validated.", level = LogLevel.ERROR)
                return null
            }
            val customSsid = _crackerCustomSsid.value.trim()
            if (!_crackerCustomPcapValid.value) {
                appendLog("WPA cracker: custom PCAP has no validated M1/M2 EAPOL handshake.", level = LogLevel.ERROR)
                return null
            }
            if (customSsid.isBlank() || customSsid.length > 32) {
                appendLog("WPA cracker: enter the target SSID (1-32 characters) for the custom PCAP.", level = LogLevel.ERROR)
                return null
            }
            CrackerSource(
                ssid = customSsid,
                preferredBssid = null,
                pcapLabel = _crackerCustomPcapName.value ?: "custom PCAP",
                savedSessionId = null,
                sourceUri = customUri,
                analysisProvider = { analyzeCustomPcapImpl(customUri) },
            )
        }

        session != null -> {
            val pcapPath = session.pcapPath
            if (pcapPath.isNullOrBlank()) {
                appendLog("WPA cracker: selected session has no PCAP capture.", level = LogLevel.ERROR)
                return null
            }
            val pcapFile = File(pcapPath)
            if (!pcapFile.exists()) {
                appendLog("WPA cracker: PCAP file no longer exists.", level = LogLevel.ERROR)
                return null
            }
            if (session.ssid.isBlank() || session.ssid == "(hidden)") {
                appendLog("WPA cracker: the saved session has no usable SSID.", level = LogLevel.ERROR)
                return null
            }
            CrackerSource(
                ssid = session.ssid,
                preferredBssid = session.bssid.takeIf { it.isNotBlank() },
                pcapLabel = pcapFile.name,
                savedSessionId = session.id,
                sourceUri = null,
                analysisProvider = { analyzePcapFileImpl(pcapFile) },
            )
        }

        else -> {
            appendLog("WPA cracker: select a saved session or choose a custom PCAP.", level = LogLevel.ERROR)
            null
        }
    }
}

private fun selectCrackerHandshake(
    analysis: WpaPcapAnalysis,
    source: CrackerSource,
): WpaHandshake? {
    val preferredBssid = source.preferredBssid ?: analysis.detectedSsids
        .firstOrNull { it.ssid == source.ssid }
        ?.bssid
    return selectBestWpaHandshake(
        candidates = analysis.candidates,
        preferredSsid = source.ssid,
        preferredBssid = preferredBssid,
    )
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
    this.clearPendingNrugochiCaptureImpl()
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
    this.clearPendingNrugochiCaptureImpl()
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
            val completeCount = analysis.completeCandidates.size
            val detected = analysis.detectedSsids
            val preferred = detected.firstOrNull { entry ->
                analysis.completeCandidates.any { candidate ->
                    candidate.handshake.bssid?.toMacString()
                        ?.equals(entry.bssid, ignoreCase = true) == true
                }
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

            _crackerCustomPcapValid.value = completeCount > 0
            _crackerCustomPcapMessage.value = when {
                completeCount > 0 && detected.isNotEmpty() ->
                    "$completeCount valid M1/M2 handshake(s) detected. ${detected.size} SSID(s) found in PCAP."
                completeCount > 0 ->
                    "$completeCount valid M1/M2 handshake(s) detected. No SSID found; enter it manually."
                else ->
                    "No complete M1+M2 EAPOL handshake found in this PCAP."
            }
            _crackerStatus.value = when {
                completeCount <= 0 -> _crackerCustomPcapMessage.value
                detected.isNotEmpty() -> "Custom PCAP validated. Select the target SSID and add wordlists."
                else -> "Custom PCAP validated. Enter the target SSID and add wordlists."
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
    this.clearPendingNrugochiCaptureImpl()
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
    val cleanName = name?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: "wordlist.txt"
    val current = _crackerWordlists.value
    if (current.any { it.uri == uri }) {
        _crackerStatus.value = "Wordlist already added: $cleanName"
        return
    }
    _crackerWordlists.value = current + CrackerWordlistOption(uri, cleanName)
    _crackerResult.value = null
    _crackerStatus.value = "Added wordlist: $cleanName (${current.size + 1} selected)"
}

internal fun MainViewModel.removeCrackerWordlistImpl(index: Int) {
    if (_crackerRunning.value) return
    val current = _crackerWordlists.value
    if (index !in current.indices) return
    val removed = current[index]
    _crackerWordlists.value = current.filterIndexed { itemIndex, _ -> itemIndex != index }
    _crackerResult.value = null
    _crackerStatus.value = "Removed wordlist: ${removed.name}"
}

internal fun MainViewModel.clearCrackerWordlistImpl() {
    if (_crackerRunning.value) return
    _crackerWordlists.value = emptyList()
    _crackerResult.value = null
    _crackerStatus.value = "Select a saved handshake and add one or more wordlists."
}

internal fun MainViewModel.pauseCrackerImpl() {
    if (!_crackerRunning.value || _crackerPaused.value) return
    crackerPauseRequested = true
    _crackerPaused.value = true
    _crackerStatus.value = "Paused at ${_crackerTested.value} tested candidate(s)."
}

internal fun MainViewModel.resumeCrackerImpl() {
    if (!_crackerRunning.value || !_crackerPaused.value) return
    crackerPauseRequested = false
    _crackerPaused.value = false
    _crackerStatus.value = "Resuming wordlist..."
}

internal fun MainViewModel.startCrackerImpl() {
    if (_crackerRunning.value) return

    val wordlists = _crackerWordlists.value
    if (wordlists.isEmpty()) {
        appendLog("WPA cracker: add at least one wordlist first.", level = LogLevel.ERROR)
        return
    }

    val source = resolveCrackerSourceImpl() ?: return
    val pendingNrugochiCapture = autoHandshake.pendingCrackerCapture

    _crackerRunning.value = true
    _crackerPaused.value = false
    crackerPauseRequested = false
    _crackerResult.value = null
    _crackerTested.value = 0L
    _crackerSpeed.value = 0.0
    _crackerStatus.value = "Parsing handshake..."
    appendLog("WPA cracker: parsing ${source.pcapLabel}...")

    crackerJob = scope.launch(Dispatchers.Default) {
        val runningJob = coroutineContext[Job]
        try {
            val analysis = source.analysisProvider()
            val handshake = selectCrackerHandshake(analysis, source)
            if (handshake == null) {
                _crackerStatus.value = "No complete M1/M2 WPA2 handshake found for this target."
                appendLog("WPA cracker: no complete M1/M2 handshake found.", level = LogLevel.ERROR)
                return@launch
            }

            _crackerStatus.value = "Testing ${wordlists.size} wordlist(s)..."
            appendLog(
                "WPA cracker: handshake ready; testing ${wordlists.size} wordlist(s) for ${source.ssid}."
            )

            var found: String? = null
            for ((index, wordlist) in wordlists.withIndex()) {
                if (runningJob?.isActive != true) break
                _crackerStatus.value =
                    "Testing wordlist ${index + 1}/${wordlists.size}: ${wordlist.name}"
                val reader = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(wordlist.uri)?.bufferedReader()
                } ?: throw IOException("Could not open wordlist: ${wordlist.name}")

                found = WpaCracker.crack(
                    handshake = handshake,
                    ssid = source.ssid,
                    wordlist = reader,
                    initialTested = _crackerTested.value,
                    shouldStop = { runningJob?.isActive != true },
                    shouldPause = { crackerPauseRequested },
                    onProgress = { progress ->
                        _crackerTested.update { maxOf(it, progress.tested) }
                        _crackerSpeed.value = progress.candidatesPerSecond
                    },
                )
                if (found != null) break
            }

            if (runningJob?.isActive != true) {
                _crackerStatus.value = "Cracker stopped."
                return@launch
            }

            if (found != null) {
                _crackerResult.value = found
                _crackerStatus.value = "Password found: $found"
                if (source.savedSessionId != null) {
                    persistCrackedCredentialImpl(source.savedSessionId, found)
                } else if (source.sourceUri != null) {
                    saveCrackedCustomSessionImpl(
                        sourceUri = source.sourceUri,
                        displayName = source.pcapLabel,
                        ssid = source.ssid,
                        bssid = handshake.bssid,
                        password = found,
                    )
                    if (pendingNrugochiCapture != null) {
                        persistNrugochiCrackResultImpl(found)
                    }
                }
                appendLog("WPA cracker: password found.")
                addHistory(
                    "wpa_cracker",
                    "WPA2 password recovered for ${source.ssid.ifBlank { source.pcapLabel }}",
                    HistoryLevel.SUCCESS,
                )
            } else {
                _crackerStatus.value = "Password not found in the selected wordlist(s)."
                appendLog("WPA cracker: password not found.")
                if (pendingNrugochiCapture != null) {
                    markNrugochiCrackFailedImpl()
                }
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
            crackerPauseRequested = false
            _crackerPaused.value = false
            _crackerRunning.value = false
            crackerJob = null
        }
    }
}

internal fun MainViewModel.exportCracker22000Impl(uri: Uri) {
    if (_crackerRunning.value) return
    val source = resolveCrackerSourceImpl() ?: return
    _crackerStatus.value = "Preparing .22000 export..."
    scope.launch(Dispatchers.IO) {
        try {
            val analysis = source.analysisProvider()
            val handshake = selectCrackerHandshake(analysis, source)
                ?: throw IOException("No complete M1/M2 handshake found for this target.")
            val line = Wpa22000Exporter.buildLine(handshake, source.ssid)
                ?: throw IOException("The selected handshake is not exportable as .22000.")
            app.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(line)
                writer.newLine()
            } ?: throw IOException("Could not open the export destination.")
            val message = "Exported .22000 for ${source.ssid}."
            _crackerStatus.value = message
            appendLog(message, level = LogLevel.SUCCESS)
            addHistory("wpa_cracker", message, HistoryLevel.SUCCESS)
            _toastMessage.value = message
        } catch (t: Throwable) {
            val message = "Could not export .22000: ${t.message ?: t.javaClass.simpleName}"
            _crackerStatus.value = message
            appendLog(message, level = LogLevel.ERROR)
            _toastMessage.value = message
        }
    }
}

internal fun MainViewModel.stopCrackerImpl() {
    if (!_crackerRunning.value) return
    crackerPauseRequested = false
    _crackerPaused.value = false
    _crackerStatus.value = "Stopping..."
    crackerJob?.cancel()
}
