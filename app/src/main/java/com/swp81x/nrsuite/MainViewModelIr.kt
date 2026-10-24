package com.swp81x.nrsuite

import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.swp81x.nrsuite.core.ir.IrDatabaseDownloader
import com.swp81x.nrsuite.core.ir.IrCommonButton
import com.swp81x.nrsuite.core.ir.IrDatabaseMetadata
import com.swp81x.nrsuite.core.ir.IrDatabasePhase
import com.swp81x.nrsuite.core.ir.IrDatabaseUiState
import com.swp81x.nrsuite.core.ir.IrCommonButtonsState
import com.swp81x.nrsuite.core.ir.IrEncodeResult
import com.swp81x.nrsuite.core.ir.IrFileParser
import com.swp81x.nrsuite.core.ir.IrJammerPhase
import com.swp81x.nrsuite.core.ir.IrProtocolEncoder
import com.swp81x.nrsuite.core.ir.IrReceivedSignal
import com.swp81x.nrsuite.core.ir.IrRemote
import com.swp81x.nrsuite.core.ir.IrRemoteSource
import com.swp81x.nrsuite.core.ir.IrRemoteSummary
import com.swp81x.nrsuite.core.ir.IrScanProgress
import com.swp81x.nrsuite.core.ir.IrSearchCandidate
import com.swp81x.nrsuite.core.ir.IrSearchRunState
import com.swp81x.nrsuite.core.ir.IrSearchUiState
import com.swp81x.nrsuite.core.ir.IrSignal
import com.swp81x.nrsuite.core.ir.IrTransmitBackend
import com.swp81x.nrsuite.core.ir.IrZipExtractionResult
import com.swp81x.nrsuite.core.notifications.NrSuiteNotifications
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.service.IrDatabaseForegroundService
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val MAX_IR_FILE_BYTES = 8L * 1024L * 1024L
private const val MIN_IR_CAPTURE_TIMINGS = 4
private const val MIN_IR_CAPTURE_DURATION_US = 1_000L
private const val IR_REPEAT_SUPPRESSION_MS = 700L
private const val IR_ROOT_DIRECTORY = "IR"
private const val IR_LIBRARY_DIRECTORY = "Library"
private const val IR_RECEIVER_DIRECTORY = "Receiver"
private const val IR_DATABASE_DIRECTORY = "Database"
private const val IR_IMPORTED_CATEGORY = "Imported"
private const val IR_RECEIVED_CATEGORY = "Received"
private const val PREF_IR_TRANSMIT_BACKEND = "ir_transmit_backend"
private const val PREF_IR_LOOP_INTERVAL_MS = "ir_loop_interval_ms"
private const val PREF_IR_FINDER_CATEGORY = "ir_finder_category"
private const val PREF_IR_FINDER_BRAND = "ir_finder_brand"
private const val DEFAULT_IR_LOOP_INTERVAL_MS = 250

internal fun MainViewModel.loadIrLibraryImpl() {
    _irRemotes.value = irRemoteIndexStore.load().sortedWith(
        compareByDescending<IrRemoteSummary> { it.favorite }
            .thenBy { it.name.lowercase() },
    )
    _irDatabaseMetadata.value = irDatabaseMetadataStore.load()
}

internal fun MainViewModel.loadIrPreferencesImpl() {
    val backend = runCatching {
        IrTransmitBackend.valueOf(
            preferences.getString(PREF_IR_TRANSMIT_BACKEND, "AUTO")
                ?.uppercase()
                ?: "AUTO",
        )
    }.getOrDefault(IrTransmitBackend.AUTO)
    _irTransmitBackend.value = backend
    _irLoopIntervalMs.value = preferences
        .getInt(PREF_IR_LOOP_INTERVAL_MS, DEFAULT_IR_LOOP_INTERVAL_MS)
        .coerceIn(100, 2_000)
    _irFinderCategory.value = preferences.getString(PREF_IR_FINDER_CATEGORY, "").orEmpty()
    _irFinderBrand.value = preferences.getString(PREF_IR_FINDER_BRAND, "All brands")
        .orEmpty()
        .ifBlank { "All brands" }
}

internal fun MainViewModel.setIrFinderSelectionImpl(category: String, brand: String) {
    val cleanCategory = category.trim()
    val cleanBrand = brand.trim().ifBlank { "All brands" }
    _irFinderCategory.value = cleanCategory
    _irFinderBrand.value = cleanBrand
    preferences.edit()
        .putString(PREF_IR_FINDER_CATEGORY, cleanCategory)
        .putString(PREF_IR_FINDER_BRAND, cleanBrand)
        .apply()
}

internal fun MainViewModel.observeIrSearchNotificationsImpl() {
    scope.launch {
        _irSearchState.collect { state ->
            val candidate = state.currentCandidate
            when (state.runState) {
                IrSearchRunState.RUNNING, IrSearchRunState.PAUSED -> {
                    lastIrSearchNotificationState = state.runState
                    val progress = if (state.candidates.isNotEmpty()) {
                        (((state.currentIndex + 1) * 100) / state.candidates.size).coerceIn(0, 100)
                    } else {
                        null
                    }
                    val mode = when {
                        state.loopingCurrent -> "Loop"
                        state.autoStepping -> "Auto"
                        else -> "Manual"
                    }
                    val detail = candidate?.let {
                        "Button ${state.currentIndex + 1}/${state.candidates.size} · " +
                            "${it.remoteName} · ${displayIrName(it.buttonName)} · $mode"
                    } ?: "Preparing candidates..."
                    NrSuiteNotifications.notifyIrSearchProgress(
                        context = app,
                        title = if (state.runState == IrSearchRunState.PAUSED) {
                            "IR Finder paused"
                        } else {
                            "IR Finder running"
                        },
                        text = detail,
                        progressPercent = progress,
                    )
                }
                IrSearchRunState.COMPLETED,
                IrSearchRunState.STOPPED,
                IrSearchRunState.ERROR -> {
                    if (lastIrSearchNotificationState == IrSearchRunState.RUNNING ||
                        lastIrSearchNotificationState == IrSearchRunState.PAUSED
                    ) {
                        val title = when (state.runState) {
                            IrSearchRunState.COMPLETED -> "IR Finder finished"
                            IrSearchRunState.ERROR -> "IR Finder error"
                            else -> "IR Finder stopped"
                        }
                        NrSuiteNotifications.notifyIrSearchResult(
                            context = app,
                            title = title,
                            text = state.errorMessage ?: state.message ?: title,
                        )
                    }
                    lastIrSearchNotificationState = state.runState
                }
                IrSearchRunState.IDLE -> {
                    lastIrSearchNotificationState = state.runState
                }
            }
        }
    }
}

internal fun MainViewModel.refreshIrCapabilitiesImpl() {
    _irCapabilities.value = irPhoneTransmitter.capabilities()
    Log.d(
        "IrRemote",
        "hasEmitter=${_irCapabilities.value.hasEmitter} " +
            "carrierRanges=${_irCapabilities.value.carrierRanges}",
    )
}

internal fun MainViewModel.importIrFileImpl(uri: Uri) {
    if (_irImporting.value) return
    val rootUri = _exportDirectory.value
    if (rootUri == null) {
        _irErrorMessage.value = "Set the NRSuite root directory before importing IR files."
        return
    }
    _irImporting.value = true
    _irErrorMessage.value = null
    scope.launch {
        try {
            val displayName = queryDocumentDisplayNameImpl(uri)
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: "imported.ir"
            val size = queryDocumentSizeImpl(uri)
            if (size > MAX_IR_FILE_BYTES) {
                throw IllegalArgumentException("IR file is larger than 8 MB.")
            }
            val content = withContext(Dispatchers.IO) {
                app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalArgumentException("Could not open the selected .ir file.")
            }
            if (content.length > MAX_IR_FILE_BYTES.toInt()) {
                throw IllegalArgumentException("IR file is larger than 8 MB.")
            }
            val parsed = IrFileParser.parse(content)
            if (parsed.isEmpty()) {
                throw IllegalArgumentException("No valid IR signals were found in this file.")
            }

            val safeFileName = sanitizeIrFileName(displayName)
            val remoteName = safeFileName.substringBeforeLast('.').ifBlank { safeFileName }
            val remote = withContext(Dispatchers.IO) {
                writeImportedIrFile(rootUri, safeFileName, content)
            } ?: throw IllegalArgumentException("Could not copy the .ir file into NRSuite/IR/Library.")
            val storedFileName = remote.name ?: safeFileName
            val relativePath = "$IR_ROOT_DIRECTORY/$IR_LIBRARY_DIRECTORY/$storedFileName"
            val remoteId = "imported:$relativePath"
            val existing = _irRemotes.value.firstOrNull { it.id == remoteId }

            val protocols = parsed
                .map { it.protocol ?: "Raw" }
                .distinct()
                .sorted()
            val summary = IrRemoteSummary(
                id = remoteId,
                name = remoteName,
                fileName = storedFileName,
                relativePath = relativePath,
                documentUri = remote.uri.toString(),
                source = IrRemoteSource.IMPORTED,
                category = existing?.category ?: IR_IMPORTED_CATEGORY,
                brand = existing?.brand.orEmpty(),
                favorite = existing?.favorite ?: false,
                lastUsedAtMs = existing?.lastUsedAtMs ?: 0L,
                buttonCount = parsed.size,
                protocols = protocols,
                buttonNames = parsed.map { it.name },
                lastModifiedMs = remote.lastModified(),
                sizeBytes = remote.length(),
            )
            val updated = _irRemotes.value
                .filterNot { it.id == summary.id }
                .plus(summary)
                .sortedWith(
                    compareByDescending<IrRemoteSummary> { it.favorite }
                        .thenBy { it.name.lowercase() },
                )
            _irRemotes.value = updated
            withContext(Dispatchers.IO) { irRemoteIndexStore.save(updated) }
            _irStatusMessage.value = "Imported ${parsed.size} IR button(s) as $remoteName."
            Log.d("IrRemote", "Imported ${parsed.size} signal(s) from $displayName")
        } catch (t: Throwable) {
            _irErrorMessage.value = t.message ?: "Could not import the IR file."
            Log.e("IrRemote", "IR import failed", t)
        } finally {
            _irImporting.value = false
        }
    }
}

internal fun MainViewModel.rescanIrLibraryImpl() {
    if (_irImporting.value) return
    val rootUri = _exportDirectory.value
    if (rootUri == null) {
        _irErrorMessage.value = "Set the NRSuite root directory before scanning IR files."
        return
    }
    _irImporting.value = true
    _irErrorMessage.value = null
    _irScanProgress.value = IrScanProgress(
        scanned = 0,
        total = 0,
        currentFile = "Discovering IR files...",
    )
    scope.launch {
        val startedAt = System.currentTimeMillis()
        try {
            val previous = _irRemotes.value.associateBy { it.id }
            Log.d("IrRemote", "IR library scan started")
            val scanned = scanIrRoot(rootUri, previous)
            _irRemotes.value = scanned.sortedWith(
                compareByDescending<IrRemoteSummary> { it.favorite }
                    .thenBy { it.name.lowercase() },
            )
            withContext(Dispatchers.IO) { irRemoteIndexStore.save(_irRemotes.value) }
            val elapsedMs = System.currentTimeMillis() - startedAt
            _irStatusMessage.value = "IR library scanned: ${scanned.size} remote(s)."
            Log.d("IrRemote", "IR library scan complete: ${scanned.size} remotes in ${elapsedMs} ms")
        } catch (t: Throwable) {
            _irErrorMessage.value = t.message ?: "Could not scan the IR library."
            Log.e("IrRemote", "IR library scan failed", t)
        } finally {
            _irImporting.value = false
            _irScanProgress.value = null
        }
    }
}

