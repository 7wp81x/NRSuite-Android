package com.swp81x.nrsuite

import android.hardware.usb.UsbDevice
import android.provider.DocumentsContract
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.usb.UsbSerialDevice
import com.swp81x.nrsuite.core.usb.UsbSerialTransport
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Raw USB serial monitor. This is intentionally independent of NrSession /
// BridgeProtocol so it can be used with any ESP32 or USB-UART firmware.

internal fun MainViewModel.markSerialMonitorPermissionRequestedImpl(device: UsbDevice) {
    serialMonitorPermissionDeviceId = device.deviceId
    markPermissionRequested(device)
}

internal fun MainViewModel.selectSerialMonitorDeviceImpl(device: UsbSerialDevice?) {
    if (_serialMonitorConnected.value || _serialMonitorConnecting.value) return
    _serialMonitorSelected.value = device
    _serialMonitorError.value = null
}

internal fun MainViewModel.setSerialMonitorBaudImpl(baudRate: Int) {
    if (_serialMonitorConnected.value || _serialMonitorConnecting.value) return
    _serialMonitorBaud.value = baudRate
}

internal fun MainViewModel.setSerialMonitorInputImpl(value: String) {
    _serialMonitorInput.value = value
}

internal fun MainViewModel.setSerialMonitorLineEndingImpl(value: String) {
    if (value in setOf("None", "LF", "CR", "CRLF")) {
        _serialMonitorLineEnding.value = value
    }
}

internal fun MainViewModel.startSerialMonitorImpl(device: UsbSerialDevice, baudRate: Int) {
    if (_serialMonitorConnected.value || _serialMonitorConnecting.value) return

    if (!usbManager.hasPermission(device.device)) {
        _serialMonitorError.value = "USB permission is required."
        appendLog("Serial Monitor: USB permission required for ${device.displayName}.", level = LogLevel.ERROR)
        return
    }

    val fingerprint = deviceFingerprintImpl(device.device)
    if (deviceSessions.containsKey(fingerprint) || idleDeviceSessions.containsKey(fingerprint)) {
        val message = "Disconnect the active NRSuite session before opening Serial Monitor."
        _serialMonitorError.value = message
        appendLog(message, level = LogLevel.ERROR)
        return
    }

    _serialMonitorSelected.value = device
    _serialMonitorBaud.value = baudRate
    _serialMonitorError.value = null
    _serialMonitorConnecting.value = true

    val transport = UsbSerialTransport(usbManager, device.driver, baudRate)
    serialMonitorTransport = transport

    serialMonitorReadJob = scope.launch(Dispatchers.IO) {
        try {
            transport.open()
            withContext(Dispatchers.Main) {
                _serialMonitorConnected.value = true
                _serialMonitorConnecting.value = false
                appendLog("Serial Monitor opened ${device.displayName} @ $baudRate baud.", level = LogLevel.USB)
            }

            val buffer = ByteArray(4096)
            while (isActive && transport.isOpen) {
                val read = try {
                    transport.read(buffer, 250)
                } catch (error: IOException) {
                    withContext(Dispatchers.Main) {
                        _serialMonitorError.value = "Serial read failed: ${error.message}"
                    }
                    break
                }
                if (read > 0) {
                    _serialMonitorLog.update { it + String(buffer, 0, read, Charsets.UTF_8) }
                }
            }
        } catch (error: Throwable) {
            withContext(Dispatchers.Main) {
                _serialMonitorError.value = error.message ?: "Serial Monitor failed."
                appendLog("Serial Monitor failed: ${error.message}", level = LogLevel.ERROR)
            }
        } finally {
            runCatching { transport.close() }
            withContext(Dispatchers.Main) {
                if (serialMonitorTransport === transport) {
                    serialMonitorTransport = null
                }
                _serialMonitorConnected.value = false
                _serialMonitorConnecting.value = false
            }
        }
    }
}

internal fun MainViewModel.stopSerialMonitorImpl() {
    serialMonitorReadJob?.cancel()
    serialMonitorReadJob = null

    val transport = serialMonitorTransport
    serialMonitorTransport = null
    _serialMonitorConnected.value = false
    _serialMonitorConnecting.value = false

    if (transport != null) {
        scope.launch(Dispatchers.IO) {
            runCatching { transport.close() }
        }
        appendLog("Serial Monitor closed.", level = LogLevel.USB)
    }
}

internal fun MainViewModel.sendSerialMonitorInputImpl() {
    val transport = serialMonitorTransport
    if (!_serialMonitorConnected.value || transport == null) {
        _serialMonitorError.value = "Connect Serial Monitor before sending data."
        return
    }

    val ending = when (_serialMonitorLineEnding.value) {
        "LF" -> "\n"
        "CR" -> "\r"
        "CRLF" -> "\r\n"
        else -> ""
    }
    val payload = (_serialMonitorInput.value + ending).toByteArray(Charsets.UTF_8)
    _serialMonitorInput.value = ""

    scope.launch(Dispatchers.IO) {
        try {
            transport.write(payload, 1000)
        } catch (error: Throwable) {
            withContext(Dispatchers.Main) {
                _serialMonitorError.value = "Serial write failed: ${error.message}"
            }
        }
    }
}

internal fun MainViewModel.exportSerialMonitorLogImpl() {
    val logText = _serialMonitorLog.value
    if (logText.isBlank()) {
        _serialMonitorError.value = "There is no Serial Monitor log to export."
        return
    }

    val rootUri = _exportDirectory.value
    if (rootUri == null) {
        _serialMonitorError.value = "Set an NRSuite root directory before exporting."
        return
    }

    scope.launch(Dispatchers.IO) {
        try {
            val logsDirectory = ensureChildDirectory(rootUri, "Logs")?.uri ?: rootUri
            val timestamp = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"),
            )
            val fileName = "serial-monitor-$timestamp.txt"
            val documentUri = DocumentsContract.createDocument(
                app.contentResolver,
                logsDirectory,
                "text/plain",
                fileName,
            ) ?: throw IOException("Storage provider did not create the log file")

            app.contentResolver.openOutputStream(documentUri, "wt")?.use { output ->
                output.write(logText.toByteArray(Charsets.UTF_8))
            } ?: throw IOException("Could not open the export file")

            withContext(Dispatchers.Main) {
                appendLog("Serial Monitor log exported to ${displayNameForTreeUri(rootUri)}/Logs/$fileName.")
            }
        } catch (error: Throwable) {
            withContext(Dispatchers.Main) {
                _serialMonitorError.value = "Serial log export failed: ${error.message}"
                appendLog("Serial Monitor export failed: ${error.message}", level = LogLevel.ERROR)
            }
        }
    }
}
