package com.swp81x.nrsuite

import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.swp81x.nrsuite.core.credentials.CapturedCredential
import com.swp81x.nrsuite.core.credentials.CredentialSession
import com.swp81x.nrsuite.core.credentials.CredentialSource
import com.swp81x.nrsuite.core.credentials.CredentialStatus
import com.swp81x.nrsuite.core.handshake.CapturedHandshakeEntry
import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.pcap.PcapReader
import com.swp81x.nrsuite.core.wpa.CrackerSessionSnapshot
import com.swp81x.nrsuite.core.wpa.CrackerSessionStatus
import com.swp81x.nrsuite.core.wpa.CrackerSourceType
import com.swp81x.nrsuite.core.wpa.CrackerTargetSnapshot
import com.swp81x.nrsuite.core.wpa.CrackerTargetStatus
import com.swp81x.nrsuite.core.wpa.Pcap22000Candidate
import com.swp81x.nrsuite.core.wpa.WpaCracker
import com.swp81x.nrsuite.core.wpa.WpaHandshake
import com.swp81x.nrsuite.core.wpa.WpaPcapAnalysis
import com.swp81x.nrsuite.core.wpa.analyzeWpaPcap
import com.swp81x.nrsuite.core.wpa.dedupeBestWpaCandidates
import com.swp81x.nrsuite.core.wpa.parseWpa22000
import com.swp81x.nrsuite.core.wpa.selectBestWpaHandshake
import java.io.BufferedReader
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class CrackerWordlistOption(
    val uri: Uri,
    val name: String,
)

private data class CrackerWordlistStream(
    val reader: BufferedReader,
    val countingInput: CountingInputStream,
    val totalBytes: Long,
)

/**
 * Resolve a user-facing document name for SAF URIs.
 *
 * Some providers return opaque document IDs such as `msf:1000` from
 * Uri.lastPathSegment instead of a filename. Prefer OpenableColumns.DISPLAY_NAME
 * and reject known provider-internal identifiers so the UI never shows them.
 */
internal fun selectDocumentDisplayName(
    suppliedName: String?,
    queriedName: String?,
    uriLastPathSegment: String?,
    fallback: String,
): String {
    return sequenceOf(suppliedName, queriedName, uriLastPathSegment)
        .mapNotNull { value ->
            val trimmed = value?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val normalized = trimmed.lowercase()
            if (normalized.startsWith("msf:") ||
                normalized.startsWith("primary:") ||
                normalized.startsWith("raw:") ||
                normalized.startsWith("content:") ||
                normalized.startsWith("document:")
            ) {
                null
            } else {
                trimmed.substringAfterLast('/').takeIf { it.isNotBlank() }
            }
        }
        .firstOrNull()
        ?: fallback
}

private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
    val bytesRead = AtomicLong(0L)

    override fun read(): Int = super.read().also { value ->
        if (value >= 0) bytesRead.incrementAndGet()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        super.read(buffer, offset, length).also { count ->
            if (count > 0) bytesRead.addAndGet(count.toLong())
        }
}

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

private fun ByteArray.toMacString(): String =
    joinToString(":") { value -> String.format("%02X", value.toInt() and 0xFF) }

private fun crackerTargetId(ssid: String, bssid: String?, station: String?): String =
    listOf(ssid, bssid.orEmpty(), station.orEmpty()).joinToString("|")

private fun buildCrackerTargetsFromPcap(
    analysis: WpaPcapAnalysis,
    sourceLabel: String,
    fallbackSsid: String? = null,
    preferredBssid: String? = null,
): List<CrackerTargetSnapshot> {
    val candidates = analysis.completeCandidates.mapNotNull { candidate ->
        val handshake = candidate.handshake
        val bssid = handshake.bssid?.toMacString() ?: return@mapNotNull null
        val station = handshake.station?.toMacString() ?: return@mapNotNull null
        Pcap22000Candidate(
            sourceName = sourceLabel,
            handshake = handshake,
            detectedSsid = candidate.ssid ?: fallbackSsid,
        )
    }
    val (deduped, _) = dedupeBestWpaCandidates(candidates)
    return deduped.mapNotNull { candidate ->
        val ssid = candidate.effectiveSsid?.trim()?.takeIf { it.isNotEmpty() }
            ?: return@mapNotNull null
        val bssid = candidate.bssidHex ?: return@mapNotNull null
        val station = candidate.stationHex ?: return@mapNotNull null
        if (!preferredBssid.isNullOrBlank() &&
            !bssid.equals(preferredBssid, ignoreCase = true) &&
            deduped.size > 1
        ) {
            // The session hint is only a preference; retain all detected targets.
        }
        CrackerTargetSnapshot(
            id = crackerTargetId(ssid, bssid, station),
            ssid = ssid,
            bssid = bssid,
            station = station,
            handshake = candidate.handshake,
            selected = true,
        )
    }
}

private fun buildManualTarget(
    analysis: WpaPcapAnalysis,
    ssid: String,
    sourceLabel: String,
): CrackerTargetSnapshot? {
    val cleanSsid = ssid.trim().take(32)
    if (cleanSsid.isBlank()) return null
    val handshake = selectBestWpaHandshake(analysis.candidates, preferredSsid = cleanSsid)
        ?: return null
    val bssid = handshake.bssid?.toMacString()
    val station = handshake.station?.toMacString()
    return CrackerTargetSnapshot(
        id = crackerTargetId(cleanSsid, bssid, station),
        ssid = cleanSsid,
        bssid = bssid,
        station = station,
        handshake = handshake,
        selected = true,
    )
}