internal fun MainViewModel.openIrRemoteImpl(id: String) {
    val summary = _irRemotes.value.firstOrNull { it.id == id } ?: return
    _irLoadingRemote.value = true
    _irErrorMessage.value = null
    scope.launch {
        try {
            val signals = withContext(Dispatchers.IO) {
                val content = app.contentResolver.openInputStream(Uri.parse(summary.documentUri))
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: throw IllegalArgumentException("Could not open ${summary.fileName}.")
                IrFileParser.parse(content).mapIndexed { index, signal ->
                    signal.copy(
                        id = "${summary.id}:$index",
                        sourceName = summary.name,
                    )
                }
            }
            if (signals.isEmpty()) {
                throw IllegalArgumentException("No valid signals were found in ${summary.fileName}.")
            }
            _irSelectedRemote.value = IrRemote(summary = summary, signals = signals)
        } catch (t: Throwable) {
            _irErrorMessage.value = t.message ?: "Could not open the IR remote."
        } finally {
            _irLoadingRemote.value = false
        }
    }
}

internal fun MainViewModel.closeIrRemoteImpl() {
    stopIrTransmissionInternal(showStatus = false)
    _irSelectedRemote.value = null
    _irStatusMessage.value = null
    _irErrorMessage.value = null
}

internal fun MainViewModel.toggleIrFavoriteImpl(id: String) {
    val updated = _irRemotes.value.map { remote ->
        if (remote.id == id) remote.copy(favorite = !remote.favorite) else remote
    }.sortedWith(
        compareByDescending<IrRemoteSummary> { it.favorite }
            .thenBy { it.name.lowercase() },
    )
    _irRemotes.value = updated
    _irSelectedRemote.value = _irSelectedRemote.value?.let { selected ->
        val summary = updated.firstOrNull { it.id == selected.summary.id }
        if (summary != null) selected.copy(summary = summary) else selected
    }
    scope.launch(Dispatchers.IO) { irRemoteIndexStore.save(updated) }
}

internal fun MainViewModel.deleteIrRemoteImpl(id: String) {
    val remote = _irRemotes.value.firstOrNull { it.id == id } ?: return
    stopIrTransmissionInternal(showStatus = false)
    _irRemotes.value = _irRemotes.value.filterNot { it.id == id }
    if (_irSelectedRemote.value?.summary?.id == id) {
        _irSelectedRemote.value = null
    }
    scope.launch {
        withContext(Dispatchers.IO) {
            runCatching { DocumentFile.fromSingleUri(app, Uri.parse(remote.documentUri))?.delete() }
            irRemoteIndexStore.save(_irRemotes.value)
        }
        _irStatusMessage.value = "Removed ${remote.name} from the IR library."
    }
}

internal fun MainViewModel.sendIrSignalImpl(signal: IrSignal) {
    if (_irTransmitting.value) return
    val encoded = when (val result = IrProtocolEncoder.encode(signal)) {
        is IrEncodeResult.Success -> result
        is IrEncodeResult.Failure -> {
            _irErrorMessage.value = result.message
            return
        }
    }
    val backendError = irBackendError()
    if (backendError != null) {
        _irErrorMessage.value = backendError
        return
    }

    _irErrorMessage.value = null
    _irTransmitting.value = true
    _irStatusMessage.value = "Sending ${displayIrName(signal.name)}..."
    markSelectedIrRemoteUsed()
    Log.d(
        "IrRemote",
        "Sending ${signal.name}: protocol=${signal.protocol ?: "raw"} " +
            "frequency=${encoded.frequencyHz} pattern=${encoded.timingsUs.size} backend=${_irTransmitBackend.value}",
    )
    irTransmitJob = scope.launch(Dispatchers.IO) {
        try {
            transmitIrEncoded(encoded)
            _irStatusMessage.value = "Sent ${displayIrName(signal.name)}."
        } catch (t: Throwable) {
            _irErrorMessage.value = t.message ?: "IR transmission failed."
            Log.e("IrRemote", "IR transmission failed", t)
        } finally {
            _irTransmitting.value = false
            irTransmitJob = null
        }
    }
}

internal fun MainViewModel.toggleIrSignalImpl(signal: IrSignal) {
    if (!_irLoopMode.value) {
        sendIrSignalImpl(signal)
        return
    }
    if (_irActiveLoopSignalId.value == signal.id) {
        stopIrTransmissionImpl()
        return
    }
    startIrLoopImpl(signal)
}

internal fun MainViewModel.setIrLoopModeImpl(enabled: Boolean) {
    _irLoopMode.value = enabled
    if (!enabled) stopIrTransmissionInternal(showStatus = false)
}

internal fun MainViewModel.setIrTransmitBackendImpl(backend: IrTransmitBackend) {
    if (_irTransmitting.value) stopIrTransmissionInternal(showStatus = false)
    stopIrJammerImpl()
    _irTransmitBackend.value = backend
    preferences.edit().putString(PREF_IR_TRANSMIT_BACKEND, backend.name).apply()
}

internal fun MainViewModel.setIrLoopIntervalMsImpl(intervalMs: Int) {
    _irLoopIntervalMs.value = intervalMs.coerceIn(100, 2_000)
    preferences.edit().putInt(PREF_IR_LOOP_INTERVAL_MS, _irLoopIntervalMs.value).apply()
}

internal fun MainViewModel.stopIrTransmissionImpl() {
    stopIrTransmissionInternal(showStatus = true)
}

private fun MainViewModel.stopIrTransmissionInternal(showStatus: Boolean) {
    irTransmitJob?.cancel()
    irTransmitJob = null
    _irTransmitting.value = false
    _irActiveLoopSignalId.value = null
    if (showStatus) _irStatusMessage.value = "IR transmission stopped."
}

internal fun MainViewModel.clearIrMessagesImpl() {
    _irStatusMessage.value = null
    _irErrorMessage.value = null
}

