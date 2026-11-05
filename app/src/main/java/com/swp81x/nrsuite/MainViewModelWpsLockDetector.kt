package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.defense.OuiRuleAction
import com.swp81x.nrsuite.core.defense.matchOuiRule
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.wps.BssidLockSnapshot
import com.swp81x.nrsuite.core.wps.WpsLockAccessPoint
import com.swp81x.nrsuite.core.wps.WpsLockObservationSource
import com.swp81x.nrsuite.core.wps.shouldRetryWpsTargetScan
import com.swp81x.nrsuite.core.wifi.NetworkTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

internal fun MainViewModel.startWpsLockScanImpl() {
    val activeSession = session
    if (activeSession == null) {
        appendLog("WPS Lock Detector: connect to a device before scanning.", level = LogLevel.ERROR)
        _toastMessage.value = "Connect a device before scanning."
        return
    }
    if (_wpsLockDetector.value.scanning) return
    if (!ensureRadioIdle("WPS Lock Detector")) return

    wpsLockScanJob?.cancel()
    _wpsLockDetector.update {
        it.copy(
            scanning = true,
            accessPoints = emptyList(),
            error = null,
        )
    }
    appendLog("WPS Lock Detector: scanning for WPS-enabled access points...")
    updateForegroundService()

    wpsLockScanJob = scope.launch(Dispatchers.IO) {
        var success = false
        try {
            val scan = runWpsLockScanWithRecovery(activeSession)
            val visibleCount = _wpsLockDetector.value.accessPoints.size
            if (scan.response?.optBoolean("ok") == true) {
                success = true
                if (shouldRetryWpsTargetScan(scan.reportedCount, scan.receivedCount)) {
                    appendLog(
                        "WPS Lock Detector: scan returned ${scan.reportedCount} WPS AP(s) but only " +
                            "${scan.receivedCount} event(s) arrived; kept $visibleCount partial AP(s).",
                        level = LogLevel.ERROR,
                    )
                } else {
                    appendLog("WPS Lock Detector: scan complete (${scan.reportedCount} WPS AP(s)).")
                }
            } else {
                val message = scan.response?.optString("msg")?.takeIf { it.isNotBlank() }
                    ?: scan.response?.optString("error")?.takeIf { it.isNotBlank() }
                    ?: "scan timed out"
                _wpsLockDetector.update { it.copy(error = message) }
                appendLog("WPS Lock Detector: scan failed: $message", level = LogLevel.ERROR)
                _toastMessage.value = "WPS lock scan failed: $message"
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val message = error.message ?: error.javaClass.simpleName
            _wpsLockDetector.update { it.copy(error = message) }
            appendLog("WPS Lock Detector: scan failed: $message", level = LogLevel.ERROR)
            _toastMessage.value = "WPS lock scan failed: $message"
        } finally {
            _wpsLockDetector.update {
                it.copy(
                    scanning = false,
                    lastScanAtMs = if (success) System.currentTimeMillis() else it.lastScanAtMs,
                )
            }
            updateForegroundService()
        }
    }
}

internal fun MainViewModel.handleWpsLockApEventImpl(event: JSONObject) {
    val bssid = event.optString("bssid").trim().uppercase()
    val channel = event.optInt("channel", 0)
    if (bssid.isBlank() || channel <= 0) return
    val vendor = ouiDatabaseRepository.lookup(bssid)?.vendor
    val ouiRule = matchOuiRule(bssid, _ouiRules.value)
    val ap = WpsLockAccessPoint(
        ssid = event.optString("ssid").ifBlank { "(hidden)" },
        bssid = bssid,
        channel = channel,
        rssi = event.optInt("rssi", -127),
        security = event.optString("security", "?"),
        wpsParsed = event.optBoolean("wps_parsed", true),
        lockReported = event.optBoolean("lock_reported", false),
        apSetupLocked = event.optBoolean("ap_setup_locked", false),
        wpsVersion = event.optInt("wps_version", 0),
        configMethods = event.optInt("config_methods", 0),
        selectedRegistrar = event.optBoolean("selected_registrar", false),
        lastSeenMs = event.optLong("last_seen_ms", System.currentTimeMillis()),
        vendor = vendor,
        ouiWhitelisted = ouiRule?.action == OuiRuleAction.WHITELIST,
        ouiBlacklisted = ouiRule?.action == OuiRuleAction.BLACKLIST,
    )
    _wpsLockDetector.update { state ->
        state.copy(
            accessPoints = (state.accessPoints.filterNot {
                it.bssid.equals(bssid, ignoreCase = true)
            } + ap).sortedByDescending { it.rssi },
        )
    }
    if (wifiScanDestination == WifiScanDestination.WPS) {
        observeWpsLockScanTargetImpl(ap)
    }
    if (wifiScanDestination == WifiScanDestination.WPS_ATTEMPT_TARGETS) {
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
        _wpsAttemptDetector.update { state ->
            state.copy(
                scanCandidates = (state.scanCandidates.filterNot {
                    it.bssid.equals(ap.bssid, ignoreCase = true)
                } + target).sortedByDescending { it.rssi },
                lockSnapshots = state.lockSnapshots + (
                    ap.bssid to BssidLockSnapshot(
                        state = ap.lockState,
                        lockReported = ap.lockReported,
                        wpsParsed = ap.wpsParsed,
                        source = WpsLockObservationSource.WPS_LOCK_SCAN,
                        receivedAtElapsedMs = android.os.SystemClock.elapsedRealtime(),
                    )
                ),
            )
        }
    }
}

internal fun MainViewModel.resetWpsLockDetectorImpl() {
    wpsLockScanJob?.cancel()
    wpsLockScanJob = null
    _wpsLockDetector.value = com.swp81x.nrsuite.core.wps.WpsLockDetectorUiState()
}
