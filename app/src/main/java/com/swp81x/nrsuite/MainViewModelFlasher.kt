package com.swp81x.nrsuite

import android.hardware.usb.UsbDevice
import android.net.Uri
import android.provider.OpenableColumns
import com.swp81x.nrsuite.core.flasher.Esp32Flasher
import com.swp81x.nrsuite.core.flasher.UsbSerialFlasherTransport
import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.usb.UsbSerialDeviceCatalog
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Firmware image selection, flash-target selection, and ROM-bootloader flashing.

internal fun MainViewModel.setFirmwareFlashFileImpl(uri: Uri, name: String?) {
    _firmwareFlashUri.value = uri
    val resolvedName = queryDocumentDisplayNameImpl(uri)
        ?.takeIf { it.isNotBlank() }
        ?: name?.takeIf { it.isNotBlank() && !it.startsWith("msf:") && !it.startsWith("primary:") }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: "firmware.bin"
    _firmwareFlashName.value = resolvedName
    _firmwareFlashSize.value = queryDocumentSizeImpl(uri)
    appendLog("Firmware image selected: ${_firmwareFlashName.value}")
}

internal fun MainViewModel.queryDocumentDisplayNameImpl(uri: Uri): String? {
    return runCatching {
        app.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
            } else {
                null
            }
        }
    }.getOrNull()
}

internal fun MainViewModel.queryDocumentSizeImpl(uri: Uri): Long {
    return runCatching {
        app.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else 0L
            } else {
                0L
            }
        } ?: 0L
    }.getOrDefault(0L)
}

internal fun MainViewModel.clearFirmwareFlashFileImpl() {
    _firmwareFlashUri.value = null
    _firmwareFlashName.value = null
    _firmwareFlashSize.value = 0
    _firmwareFlashProgress.value = 0
    _firmwareFlashStatus.value = null
}

internal fun MainViewModel.selectFirmwareTargetImpl(device: UsbDevice) {
    val entry = _devices.value.firstOrNull { it.device.deviceId == device.deviceId }
        ?: UsbSerialDeviceCatalog.find(usbManager, device)
    if (entry == null) {
        appendLog("No supported USB serial driver for ${device.deviceName}.")
        return
    }
    _firmwareTargetDevice.value = entry
    appendLog("Firmware flash target: ${entry.displayName}")
}

internal fun MainViewModel.startFirmwareFlashImpl(targetChip: String, skipReset: Boolean) {
    val uri = _firmwareFlashUri.value
    if (uri == null) {
        appendLog("Choose a merged firmware .bin before flashing.")
        return
    }
    if (_firmwareFlashing.value) return

    val entry = _firmwareTargetDevice.value
    if (entry == null) {
        appendLog("Select a flash target device before flashing.")
        return
    }
    if (!usbManager.hasPermission(entry.device)) {
        appendLog("USB permission is required for the selected flash target.")
        return
    }

    _firmwareFlashing.value = true
    _firmwareFlashProgress.value = 0
    _firmwareFlashStatus.value = "Reading firmware image..."

    scope.launch {
        try {
            val bytes = withContext(Dispatchers.IO) {
                app.contentResolver
                    .openInputStream(uri)
                    ?.use { it.readBytes() }
            }
            if (bytes == null || bytes.isEmpty()) {
                throw IOException("Could not read the selected firmware image.")
            }

            _firmwareFlashStatus.value = "Stopping active modules..."
            stopActiveOperations()

            val currentSession = session
            sessionObservers.forEach { it.cancel() }
            sessionObservers = emptyList()
            session = null
            activeDeviceFingerprint = null
            _connectionState.value = ConnectionState.Disconnected
            _activeDeviceName.value = null
            currentSession?.disconnect()

            val resetMode = when {
                skipReset -> Esp32Flasher.ResetMode.NONE
                entry.device.vendorId == 0x303A && entry.device.productId == 0x1001 ->
                    Esp32Flasher.ResetMode.USB_JTAG
                else -> Esp32Flasher.ResetMode.CLASSIC
            }

            val flasher = Esp32Flasher(
                transport = UsbSerialFlasherTransport(usbManager, entry.driver),
                supportsEncryptedFlash = targetChip in setOf("ESP32-S2", "ESP32-S3", "ESP32-C3"),
            )

            _firmwareFlashStatus.value = if (skipReset) {
                "Connecting to ROM bootloader..."
            } else {
                "Resetting into ROM bootloader..."
            }

            withContext(Dispatchers.IO) {
                flasher.flash(
                    firmware = bytes,
                    offset = 0,
                    resetMode = resetMode,
                    onStage = { stage -> _firmwareFlashStatus.value = stage },
                ) { percent ->
                    val written = (bytes.size.toLong() * percent / 100L).toInt()
                    _firmwareFlashProgress.value = percent
                    _firmwareFlashStatus.value = if (percent >= 100) {
                        "Firmware written — rebooting device..."
                    } else {
                        "Writing... $percent%  (${written / 1024} / ${bytes.size / 1024} KB)"
                    }
                }
            }

            _firmwareFlashProgress.value = 100
            _firmwareFlashStatus.value = "Flash complete. The device is rebooting; reconnect after it disappears."
            appendLog("Firmware flash complete (${bytes.size} bytes at 0x0).", level = LogLevel.SUCCESS)
            addHistory("firmware", "Flashed ${bytes.size} bytes at 0x0", HistoryLevel.SUCCESS)
            delay(2_000)
            refreshDevices()
        } catch (t: Throwable) {
            _firmwareFlashProgress.value = 0
            _firmwareFlashStatus.value = "Flash failed: ${t.message ?: t.javaClass.simpleName}"
            appendLog(
                "Firmware flash failed: ${t.message ?: t.javaClass.simpleName}",
                level = LogLevel.ERROR,
            )
            addHistory("firmware", "Firmware flash failed", HistoryLevel.ERROR)
        } finally {
            _firmwareFlashing.value = false
            updateForegroundService()
        }
    }
}