private fun MainViewModel.startIrLoopImpl(signal: IrSignal) {
    val encoded = when (val result = IrProtocolEncoder.encode(signal)) {
        is IrEncodeResult.Success -> result
        is IrEncodeResult.Failure -> {
            _irErrorMessage.value = result.message
            return
        }
    }
    val backendError = irBackendError()
    if (backendError != null) {
        _irErrorMessage.value = backendError
        return
    }

    irTransmitJob?.cancel()
    _irErrorMessage.value = null
    _irTransmitting.value = true
    _irActiveLoopSignalId.value = signal.id
    _irStatusMessage.value = "Looping ${displayIrName(signal.name)}."
    markSelectedIrRemoteUsed()
    Log.d(
        "IrRemote",
        "Looping ${signal.name}: protocol=${signal.protocol ?: "raw"} " +
            "frequency=${encoded.frequencyHz} pattern=${encoded.timingsUs.size}",
    )
    irTransmitJob = scope.launch(Dispatchers.IO) {
        try {
            while (isActive && _irActiveLoopSignalId.value == signal.id) {
                transmitIrEncoded(encoded)
                delay(_irLoopIntervalMs.value.toLong())
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            _irErrorMessage.value = t.message ?: "IR loop failed."
            Log.e("IrRemote", "IR loop failed", t)
        } finally {
            if (_irActiveLoopSignalId.value == signal.id) {
                _irActiveLoopSignalId.value = null
                _irTransmitting.value = false
                irTransmitJob = null
            }
        }
    }
}

private fun MainViewModel.connectedEsp32(): ConnectionState.Connected? =
    session?.state?.value as? ConnectionState.Connected

private fun MainViewModel.resolveIrBackend(): IrTransmitBackend? {
    val phoneReady = _irCapabilities.value.hasEmitter
    val esp32 = connectedEsp32()
    val esp32Ready = esp32 != null &&
        "ir_tx" in esp32.features &&
        esp32.irTxGpio != null
    return when (_irTransmitBackend.value) {
        IrTransmitBackend.PHONE -> IrTransmitBackend.PHONE.takeIf { phoneReady }
        IrTransmitBackend.ESP32 -> IrTransmitBackend.ESP32.takeIf { esp32Ready }
        IrTransmitBackend.AUTO -> when {
            esp32Ready -> IrTransmitBackend.ESP32
            phoneReady -> IrTransmitBackend.PHONE
            else -> null
        }
    }
}

private fun MainViewModel.irBackendError(): String? {
    if (resolveIrBackend() != null) return null
    val backend = _irTransmitBackend.value
    val connected = connectedEsp32()
    return when (backend) {
        IrTransmitBackend.PHONE ->
            "This phone has no IR emitter. Select another backend."
        IrTransmitBackend.ESP32 -> when {
            connected == null -> "ESP32 IR is selected, but no ESP32 is connected."
            "ir_tx" !in connected.features ->
                "The connected firmware does not advertise IR TX support."
            connected.irTxGpio == null ->
                "Configure the ESP32 IR TX GPIO in IR Configuration."
            else -> "ESP32 IR is not available."
        }
        IrTransmitBackend.AUTO -> when {
            connected == null ->
                "No IR transmitter is available. Connect an ESP32 with IR TX support or use a phone with an IR emitter."
            "ir_tx" !in connected.features ->
                "No phone IR emitter detected and the connected firmware does not advertise IR TX support."
            else ->
                "No phone IR emitter detected. Configure the ESP32 IR TX GPIO in IR Configuration."
        }
    }
}

private suspend fun MainViewModel.transmitIrEncoded(encoded: IrEncodeResult.Success) {
    when (resolveIrBackend()) {
        IrTransmitBackend.PHONE -> {
            val result = irPhoneTransmitter.transmit(encoded.frequencyHz, encoded.timingsUs)
            result.exceptionOrNull()?.let { throw it }
        }
        IrTransmitBackend.ESP32 -> {
            val activeSession = session
                ?: throw IOException("No ESP32 session is connected.")
            val args = JSONObject().apply {
                put("frequency_hz", encoded.frequencyHz)
                put("repeat", 1)
                put(
                    "timings_us",
                    JSONArray().apply {
                        encoded.timingsUs.forEach { put(it) }
                    },
                )
            }
            val response = activeSession.sendCommand(
                cmd = "IR_TX_RAW",
                args = args,
                timeoutMs = 8_000,
            ) ?: throw IOException("ESP32 IR TX timed out.")
            if (!response.optBoolean("ok")) {
                throw IOException(
                    response.optString("msg").takeIf { it.isNotBlank() }
                        ?: "ESP32 IR TX failed.",
                )
            }
            activeSession.updateIrStatus(response)
        }
        null -> throw IOException("No IR transmitter is configured.")
        IrTransmitBackend.AUTO -> throw IOException("IR backend resolution failed.")
    }
}

internal fun MainViewModel.refreshIrEsp32StatusImpl() {
    scope.launch(Dispatchers.IO) {
        val activeSession = session ?: return@launch
        val response = activeSession.sendCommand("IR_STATUS", timeoutMs = 5_000)
            ?: run {
                _irErrorMessage.value = "ESP32 IR status timed out."
                return@launch
            }
        if (!response.optBoolean("ok")) {
            _irErrorMessage.value = response.optString("msg").takeIf { it.isNotBlank() }
                ?: "Could not read ESP32 IR status."
            return@launch
        }
        activeSession.updateIrStatus(response)
        _irErrorMessage.value = null
    }
}

internal fun MainViewModel.setIrEsp32PinsImpl(txGpio: Int, rxGpio: Int) {
    scope.launch(Dispatchers.IO) {
        val activeSession = session
        if (activeSession == null) {
            _irErrorMessage.value = "No ESP32 is connected."
            _toastMessage.value = "IR GPIO configuration failed: no ESP32 connected."
            return@launch
        }
        val args = JSONObject().apply {
            put("tx_gpio", txGpio)
            put("rx_gpio", rxGpio)
        }
        val response = activeSession.sendCommand(
            cmd = "IR_SET_PINS",
            args = args,
            timeoutMs = 8_000,
        ) ?: run {
            _irErrorMessage.value = "Saving ESP32 IR GPIOs timed out."
            _toastMessage.value = "IR GPIO configuration failed: timeout."
            return@launch
        }
        if (!response.optBoolean("ok")) {
            val message = response.optString("msg").takeIf { it.isNotBlank() }
                ?: "Could not save ESP32 IR GPIOs."
            _irErrorMessage.value = message
            _toastMessage.value = "IR GPIO configuration failed: $message"
            return@launch
        }
        activeSession.updateIrStatus(response)
        _irErrorMessage.value = null
        _irStatusMessage.value = "ESP32 IR pins saved: TX GPIO $txGpio, RX GPIO $rxGpio."
        _toastMessage.value = "IR GPIO configuration saved: TX GPIO $txGpio, RX GPIO $rxGpio."
    }
}

internal fun MainViewModel.startIrEsp32RxImpl() {
    scope.launch(Dispatchers.IO) {
        val activeSession = session ?: run {
            _irErrorMessage.value = "No ESP32 is connected."
            return@launch
        }
        val response = activeSession.sendCommand("IR_RX_START", timeoutMs = 5_000)
            ?: run {
                _irErrorMessage.value = "Starting ESP32 IR RX timed out."
                return@launch
            }
        if (!response.optBoolean("ok")) {
            _irErrorMessage.value = response.optString("msg").takeIf { it.isNotBlank() }
                ?: "Could not start ESP32 IR RX."
            return@launch
        }
        activeSession.updateIrStatus(response)
        _irStatusMessage.value = "ESP32 IR receiver started."
    }
}

internal fun MainViewModel.stopIrEsp32RxImpl() {
    scope.launch(Dispatchers.IO) {
        val activeSession = session ?: return@launch
        val response = activeSession.sendCommand("IR_RX_STOP", timeoutMs = 5_000)
            ?: return@launch
        if (response.optBoolean("ok")) {
            activeSession.updateIrStatus(response)
            _irStatusMessage.value = "ESP32 IR receiver stopped."
        } else {
            _irErrorMessage.value = response.optString("msg").takeIf { it.isNotBlank() }
                ?: "Could not stop ESP32 IR RX."
        }
    }
}

private fun MainViewModel.markSelectedIrRemoteUsed() {
    val selected = _irSelectedRemote.value ?: return
    val now = System.currentTimeMillis()
    val updated = _irRemotes.value.map {
        if (it.id == selected.summary.id) it.copy(lastUsedAtMs = now) else it
    }
    _irRemotes.value = updated
    _irSelectedRemote.value = selected.copy(
        summary = selected.summary.copy(lastUsedAtMs = now),
    )
    scope.launch(Dispatchers.IO) { irRemoteIndexStore.save(updated) }
}

internal fun MainViewModel.startIrJammerImpl(durationMs: Int) {
    if (irJammerJob?.isActive == true) return
    val unlimited = durationMs <= 0
    val duration = if (unlimited) 0L else durationMs.coerceIn(1_000, 30_000).toLong()
    val backend = resolveIrBackend()
    if (backend == null) {
        _irJammerState.value = _irJammerState.value.copy(
            phase = IrJammerPhase.ERROR,
            errorMessage = irBackendError() ?: "No IR transmitter is available.",
        )
        return
    }
    val connected = connectedEsp32()
    val backendLabel = when (backend) {
        IrTransmitBackend.PHONE -> "Phone IR"
        IrTransmitBackend.ESP32 -> "ESP32 IR · GPIO ${connected?.irTxGpio ?: "?"}"
        IrTransmitBackend.AUTO -> "Auto"
    }

    stopIrTransmissionInternal(showStatus = false)
    if (_irSearchState.value.runState == IrSearchRunState.RUNNING ||
        _irSearchState.value.runState == IrSearchRunState.PAUSED
    ) {
        stopIrSearchImpl()
    }

    irJammerJob = scope.launch(Dispatchers.IO) {
        val startedAt = SystemClock.elapsedRealtime()
        _irJammerState.value = com.swp81x.nrsuite.core.ir.IrJammerUiState(
            phase = IrJammerPhase.RUNNING,
            totalMs = duration,
            remainingMs = duration,
            unlimited = unlimited,
            backendLabel = backendLabel,
            message = if (unlimited) "IR jammer active in unlimited mode." else "IR jammer active.",
        )
        var terminalError: String? = null
        try {
            val activeSession = session
            if (activeSession != null && connectedEsp32()?.irRxActive == true) {
                activeSession.sendCommand("IR_RX_STOP", timeoutMs = 5_000)?.let { response ->
                    if (response.optBoolean("ok")) activeSession.updateIrStatus(response)
                }
            }
            while (isActive) {
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                val remaining = if (unlimited) {
                    0L
                } else {
                    (duration - elapsed).coerceAtLeast(0L)
                }
                _irJammerState.value = _irJammerState.value.copy(
                    remainingMs = remaining,
                    phase = IrJammerPhase.RUNNING,
                    errorMessage = null,
                )
                if (!unlimited && remaining <= 0L) break
                transmitIrEncoded(randomIrJammerPattern())
                delay(25)
            }
            _irJammerState.value = _irJammerState.value.copy(
                phase = IrJammerPhase.COMPLETED,
                remainingMs = 0L,
                message = if (unlimited) {
                    "IR jammer stopped."
                } else {
                    "IR jammer stopped after the configured time limit."
                },
            )
        } catch (t: CancellationException) {
            _irJammerState.value = _irJammerState.value.copy(
                phase = IrJammerPhase.COMPLETED,
                remainingMs = 0L,
                message = "IR jammer stopped.",
            )
            throw t
        } catch (t: Throwable) {
            terminalError = t.message ?: "IR jammer failed."
            _irJammerState.value = _irJammerState.value.copy(
                phase = IrJammerPhase.ERROR,
                remainingMs = 0L,
                errorMessage = terminalError,
            )
            _irErrorMessage.value = terminalError
            Log.e("IrRemote", "IR jammer failed", t)
        } finally {
            irJammerJob = null
        }
    }
}

internal fun MainViewModel.stopIrJammerImpl() {
    irJammerJob?.cancel()
    irJammerJob = null
    if (_irJammerState.value.running) {
        _irJammerState.value = _irJammerState.value.copy(
            phase = IrJammerPhase.COMPLETED,
            remainingMs = 0L,
            message = "IR jammer stopped.",
        )
    }
}

private fun randomIrJammerPattern(): IrEncodeResult.Success {
    val pairs = Random.nextInt(16, 40)
    val timings = IntArray(pairs * 2) { index ->
        if (index % 2 == 0) {
            Random.nextInt(250, 1_200)
        } else {
            Random.nextInt(250, 4_000)
        }
    }
    return IrEncodeResult.Success(
        timingsUs = timings,
        frequencyHz = 38_000,
    )
}

internal fun MainViewModel.handleIrRxEvent(event: JSONObject) {
    val timingsArray = event.optJSONArray("timings_us") ?: return
    val timings = buildList {
        for (index in 0 until timingsArray.length()) {
            val value = timingsArray.optInt(index, -1)
            if (value > 0) add(value)
        }
    }
    if (timings.size < MIN_IR_CAPTURE_TIMINGS) return
    if (timings.sumOf { it.toLong() } < MIN_IR_CAPTURE_DURATION_US) return

    val capturedAt = System.currentTimeMillis()
    val previousPattern = lastIrCapturePattern
    val isRepeat = previousPattern != null &&
        capturedAt - lastIrCaptureAtMs <= IR_REPEAT_SUPPRESSION_MS &&
        isLikelySameIrPattern(previousPattern, timings)
    if (isRepeat) {
        _irStatusMessage.value = "Repeat frame ignored."
        return
    }

    val capture = IrReceivedSignal(
        id = "irrx:$capturedAt:${_irNextButtonNumber.value}",
        frequencyHz = event.optInt("frequency_hz", 38000),
        protocol = event.optString("protocol", "RAW"),
        gpio = event.optInt("gpio", -1).takeIf { it >= 0 },
        timingsUs = timings,
        capturedAtMs = capturedAt,
        overflow = event.optBoolean("overflow", false),
    )
    lastIrCapturePattern = timings
    lastIrCaptureAtMs = capturedAt
    _irPendingCapture.value = capture
    _irStatusMessage.value = "Captured ${timings.size} timing(s). Save it as a button or press another button to replace it."
}

private fun isLikelySameIrPattern(first: List<Int>, second: List<Int>): Boolean {
    if (kotlin.math.abs(first.size - second.size) > 2) return false
    val common = minOf(first.size, second.size)
    if (common == 0) return false
    var matched = 0
    for (index in 0 until common) {
        val a = first[index]
        val b = second[index]
        val tolerance = maxOf(120, (maxOf(a, b) * 0.22f).toInt())
        if (kotlin.math.abs(a - b) <= tolerance) matched++
    }
    return matched >= (common * 0.9f).toInt()
}

internal fun MainViewModel.savePendingIrButtonImpl(name: String) {
    val capture = _irPendingCapture.value ?: return
    val cleanName = name.trim().replace(Regex("[\\r\\n]+"), " ")
        .ifBlank { "btn_%02d".format(_irNextButtonNumber.value) }
    val button = com.swp81x.nrsuite.core.ir.IrLearnedButton(
        id = "learned:${capture.capturedAtMs}:${_irNextButtonNumber.value}",
        name = cleanName,
        frequencyHz = capture.frequencyHz,
        protocol = capture.protocol,
        timingsUs = capture.timingsUs,
        capturedAtMs = capture.capturedAtMs,
        overflow = capture.overflow,
    )
    _irLearnedButtons.value = _irLearnedButtons.value + button
    _irPendingCapture.value = null
    _irNextButtonNumber.value = _irNextButtonNumber.value + 1
    _irStatusMessage.value = "Saved $cleanName to the capture list."
    _irErrorMessage.value = null
}

internal fun MainViewModel.discardPendingIrButtonImpl() {
    _irPendingCapture.value = null
    _irStatusMessage.value = "Pending capture discarded."
}

internal fun MainViewModel.replayPendingIrButtonImpl() {
    val capture = _irPendingCapture.value ?: return
    replayRawIrCapture(
        name = "pending capture",
        frequencyHz = capture.frequencyHz,
        timingsUs = capture.timingsUs,
    )
}

internal fun MainViewModel.deleteIrLearnedButtonImpl(id: String) {
    _irLearnedButtons.value = _irLearnedButtons.value.filterNot { it.id == id }
}

internal fun MainViewModel.renameIrLearnedButtonImpl(id: String, name: String) {
    val cleanName = name.trim().replace(Regex("[\\r\\n]+"), " ")
    if (cleanName.isBlank()) return
    _irLearnedButtons.value = _irLearnedButtons.value.map {
        if (it.id == id) it.copy(name = cleanName) else it
    }
}

internal fun MainViewModel.replayIrLearnedButtonImpl(id: String) {
    val button = _irLearnedButtons.value.firstOrNull { it.id == id } ?: return
    replayRawIrCapture(
        name = button.name,
        frequencyHz = button.frequencyHz,
        timingsUs = button.timingsUs,
    )
}

internal fun MainViewModel.clearIrLearnedButtonsImpl() {
    _irLearnedButtons.value = emptyList()
    _irNextButtonNumber.value = 1
    _irStatusMessage.value = "Capture button list cleared."
}

private fun MainViewModel.replayRawIrCapture(
    name: String,
    frequencyHz: Int,
    timingsUs: List<Int>,
) {
    if (_irTransmitting.value) return
    if (irBackendError() != null) {
        _irErrorMessage.value = irBackendError()
        return
    }
    scope.launch(Dispatchers.IO) {
        val activeSession = session
        var wasReceiving = (connectedEsp32()?.irRxActive == true)
        try {
            _irTransmitting.value = true
            if (wasReceiving && activeSession != null) {
                val stop = activeSession.sendCommand("IR_RX_STOP", timeoutMs = 5_000)
                if (stop?.optBoolean("ok") == true) {
                    activeSession.updateIrStatus(stop)
                } else {
                    wasReceiving = false
                }
            }
            val signal = IrSignal(
                id = "replay:${System.currentTimeMillis()}",
                name = name,
                type = com.swp81x.nrsuite.core.ir.IrSignalType.RAW,
                frequencyHz = frequencyHz,
                rawTimingsUs = timingsUs,
            )
            val encoded = when (val result = IrProtocolEncoder.encode(signal)) {
                is IrEncodeResult.Success -> result
                is IrEncodeResult.Failure -> throw IOException(result.message)
            }
            transmitIrEncoded(encoded)
            _irStatusMessage.value = "Replayed $name."
            _irErrorMessage.value = null
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            _irErrorMessage.value = t.message ?: "Could not replay IR capture."
            Log.e("IrRemote", "IR replay failed", t)
        } finally {
            _irTransmitting.value = false
            if (wasReceiving && activeSession != null) {
                val start = activeSession.sendCommand("IR_RX_START", timeoutMs = 5_000)
                if (start?.optBoolean("ok") == true) {
                    activeSession.updateIrStatus(start)
                }
            }
        }
    }
}

internal fun MainViewModel.saveIrLearnedRemoteImpl(remoteName: String) {
    if (_irSavingReceivedRemote.value) return
    val buttons = _irLearnedButtons.value
    if (buttons.isEmpty()) {
        _irErrorMessage.value = "Save at least one captured button first."
        return
    }
    val rootUri = _exportDirectory.value
    if (rootUri == null) {
        _irErrorMessage.value = "Set the NRSuite root directory before saving IR remotes."
        return
    }
    val cleanRemoteName = remoteName.trim()
    if (cleanRemoteName.isBlank()) {
        _irErrorMessage.value = "Remote file name is required."
        return
    }
    _irSavingReceivedRemote.value = true
    _irErrorMessage.value = null
    scope.launch(Dispatchers.IO) {
        try {
            val root = DocumentFile.fromTreeUri(app, rootUri)
                ?: throw IOException("Could not open the NRSuite root.")
            val irDirectory = ensureDocumentDirectory(root, IR_ROOT_DIRECTORY)
                ?: throw IOException("Could not create NRSuite/IR.")
            val receiverDirectory = ensureDocumentDirectory(irDirectory, IR_RECEIVER_DIRECTORY)
                ?: throw IOException("Could not create NRSuite/IR/Receiver.")
            val fileName = sanitizeIrFileName(cleanRemoteName)
            if (receiverDirectory.findFile(fileName) != null) {
                _irErrorMessage.value = "IR remote file already exists: $fileName. Choose a different name."
                _toastMessage.value = "IR remote file already exists."
                return@launch
            }
            val combined = buttons.joinToString("\n\n") { button ->
                buildString {
                    append("# Captured by NRSuite IR Receiver\n")
                    append("name: ").append(button.name).append('\n')
                    append("type: raw\n")
                    append("frequency: ").append(button.frequencyHz).append('\n')
                    append("duty_cycle: 0.33\n")
                    append("data: ").append(button.timingsUs.joinToString(" ")).append('\n')
                }
            }
            val parsed = IrFileParser.parse(combined)
            if (parsed.isEmpty()) {
                throw IOException("Could not build a valid IR file from the captures.")
            }
            val createdUri = DocumentsContract.createDocument(
                app.contentResolver,
                receiverDirectory.uri,
                "application/octet-stream",
                fileName,
            ) ?: receiverDirectory.createFile("application/octet-stream", fileName)?.uri
                ?: throw IOException("Could not create $fileName.")
            val target = DocumentFile.fromSingleUri(app, createdUri)
                ?: throw IOException("Could not open $fileName.")
            app.contentResolver.openOutputStream(target.uri, "wt")?.use { output ->
                output.write(combined.toByteArray(Charsets.UTF_8))
            } ?: throw IOException("Could not write $fileName.")

            val relativePath = "$IR_ROOT_DIRECTORY/$IR_RECEIVER_DIRECTORY/$fileName"
            val remoteId = "received:$relativePath"
            val old = _irRemotes.value.firstOrNull { it.id == remoteId }
            val summary = IrRemoteSummary(
                id = remoteId,
                name = fileName.substringBeforeLast('.').ifBlank { fileName },
                fileName = fileName,
                relativePath = relativePath,
                documentUri = target.uri.toString(),
                source = IrRemoteSource.RECEIVED,
                category = IR_RECEIVED_CATEGORY,
                brand = old?.brand.orEmpty(),
                favorite = old?.favorite ?: false,
                lastUsedAtMs = old?.lastUsedAtMs ?: 0L,
                buttonCount = parsed.size,
                protocols = parsed.map { it.protocol ?: "Raw" }.distinct().sorted(),
                buttonNames = parsed.map { it.name },
                lastModifiedMs = target.lastModified(),
                sizeBytes = target.length(),
            )
            val updated = _irRemotes.value
                .filterNot { it.id == remoteId }
                .plus(summary)
                .sortedWith(
                    compareByDescending<IrRemoteSummary> { it.favorite }
                        .thenBy { it.name.lowercase() },
                )
            _irRemotes.value = updated
            irRemoteIndexStore.save(updated)
            _irLearnedButtons.value = emptyList()
            _irNextButtonNumber.value = 1
            _irStatusMessage.value = "Saved ${buttons.size} captured button(s) to $fileName."
            _irErrorMessage.value = null
            _toastMessage.value = "Captured remote saved: $fileName"
            Log.d("IrRemote", "Saved captured remote as $fileName (${parsed.size} button(s))")
        } catch (t: Throwable) {
            _irErrorMessage.value = t.message ?: "Could not save the captured remote."
            _toastMessage.value = t.message ?: "Could not save the captured remote."
            Log.e("IrRemote", "Captured remote save failed", t)
        } finally {
            _irSavingReceivedRemote.value = false
        }
    }
}

private fun MainViewModel.writeImportedIrFile(
    rootUri: Uri,
    fileName: String,
    content: String,
): DocumentFile? {
    val root = DocumentFile.fromTreeUri(app, rootUri) ?: return null
    val irDirectory = ensureDocumentDirectory(root, IR_ROOT_DIRECTORY) ?: return null
    val libraryDirectory = ensureDocumentDirectory(irDirectory, IR_LIBRARY_DIRECTORY) ?: return null
    libraryDirectory.findFile(fileName)?.delete()
    val createdUri = runCatching {
        DocumentsContract.createDocument(
            app.contentResolver,
            libraryDirectory.uri,
            "application/octet-stream",
            fileName,
        )
    }.getOrNull() ?: libraryDirectory.createFile("application/octet-stream", fileName)?.uri
    ?: return null
    app.contentResolver.openOutputStream(createdUri, "wt")?.use { output ->
        output.write(content.toByteArray(Charsets.UTF_8))
    } ?: return null
    return DocumentFile.fromSingleUri(app, createdUri)
}


private const val IR_DATABASE_FLIPPER_DIRECTORY = "Flipper-IRDB"
private const val IR_DATABASE_STAGING_DIRECTORY = "_IRDB_importing"
private const val IR_DATABASE_CACHE_ARCHIVE = "Flipper-IRDB-main.zip"
private const val MAX_IR_ZIP_ARCHIVE_BYTES = 1L * 1024L * 1024L * 1024L

internal fun MainViewModel.downloadIrDatabaseImpl() {
    startIrDatabasePipelineImpl(forceDownload = true)
}

internal fun MainViewModel.retryIrDatabaseOperationImpl() {
    startIrDatabasePipelineImpl(forceDownload = false)
}

internal fun MainViewModel.cancelIrDatabaseOperationImpl() {
    irDatabaseDownloader.cancel()
    irDatabaseJob?.cancel()
}

private fun MainViewModel.startIrDatabasePipelineImpl(forceDownload: Boolean) {
    if (irDatabaseJob?.isActive == true) return
    val rootUri = _exportDirectory.value
    if (rootUri == null) {
        _irDatabaseState.value = IrDatabaseUiState(
            phase = IrDatabasePhase.FAILED,
            errorMessage = "Set the NRSuite root directory before downloading Flipper-IRDB.",
            canRetry = false,
        )
        return
    }
    val cacheDirectory = File(app.cacheDir, "ir_database").apply { mkdirs() }
    val archive = File(cacheDirectory, IR_DATABASE_CACHE_ARCHIVE)
    irDatabaseJob = scope.launch(Dispatchers.IO) {
        var foregroundStarted = false
        try {
            IrDatabaseForegroundService.start(app, "Preparing Flipper-IRDB...")
            foregroundStarted = true
            var archiveVersion = _irDatabaseMetadata.value?.archiveVersion.orEmpty()
            if (forceDownload || !archive.exists()) {
                if (forceDownload && archive.exists()) archive.delete()
                _irDatabaseState.value = IrDatabaseUiState(
                    phase = IrDatabasePhase.DOWNLOADING,
                    message = "Downloading Flipper-IRDB...",
                )
                updateIrDatabaseNotification(
                    title = "Flipper-IRDB download",
                    text = "Connecting to the database server...",
                    indeterminate = true,
                )
                var lastNotificationAt = 0L
                val result = irDatabaseDownloader.download(
                    url = IrDatabaseDownloader.DEFAULT_URL,
                    destination = archive,
                    onProgress = { downloaded, total ->
                        _irDatabaseState.value = IrDatabaseUiState(
                            phase = IrDatabasePhase.DOWNLOADING,
                            downloadedBytes = downloaded,
                            totalBytes = total,
                            message = "Downloading Flipper-IRDB...",
                        )
                        val now = System.currentTimeMillis()
                        if (now - lastNotificationAt >= 750L) {
                            lastNotificationAt = now
                            val percent = _irDatabaseState.value.downloadPercent
                            updateIrDatabaseNotification(
                                title = "Flipper-IRDB download",
                                text = if (percent == null) {
                                    "Downloading database archive..."
                                } else {
                                    "Downloading database archive: $percent%"
                                },
                                progressPercent = percent,
                                indeterminate = percent == null,
                            )
                        }
                    },
                    onRetry = { attempt, maxAttempts, reason ->
                        val previousState = _irDatabaseState.value
                        _irDatabaseState.value = IrDatabaseUiState(
                            phase = IrDatabasePhase.DOWNLOADING,
                            downloadedBytes = archive.length().takeIf { it > 0L }
                                ?: previousState.downloadedBytes,
                            totalBytes = previousState.totalBytes,
                            message = "Connection interrupted. Retrying $attempt/$maxAttempts...",
                            retryAttempt = attempt,
                            maxRetryAttempts = maxAttempts,
                            canRetry = true,
                        )
                        updateIrDatabaseNotification(
                            title = "Flipper-IRDB download interrupted",
                            text = "$reason Retrying $attempt/$maxAttempts...",
                            indeterminate = true,
                        )
                        Log.w("IrRemote", "IRDB download retry $attempt/$maxAttempts: $reason")
                    },
                )
                archiveVersion = result.archiveVersion
                val now = System.currentTimeMillis()
                val currentMetadata = _irDatabaseMetadata.value ?: IrDatabaseMetadata()
                _irDatabaseMetadata.value = currentMetadata.copy(
                    sourceUrl = IrDatabaseDownloader.DEFAULT_URL,
                    archiveVersion = archiveVersion,
                    downloadedAtMs = now,
                    archiveBytes = result.sizeBytes,
                    destinationPath = "IR/Database/$IR_DATABASE_FLIPPER_DIRECTORY",
                )
                irDatabaseMetadataStore.save(_irDatabaseMetadata.value!!)
            }

            extractFlipperDatabaseAndIndex(
                rootUri = rootUri,
                archive = archive,
                archiveVersion = archiveVersion.ifBlank { "main" },
            )
        } catch (_: CancellationException) {
            _irDatabaseState.value = _irDatabaseState.value.copy(
                phase = IrDatabasePhase.CANCELLED,
                message = null,
                errorMessage = "Flipper-IRDB operation cancelled. Partial download or archive kept for retry.",
                canRetry = archive.exists() || File(cacheDirectory, "${archive.name}.part").exists(),
            )
            updateIrDatabaseNotification(
                title = "Flipper-IRDB cancelled",
                text = "The operation was cancelled. Partial data is available for retry.",
                indeterminate = false,
            )
            _irStatusMessage.value = "Flipper-IRDB operation cancelled."
            Log.d("IrRemote", "IRDB operation cancelled")
        } catch (t: Throwable) {
            _irDatabaseState.value = _irDatabaseState.value.copy(
                phase = IrDatabasePhase.FAILED,
                message = null,
                errorMessage = t.message ?: "Flipper-IRDB operation failed.",
                canRetry = archive.exists() || File(cacheDirectory, "${archive.name}.part").exists(),
            )
            _irErrorMessage.value = t.message ?: "Flipper-IRDB operation failed."
            updateIrDatabaseNotification(
                title = "Flipper-IRDB failed",
                text = t.message ?: "The operation failed. Open IR Configuration to retry.",
                indeterminate = false,
            )
            Log.e("IrRemote", "IRDB operation failed", t)
        } finally {
            if (foregroundStarted) IrDatabaseForegroundService.stop(app)
            irDatabaseJob = null
        }
    }
}

private suspend fun MainViewModel.extractFlipperDatabaseAndIndex(
    rootUri: Uri,
    archive: File,
    archiveVersion: String,
) {
    val databaseDirectory = withContext(Dispatchers.IO) {
        openIrDatabaseDirectory(rootUri)
    } ?: throw IOException("Could not open NRSuite/IR/Database.")

    _irDatabaseState.value = IrDatabaseUiState(
        phase = IrDatabasePhase.EXTRACTING,
        message = "Extracting Flipper-IRDB...",
    )
    updateIrDatabaseNotification(
        title = "Flipper-IRDB extraction",
        text = "Preparing the database folders...",
        indeterminate = true,
    )
    val staging = withContext(Dispatchers.IO) {
        prepareStagingDirectory(databaseDirectory)
    } ?: throw IOException("Could not create the Flipper-IRDB staging directory.")

    var lastNotificationAt = 0L
    val extraction = try {
        irZipExtractor.extract(archive, staging) { extracted, total, currentPath ->
            _irDatabaseState.value = IrDatabaseUiState(
                phase = IrDatabasePhase.EXTRACTING,
                extractedFiles = extracted,
                totalFiles = total,
                currentItem = currentPath,
                message = "Extracting Flipper-IRDB...",
            )
            val now = System.currentTimeMillis()
            if (now - lastNotificationAt >= 750L || extracted == total) {
                lastNotificationAt = now
                val percent = _irDatabaseState.value.extractionPercent
                updateIrDatabaseNotification(
                    title = "Flipper-IRDB extraction",
                    text = if (percent == null) {
                        "Extracting IR files..."
                    } else {
                        "Extracting IR files: $percent% ($extracted/$total)"
                    },
                    progressPercent = percent,
                    indeterminate = percent == null,
                )
            }
        }
    } catch (t: Throwable) {
        withContext(Dispatchers.IO) { staging.delete() }
        throw t
    }

    _irDatabaseState.value = IrDatabaseUiState(
        phase = IrDatabasePhase.EXTRACTING,
        extractedFiles = extraction.extractedFiles,
        totalFiles = extraction.totalIrFiles,
        currentItem = "Finalizing Flipper-IRDB folder...",
        message = "Finalizing Flipper-IRDB...",
    )
    updateIrDatabaseNotification(
        title = "Flipper-IRDB finalizing",
        text = "Finalizing ${extraction.extractedFiles} extracted file(s)...",
        indeterminate = true,
    )
    val commit = withContext(Dispatchers.IO) {
        commitStagedDirectory(
            parent = databaseDirectory,
            staging = staging,
            targetName = IR_DATABASE_FLIPPER_DIRECTORY,
        )
    }
    if (!commit.committed) {
        throw IOException("Could not replace the existing Flipper-IRDB folder.")
    }

    _irDatabaseState.value = IrDatabaseUiState(
        phase = IrDatabasePhase.INDEXING,
        extractedFiles = extraction.extractedFiles,
        totalFiles = extraction.totalIrFiles,
        message = "Indexing Flipper-IRDB...",
    )
    updateIrDatabaseNotification(
        title = "Flipper-IRDB indexing",
        text = "Indexing ${extraction.extractedFiles} IR file(s)...",
        indeterminate = true,
    )

    val previous = _irRemotes.value.associateBy { it.id }
    val scanned = scanIrRoot(rootUri, previous).sortedWith(
        compareByDescending<IrRemoteSummary> { it.favorite }
            .thenBy { it.name.lowercase() },
    )
    _irRemotes.value = scanned
    withContext(Dispatchers.IO) { irRemoteIndexStore.save(scanned) }
    _irScanProgress.value = null

    val flipperRemotes = scanned.count {
        it.source == IrRemoteSource.DATABASE &&
            it.relativePath.startsWith("IR/$IR_DATABASE_DIRECTORY/$IR_DATABASE_FLIPPER_DIRECTORY/")
    }
    val metadata = IrDatabaseMetadata(
        sourceUrl = IrDatabaseDownloader.DEFAULT_URL,
        archiveVersion = archiveVersion,
        downloadedAtMs = _irDatabaseMetadata.value?.downloadedAtMs ?: System.currentTimeMillis(),
        indexedAtMs = System.currentTimeMillis(),
        archiveBytes = archive.length(),
        extractedFiles = extraction.extractedFiles,
        indexedRemotes = flipperRemotes,
        destinationPath = "IR/$IR_DATABASE_DIRECTORY/$IR_DATABASE_FLIPPER_DIRECTORY",
    )
    withContext(Dispatchers.IO) { irDatabaseMetadataStore.save(metadata) }
    _irDatabaseMetadata.value = metadata
    _irDatabaseState.value = IrDatabaseUiState(
        phase = IrDatabasePhase.COMPLETED,
        extractedFiles = extraction.extractedFiles,
        totalFiles = extraction.totalIrFiles,
        message = "Flipper-IRDB ready: ${extraction.extractedFiles} file(s), " +
            "$flipperRemotes indexed remote(s).",
    )
    _irStatusMessage.value = "Flipper-IRDB download and indexing completed."
    updateIrDatabaseNotification(
        title = "Flipper-IRDB ready",
        text = "${extraction.extractedFiles} files extracted, $flipperRemotes remotes indexed.",
        indeterminate = false,
    )
    commit.cleanup?.let { backup ->
        scope.launch(Dispatchers.IO) {
            deleteIrDocumentTree(backup)
            Log.d("IrRemote", "Removed previous Flipper-IRDB backup tree.")
        }
    }
    archive.delete()
    Log.d(
        "IrRemote",
        "IRDB complete: extracted=${extraction.extractedFiles} skipped=${extraction.skippedFiles} " +
            "remotes=$flipperRemotes archiveVersion=$archiveVersion",
    )
}

internal fun MainViewModel.importIrZipImpl(uri: Uri) {
    if (irDatabaseJob?.isActive == true) return
    val rootUri = _exportDirectory.value
    if (rootUri == null) {
        _irDatabaseState.value = IrDatabaseUiState(
            phase = IrDatabasePhase.FAILED,
            errorMessage = "Set the NRSuite root directory before importing an IR ZIP.",
        )
        return
    }
    irDatabaseJob = scope.launch(Dispatchers.IO) {
        var foregroundStarted = false
        var tempZip: File? = null
        try {
            IrDatabaseForegroundService.start(app, "Importing IR ZIP...")
            foregroundStarted = true
            val displayName = queryDocumentDisplayNameImpl(uri) ?: "IR-import.zip"
            val archiveSize = queryDocumentSizeImpl(uri)
            if (archiveSize > MAX_IR_ZIP_ARCHIVE_BYTES) {
                throw IOException("Selected ZIP is larger than 1 GB.")
            }
            _irDatabaseState.value = IrDatabaseUiState(
                phase = IrDatabasePhase.EXTRACTING,
                message = "Copying $displayName...",
            )
            updateIrDatabaseNotification(
                title = "IR ZIP import",
                text = "Copying $displayName...",
                indeterminate = true,
            )
            val cacheDir = File(app.cacheDir, "ir_database").apply { mkdirs() }
            tempZip = File(cacheDir, "IR-upload-${System.currentTimeMillis()}.zip")
            var copied = 0L
            app.contentResolver.openInputStream(uri)?.use { input ->
                tempZip.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        copied += read
                        if (copied > MAX_IR_ZIP_ARCHIVE_BYTES) {
                            throw IOException("Selected ZIP is larger than 1 GB.")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: throw IOException("Could not open the selected ZIP.")
            if (copied == 0L) throw IOException("Selected ZIP is empty.")

            val baseName = sanitizeIrFileName(displayName)
                .removeSuffix(".ir")
                .removeSuffix(".zip")
                .ifBlank { "Imported-IRDB" }
            val destinationName = "IRDB-$baseName".take(80)
            _irDatabaseState.value = IrDatabaseUiState(
                phase = IrDatabasePhase.EXTRACTING,
                message = "Extracting $displayName...",
            )
            updateIrDatabaseNotification(
                title = "IR ZIP import",
                text = "Extracting ${displayName}...",
                indeterminate = true,
            )
            val databaseDirectory = withContext(Dispatchers.IO) {
                openIrDatabaseDirectory(rootUri)
            } ?: throw IOException("Could not open NRSuite/IR/Database.")
            val staging = withContext(Dispatchers.IO) {
                prepareStagingDirectory(databaseDirectory)
            } ?: throw IOException("Could not create the ZIP staging directory.")

            var lastNotificationAt = 0L
            val extraction = try {
                irZipExtractor.extract(tempZip, staging) { extracted, total, currentPath ->
                    _irDatabaseState.value = IrDatabaseUiState(
                        phase = IrDatabasePhase.EXTRACTING,
                        extractedFiles = extracted,
                        totalFiles = total,
                        currentItem = currentPath,
                        message = "Extracting $displayName...",
                    )
                    val now = System.currentTimeMillis()
                    if (now - lastNotificationAt >= 750L || extracted == total) {
                        lastNotificationAt = now
                        val percent = _irDatabaseState.value.extractionPercent
                        updateIrDatabaseNotification(
                            title = "IR ZIP import",
                            text = if (percent == null) {
                                "Extracting IR files..."
                            } else {
                                "Extracting IR files: $percent% ($extracted/$total)"
                            },
                            progressPercent = percent,
                            indeterminate = percent == null,
                        )
                    }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.IO) { staging.delete() }
                throw t
            }

            _irDatabaseState.value = IrDatabaseUiState(
                phase = IrDatabasePhase.EXTRACTING,
                extractedFiles = extraction.extractedFiles,
                totalFiles = extraction.totalIrFiles,
                currentItem = "Finalizing imported database folder...",
                message = "Finalizing ZIP import...",
            )
            updateIrDatabaseNotification(
                title = "IR ZIP finalizing",
                text = "Finalizing ${extraction.extractedFiles} extracted file(s)...",
                indeterminate = true,
            )
            val commit = withContext(Dispatchers.IO) {
                commitStagedDirectory(
                    parent = databaseDirectory,
                    staging = staging,
                    targetName = destinationName,
                )
            }
            if (!commit.committed) {
                throw IOException("Could not move the imported ZIP contents into the IR database.")
            }

            _irDatabaseState.value = IrDatabaseUiState(
                phase = IrDatabasePhase.INDEXING,
                extractedFiles = extraction.extractedFiles,
                totalFiles = extraction.totalIrFiles,
                message = "Indexing imported IR files...",
            )
            updateIrDatabaseNotification(
                title = "IR ZIP import",
                text = "Indexing ${extraction.extractedFiles} extracted file(s)...",
                indeterminate = true,
            )
            val scanned = scanIrRoot(rootUri, _irRemotes.value.associateBy { it.id })
                .sortedWith(
                    compareByDescending<IrRemoteSummary> { it.favorite }
                        .thenBy { it.name.lowercase() },
                )
            _irRemotes.value = scanned
            withContext(Dispatchers.IO) { irRemoteIndexStore.save(scanned) }
            _irScanProgress.value = null
            val importedRemotes = scanned.count {
                it.source == IrRemoteSource.DATABASE &&
                    it.relativePath.startsWith("IR/$IR_DATABASE_DIRECTORY/$destinationName/")
            }
            _irDatabaseState.value = IrDatabaseUiState(
                phase = IrDatabasePhase.COMPLETED,
                extractedFiles = extraction.extractedFiles,
                totalFiles = extraction.totalIrFiles,
                message = "ZIP import ready: ${extraction.extractedFiles} file(s), " +
                    "$importedRemotes indexed remote(s).",
            )
            _irStatusMessage.value = "IR ZIP import completed."
            updateIrDatabaseNotification(
                title = "IR ZIP import ready",
                text = "${extraction.extractedFiles} files extracted, $importedRemotes remotes indexed.",
                indeterminate = false,
            )
            commit.cleanup?.let { backup ->
                scope.launch(Dispatchers.IO) {
                    deleteIrDocumentTree(backup)
                    Log.d("IrRemote", "Removed previous IR ZIP backup tree.")
                }
            }
            Log.d(
                "IrRemote",
                "IR ZIP import complete: destination=$destinationName extracted=${extraction.extractedFiles} " +
                    "skipped=${extraction.skippedFiles} remotes=$importedRemotes",
            )
        } catch (_: CancellationException) {
            _irDatabaseState.value = IrDatabaseUiState(
                phase = IrDatabasePhase.CANCELLED,
                errorMessage = "IR ZIP import cancelled.",
            )
            updateIrDatabaseNotification(
                title = "IR ZIP import cancelled",
                text = "The import was cancelled.",
                indeterminate = false,
            )
        } catch (t: Throwable) {
            _irDatabaseState.value = IrDatabaseUiState(
                phase = IrDatabasePhase.FAILED,
                errorMessage = t.message ?: "IR ZIP import failed.",
            )
            _irErrorMessage.value = t.message ?: "IR ZIP import failed."
            updateIrDatabaseNotification(
                title = "IR ZIP import failed",
                text = t.message ?: "The import failed. Open IR Configuration to retry.",
                indeterminate = false,
            )
            Log.e("IrRemote", "IR ZIP import failed", t)
        } finally {
            tempZip?.delete()
            if (foregroundStarted) IrDatabaseForegroundService.stop(app)
            irDatabaseJob = null
        }
    }
}

internal fun MainViewModel.deleteIrDatabaseImpl() {
    if (irDatabaseJob?.isActive == true) return
    val rootUri = _exportDirectory.value
    if (rootUri == null) {
        _irErrorMessage.value = "Set the NRSuite root directory before deleting Flipper-IRDB."
        return
    }
    irDatabaseJob = scope.launch(Dispatchers.IO) {
        try {
            val databaseDirectory = openIrDatabaseDirectory(rootUri)
                ?: throw IOException("Could not open NRSuite/IR/Database.")
            databaseDirectory.listFiles()
                .filter { it.name?.startsWith("Flipper-IRDB", ignoreCase = true) == true }
                .forEach { it.delete() }
            databaseDirectory.listFiles()
                .filter {
                    it.name?.startsWith("_IRDB_", ignoreCase = true) == true
                }
                .forEach { deleteIrDocumentTree(it) }
            val updated = _irRemotes.value.filterNot { remote ->
                remote.relativePath.startsWith("IR/$IR_DATABASE_DIRECTORY/") &&
                    (remote.relativePath.startsWith(
                        "IR/$IR_DATABASE_DIRECTORY/$IR_DATABASE_FLIPPER_DIRECTORY/",
                    ) || remote.relativePath.startsWith(
                        "IR/$IR_DATABASE_DIRECTORY/Flipper-IRDB-main/",
                    ))
            }
            _irRemotes.value = updated
            irRemoteIndexStore.save(updated)
            irDatabaseMetadataStore.clear()
            _irDatabaseMetadata.value = null
            _irDatabaseState.value = IrDatabaseUiState(
                phase = IrDatabasePhase.COMPLETED,
                message = "Flipper-IRDB deleted from NRSuite/IR/Database.",
            )
            _irStatusMessage.value = "Flipper-IRDB deleted."
            updateIrDatabaseNotification(
                title = "Flipper-IRDB deleted",
                text = "The database and its indexed remotes were removed.",
                indeterminate = false,
            )
        } catch (t: Throwable) {
            _irDatabaseState.value = IrDatabaseUiState(
                phase = IrDatabasePhase.FAILED,
                errorMessage = t.message ?: "Could not delete Flipper-IRDB.",
            )
            _irErrorMessage.value = t.message ?: "Could not delete Flipper-IRDB."
            Log.e("IrRemote", "IRDB delete failed", t)
        } finally {
            irDatabaseJob = null
        }
    }
}

private fun MainViewModel.updateIrDatabaseNotification(
    title: String,
    text: String,
    progressPercent: Int? = null,
    indeterminate: Boolean = false,
) {
    NrSuiteNotifications.notifyIrDatabaseProgress(
        context = app,
        title = title,
        text = text,
        progressPercent = progressPercent,
        indeterminate = indeterminate,
    )
}

private fun MainViewModel.openIrDatabaseDirectory(rootUri: Uri): DocumentFile? {
    val root = DocumentFile.fromTreeUri(app, rootUri) ?: return null
    val irDirectory = ensureDocumentDirectory(root, IR_ROOT_DIRECTORY) ?: return null
    return ensureDocumentDirectory(irDirectory, IR_DATABASE_DIRECTORY)
}

private fun prepareStagingDirectory(databaseDirectory: DocumentFile): DocumentFile? {
    databaseDirectory.findFile(IR_DATABASE_STAGING_DIRECTORY)?.delete()
    return databaseDirectory.createDirectory(IR_DATABASE_STAGING_DIRECTORY)
}

private data class IrStagedDirectoryCommit(
    val committed: Boolean,
    val cleanup: DocumentFile? = null,
)

private fun commitStagedDirectory(
    parent: DocumentFile,
    staging: DocumentFile,
    targetName: String,
): IrStagedDirectoryCommit {
    val existing = parent.findFile(targetName)
    if (existing == null) {
        return IrStagedDirectoryCommit(committed = staging.renameTo(targetName))
    }
    val backupName = "_IRDB_backup_${System.currentTimeMillis()}"
    if (!existing.renameTo(backupName)) {
        return IrStagedDirectoryCommit(committed = false)
    }
    if (staging.renameTo(targetName)) {
        return IrStagedDirectoryCommit(
            committed = true,
            cleanup = parent.findFile(backupName),
        )
    }
    parent.findFile(backupName)?.renameTo(targetName)
    return IrStagedDirectoryCommit(committed = false)
}

private fun MainViewModel.deleteIrDocumentTree(document: DocumentFile) {
    val deleted = runCatching {
        DocumentsContract.deleteDocument(app.contentResolver, document.uri)
    }.getOrDefault(false)
    if (deleted) return
    runCatching {
        document.listFiles().forEach { child ->
            if (child.isDirectory) deleteIrDocumentTree(child) else child.delete()
        }
        document.delete()
    }
}

private data class IrScanCandidate(
    val documentUri: String,
    val fileName: String,
    val relativePath: String,
    val source: IrRemoteSource,
    val category: String,
    val brand: String,
    val lastModifiedMs: Long,
    val sizeBytes: Long,
)

private suspend fun MainViewModel.scanIrRoot(
    rootUri: Uri,
    previous: Map<String, IrRemoteSummary>,
): List<IrRemoteSummary> {
    val candidates = collectIrCandidatesFast(rootUri)
    if (candidates.isEmpty()) return emptyList()

    _irScanProgress.value = IrScanProgress(0, candidates.size)
    Log.d("IrRemote", "IR library scan candidates=${candidates.size}")
    val completed = AtomicInteger(0)
    val reused = AtomicInteger(0)
    val semaphore = Semaphore(8)
    return coroutineScope {
        candidates.chunked(64).flatMap { chunk ->
            chunk.map { candidate ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val id = "${candidate.source.name.lowercase()}:${candidate.relativePath}"
                        val old = previous[id]
                        val canReuse = old != null &&
                            old.documentUri == candidate.documentUri &&
                            old.lastModifiedMs == candidate.lastModifiedMs &&
                            old.sizeBytes == candidate.sizeBytes &&
                            old.buttonCount > 0 &&
                            old.buttonNames.isNotEmpty()
                        val metadata = if (canReuse) null else readIrMetadata(candidate)
                        val index = completed.incrementAndGet()
                        if (index == candidates.size || index % 10 == 0) {
                            _irScanProgress.value = IrScanProgress(
                                scanned = index,
                                total = candidates.size,
                                currentFile = candidate.fileName,
                            )
                        }
                        if (canReuse) reused.incrementAndGet()
                        val summary = if (canReuse) {
                            old.copy(
                                name = candidate.fileName.substringBeforeLast('.').ifBlank {
                                    candidate.fileName
                                },
                                fileName = candidate.fileName,
                                relativePath = candidate.relativePath,
                                documentUri = candidate.documentUri,
                                source = candidate.source,
                                category = candidate.category,
                                brand = candidate.brand,
                            )
                        } else {
                            metadata?.let {
                                IrRemoteSummary(
                                    id = id,
                                    name = candidate.fileName.substringBeforeLast('.').ifBlank {
                                        candidate.fileName
                                    },
                                    fileName = candidate.fileName,
                                    relativePath = candidate.relativePath,
                                    documentUri = candidate.documentUri,
                                    source = candidate.source,
                                    category = candidate.category,
                                    brand = candidate.brand,
                                    favorite = old?.favorite ?: false,
                                    lastUsedAtMs = old?.lastUsedAtMs ?: 0L,
                                    buttonCount = it.buttonCount,
                                    protocols = it.protocols,
                                    buttonNames = it.buttonNames,
                                    lastModifiedMs = candidate.lastModifiedMs,
                                    sizeBytes = candidate.sizeBytes,
                                )
                            }
                        }
                        summary
                    }
                }
            }.awaitAll()
        }.filterNotNull().also {
            Log.d(
                "IrRemote",
                "IR incremental index reused ${reused.get()} unchanged file(s) of ${candidates.size}.",
            )
        }
    }
}

private data class IrDirectoryNode(
    val documentId: String,
    val source: IrRemoteSource,
    val relativeSegments: List<String>,
)

private suspend fun MainViewModel.collectIrCandidatesFast(rootUri: Uri): List<IrScanCandidate> {
    val roots = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(app, rootUri) ?: return@withContext emptyList<IrDirectoryNode>()
        val irDirectory = ensureDocumentDirectory(root, IR_ROOT_DIRECTORY)
            ?: return@withContext emptyList<IrDirectoryNode>()
        val nodes = mutableListOf<IrDirectoryNode>()
        ensureDocumentDirectory(irDirectory, IR_LIBRARY_DIRECTORY)?.let { directory ->
            documentIdFor(directory.uri)?.let { id ->
                nodes += IrDirectoryNode(
                    documentId = id,
                    source = IrRemoteSource.IMPORTED,
                    relativeSegments = listOf(IR_ROOT_DIRECTORY, IR_LIBRARY_DIRECTORY),
                )
            }
        }
        ensureDocumentDirectory(irDirectory, IR_RECEIVER_DIRECTORY)?.let { directory ->
            documentIdFor(directory.uri)?.let { id ->
                nodes += IrDirectoryNode(
                    documentId = id,
                    source = IrRemoteSource.RECEIVED,
                    relativeSegments = listOf(IR_ROOT_DIRECTORY, IR_RECEIVER_DIRECTORY),
                )
            }
        }
        ensureDocumentDirectory(irDirectory, IR_DATABASE_DIRECTORY)?.let { directory ->
            documentIdFor(directory.uri)?.let { id ->
                nodes += IrDirectoryNode(
                    documentId = id,
                    source = IrRemoteSource.DATABASE,
                    relativeSegments = listOf(IR_ROOT_DIRECTORY, IR_DATABASE_DIRECTORY),
                )
            }
        }
        nodes
    }
    if (roots.isEmpty()) return emptyList()

    val candidates = Collections.synchronizedList(mutableListOf<IrScanCandidate>())
    val discovered = AtomicInteger(0)
    val queue = Channel<IrDirectoryNode>(Channel.UNLIMITED)
    val pending = AtomicInteger(0)
    var lastDiscoveryUpdate = 0L

    fun reportDiscovery(path: String) {
        val count = discovered.incrementAndGet()
        val now = System.currentTimeMillis()
        if (count == 1 || now - lastDiscoveryUpdate >= 150L || count % 100 == 0) {
            lastDiscoveryUpdate = now
            _irScanProgress.value = IrScanProgress(
                scanned = 0,
                total = 0,
                currentFile = path,
                discoveredCount = count,
            )
        }
    }

    fun enqueue(node: IrDirectoryNode) {
        pending.incrementAndGet()
        queue.trySend(node)
    }

    roots.forEach(::enqueue)

    coroutineScope {
        val workers = List(8) {
            launch(Dispatchers.IO) {
                for (node in queue) {
                    try {
                        val path = node.relativeSegments.joinToString("/")
                        reportDiscovery(path)
                        val children = queryIrChildren(rootUri, node.documentId)
                        for (child in children) {
                            if (child.isDirectory) {
                                if (!child.name.startsWith("_IRDB_", ignoreCase = true)) {
                                    enqueue(
                                        IrDirectoryNode(
                                            documentId = child.documentId,
                                            source = node.source,
                                            relativeSegments = node.relativeSegments + child.name,
                                        ),
                                    )
                                }
                            } else if (child.name.endsWith(".ir", ignoreCase = true)) {
                                val relativePath = (
                                    node.relativeSegments + child.name
                                    ).joinToString("/")
                                reportDiscovery(relativePath)
                                val pathWithoutFile = node.relativeSegments.drop(2)
                                val meaningfulSegments = pathWithoutFile.filterNot {
                                    it.startsWith("Flipper-IRDB", ignoreCase = true)
                                }
                                val category = when (node.source) {
                                    IrRemoteSource.IMPORTED -> IR_IMPORTED_CATEGORY
                                    IrRemoteSource.RECEIVED -> IR_RECEIVED_CATEGORY
                                    IrRemoteSource.DATABASE ->
                                        meaningfulSegments.firstOrNull() ?: "Database"
                                }
                                val brand = when (node.source) {
                                    IrRemoteSource.IMPORTED,
                                    IrRemoteSource.RECEIVED -> ""
                                    IrRemoteSource.DATABASE ->
                                        meaningfulSegments.drop(1).firstOrNull().orEmpty()
                                }
                                candidates += IrScanCandidate(
                                    documentUri = child.documentUri,
                                    fileName = child.name,
                                    relativePath = relativePath,
                                    source = node.source,
                                    category = category,
                                    brand = brand,
                                    lastModifiedMs = child.lastModifiedMs,
                                    sizeBytes = child.sizeBytes,
                                )
                            }
                        }
                    } catch (t: Throwable) {
                        Log.w("IrRemote", "IR directory scan failed for ${node.relativeSegments}: ${t.message}")
                    } finally {
                        if (pending.decrementAndGet() == 0) queue.close()
                    }
                }
            }
        }
        workers.joinAll()
    }

    Log.d(
        "IrRemote",
        "IR discovery complete: entries=${discovered.get()} candidates=${candidates.size}",
    )
    return candidates.toList()
}