private fun buildTargetsFrom22000(
    content: String,
): Pair<List<CrackerTargetSnapshot>, String> {
    val parsed = parseWpa22000(content.lineSequence())
    val candidates = parsed.crackable.mapNotNull { record ->
        val handshake = record.handshake ?: return@mapNotNull null
        val ssid = record.ssid?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        Pcap22000Candidate(
            sourceName = "22000",
            handshake = handshake,
            detectedSsid = ssid,
        )
    }
    val (deduped, _) = dedupeBestWpaCandidates(candidates)
    val targets = deduped.mapNotNull { candidate ->
        val ssid = candidate.effectiveSsid ?: return@mapNotNull null
        val bssid = candidate.bssidHex ?: return@mapNotNull null
        val station = candidate.stationHex ?: return@mapNotNull null
        CrackerTargetSnapshot(
            id = crackerTargetId(ssid, bssid, station),
            ssid = ssid,
            bssid = bssid,
            station = station,
            handshake = candidate.handshake,
            selected = true,
        )
    }
    val message = buildString {
        append("${targets.size} crackable EAPOL target(s)")
        if (parsed.pmkidCount > 0) append(" · ${parsed.pmkidCount} PMKID unsupported")
        if (parsed.malformedCount > 0) append(" · ${parsed.malformedCount} rejected line(s)")
    }
    return targets to message
}

internal fun MainViewModel.loadCredentialSessionsImpl() {
    scope.launch(Dispatchers.IO) {
        val loaded = runCatching { credentialStore.loadSessions() }
            .getOrDefault(emptyList())
            .sortedByDescending { it.startedAt }
        _credentialSessions.value = loaded
    }
}

