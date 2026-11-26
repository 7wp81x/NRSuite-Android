package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.session.ConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Explicit user actions for recovering from firmware heap pressure. Neither
 * path reboots automatically; the caller must present a user decision first.
 */
internal fun MainViewModel.stopModulesForMemoryImpl() {
    val activeSession = session ?: run {
        appendLog("Connect to a device before stopping modules.", level = LogLevel.ERROR)
        return
    }
    appendLog("Stopping active modules to recover firmware memory...", tag = "Device")
    stopActiveOperations()
    scope.launch {
        val response = runCatching {
            activeSession.sendCommand("STOP_ALL", timeoutMs = 3_000)
        }.getOrElse { error ->
            appendLog(
                "Stop-all request failed: ${error.message ?: error.javaClass.simpleName}",
                level = LogLevel.ERROR,
                tag = "Device",
            )
            null
        }
        if (response?.optBoolean("ok") == true) {
            activeSession.refreshStatus()
            appendLog("Active modules stopped; firmware memory telemetry refreshed.", tag = "Device")
            _toastMessage.value = "Active modules stopped."
        } else if (response != null) {
            appendLog(
                "Firmware rejected stop-all: ${response.optString("msg").ifBlank { "unknown error" }}",
                level = LogLevel.ERROR,
                tag = "Device",
            )
        }
    }
}

internal fun MainViewModel.restartEsp32Impl() {
    if (_deviceRestarting.value) return
    if (_connectionState.value !is ConnectionState.Connected) {
        appendLog("Connect to a device before restarting the ESP32.", level = LogLevel.ERROR)
        return
    }
    val activeSession = session ?: run {
        appendLog("No active device session is available for restart.", level = LogLevel.ERROR)
        return
    }

    // The reboot is explicit user action. Stop local orchestration before the
    // firmware disappears so periodic jobs do not report cascading failures.
    stopActiveOperations()
    _bleLowHeapWarning.value = null
    _deviceRestarting.value = true
    scope.launch {
        appendLog("Sending explicit ESP32 restart request...", tag = "Device")
        try {
            val response = activeSession.sendCommand("REBOOT", timeoutMs = 3_000)
            when {
                response?.optBoolean("ok") == true -> {
                    appendLog(
                        "ESP32 acknowledged restart. USB will re-enumerate; reconnect when it appears.",
                        level = LogLevel.SUCCESS,
                        tag = "Device",
                    )
                    _toastMessage.value = "ESP32 restarting. Reconnect when USB returns."
                }
                response != null -> {
                    appendLog(
                        "ESP32 restart rejected: ${response.optString("msg").ifBlank { "unknown error" }}",
                        level = LogLevel.ERROR,
                        tag = "Device",
                    )
                    _toastMessage.value = "ESP32 restart was rejected by firmware."
                }
                else -> {
                    // A reboot can win the race with the response even though
                    // firmware sends OK first. Treat this as a request sent,
                    // not a proven failure.
                    appendLog(
                        "No restart response received; USB may already be re-enumerating.",
                        level = LogLevel.INFO,
                        tag = "Device",
                    )
                    _toastMessage.value = "Restart requested. Reconnect when USB returns."
                }
            }
        } finally {
            delay(1_200)
            _deviceRestarting.value = false
        }
    }
}