private data class IrChildRecord(
    val documentId: String,
    val documentUri: String,
    val name: String,
    val isDirectory: Boolean,
    val lastModifiedMs: Long,
    val sizeBytes: Long,
)

private fun MainViewModel.queryIrChildren(
    rootUri: Uri,
    parentDocumentId: String,
): List<IrChildRecord> {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, parentDocumentId)
    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_SIZE,
    )
    val result = mutableListOf<IrChildRecord>()
    app.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
        val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
        while (cursor.moveToNext()) {
            val documentId = if (idIndex >= 0) cursor.getString(idIndex) else null
            val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
            if (documentId.isNullOrBlank() || name.isNullOrBlank()) continue
            val mimeType = if (mimeIndex >= 0) cursor.getString(mimeIndex) else null
            result += IrChildRecord(
                documentId = documentId,
                documentUri = DocumentsContract.buildDocumentUriUsingTree(rootUri, documentId).toString(),
                name = name,
                isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR,
                lastModifiedMs = if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) {
                    cursor.getLong(modifiedIndex)
                } else {
                    0L
                },
                sizeBytes = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    cursor.getLong(sizeIndex)
                } else {
                    0L
                },
            )
        }
    }
    return result
}

private fun documentIdFor(uri: Uri): String? {
    return runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
}