internal fun MainViewModel.loadCrackerSessionsImpl() {
    scope.launch(Dispatchers.IO) {
        _crackerSessions.value = runCatching { crackerSessionStore.loadSessions() }
            .getOrDefault(emptyList())
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

internal suspend fun MainViewModel.saveCrackedCustomSessionImpl(
    sourceUri: Uri,
    displayName: String,
    ssid: String,
    bssid: ByteArray?,
    password: String,
) {
    val copiedFile = withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(app.filesDir, "Credentials")
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException("Could not create credential capture directory")
            }
            val sourceExtension = displayName.substringAfterLast('.', "pcap")
                .takeIf { it.length in 1..8 }
                ?: "pcap"
            val destination = File(
                directory,
                "wpa_cracker_${System.currentTimeMillis()}.$sourceExtension",
            )
            val input = app.contentResolver.openInputStream(sourceUri)
                ?: throw IOException("Could not open source capture")
            input.use { source ->
                destination.outputStream().use { sink -> source.copyTo(sink) }
            }
            destination
        }.onFailure { error ->
            appendLog("Could not save cracked custom capture: ${error.message}", level = LogLevel.ERROR)
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

internal fun MainViewModel.selectCrackerSourceImpl(type: CrackerSourceType) {
    if (_crackerRunning.value) return
    _crackerSourceType.value = type
    this.clearPendingNrugochiCaptureImpl()
    _crackerSelectedSession.value = null
    _crackerSelectedCapturedPath.value = null
    _crackerCustomPcapUri.value = null
    _crackerCustomPcapName.value = null
    _crackerImported22000Uri.value = null
    _crackerImported22000Name.value = null
    _crackerTargets.value = emptyList()
    _crackerTargetMessage.value = null
    _crackerManualMode.value = false
    _crackerManualSsid.value = ""
    _crackerActiveSessionId.value = null
    _crackerTested.value = 0
    _crackerSpeed.value = 0.0
    _crackerResult.value = null
    _crackerProgressFraction.value = 0f
    _crackerProgressKnown.value = false
    _crackerCurrentTargetIndex.value = 0
    _crackerSelectedTargetCount.value = 0
    _crackerCurrentTargetSsid.value = ""
    _crackerStatus.value = when (type) {
        CrackerSourceType.SAVED_CAPTURE -> "Select a saved capture."
        CrackerSourceType.CUSTOM_PCAP -> "Choose a classic PCAP file."
        CrackerSourceType.IMPORTED_22000 -> "Choose a Hashcat .22000 file."
    }
}

internal fun MainViewModel.selectCrackerSessionImpl(session: CredentialSession?) {
    if (_crackerRunning.value) return
    this.clearPendingNrugochiCaptureImpl()
    _crackerSourceType.value = CrackerSourceType.SAVED_CAPTURE
    _crackerSelectedSession.value = session
    _crackerSelectedCapturedPath.value = null
    _crackerCustomPcapUri.value = null
    _crackerCustomPcapName.value = null
    _crackerImported22000Uri.value = null
    _crackerImported22000Name.value = null
    _crackerManualMode.value = false
    _crackerManualSsid.value = ""
    _crackerTargets.value = emptyList()
    _crackerTargetMessage.value = null
    _crackerActiveSessionId.value = null
    _crackerTested.value = 0
    _crackerSpeed.value = 0.0
    _crackerResult.value = null
    _crackerProgressFraction.value = 0f
    _crackerProgressKnown.value = false
    _crackerCurrentTargetIndex.value = 0
    _crackerSelectedTargetCount.value = 0
    _crackerCurrentTargetSsid.value = ""
    if (session == null) {
        _crackerStatus.value = "Select a saved capture."
        return
    }
    val pcapPath = session.pcapPath
    if (pcapPath.isNullOrBlank()) {
        _crackerStatus.value = "Selected capture has no PCAP file."
        return
    }
    val file = File(pcapPath)
    if (!file.exists()) {
        _crackerStatus.value = "PCAP file no longer exists."
        return
    }
    _crackerStatus.value = "Analyzing ${file.name}..."
    scope.launch(Dispatchers.IO) {
        _crackerTargetsLoading.value = true
        try {
            val analysis = analyzePcapFileImpl(file)
            if (_crackerSelectedSession.value?.id != session.id) return@launch
            crackerLastPcapAnalysis = analysis
            val fallback = session.ssid.takeIf {
                it.isNotBlank() && it != "(hidden)" && analysis.detectedSsids.isEmpty()
            }
            var targets = buildCrackerTargetsFromPcap(
                analysis = analysis,
                sourceLabel = file.name,
                fallbackSsid = fallback,
                preferredBssid = session.bssid,
            )
            if (targets.isEmpty()) {
                val fallbackTarget = buildManualTarget(
                    analysis = analysis,
                    ssid = session.ssid,
                    sourceLabel = file.name,
                )
                if (fallbackTarget != null) targets = listOf(fallbackTarget)
            }
            _crackerTargets.value = targets
            _crackerTargetMessage.value = when {
                targets.size > 1 -> "${targets.size} targets detected in this capture."
                targets.size == 1 -> "1 target detected."
                else -> "No crackable M1/M2 target with an SSID was found."
            }
            _crackerStatus.value = if (targets.isEmpty()) {
                "No crackable targets found."
            } else {
                "Review the detected targets and choose one wordlist."
            }
        } catch (t: Throwable) {
            _crackerTargets.value = emptyList()
            _crackerTargetMessage.value = "PCAP analysis failed: ${t.message ?: t.javaClass.simpleName}"
            _crackerStatus.value = _crackerTargetMessage.value.orEmpty()
        } finally {
            _crackerTargetsLoading.value = false
        }
    }
}

internal fun MainViewModel.selectCrackerCapturedHandshakeImpl(entry: CapturedHandshakeEntry) {
    if (_crackerRunning.value) return
    val file = File(entry.pcapPath)
    if (!file.exists()) {
        _crackerStatus.value = "Captured handshake file no longer exists."
        return
    }
    this.clearPendingNrugochiCaptureImpl()
    autoHandshake.pendingCrackerCapture = entry
    _crackerSourceType.value = CrackerSourceType.SAVED_CAPTURE
    _crackerSelectedSession.value = null
    _crackerSelectedCapturedPath.value = entry.pcapPath
    _crackerCustomPcapName.value = file.name
    _crackerCustomPcapUri.value = runCatching {
        FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
    }.getOrNull()
    _crackerImported22000Uri.value = null
    _crackerImported22000Name.value = null
    _crackerManualMode.value = false
    _crackerManualSsid.value = ""
    _crackerTargets.value = emptyList()
    _crackerTargetMessage.value = null
    _crackerActiveSessionId.value = null
    _crackerResult.value = null
    resetCrackerProgressState()
    _crackerStatus.value = "Analyzing ${file.name}..."
    crackerLastPcapAnalysis = null

    scope.launch(Dispatchers.IO) {
        _crackerTargetsLoading.value = true
        try {
            val analysis = analyzePcapFileImpl(file)
            if (_crackerSelectedCapturedPath.value != entry.pcapPath) return@launch
            crackerLastPcapAnalysis = analysis
            var targets = buildCrackerTargetsFromPcap(
                analysis = analysis,
                sourceLabel = file.name,
                fallbackSsid = entry.ssid.takeIf { it.isNotBlank() && it != "(hidden)" },
                preferredBssid = entry.bssid,
            )
            if (targets.isEmpty()) {
                val fallbackTarget = buildManualTarget(
                    analysis = analysis,
                    ssid = entry.ssid,
                    sourceLabel = file.name,
                )
                if (fallbackTarget != null) targets = listOf(fallbackTarget)
            }
            _crackerTargets.value = targets
            _crackerTargetMessage.value = when {
                targets.size > 1 -> "${targets.size} handshakes detected in ${file.name}."
                targets.size == 1 -> "1 handshake detected in ${file.name}."
                else -> "No crackable M1/M2 handshake with an SSID was found."
            }
            _crackerStatus.value = if (targets.isEmpty()) {
                "No crackable handshakes found."
            } else {
                "Review the handshakes and choose one wordlist."
            }
        } catch (t: Throwable) {
            _crackerTargets.value = emptyList()
            _crackerTargetMessage.value =
                "Capture analysis failed: ${t.message ?: t.javaClass.simpleName}"
            _crackerStatus.value = _crackerTargetMessage.value.orEmpty()
        } finally {
            _crackerTargetsLoading.value = false
        }
    }
}

internal fun MainViewModel.setCrackerCustomPcapImpl(uri: Uri, name: String?) {
    if (_crackerRunning.value) return
    this.clearPendingNrugochiCaptureImpl()
    _crackerSourceType.value = CrackerSourceType.CUSTOM_PCAP
    _crackerSelectedSession.value = null
    _crackerSelectedCapturedPath.value = null
    _crackerCustomPcapUri.value = uri
    _crackerCustomPcapName.value = selectDocumentDisplayName(
        suppliedName = name,
        queriedName = queryDocumentDisplayNameImpl(uri),
        uriLastPathSegment = uri.lastPathSegment,
        fallback = "capture.pcap",
    )
    _crackerImported22000Uri.value = null
    _crackerImported22000Name.value = null
    _crackerManualMode.value = false
    _crackerManualSsid.value = ""
    _crackerTargets.value = emptyList()
    _crackerTargetMessage.value = null
    _crackerActiveSessionId.value = null
    _crackerTested.value = 0
    _crackerSpeed.value = 0.0
    _crackerResult.value = null
    _crackerProgressFraction.value = 0f
    _crackerProgressKnown.value = false
    _crackerCurrentTargetIndex.value = 0
    _crackerSelectedTargetCount.value = 0
    _crackerCurrentTargetSsid.value = ""
    _crackerStatus.value = "Analyzing custom PCAP..."
    crackerLastPcapAnalysis = null

    scope.launch(Dispatchers.IO) {
        _crackerTargetsLoading.value = true
        try {
            val analysis = analyzeCustomPcapImpl(uri)
            if (_crackerCustomPcapUri.value != uri) return@launch
            crackerLastPcapAnalysis = analysis
            val targets = buildCrackerTargetsFromPcap(
                analysis = analysis,
                sourceLabel = _crackerCustomPcapName.value ?: "custom PCAP",
            )
            _crackerTargets.value = targets
            if (targets.isEmpty() && analysis.completeCandidates.isNotEmpty()) {
                _crackerManualMode.value = true
                _crackerTargetMessage.value =
                    "No SSIDs detected. Enter one target SSID to use manual mode."
                _crackerStatus.value = "Enter a target SSID for manual single-target mode."
            } else {
                _crackerTargetMessage.value = when {
                    targets.size > 1 -> "${targets.size} targets detected."
                    targets.size == 1 -> "1 target detected."
                    else -> "No crackable M1/M2 handshake found."
                }
                _crackerStatus.value = if (targets.isEmpty()) {
                    "No crackable handshake found."
                } else {
                    "Review the detected targets and choose one wordlist."
                }
            }
        } catch (t: Throwable) {
            _crackerTargets.value = emptyList()
            _crackerTargetMessage.value = "PCAP analysis failed: ${t.message ?: t.javaClass.simpleName}"
            _crackerStatus.value = _crackerTargetMessage.value.orEmpty()
        } finally {
            _crackerTargetsLoading.value = false
        }
    }
}

internal fun MainViewModel.setCrackerImported22000Impl(uri: Uri, name: String?) {
    if (_crackerRunning.value) return
    this.clearPendingNrugochiCaptureImpl()
    _crackerSourceType.value = CrackerSourceType.IMPORTED_22000
    _crackerSelectedSession.value = null
    _crackerSelectedCapturedPath.value = null
    _crackerCustomPcapUri.value = null
    _crackerCustomPcapName.value = null
    _crackerImported22000Uri.value = uri
    _crackerImported22000Name.value = selectDocumentDisplayName(
        suppliedName = name,
        queriedName = queryDocumentDisplayNameImpl(uri),
        uriLastPathSegment = uri.lastPathSegment,
        fallback = "handshakes.22000",
    )
    _crackerManualMode.value = false
    _crackerManualSsid.value = ""
    _crackerTargets.value = emptyList()
    _crackerTargetMessage.value = null
    _crackerActiveSessionId.value = null
    _crackerTested.value = 0
    _crackerSpeed.value = 0.0
    _crackerResult.value = null
    _crackerProgressFraction.value = 0f
    _crackerProgressKnown.value = false
    _crackerCurrentTargetIndex.value = 0
    _crackerSelectedTargetCount.value = 0
    _crackerCurrentTargetSsid.value = ""
    _crackerStatus.value = "Reading .22000 file..."
    crackerLastPcapAnalysis = null

    scope.launch(Dispatchers.IO) {
        _crackerTargetsLoading.value = true
        try {
            val content = app.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                it.readText()
            } ?: throw IOException("Could not open the selected .22000 file.")
            if (_crackerImported22000Uri.value != uri) return@launch
            val (targets, message) = buildTargetsFrom22000(content)
            _crackerTargets.value = targets
            _crackerTargetMessage.value = message
            _crackerStatus.value = if (targets.isEmpty()) {
                "No crackable WPA*02 targets found."
            } else {
                "Review the imported targets and choose one wordlist."
            }
        } catch (t: Throwable) {
            _crackerTargets.value = emptyList()
            _crackerTargetMessage.value = "Import failed: ${t.message ?: t.javaClass.simpleName}"
            _crackerStatus.value = _crackerTargetMessage.value.orEmpty()
        } finally {
            _crackerTargetsLoading.value = false
        }
    }
}

internal fun MainViewModel.clearCrackerCustomPcapImpl() {
    if (_crackerRunning.value) return
    this.clearPendingNrugochiCaptureImpl()
    _crackerCustomPcapUri.value = null
    _crackerCustomPcapName.value = null
    _crackerSelectedCapturedPath.value = null
    _crackerImported22000Uri.value = null
    _crackerImported22000Name.value = null
    _crackerTargets.value = emptyList()
    _crackerTargetMessage.value = null
    _crackerManualMode.value = false
    _crackerManualSsid.value = ""
    _crackerActiveSessionId.value = null
    _crackerResult.value = null
    resetCrackerProgressState()
    crackerLastPcapAnalysis = null
    _crackerStatus.value = "Choose a capture or .22000 file."
}

internal fun MainViewModel.setCrackerManualModeImpl(enabled: Boolean) {
    if (_crackerRunning.value) return
    if (_crackerSourceType.value != CrackerSourceType.CUSTOM_PCAP) return
    _crackerManualMode.value = enabled
    _crackerActiveSessionId.value = null
    _crackerResult.value = null
    resetCrackerProgressState()
    val analysis = crackerLastPcapAnalysis
    if (analysis == null) return
    if (enabled) {
        val target = buildManualTarget(
            analysis = analysis,
            ssid = _crackerManualSsid.value,
            sourceLabel = _crackerCustomPcapName.value ?: "custom PCAP",
        )
        _crackerTargets.value = listOfNotNull(target)
        _crackerStatus.value = if (target == null) {
            "Enter a target SSID for manual single-target mode."
        } else {
            "Manual single-target mode ready."
        }
    } else {
        val targets = buildCrackerTargetsFromPcap(
            analysis = analysis,
            sourceLabel = _crackerCustomPcapName.value ?: "custom PCAP",
        )
        _crackerTargets.value = targets
        _crackerStatus.value = if (targets.isEmpty()) {
            "No SSID-tagged handshakes found."
        } else {
            "Review the detected targets and choose one wordlist."
        }
    }
}

internal fun MainViewModel.setCrackerManualSsidImpl(value: String) {
    if (_crackerRunning.value || !_crackerManualMode.value) return
    _crackerManualSsid.value = value.take(32)
    _crackerActiveSessionId.value = null
    _crackerResult.value = null
    resetCrackerProgressState()
    val analysis = crackerLastPcapAnalysis ?: return
    _crackerTargets.value = listOfNotNull(
        buildManualTarget(
            analysis = analysis,
            ssid = _crackerManualSsid.value,
            sourceLabel = _crackerCustomPcapName.value ?: "custom PCAP",
        ),
    )
}

internal fun MainViewModel.toggleCrackerTargetImpl(id: String, selected: Boolean) {
    if (_crackerRunning.value) return
    _crackerTargets.update { targets ->
        targets.map { target ->
            if (target.id == id) target.copy(selected = selected) else target
        }
    }
    _crackerActiveSessionId.value = null
}

internal fun MainViewModel.selectAllCrackerTargetsImpl(selected: Boolean) {
    if (_crackerRunning.value) return
    _crackerTargets.update { targets -> targets.map { it.copy(selected = selected) } }
    _crackerActiveSessionId.value = null
}

internal fun MainViewModel.setCrackerWordlistImpl(uri: Uri, name: String?) {
    if (_crackerRunning.value) return
    val cleanName = selectDocumentDisplayName(
        suppliedName = name,
        queriedName = queryDocumentDisplayNameImpl(uri),
        uriLastPathSegment = uri.lastPathSegment,
        fallback = "wordlist.txt",
    )
    _crackerWordlist.value = CrackerWordlistOption(uri, cleanName)
    _crackerTargets.update { targets ->
        targets.map {
            it.copy(
                tested = 0L,
                status = CrackerTargetStatus.PENDING,
                result = null,
            )
        }
    }
    _crackerActiveSessionId.value = null
    _crackerTested.value = 0
    _crackerSpeed.value = 0.0
    _crackerResult.value = null
    _crackerProgressFraction.value = 0f
    _crackerProgressKnown.value = false
    _crackerCurrentTargetIndex.value = 0
    _crackerSelectedTargetCount.value = 0
    _crackerCurrentTargetSsid.value = ""
    _crackerStatus.value = "Wordlist ready: $cleanName"
}

internal fun MainViewModel.clearCrackerWordlistImpl() {
    if (_crackerRunning.value) return
    _crackerWordlist.value = null
    _crackerActiveSessionId.value = null
    _crackerResult.value = null
    resetCrackerProgressState()
    _crackerStatus.value = "Choose one wordlist."
}

private fun MainViewModel.resetCrackerProgressState() {
    _crackerTested.value = 0L
    _crackerSpeed.value = 0.0
    _crackerProgressFraction.value = 0f
    _crackerProgressKnown.value = false
    _crackerCurrentTargetIndex.value = 0
    _crackerSelectedTargetCount.value = 0
    _crackerCurrentTargetSsid.value = ""
}

internal fun MainViewModel.pauseCrackerImpl() {
    if (!_crackerRunning.value || _crackerPaused.value) return
    crackerPauseRequested = true
    _crackerPaused.value = true
    _crackerStatus.value = "Paused at ${_crackerTested.value} tested candidate(s)."
    persistCrackerSessionImpl(CrackerSessionStatus.PAUSED)
    updateForegroundService()
}

internal fun MainViewModel.resumeCrackerImpl() {
    if (!_crackerRunning.value || !_crackerPaused.value) return
    crackerPauseRequested = false
    _crackerPaused.value = false
    _crackerStatus.value = "Resuming..."
    updateForegroundService()
}

internal fun MainViewModel.startCrackerImpl() {
    if (_crackerRunning.value) return
    val wordlist = _crackerWordlist.value ?: run {
        _crackerStatus.value = "Choose a wordlist first."
        return
    }
    val selected = _crackerTargets.value
        .filter {
            it.selected &&
                it.status != CrackerTargetStatus.CRACKED &&
                it.status != CrackerTargetStatus.EXHAUSTED
        }
        .map { target ->
            if (target.status == CrackerTargetStatus.STOPPED ||
                target.status == CrackerTargetStatus.ERROR
            ) {
                target.copy(
                    tested = (target.tested - CHECKPOINT_SAFETY_CANDIDATES)
                        .coerceAtLeast(0L),
                    status = CrackerTargetStatus.PENDING,
                )
            } else {
                target
            }
        }
    if (selected.isEmpty()) {
        _crackerStatus.value = "No pending selected targets. Choose a new wordlist or source."
        return
    }

    if (_crackerActiveSessionId.value == null) {
        _crackerActiveSessionId.value = System.currentTimeMillis().toString()
    }
    crackerStopRequested = false
    crackerPauseRequested = false
    _crackerRunning.value = true
    _crackerPaused.value = false
    _crackerResult.value = null
    _crackerSpeed.value = 0.0
    _crackerTested.value = 0
    _crackerSelectedTargetCount.value = selected.size
    _crackerCurrentTargetIndex.value = 0
    _crackerCurrentTargetSsid.value = selected.first().ssid
    _crackerProgressFraction.value = 0f
    _crackerProgressKnown.value = false
    _crackerStatus.value = "Preparing cracker..."
    persistCrackerSessionImpl(CrackerSessionStatus.PAUSED)
    updateForegroundService()

    val runningJob = scope.launch(Dispatchers.Default) {
        val job = coroutineContext[Job]
        var crackedCount = 0
        var lastNotificationAt = 0L
        var lastCheckpointAt = 0L
        try {
            selected.forEachIndexed { index, initialTarget ->
                if (job?.isActive != true || crackerStopRequested) return@forEachIndexed
                _crackerCurrentTargetIndex.value = index
                _crackerCurrentTargetSsid.value = initialTarget.ssid
                _crackerTested.value = initialTarget.tested
                _crackerStatus.value =
                    "Cracking ${initialTarget.ssid} (${index + 1}/${selected.size})"
                updateCrackerTarget(initialTarget.id) {
                    it.copy(status = CrackerTargetStatus.RUNNING, result = null)
                }

                val stream = openCrackerWordlist(wordlist.uri)
                try {
                    if (initialTarget.tested > 0) {
                        _crackerStatus.value =
                            "Resuming ${initialTarget.ssid} at ${initialTarget.tested} candidates..."
                        skipWordlistCandidates(stream.reader, initialTarget.tested)
                    }
                    val found = WpaCracker.crack(
                        handshake = initialTarget.handshake,
                        ssid = initialTarget.ssid,
                        wordlist = stream.reader,
                        initialTested = initialTarget.tested,
                        shouldStop = {
                            crackerStopRequested || job?.isActive != true
                        },
                        shouldPause = { crackerPauseRequested },
                        onProgress = { progress ->
                            val fraction = if (stream.totalBytes > 0) {
                                (stream.countingInput.bytesRead.get().toDouble() /
                                    stream.totalBytes.toDouble()).coerceIn(0.0, 1.0)
                            } else {
                                null
                            }
                            _crackerProgressKnown.value = fraction != null
                            if (fraction != null) {
                                _crackerProgressFraction.value =
                                    ((index + fraction) / selected.size).toFloat()
                            }
                            _crackerTested.value = progress.tested
                            _crackerSpeed.value = progress.candidatesPerSecond
                            updateCrackerTarget(initialTarget.id) {
                                it.copy(
                                    tested = progress.tested,
                                    status = CrackerTargetStatus.RUNNING,
                                )
                            }
                            val now = System.currentTimeMillis()
                            if (now - lastNotificationAt >= 1_000L) {
                                lastNotificationAt = now
                                updateForegroundService()
                            }
                            if (now - lastCheckpointAt >= 5_000L) {
                                lastCheckpointAt = now
                                persistCrackerSessionImpl(CrackerSessionStatus.PAUSED)
                            }
                        },
                    )

                    if (job?.isActive != true || crackerStopRequested) {
                        updateCrackerTarget(initialTarget.id) {
                            it.copy(status = CrackerTargetStatus.STOPPED)
                        }
                        return@forEachIndexed
                    }

                    if (found != null) {
                        crackedCount++
                        _crackerResult.value = found
                        updateCrackerTarget(initialTarget.id) {
                            it.copy(
                                status = CrackerTargetStatus.CRACKED,
                                tested = _crackerTested.value,
                                result = found,
                            )
                        }
                        _crackerStatus.value =
                            "Password found for ${initialTarget.ssid}: $found"
                        saveCrackerCredentialResult(initialTarget, found)
                        if (autoHandshake.pendingCrackerCapture?.bssid
                                ?.equals(initialTarget.bssid, ignoreCase = true) == true
                        ) {
                            persistNrugochiCrackResultImpl(found)
                        }
                    } else {
                        updateCrackerTarget(initialTarget.id) {
                            it.copy(
                                status = CrackerTargetStatus.EXHAUSTED,
                                tested = _crackerTested.value,
                            )
                        }
                        _crackerStatus.value =
                            "Wordlist exhausted for ${initialTarget.ssid}."
                    }
                    persistCrackerSessionImpl(CrackerSessionStatus.PAUSED)
                } finally {
                    runCatching { stream.reader.close() }
                }
            }

            if (!crackerStopRequested && job?.isActive == true) {
                if (autoHandshake.pendingCrackerCapture != null) {
                    markNrugochiCrackFailedImpl()
                }
                _crackerStatus.value =
                    "Cracker complete: $crackedCount password(s) found across ${selected.size} target(s)."
                persistCrackerSessionImpl(CrackerSessionStatus.COMPLETED)
                com.swp81x.nrsuite.core.notifications.NrSuiteNotifications.notifyTaskAlert(
                    app,
                    "WPA cracker complete",
                    _crackerStatus.value,
                    smallIconRes = R.drawable.ic_stat_cracker,
                )
            }
        } catch (cancelled: CancellationException) {
            _crackerStatus.value = "Cracker stopped."
            _crackerTargets.update { targets ->
                targets.map {
                    if (it.status == CrackerTargetStatus.RUNNING) {
                        it.copy(status = CrackerTargetStatus.STOPPED)
                    } else {
                        it
                    }
                }
            }
            persistCrackerSessionImpl(CrackerSessionStatus.STOPPED)
            throw cancelled
        } catch (t: Throwable) {
            _crackerStatus.value = "Cracker failed: ${t.message ?: t.javaClass.simpleName}"
            appendLog(_crackerStatus.value, level = LogLevel.ERROR)
            _crackerTargets.update { targets ->
                targets.map {
                    if (it.status == CrackerTargetStatus.RUNNING) {
                        it.copy(status = CrackerTargetStatus.ERROR)
                    } else {
                        it
                    }
                }
            }
            persistCrackerSessionImpl(CrackerSessionStatus.INTERRUPTED)
        } finally {
            crackerStopRequested = false
            crackerPauseRequested = false
            _crackerPaused.value = false
            _crackerRunning.value = false
            crackerJob = null
            resetCrackerProgressState()
            updateForegroundService()
        }
    }
    crackerJob = runningJob
}

internal fun MainViewModel.stopCrackerImpl() {
    if (!_crackerRunning.value) return
    crackerStopRequested = true
    crackerPauseRequested = false
    _crackerPaused.value = false
    _crackerStatus.value = "Stopping..."
    persistCrackerSessionImpl(CrackerSessionStatus.STOPPED)
    crackerJob?.cancel()
    updateForegroundService()
}

private fun MainViewModel.updateCrackerTarget(
    id: String,
    transform: (CrackerTargetSnapshot) -> CrackerTargetSnapshot,
) {
    _crackerTargets.update { targets ->
        targets.map { if (it.id == id) transform(it) else it }
    }
}

private suspend fun MainViewModel.openCrackerWordlist(uri: Uri): CrackerWordlistStream =
    withContext(Dispatchers.IO) {
        val totalBytes = runCatching {
            app.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            } ?: 0L
        }.getOrDefault(0L)
        val input = app.contentResolver.openInputStream(uri)
            ?: throw IOException("Could not open the selected wordlist.")
        val counting = CountingInputStream(input)
        CrackerWordlistStream(
            reader = counting.bufferedReader(),
            countingInput = counting,
            totalBytes = totalBytes,
        )
    }

