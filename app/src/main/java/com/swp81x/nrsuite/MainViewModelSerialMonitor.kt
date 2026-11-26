package com.swp81x.nrsuite

import android.hardware.usb.UsbDevice
import android.provider.DocumentsContract
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.serial.SerialLineDirection
import com.swp81x.nrsuite.core.serial.SerialLogLine
import com.swp81x.nrsuite.core.serial.StreamingUtf8Decoder
import com.swp81x.nrsuite.core.serial.formatSerialLogText
import com.swp81x.nrsuite.core.usb.UsbSerialDevice
import com.swp81x.nrsuite.core.usb.UsbSerialTransport
import java.io.IOException
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Raw USB serial monitor. This is intentionally independent of NrSession /
// BridgeProtocol so it can be used with any ESP32 or USB-UART firmware.

internal fun MainViewModel.serialMonitorOwnsDeviceImpl(device: UsbDevice): Boolean {
    val monitorDevice = _serialMonitorSelected.value?.device ?: return false
    val active = _serialMonitorConnected.value ||
        _serialMonitorConnecting.value ||
        serialMonitorTransport != null
    if (!active) return false
    return monitorDevice.deviceId == device.deviceId ||
        monitorDevice.deviceName == device.deviceName
}

private val SERIAL_LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
private const val MAX_SERIAL_MONITOR_LOG_LINES = 5_000
private const val MAX_SERIAL_MONITOR_LINE_CHARS = 32_768

internal fun MainViewModel.markSerialMonitorPermissionRequestedImpl(device: UsbDevice) {
    serialMonitorPermissionDeviceId = device.deviceId
    markPermissionRequested(device)
}

internal fun MainViewModel.selectSerialMonitorDeviceImpl(device: UsbSerialDevice?) {
    if (_serialMonitorConnected.value || _serialMonitorConnecting.value) return
    _serialMonitorSelected.value = device
    _serialMonitorBlockingMessage.value = null
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

internal fun MainViewModel.setSerialMonitorHexModeImpl(enabled: Boolean) {
    _serialMonitorHexMode.value = enabled
}

internal fun MainViewModel.setSerialMonitorShowTimestampsImpl(enabled: Boolean) {
    _serialMonitorShowTimestamps.value = enabled
    preferences.edit().putBoolean(PREF_SERIAL_MONITOR_SHOW_TIMESTAMPS, enabled).apply()
}

internal fun MainViewModel.setSerialMonitorWrapLinesImpl(enabled: Boolean) {
    _serialMonitorWrapLines.value = enabled
    preferences.edit().putBoolean(PREF_SERIAL_MONITOR_WRAP_LINES, enabled).apply()
}

internal fun MainViewModel.setSerialMonitorFollowOutputImpl(enabled: Boolean) {
    _serialMonitorFollowOutput.value = enabled
}

internal fun MainViewModel.setSerialMonitorTextSizeSpImpl(sizeSp: Float) {
    val normalized = sizeSp.coerceIn(10f, 24f)
    _serialMonitorTextSizeSp.value = normalized
    preferences.edit().putFloat(PREF_SERIAL_MONITOR_TEXT_SIZE_SP, normalized).apply()
}

internal fun MainViewModel.clearSerialMonitorLogImpl() {
    synchronized(serialMonitorLogLock) {
        serialMonitorLineAccumulator.clear()
        serialMonitorPartialTimestamp = null
        _serialMonitorPartialLine.value = null
        _serialMonitorLogLines.value = emptyList()
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
        _serialMonitorBlockingMessage.value = message
        _serialMonitorError.value = null
        appendLog(message, level = LogLevel.ERROR)
        return
    }

    dismissUsbAttachPrompt(device.device.deviceId)

    _serialMonitorSelected.value = device
    _serialMonitorBaud.value = baudRate
    _serialMonitorBlockingMessage.value = null
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
            val decoder = StreamingUtf8Decoder()

            while (isActive && transport.isOpen) {
                val read = try {
                    transport.read(buffer, 250)
                } catch (error: IOException) {
                    withContext(Dispatchers.Main) {
                        _serialMonitorError.value = "Serial read failed: ${error.message}"
                        appendLog("Serial read failed: ${error.message}", level = LogLevel.ERROR)
                    }
                    break
                }
                if (read > 0) {
                    val decoded = decoder.decode(buffer, 0, read)
                    if (decoded.isNotEmpty()) {
                        appendSerialMonitorChunk(decoded)
                    }
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
                flushSerialMonitorPartialLine()
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
    _serialMonitorBlockingMessage.value = null

    flushSerialMonitorPartialLine()
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

    val input = _serialMonitorInput.value
    val ending = when (_serialMonitorLineEnding.value) {
        "LF" -> "\n"
        "CR" -> "\r"
        "CRLF" -> "\r\n"
        else -> ""
    }
    val payload = (input + ending).toByteArray(Charsets.UTF_8)
    _serialMonitorInput.value = ""

    if (input.isNotEmpty()) {
        appendSerialMonitorTransmittedText(input)
    }

    scope.launch(Dispatchers.IO) {
        try {
            transport.write(payload, 1000)
        } catch (error: Throwable) {
            withContext(Dispatchers.Main) {
                _serialMonitorError.value = "Serial write failed: ${error.message}"
                appendLog("Serial write failed: ${error.message}", level = LogLevel.ERROR)
            }
        }
    }
}

internal fun MainViewModel.exportSerialMonitorLogImpl() {
    val lines = _serialMonitorLogLines.value
    val partialLine = _serialMonitorPartialLine.value
    if (lines.isEmpty() && partialLine == null) {
        val message = "There is no Serial Monitor log to export."
        _serialMonitorError.value = message
        _toastMessage.value = message
        return
    }

    val rootUri = _exportDirectory.value
    if (rootUri == null) {
        val message = "Set an NRSuite root directory before exporting."
        _serialMonitorError.value = message
        _toastMessage.value = message
        return
    }

    val logText = formatSerialLogText(
        lines = lines,
        partialLine = partialLine,
        showTimestamps = _serialMonitorShowTimestamps.value,
        hexMode = _serialMonitorHexMode.value,
    )

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
                val exportedPath = "${displayNameForTreeUri(rootUri)}/Logs/$fileName"
                _serialMonitorError.value = null
                appendLog("Serial Monitor log exported to $exportedPath.")
                _toastMessage.value = "Serial log saved to Logs/$fileName"
            }
        } catch (error: Throwable) {
            withContext(Dispatchers.Main) {
                val message = "Serial log export failed: ${error.message ?: "unknown error"}"
                _serialMonitorError.value = message
                _toastMessage.value = message
                appendLog(message, level = LogLevel.ERROR)
            }
        }
    }
}