private data class IrMetadata(
    val buttonCount: Int,
    val protocols: List<String>,
    val buttonNames: List<String>,
)

private fun MainViewModel.readIrMetadata(candidate: IrScanCandidate): IrMetadata? {
    val content = runCatching {
        app.contentResolver.openInputStream(Uri.parse(candidate.documentUri))
            ?.bufferedReader()
            ?.use { it.readText() }
    }.getOrNull() ?: return null
    var buttonCount = 0
    val protocols = linkedSetOf<String>()
    val buttonNames = mutableListOf<String>()
    content.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEach
        when {
            line.startsWith("name:", ignoreCase = true) -> {
                buttonCount++
                line.substringAfter(':').trim().takeIf { it.isNotBlank() }?.let(buttonNames::add)
            }
            line.startsWith("protocol:", ignoreCase = true) -> {
                line.substringAfter(':').trim().takeIf { it.isNotBlank() }?.let(protocols::add)
            }
            line.startsWith("type:", ignoreCase = true) &&
                line.substringAfter(':').trim().equals("raw", ignoreCase = true) -> {
                protocols += "Raw"
            }
        }
    }
    if (buttonCount <= 0) return null
    return IrMetadata(
        buttonCount = buttonCount,
        protocols = protocols.toList().sorted(),
        buttonNames = buttonNames,
    )
}

