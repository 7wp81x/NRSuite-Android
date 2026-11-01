package com.swp81x.nrsuite

import android.net.Uri
import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.pcap.PcapReader
import com.swp81x.nrsuite.core.wpa.Pcap22000Candidate
import com.swp81x.nrsuite.core.wpa.Pcap22000Export
import com.swp81x.nrsuite.core.wpa.analyzeWpaPcap
import com.swp81x.nrsuite.core.wpa.buildPcap22000Export
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Pcap22000Input(
    val uri: Uri,
    val name: String,
)

data class Pcap22000UiState(
    val inputs: List<Pcap22000Input> = emptyList(),
    val candidates: List<Pcap22000Candidate> = emptyList(),
    val ssidOverrides: Map<String, String> = emptyMap(),
    val exportPreview: Pcap22000Export? = null,
    val analyzing: Boolean = false,
    val summary: String? = null,
    val error: String? = null,
    val lastExportCount: Int? = null,
    val lastExportName: String? = null,
)

internal fun MainViewModel.addPcap22000InputsImpl(uris: List<Uri>) {
    if (_pcap22000State.value.analyzing) return
    val existing = _pcap22000State.value.inputs.map { it.uri }.toSet()
    val additions = uris
        .filterNot { it in existing }
        .map { uri ->
            Pcap22000Input(
                uri = uri,
                name = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                    ?: "capture-${System.currentTimeMillis()}.pcap",
            )
        }
    if (additions.isEmpty()) return
    _pcap22000State.update {
        it.copy(
            inputs = it.inputs + additions,
            error = null,
            lastExportCount = null,
            lastExportName = null,
        )
    }
    refreshPcap22000AnalysisImpl()
}

internal fun MainViewModel.removePcap22000InputImpl(index: Int) {
    if (_pcap22000State.value.analyzing) return
    val current = _pcap22000State.value.inputs
    if (index !in current.indices) return
    _pcap22000State.update {
        it.copy(
            inputs = current.filterIndexed { itemIndex, _ -> itemIndex != index },
            error = null,
            lastExportCount = null,
            lastExportName = null,
        )
    }
    refreshPcap22000AnalysisImpl()
}

internal fun MainViewModel.clearPcap22000InputsImpl() {
    if (_pcap22000State.value.analyzing) return
    _pcap22000State.value = Pcap22000UiState()
}

internal fun MainViewModel.setPcap22000SsidOverrideImpl(bssid: String, ssid: String) {
    val cleanBssid = bssid.trim().uppercase()
    val cleanSsid = ssid.trim().take(32)
    if (cleanBssid.isBlank() || cleanSsid.isBlank()) return
    _pcap22000State.update {
        it.copy(
            ssidOverrides = it.ssidOverrides + (cleanBssid to cleanSsid),
            lastExportCount = null,
            lastExportName = null,
        )
    }
    recomputePcap22000PreviewImpl()
}

internal fun MainViewModel.removePcap22000SsidOverrideImpl(bssid: String) {
    val cleanBssid = bssid.trim().uppercase()
    _pcap22000State.update {
        it.copy(
            ssidOverrides = it.ssidOverrides - cleanBssid,
            lastExportCount = null,
            lastExportName = null,
        )
    }
    recomputePcap22000PreviewImpl()
}

private fun MainViewModel.refreshPcap22000AnalysisImpl() {
    val inputs = _pcap22000State.value.inputs
    if (inputs.isEmpty()) {
        _pcap22000State.value = Pcap22000UiState()
        return
    }
    pcap22000Job?.cancel()
    pcap22000Job = scope.launch(Dispatchers.IO) {
        val runningJob = coroutineContext[Job]
        _pcap22000State.update { it.copy(analyzing = true, error = null, summary = "Analyzing PCAP files...") }
        try {
            val allCandidates = mutableListOf<Pcap22000Candidate>()
            val failures = mutableListOf<String>()
            inputs.forEach { input ->
                if (runningJob?.isActive != true) return@launch
                try {
                    val stream = app.contentResolver.openInputStream(input.uri)
                        ?: throw IOException("Could not open ${input.name}")
                    val analysis = analyzeWpaPcap(PcapReader(stream))
                    allCandidates.addAll(
                        analysis.candidates.map { candidate ->
                            Pcap22000Candidate(
                                sourceName = input.name,
                                handshake = candidate.handshake,
                                detectedSsid = candidate.ssid,
                            )
                        },
                    )
                } catch (t: Throwable) {
                    failures += "${input.name}: ${t.message ?: t.javaClass.simpleName}"
                }
            }
            _pcap22000State.update {
                it.copy(
                    candidates = allCandidates,
                    analyzing = false,
                    error = failures.takeIf { list -> list.isNotEmpty() }?.joinToString("\n"),
                )
            }
            recomputePcap22000PreviewImpl()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            _pcap22000State.update {
                it.copy(
                    analyzing = false,
                    exportPreview = null,
                    summary = null,
                    error = t.message ?: t.javaClass.simpleName,
                )
            }
        } finally {
            pcap22000Job = null
        }
    }
}

private fun MainViewModel.recomputePcap22000PreviewImpl() {
    val state = _pcap22000State.value
    if (state.inputs.isEmpty()) return
    val result = buildPcap22000Export(
        candidates = state.candidates,
        ssidOverrides = state.ssidOverrides,
    )
    val summary = buildString {
        append("${state.candidates.size} handshake candidate(s), ")
        append("${result.lines.size} exportable .22000 line(s)")
        if (result.duplicatesRemoved > 0) append(", ${result.duplicatesRemoved} duplicate(s) removed")
        if (result.missingSsid > 0) append(", ${result.missingSsid} missing SSID")
        if (result.unsupported > 0) append(", ${result.unsupported} unsupported")
        append('.')
    }
    _pcap22000State.update { it.copy(exportPreview = result, summary = summary) }
}

internal fun MainViewModel.exportPcap22000Impl(uri: Uri) {
    val state = _pcap22000State.value
    val preview = state.exportPreview ?: run {
        _pcap22000State.update { it.copy(error = "No analyzed PCAP candidates are ready for export.") }
        return
    }
    if (preview.lines.isEmpty()) {
        _pcap22000State.update { it.copy(error = "No exportable handshakes. Resolve missing SSIDs first.") }
        return
    }
    scope.launch(Dispatchers.IO) {
        try {
            app.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                preview.lines.forEach { line ->
                    writer.write(line)
                    writer.newLine()
                }
            } ?: throw IOException("Could not open the export destination.")
            val message = "Exported ${preview.lines.size} handshake(s) successfully."
            _pcap22000State.update {
                it.copy(
                    error = null,
                    lastExportCount = preview.lines.size,
                    lastExportName = uri.lastPathSegment?.substringAfterLast('/'),
                )
            }
            appendLog(message, level = LogLevel.SUCCESS)
            addHistory("pcap22000", message, HistoryLevel.SUCCESS)
            _toastMessage.value = message
        } catch (t: Throwable) {
            val message = "PCAP .22000 export failed: ${t.message ?: t.javaClass.simpleName}"
            _pcap22000State.update { it.copy(error = message) }
            appendLog(message, level = LogLevel.ERROR)
            _toastMessage.value = message
        }
    }
}