private suspend fun skipWordlistCandidates(reader: BufferedReader, count: Long) =
    withContext(Dispatchers.IO) {
        var skipped = 0L
        while (skipped < count) {
            val line = reader.readLine() ?: break
            if (line.isNotEmpty()) skipped++
        }
    }

private fun MainViewModel.saveCrackerCredentialResult(
    target: CrackerTargetSnapshot,
    password: String,
) {
    val savedSessionId = _crackerSelectedSession.value?.id
    if (savedSessionId != null) {
        persistCrackedCredentialImpl(savedSessionId, password)
        return
    }
    val sourceUri = _crackerCustomPcapUri.value ?: _crackerImported22000Uri.value ?: return
    val sourceName = _crackerCustomPcapName.value
        ?: _crackerImported22000Name.value
        ?: "capture"
    scope.launch(Dispatchers.IO) {
        saveCrackedCustomSessionImpl(
            sourceUri = sourceUri,
            displayName = sourceName,
            ssid = target.ssid,
            bssid = target.handshake.bssid,
            password = password,
        )
    }
}

internal fun MainViewModel.persistCrackerSessionImpl(status: CrackerSessionStatus) {
    val wordlist = _crackerWordlist.value ?: return
    val activeId = _crackerActiveSessionId.value ?: return
    val targets = _crackerTargets.value
    if (targets.isEmpty()) return

    val sourceType = _crackerSourceType.value
    val sourceLabel = when (sourceType) {
        CrackerSourceType.SAVED_CAPTURE ->
            _crackerSelectedSession.value?.ssid?.ifBlank { "Saved capture" } ?: "Saved capture"
        CrackerSourceType.CUSTOM_PCAP -> _crackerCustomPcapName.value ?: "custom PCAP"
        CrackerSourceType.IMPORTED_22000 -> _crackerImported22000Name.value ?: "handshakes.22000"
    }
    val sourceUri = when (sourceType) {
        CrackerSourceType.CUSTOM_PCAP -> _crackerCustomPcapUri.value?.toString()
        CrackerSourceType.IMPORTED_22000 -> _crackerImported22000Uri.value?.toString()
        CrackerSourceType.SAVED_CAPTURE -> _crackerSelectedCapturedPath.value
            ?.let { File(it).toURI().toString() }
    }
    val safeTargets = targets.map { target ->
        val checkpoint = when (target.status) {
            CrackerTargetStatus.PENDING,
            CrackerTargetStatus.RUNNING,
            CrackerTargetStatus.STOPPED,
            CrackerTargetStatus.ERROR,
            -> (target.tested - CHECKPOINT_SAFETY_CANDIDATES).coerceAtLeast(0L)
            CrackerTargetStatus.CRACKED,
            CrackerTargetStatus.EXHAUSTED,
            -> target.tested
        }
        target.copy(tested = checkpoint)
    }
    val snapshot = CrackerSessionSnapshot(
        id = activeId,
        sourceType = sourceType,
        sourceLabel = sourceLabel,
        sourceUri = sourceUri,
        savedSessionId = _crackerSelectedSession.value?.id,
        wordlistUri = wordlist.uri.toString(),
        wordlistName = wordlist.name,
        targets = safeTargets,
        currentTargetIndex = _crackerCurrentTargetIndex.value,
        updatedAt = System.currentTimeMillis(),
        status = status,
    )
    _crackerSessions.update { sessions ->
        (sessions.filterNot { it.id == snapshot.id } + snapshot)
            .sortedByDescending { it.updatedAt }
    }
    scope.launch(Dispatchers.IO) {
        runCatching { crackerSessionStore.saveSessions(_crackerSessions.value) }
    }
}