private fun ensureDocumentDirectory(parent: DocumentFile, name: String): DocumentFile? {
    return parent.findFile(name)?.takeIf { it.isDirectory } ?: parent.createDirectory(name)
}

private fun sanitizeIrFileName(value: String): String {
    val cleaned = value.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
    return if (cleaned.endsWith(".ir", ignoreCase = true)) cleaned else "$cleaned.ir"
}

private fun displayIrName(value: String): String = value.replace('_', ' ').trim()

internal const val MIN_IR_COMMON_BUTTON_REMOTES = 3
internal const val DEFAULT_IR_COMMON_BUTTON_MIN_REMOTES = 10
private const val MAX_IR_SEARCH_CANDIDATES = 2_000

internal fun MainViewModel.prepareIrCommonButtonsImpl(
    category: String,
    brand: String,
    minRemotes: Int,
) {
    val threshold = minRemotes.coerceIn(MIN_IR_COMMON_BUTTON_REMOTES, 100)
    val key = irCommonButtonCacheKey(category, brand, threshold)
    val cached = irCommonButtonCache[key]
    _irCommonButtonsState.value = if (cached != null) {
        IrCommonButtonsState(
            category = category,
            brand = brand,
            minRemotes = threshold,
            loaded = true,
            buttons = cached,
        )
    } else {
        IrCommonButtonsState(
            category = category,
            brand = brand,
            minRemotes = threshold,
            loaded = false,
        )
    }
}

