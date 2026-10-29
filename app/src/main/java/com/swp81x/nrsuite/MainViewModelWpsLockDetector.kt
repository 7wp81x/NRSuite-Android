package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.defense.OuiRuleAction
import com.swp81x.nrsuite.core.defense.matchOuiRule
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.wps.WpsLockAccessPoint
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
            val args = JSONObject().apply {
                put("band", "auto")
                put("dwell_ms", 120)
            }
            val response = activeSession.sendCommand(
                "WPS_LOCK_SCAN",
                args,
                timeoutMs = 45_000,
            )
            if (response?.optBoolean("ok") == true) {
                success = true
                val count = response.optInt("count", _wpsLockDetector.value.accessPoints.size)
                appendLog("WPS Lock Detector: scan complete ($count WPS AP(s)).")
            } else {
                val message = response?.optString("msg")?.takeIf { it.isNotBlank() }
                    ?: response?.optString("error")?.takeIf { it.isNotBlank() }
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
}

internal fun MainViewModel.resetWpsLockDetectorImpl() {
    wpsLockScanJob?.cancel()
    wpsLockScanJob = null
    _wpsLockDetector.value = com.swp81x.nrsuite.core.wps.WpsLockDetectorUiState()
}