internal fun MainViewModel.resumeCrackerSessionImpl(id: String) {
    if (_crackerRunning.value) return
    val session = _crackerSessions.value.firstOrNull { it.id == id } ?: return
    _crackerSourceType.value = session.sourceType
    _crackerSelectedSession.value = session.savedSessionId?.let { savedId ->
        _credentialSessions.value.firstOrNull { it.id == savedId }
    }
    _crackerSelectedCapturedPath.value = session.sourceUri
        ?.takeIf { session.sourceType == CrackerSourceType.SAVED_CAPTURE }
        ?.let { runCatching { java.net.URI(it).path }.getOrNull() }
    if (session.sourceType == CrackerSourceType.SAVED_CAPTURE) {
        _crackerSelectedCapturedPath.value?.let { path ->
            val file = File(path)
            if (file.exists()) {
                _crackerCustomPcapName.value = file.name
                _crackerCustomPcapUri.value = runCatching {
                    FileProvider.getUriForFile(
                        app,
                        "${app.packageName}.fileprovider",
                        file,
                    )
                }.getOrNull()
            }
        }
    }
    if (session.sourceType == CrackerSourceType.CUSTOM_PCAP) {
        _crackerCustomPcapUri.value = session.sourceUri?.let(Uri::parse)
        _crackerCustomPcapName.value = session.sourceLabel
    }
    if (session.sourceType == CrackerSourceType.IMPORTED_22000) {
        _crackerImported22000Uri.value = session.sourceUri?.let(Uri::parse)
        _crackerImported22000Name.value = session.sourceLabel
    }
    _crackerWordlist.value = CrackerWordlistOption(
        uri = Uri.parse(session.wordlistUri),
        name = session.wordlistName,
    )
    _crackerTargets.value = session.targets.map {
        if (it.status == CrackerTargetStatus.RUNNING) {
            it.copy(status = CrackerTargetStatus.PENDING)
        } else {
            it
        }
    }
    _crackerActiveSessionId.value = session.id
    _crackerCurrentTargetIndex.value = session.currentTargetIndex
    _crackerTested.value = session.targets.getOrNull(session.currentTargetIndex)?.tested ?: 0
    _crackerSpeed.value = 0.0
    _crackerResult.value = null
    _crackerProgressFraction.value = 0f
    _crackerProgressKnown.value = false
    _crackerSelectedTargetCount.value = session.targets.count { it.selected }
    _crackerCurrentTargetSsid.value =
        session.targets.getOrNull(session.currentTargetIndex)?.ssid.orEmpty()
    _crackerManualMode.value = false
    _crackerManualSsid.value = ""
    _crackerStatus.value = "Session loaded. Press Start to resume."
    _crackerTargetMessage.value = "Resumed session: ${session.sourceLabel}"
}

internal fun MainViewModel.deleteCrackerSessionImpl(id: String) {
    if (_crackerRunning.value && _crackerActiveSessionId.value == id) return
    _crackerSessions.update { sessions -> sessions.filterNot { it.id == id } }
    if (_crackerActiveSessionId.value == id) {
        _crackerActiveSessionId.value = null
    }
    scope.launch(Dispatchers.IO) {
        runCatching { crackerSessionStore.saveSessions(_crackerSessions.value) }
    }
}

private const val CHECKPOINT_SAFETY_CANDIDATES = 128L