internal fun MainViewModel.refreshIrCommonButtonsImpl(
    category: String,
    brand: String,
    minRemotes: Int,
) {
    if (_irCommonButtonsState.value.loading) return
    val threshold = minRemotes.coerceIn(MIN_IR_COMMON_BUTTON_REMOTES, 100)
    val key = irCommonButtonCacheKey(category, brand, threshold)
    _irCommonButtonsState.value = IrCommonButtonsState(
        category = category,
        brand = brand,
        minRemotes = threshold,
        loading = true,
    )
    scope.launch(Dispatchers.Default) {
        try {
            val filtered = _irRemotes.value.asSequence()
                .filter { category == "All categories" || it.category == category }
                .filter { brand == "All brands" || it.brand.equals(brand, ignoreCase = true) }
                .toList()
            val counts = linkedMapOf<String, Int>()
            filtered.forEach { remote ->
                remote.buttonNames
                    .asSequence()
                    .map(::canonicalIrButtonName)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .forEach { keyName ->
                        counts[keyName] = (counts[keyName] ?: 0) + 1
                    }
            }
            val buttons = counts.asSequence()
                .filter { it.value >= threshold }
                .map { (name, count) ->
                    IrCommonButton(
                        name = prettyCommonIrButtonName(name),
                        count = count,
                    )
                }
                .sortedWith(
                    compareByDescending<IrCommonButton> { it.count }
                        .thenBy { it.name.lowercase() },
                )
                .toList()
            irCommonButtonCache[key] = buttons
            val current = _irCommonButtonsState.value
            if (
                current.category == category &&
                current.brand == brand &&
                current.minRemotes == threshold
            ) {
                _irCommonButtonsState.value = IrCommonButtonsState(
                    category = category,
                    brand = brand,
                    minRemotes = threshold,
                    loaded = true,
                    buttons = buttons,
                )
            }
        } catch (t: Throwable) {
            val current = _irCommonButtonsState.value
            if (
                current.category == category &&
                current.brand == brand &&
                current.minRemotes == threshold
            ) {
                _irCommonButtonsState.value = current.copy(
                    loading = false,
                    loaded = false,
                    errorMessage = t.message ?: "Could not build common-button list.",
                )
            }
        }
    }
}

internal fun MainViewModel.startIrSearchImpl(
    category: String,
    brand: String,
    selectedButtons: List<String>,
    intervalMs: Int,
) {
    irSearchJob?.cancel()
    irSearchJob = null
    if (category.isBlank() || category == "All categories") {
        _irSearchState.value = IrSearchUiState(
            runState = IrSearchRunState.ERROR,
            errorMessage = "Select a specific category before starting the search.",
        )
        return
    }
    val selectedKeys = selectedButtons
        .map(::canonicalIrButtonName)
        .filter { it.isNotBlank() }
        .toSet()
    if (selectedKeys.isEmpty()) {
        _irSearchState.value = IrSearchUiState(
            runState = IrSearchRunState.ERROR,
            errorMessage = "Select at least one common button before starting the search.",
        )
        return
    }

    val matches = _irRemotes.value.asSequence()
        .filter { category == "All categories" || it.category == category }
        .filter { brand == "All brands" || it.brand.equals(brand, ignoreCase = true) }
        .flatMap { remote ->
            remote.buttonNames.asSequence()
                .map { original -> original to canonicalIrButtonName(original) }
                .filter { (_, canonical) -> canonical in selectedKeys }
                .distinctBy { (_, canonical) -> canonical }
                .map { (original, _) ->
                    IrSearchCandidate(
                        remoteId = remote.id,
                        remoteName = remote.name,
                        category = remote.category,
                        brand = remote.brand,
                        buttonName = original,
                    )
                }
        }
        .sortedWith(
            compareBy<IrSearchCandidate> { it.brand.lowercase() }
                .thenBy { it.remoteName.lowercase() }
                .thenBy { it.buttonName.lowercase() },
        )
        .toList()

    if (matches.isEmpty()) {
        _irSearchState.value = IrSearchUiState(
            runState = IrSearchRunState.ERROR,
            errorMessage = if (_irRemotes.value.any { it.buttonNames.isEmpty() }) {
                "No matching buttons found. Rescan the IR library to rebuild the button-name index."
            } else {
                "No remotes matched that category, brand, and selected button combination."
            },
        )
        return
    }
    if (matches.size > MAX_IR_SEARCH_CANDIDATES) {
        _irSearchState.value = IrSearchUiState(
            runState = IrSearchRunState.ERROR,
            errorMessage = "Too many candidates (${matches.size}). Select a brand or a narrower category before searching.",
        )
        return
    }

    val backendError = irBackendError()
    if (backendError != null) {
        _irSearchState.value = IrSearchUiState(
            runState = IrSearchRunState.ERROR,
            candidates = matches,
            intervalMs = intervalMs.coerceIn(500, 10_000),
            errorMessage = backendError,
        )
        return
    }

    _irSearchState.value = IrSearchUiState(
        runState = IrSearchRunState.RUNNING,
        candidates = matches,
        currentIndex = 0,
        intervalMs = intervalMs.coerceIn(500, 10_000),
        autoStepping = true,
        loopingCurrent = false,
        message = "Auto-searching ${matches.size} candidate(s).",
    )
    startIrSearchAutoJob()
}

