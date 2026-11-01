package com.swp81x.nrsuite

import android.net.Uri
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.wps.WpsPixieCapturedMaterial
import com.swp81x.nrsuite.core.wps.WpsPixieCracker
import com.swp81x.nrsuite.core.wps.WpsPixieMaterial
import com.swp81x.nrsuite.core.wps.WpsPixieMode
import com.swp81x.nrsuite.core.wps.WpsPixiePcapParser
import com.swp81x.nrsuite.core.wps.WpsPixieResult
import com.swp81x.nrsuite.core.wps.WpsPinRecord
import com.swp81x.nrsuite.core.wps.WpsPixieSerialParser
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

data class WpsPixieUiState(
    val sourceName: String? = null,
    val sourceKind: String? = null,
    val status: String = "Select a WPS PCAP or import a firmware serial log.",
    val validating: Boolean = false,
    val captured: WpsPixieCapturedMaterial? = null,
    val authKeyHex: String = "",
    val running: Boolean = false,
    val progressText: String = "",
    val result: WpsPixieResult? = null,
    val error: String? = null,
)

internal fun MainViewModel.importWpsPixiePcapImpl(uri: Uri, name: String?) {
    val state = _wpsPixie.value
    if (state.running || state.validating) return

    _wpsPixie.update {
        it.copy(
            sourceName = name ?: uri.lastPathSegment ?: "capture.pcap",
            sourceKind = "PCAP",
            validating = true,
            status = "Parsing WPS M1/M2/M3 material from PCAP...",
            captured = null,
            result = null,
            error = null,
        )
    }

    scope.launch(Dispatchers.IO) {
        try {
            val input = app.contentResolver.openInputStream(uri)
                ?: throw IOException("Could not open the selected PCAP.")
            val captured = input.use { WpsPixiePcapParser.parse(it) }
            _wpsPixie.update { current ->
                if (captured == null) {
                    current.copy(
                        validating = false,
                        captured = null,
                        status = "No complete WPS M1/M2/M3 exchange found in this PCAP.",
                        error = null,
                    )
                } else {
                    current.copy(
                        validating = false,
                        captured = captured,
                        status = "WPS material loaded. AuthKey is still required.",
                        error = null,
                    )
                }
            }
            appendLog(
                if (captured == null) {
                    "WPS Pixie: PCAP contained no complete M1/M2/M3 tuple."
                } else {
                    "WPS Pixie: parsed M1/M2/M3 from ${_wpsPixie.value.sourceName}."
                },
                level = if (captured == null) LogLevel.ERROR else LogLevel.INFO,
            )
        } catch (error: Throwable) {
            _wpsPixie.update {
                it.copy(
                    validating = false,
                    status = "PCAP import failed.",
                    error = error.message ?: error.javaClass.simpleName,
                )
            }
            appendLog(
                "WPS Pixie PCAP import failed: ${error.message ?: error.javaClass.simpleName}",
                level = LogLevel.ERROR,
            )
        }
    }
}

internal fun MainViewModel.importWpsPixieSerialLogImpl(uri: Uri, name: String?) {
    val state = _wpsPixie.value
    if (state.running || state.validating) return

    _wpsPixie.update {
        it.copy(
            sourceName = name ?: uri.lastPathSegment ?: "serial.log",
            sourceKind = "Serial log",
            validating = true,
            status = "Parsing WPS material and AuthKey from serial log...",
            result = null,
            error = null,
        )
    }

    scope.launch(Dispatchers.IO) {
        try {
            val text = app.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: throw IOException("Could not open the selected serial log.")
            val parsed = WpsPixieSerialParser.parse(text)
            val material = parsed.material
            val authKeyHex = parsed.authKey?.joinToString("") { "%02x".format(it) }
            _wpsPixie.update { current ->
                current.copy(
                    validating = false,
                    captured = material ?: current.captured,
                    authKeyHex = authKeyHex ?: current.authKeyHex,
                    status = when {
                        material != null && authKeyHex != null ->
                            "Serial material and AuthKey loaded. Ready to crack."
                        material != null ->
                            "WPS material loaded. Paste the AuthKey to continue."
                        authKeyHex != null ->
                            "AuthKey loaded. Select a PCAP to load M1/M2/M3 material."
                        else ->
                            "No WPS Pixie material or AuthKey found in the serial log."
                    },
                    error = null,
                )
            }
            appendLog(
                "WPS Pixie: serial log imported (material=${material != null}, authKey=${authKeyHex != null}).",
            )
        } catch (error: Throwable) {
            _wpsPixie.update {
                it.copy(
                    validating = false,
                    status = "Serial log import failed.",
                    error = error.message ?: error.javaClass.simpleName,
                )
            }
            appendLog(
                "WPS Pixie serial import failed: ${error.message ?: error.javaClass.simpleName}",
                level = LogLevel.ERROR,
            )
        }
    }
}