private fun MainViewModel.appendSerialMonitorChunk(chunk: String) {
    synchronized(serialMonitorLogLock) {
        val hadPartialLine = serialMonitorLineAccumulator.partialLine().isNotEmpty()
        val existingTimestamp = serialMonitorPartialTimestamp
        val completedLines = serialMonitorLineAccumulator.append(chunk)
        val now = currentSerialTimestamp()

        if (completedLines.isNotEmpty()) {
            val newLines = completedLines.mapIndexed { index, text ->
                SerialLogLine(
                    timestamp = if (index == 0 && hadPartialLine) {
                        existingTimestamp ?: now
                    } else {
                        now
                    },
                    text = text,
                )
            }
            _serialMonitorLogLines.update { current ->
                (current + newLines).takeLast(MAX_SERIAL_MONITOR_LOG_LINES)
            }
            serialMonitorPartialTimestamp = null
        }

        var partialText = serialMonitorLineAccumulator.partialLine()
        if (partialText.length > MAX_SERIAL_MONITOR_LINE_CHARS) {
            val timestamp = serialMonitorPartialTimestamp ?: now
            serialMonitorLineAccumulator.drainPartialLine()
            _serialMonitorLogLines.update { current ->
                (current + SerialLogLine(timestamp = timestamp, text = partialText))
                    .takeLast(MAX_SERIAL_MONITOR_LOG_LINES)
            }
            partialText = ""
        }

        if (partialText.isEmpty()) {
            serialMonitorPartialTimestamp = null
            _serialMonitorPartialLine.value = null
        } else {
            val timestamp = serialMonitorPartialTimestamp ?: now.also {
                serialMonitorPartialTimestamp = it
            }
            _serialMonitorPartialLine.value = SerialLogLine(
                timestamp = timestamp,
                text = partialText,
            )
        }
    }
}

private fun MainViewModel.appendSerialMonitorTransmittedText(text: String) {
    val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
    val now = currentSerialTimestamp()
    val lines = normalized.split('\n').map { line ->
        SerialLogLine(
            timestamp = now,
            text = line,
            direction = SerialLineDirection.TX,
        )
    }
    synchronized(serialMonitorLogLock) {
        _serialMonitorLogLines.update { current ->
            (current + lines).takeLast(MAX_SERIAL_MONITOR_LOG_LINES)
        }
    }
}

private fun MainViewModel.flushSerialMonitorPartialLine() {
    synchronized(serialMonitorLogLock) {
        val text = serialMonitorLineAccumulator.drainPartialLine()
        if (text.isNotEmpty()) {
            val timestamp = serialMonitorPartialTimestamp ?: currentSerialTimestamp()
            _serialMonitorLogLines.update { current ->
                (current + SerialLogLine(timestamp = timestamp, text = text))
                    .takeLast(MAX_SERIAL_MONITOR_LOG_LINES)
            }
        }
        serialMonitorPartialTimestamp = null
        _serialMonitorPartialLine.value = null
    }
}

private fun currentSerialTimestamp(): String = LocalTime.now().format(SERIAL_LOG_TIME_FORMAT)