private fun MainViewModel.startIrSearchAutoJob() {
    irSearchJob?.cancel()
    val job = scope.launch(Dispatchers.IO) {
        try {
            while (isActive) {
                val state = _irSearchState.value
                if (!state.autoStepping || state.runState != IrSearchRunState.RUNNING) break
                if (state.currentIndex >= state.candidates.size) {
                    _irSearchState.value = state.copy(
                        runState = IrSearchRunState.COMPLETED,
                        autoStepping = false,
                        loopingCurrent = false,
                        message = if (state.foundCandidates.isEmpty()) {
                            "Search complete. No match was saved."
                        } else {
                            "Search complete. ${state.foundCandidates.size} result(s) saved."
                        },
                    )
                    break
                }
                val candidate = state.currentCandidate ?: break
                sendIrSearchCandidate(candidate)
                val sentIndex = state.currentIndex
                var elapsed = 0
                while (elapsed < state.intervalMs && isActive) {
                    delay(100)
                    elapsed += 100
                    val latest = _irSearchState.value
                    if (!latest.autoStepping ||
                        latest.runState != IrSearchRunState.RUNNING ||
                        latest.currentIndex != sentIndex
                    ) {
                        break
                    }
                }
                val latest = _irSearchState.value
                if (latest.autoStepping &&
                    latest.runState == IrSearchRunState.RUNNING &&
                    latest.currentIndex == sentIndex
                ) {
                    val next = sentIndex + 1
                    _irSearchState.value = latest.copy(
                        currentIndex = next,
                        message = if (next >= latest.candidates.size) {
                            "Search complete. No match was saved."
                        } else {
                            "Auto-searching candidate ${next + 1} of ${latest.candidates.size}."
                        },
                    )
                }
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            _irSearchState.value = _irSearchState.value.copy(
                runState = IrSearchRunState.ERROR,
                autoStepping = false,
                loopingCurrent = false,
                errorMessage = t.message ?: "IR search failed.",
            )
            Log.e("IrRemote", "IR search failed", t)
        } finally {
            if (irSearchJob == coroutineContext[Job]) irSearchJob = null
        }
    }
    irSearchJob = job
}

internal fun MainViewModel.pauseIrSearchImpl() {
    irSearchJob?.cancel()
    irSearchJob = null
    _irSearchState.value = _irSearchState.value.copy(
        runState = IrSearchRunState.PAUSED,
        autoStepping = false,
        loopingCurrent = false,
        message = "Auto-step paused.",
    )
}

internal fun MainViewModel.resumeIrSearchImpl() {
    val state = _irSearchState.value
    if (state.candidates.isEmpty() ||
        state.runState in setOf(
            IrSearchRunState.COMPLETED,
            IrSearchRunState.STOPPED,
            IrSearchRunState.ERROR,
        )
    ) {
        return
    }
    _irSearchState.value = state.copy(
        runState = IrSearchRunState.RUNNING,
        autoStepping = true,
        loopingCurrent = false,
        message = "Auto-step resumed.",
    )
    startIrSearchAutoJob()
}

internal fun MainViewModel.previousIrSearchImpl() {
    moveIrSearchCandidate(delta = -1)
}

internal fun MainViewModel.nextIrSearchImpl() {
    moveIrSearchCandidate(delta = 1)
}

private fun MainViewModel.moveIrSearchCandidate(delta: Int) {
    val state = _irSearchState.value
    if (state.candidates.isEmpty()) return
    irSearchJob?.cancel()
    irSearchJob = null
    val target = (state.currentIndex + delta).coerceIn(0, state.candidates.size - 1)
    if (state.loopingCurrent) {
        _irSearchState.value = state.copy(
            currentIndex = target,
            runState = IrSearchRunState.RUNNING,
            autoStepping = false,
            loopingCurrent = true,
            message = "Looping candidate ${target + 1} of ${state.candidates.size}.",
        )
        startIrSearchLoopJob()
    } else {
        _irSearchState.value = state.copy(
            currentIndex = target,
            runState = IrSearchRunState.PAUSED,
            autoStepping = false,
            loopingCurrent = false,
            message = "Manual candidate ${target + 1} of ${state.candidates.size}.",
        )
        sendCurrentIrSearchCandidate()
    }
}

internal fun MainViewModel.manualSendIrSearchImpl() {
    val state = _irSearchState.value
    val candidate = state.currentCandidate ?: return
    irSearchJob?.cancel()
    irSearchJob = null
    _irSearchState.value = state.copy(
        runState = IrSearchRunState.PAUSED,
        autoStepping = false,
        loopingCurrent = false,
        message = "Manual send: ${displayIrName(candidate.buttonName)}.",
    )
    sendCurrentIrSearchCandidate()
}

internal fun MainViewModel.toggleIrSearchLoopImpl() {
    val state = _irSearchState.value
    if (state.currentCandidate == null) return
    if (state.loopingCurrent) {
        irSearchJob?.cancel()
        irSearchJob = null
        _irSearchState.value = state.copy(
            runState = IrSearchRunState.PAUSED,
            autoStepping = false,
            loopingCurrent = false,
            message = "Current-signal loop stopped.",
        )
        return
    }
    irSearchJob?.cancel()
    irSearchJob = null
    _irSearchState.value = state.copy(
        runState = IrSearchRunState.RUNNING,
        autoStepping = false,
        loopingCurrent = true,
        message = "Looping current candidate.",
    )
    startIrSearchLoopJob()
}

private fun MainViewModel.startIrSearchLoopJob() {
    irSearchJob?.cancel()
    val job = scope.launch(Dispatchers.IO) {
        try {
            while (isActive) {
                val latest = _irSearchState.value
                if (!latest.loopingCurrent) break
                val candidate = latest.currentCandidate ?: break
                sendIrSearchCandidate(candidate)
                var elapsed = 0
                while (elapsed < latest.intervalMs && isActive) {
                    delay(100)
                    elapsed += 100
                    if (!_irSearchState.value.loopingCurrent) break
                }
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            _irSearchState.value = _irSearchState.value.copy(
                runState = IrSearchRunState.ERROR,
                autoStepping = false,
                loopingCurrent = false,
                errorMessage = t.message ?: "IR search loop failed.",
            )
        } finally {
            if (irSearchJob == coroutineContext[Job]) irSearchJob = null
        }
    }
    irSearchJob = job
}

private fun MainViewModel.sendCurrentIrSearchCandidate() {
    val candidate = _irSearchState.value.currentCandidate ?: return
    scope.launch(Dispatchers.IO) {
        try {
            sendIrSearchCandidate(candidate)
            _irSearchState.value = _irSearchState.value.copy(
                message = "Sent ${candidate.remoteName} · ${displayIrName(candidate.buttonName)}.",
            )
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            _irSearchState.value = _irSearchState.value.copy(
                runState = IrSearchRunState.ERROR,
                autoStepping = false,
                loopingCurrent = false,
                errorMessage = t.message ?: "IR search send failed.",
            )
        }
    }
}

private suspend fun MainViewModel.sendIrSearchCandidate(candidate: IrSearchCandidate) {
    val remote = _irRemotes.value.firstOrNull { it.id == candidate.remoteId } ?: return
    val signals = loadIrSignalsForSummary(remote)
    val signal = signals.firstOrNull {
        it.name.equals(candidate.buttonName, ignoreCase = true)
    } ?: signals.firstOrNull {
        canonicalIrButtonName(it.name) == canonicalIrButtonName(candidate.buttonName)
    } ?: throw IllegalArgumentException("Could not find ${candidate.buttonName} in ${remote.name}.")
    val encoded = when (val result = IrProtocolEncoder.encode(signal)) {
        is IrEncodeResult.Success -> result
        is IrEncodeResult.Failure -> throw IllegalArgumentException(result.message)
    }
    irPhoneTransmitter.transmit(encoded.frequencyHz, encoded.timingsUs)
        .exceptionOrNull()
        ?.let { throw it }
    markIrRemoteUsed(remote.id)
}

internal fun MainViewModel.stopIrSearchImpl() {
    irSearchJob?.cancel()
    irSearchJob = null
    _irSearchState.value = _irSearchState.value.copy(
        runState = IrSearchRunState.STOPPED,
        autoStepping = false,
        loopingCurrent = false,
        message = "Search stopped.",
    )
}

internal fun MainViewModel.removeIrSearchMarkedResultImpl(
    remoteId: String,
    buttonName: String,
) {
    _irSearchState.value = _irSearchState.value.copy(
        foundCandidates = _irSearchState.value.foundCandidates.filterNot {
            it.remoteId == remoteId && it.buttonName == buttonName
        },
    )
}

internal fun MainViewModel.markIrSearchFoundImpl() {
    val state = _irSearchState.value
    val candidate = state.currentCandidate ?: return
    irSearchJob?.cancel()
    irSearchJob = null
    val now = System.currentTimeMillis()
    val updated = _irRemotes.value.map {
        if (it.id == candidate.remoteId) {
            it.copy(favorite = true, lastUsedAtMs = now)
        } else {
            it
        }
    }.sortedWith(
        compareByDescending<IrRemoteSummary> { it.favorite }
            .thenBy { it.name.lowercase() },
    )
    _irRemotes.value = updated
    scope.launch(Dispatchers.IO) { irRemoteIndexStore.save(updated) }
    val alreadyMarked = state.foundCandidates.any {
        it.remoteId == candidate.remoteId && it.buttonName == candidate.buttonName
    }
    val found = if (alreadyMarked) {
        state.foundCandidates
    } else {
        state.foundCandidates + candidate
    }
    _irSearchState.value = state.copy(
        runState = IrSearchRunState.PAUSED,
        autoStepping = false,
        loopingCurrent = false,
        foundCandidates = found,
        message = "Marked ${candidate.remoteName} · ${displayIrName(candidate.buttonName)}.",
    )
}

private fun irCommonButtonCacheKey(category: String, brand: String, minRemotes: Int): String =
    "$category|$brand|$minRemotes"

private fun canonicalIrButtonName(value: String): String {
    return value.trim()
        .lowercase()
        .replace('_', ' ')
        .replace(Regex("\\s+"), " ")
}

private fun prettyCommonIrButtonName(canonical: String): String {
    return canonical.split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase() else char.toString()
            }
        }
}

private suspend fun MainViewModel.loadIrSignalsForSummary(
    summary: IrRemoteSummary,
): List<IrSignal> {
    val content = app.contentResolver.openInputStream(Uri.parse(summary.documentUri))
        ?.bufferedReader()
        ?.use { it.readText() }
        ?: return emptyList()
    return IrFileParser.parse(content).mapIndexed { index, signal ->
        signal.copy(id = "${summary.id}:$index", sourceName = summary.name)
    }
}

private fun MainViewModel.markIrRemoteUsed(summaryId: String) {
    val now = System.currentTimeMillis()
    val updated = _irRemotes.value.map {
        if (it.id == summaryId) it.copy(lastUsedAtMs = now) else it
    }
    _irRemotes.value = updated
    scope.launch(Dispatchers.IO) { irRemoteIndexStore.save(updated) }
}