internal fun MainViewModel.setWpsPixieAuthKeyImpl(value: String) {
    if (_wpsPixie.value.running) return
    val sanitized = value
        .filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
        .take(64)
    _wpsPixie.update {
        it.copy(
            authKeyHex = sanitized,
            status = when {
                it.captured == null -> it.status
                sanitized.length == 64 -> "Ready to crack."
                else -> "AuthKey must be 64 hex characters."
            },
            error = null,
        )
    }
}

internal fun MainViewModel.startWpsPixieCrackImpl() {
    val current = _wpsPixie.value
    if (current.running) return
    val captured = current.captured
    if (captured == null) {
        _wpsPixie.update { it.copy(error = "Select a WPS PCAP or import a serial log first.") }
        return
    }
    val authKey = runCatching { WpsPixieMaterial.hexToBytes(current.authKeyHex) }.getOrNull()
    if (authKey == null || authKey.size != WpsPixieMaterial.HASH_LENGTH) {
        _wpsPixie.update { it.copy(error = "AuthKey must be exactly 64 hex characters.") }
        return
    }

    val material = captured.withAuthKey(authKey)
    wpsPixieJob?.cancel()
    _wpsPixie.update {
        it.copy(
            running = true,
            progressText = "Starting search...",
            result = null,
            error = null,
            status = "Searching Pixiewps PRNG families...",
        )
    }

    wpsPixieJob = scope.launch(Dispatchers.Default) {
        try {
            val result = runInterruptible {
                WpsPixieCracker.crack(
                    material = material,
                    shouldStop = { Thread.currentThread().isInterrupted },
                    onProgress = { progress ->
                        _wpsPixie.update {
                            it.copy(
                                progressText = "${progress.phase.label}: ${progress.tested}/${progress.total}",
                            )
                        }
                    },
                )
            }
            _wpsPixie.update { state ->
                if (result == null) {
                    state.copy(
                        running = false,
                        progressText = "",
                        status = "PIN search finished without a match.",
                        error = "Check the AuthKey. RTL/eCos window searches can miss nonces outside their supported ranges.",
                    )
                } else {
                    state.copy(
                        running = false,
                        progressText = "",
                        result = result,
                        status = "WPS PIN recovered: ${result.pin}",
                        error = null,
                    )
                }
            }
            if (result != null) {
                appendLog(
                    "WPS Pixie: PIN ${result.pin} recovered via ${result.mode.label}.",
                    level = LogLevel.SUCCESS,
                )
            } else {
                appendLog("WPS Pixie: PIN search found no match.", level = LogLevel.ERROR)
            }
        } catch (cancelled: CancellationException) {
            _wpsPixie.update {
                it.copy(running = false, progressText = "", status = "PIN search stopped.")
            }
            throw cancelled
        } catch (error: Throwable) {
            _wpsPixie.update {
                it.copy(
                    running = false,
                    progressText = "",
                    status = "PIN search failed.",
                    error = error.message ?: error.javaClass.simpleName,
                )
            }
            appendLog(
                "WPS Pixie crack failed: ${error.message ?: error.javaClass.simpleName}",
                level = LogLevel.ERROR,
            )
        }
    }
}

internal fun MainViewModel.stopWpsPixieCrackImpl() {
    wpsPixieJob?.cancel()
    wpsPixieJob = null
    _wpsPixie.update {
        it.copy(running = false, progressText = "", status = "PIN search stopped.")
    }
}

internal fun MainViewModel.clearWpsPixieMaterialImpl() {
    if (_wpsPixie.value.running) return
    _wpsPixie.value = WpsPixieUiState()
}

internal fun MainViewModel.loadWpsPinsImpl() {
    scope.launch(Dispatchers.IO) {
        _wpsPins.value = runCatching { wpsPinStore.loadPins() }
            .getOrDefault(emptyList())
            .sortedByDescending { it.updatedAt }
    }
}

internal fun MainViewModel.findWpsPinImpl(bssid: String): WpsPinRecord? =
    _wpsPins.value.firstOrNull { it.bssid.equals(bssid, ignoreCase = true) }

internal fun MainViewModel.upsertWpsPinImpl(record: WpsPinRecord) {
    scope.launch(Dispatchers.IO) {
        _wpsPins.value = wpsPinStore.upsert(record).sortedByDescending { it.updatedAt }
    }
}

internal fun MainViewModel.deleteWpsPinImpl(bssid: String) {
    scope.launch(Dispatchers.IO) {
        _wpsPins.value = wpsPinStore.delete(bssid).sortedByDescending { it.updatedAt }
    }
}
