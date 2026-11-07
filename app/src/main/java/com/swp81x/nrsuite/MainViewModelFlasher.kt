package com.swp81x.nrsuite

import android.hardware.usb.UsbDevice
import android.net.Uri
import android.provider.OpenableColumns
import com.swp81x.nrsuite.core.flasher.Esp32Flasher
import com.swp81x.nrsuite.core.flasher.UsbSerialFlasherTransport
import com.swp81x.nrsuite.core.flasher.looksLikeTinyUsbFirmware
import com.swp81x.nrsuite.core.flasher.recommendedEsp32ResetMode
import com.swp81x.nrsuite.core.flasher.triggerTinyUsbBootloaderReboot
import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.notifications.NrSuiteNotifications
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.update.firmwareChipFromFirmwareName
import com.swp81x.nrsuite.core.update.firmwareHardwareChip
import com.swp81x.nrsuite.core.update.firmwareTargetForConnectedDevice
import com.swp81x.nrsuite.core.usb.UsbSerialDeviceCatalog
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Firmware image selection, flash-target selection, and ROM-bootloader flashing.

internal fun MainViewModel.markFirmwarePermissionRequestedImpl(device: UsbDevice) {
    firmwarePermissionDeviceId = device.deviceId
    markPermissionRequested(device)
}

internal fun MainViewModel.setFirmwareFlashFileImpl(uri: Uri, name: String?) {
    val resolvedName = queryDocumentDisplayNameImpl(uri)
        ?.takeIf { it.isNotBlank() }
        ?: name?.takeIf { it.isNotBlank() && !it.startsWith("msf:") && !it.startsWith("primary:") }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: "firmware.bin"
    if (!resolvedName.endsWith(".bin", ignoreCase = true)) {
        _firmwareFlashUri.value = null
        _firmwareFlashName.value = null
        _firmwareFlashSize.value = 0
        val message = "Select a .bin firmware image (got '$resolvedName')."
        appendLog(message, level = LogLevel.ERROR)
        _toastMessage.value = message
        return
    }
    _firmwareFlashUri.value = uri
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
    if (!usbManager.hasPermission(device)) {
        _firmwareTargetDevice.value = null
        appendLog("USB permission is required before selecting a flash target.")
        return
    }

    val entry = _devices.value.firstOrNull { it.device.deviceId == device.deviceId }
        ?: UsbSerialDeviceCatalog.find(usbManager, device)
    if (entry == null) {
        appendLog("No supported USB serial driver for ${device.deviceName}.")
        return
    }

    // A flash target selection is device-scoped. Never carry a chip/firmware
    // identity from the previously selected board into a new USB target.
    val previousTarget = _firmwareTargetDevice.value
    val targetChanged = previousTarget == null || runCatching {
        deviceFingerprintImpl(previousTarget.device) != deviceFingerprintImpl(device)
    }.getOrDefault(true)
    if (targetChanged) {
        _firmwareTargetChip.value = null
        _firmwareTargetFirmwareVersion.value = null
        _firmwareAssetDownload.value =
            com.swp81x.nrsuite.core.update.FirmwareAssetDownloadState.Idle
    }

    _firmwareTargetDevice.value = entry
    val fingerprint = deviceFingerprintImpl(device)
    val targetState = _deviceConnectionStates.value[fingerprint] as? ConnectionState.Connected
    val targetChip = targetState
        ?.let { firmwareTargetForConnectedDevice(it.chip, it.features) }
        ?: firmwareTargetChipHints[device.deviceId]
    _firmwareTargetChip.value = targetChip
    _firmwareTargetFirmwareVersion.value = targetState?.firmwareVersion
    if (!targetChip.isNullOrBlank()) {
        val imageChip = firmwareChipFromFirmwareName(_firmwareFlashName.value)
        if (imageChip != null && !imageChip.equals(targetChip, ignoreCase = true)) {
            clearFirmwareFlashFileImpl()
            _firmwareAssetDownload.value =
                com.swp81x.nrsuite.core.update.FirmwareAssetDownloadState.Idle
            _toastMessage.value =
                "Firmware image cleared: target $targetChip requires matching firmware."
        }
    }
    appendLog(
        "Firmware flash target: ${entry.displayName}" +
            (targetChip?.let { " ($it)" } ?: ""),
    )
}

internal fun MainViewModel.armManualFirmwareRecoveryImpl(targetChip: String) {
    if (_firmwareFlashUri.value == null) {
        appendLog("Choose a firmware image before arming manual bootloader recovery.", level = LogLevel.ERROR)
        return
    }
    manualRecoveryTargetChip = targetChip
    _firmwareManualRecovery.value = true
    _firmwareFlashStatus.value =
        "Manual bootloader armed. Hold BOOT, unplug/replug USB, release BOOT. " +
            "The flash will start when the device appears."
    appendLog("Manual bootloader recovery armed for $targetChip.", tag = "flasher")
}

internal fun MainViewModel.disarmManualFirmwareRecoveryImpl() {
    _firmwareManualRecovery.value = false
    manualRecoveryTargetChip = null
    appendLog("Manual bootloader recovery disarmed.", tag = "flasher")
}

internal fun MainViewModel.flashManualFirmwareNowImpl() {
    if (_firmwareFlashUri.value == null) {
        _firmwareManualRecovery.value = false
        appendLog("No firmware image is selected for manual bootloader recovery.", level = LogLevel.ERROR)
        return
    }
    if (_firmwareFlashing.value) return

    val entry = _firmwareTargetDevice.value
        ?.takeIf { usbManager.hasPermission(it.device) }
        ?: _devices.value.firstOrNull { usbManager.hasPermission(it.device) }
    if (entry == null) {
        _firmwareFlashStatus.value =
            "Waiting for a bootloader device with USB permission. " +
                "Hold BOOT, unplug/replug, then grant USB permission if prompted."
        return
    }

    _firmwareTargetDevice.value = entry
    val chip = manualRecoveryTargetChip ?: "ESP32"
    _firmwareManualRecovery.value = false
    startFirmwareFlashImpl(
        targetChip = chip,
        skipReset = true,
        eraseBeforeFlash = false,
        manualResetFallback = true,
    )
}

internal fun MainViewModel.flashManualFirmwareWithResetImpl() {
    if (_firmwareFlashUri.value == null) {
        appendLog("No firmware image is selected for manual bootloader recovery.", level = LogLevel.ERROR)
        return
    }
    if (_firmwareFlashing.value) return

    val entry = _firmwareTargetDevice.value
        ?.takeIf { usbManager.hasPermission(it.device) }
        ?: _devices.value.firstOrNull { usbManager.hasPermission(it.device) }
    if (entry == null) {
        _firmwareFlashStatus.value =
            "No flash target with USB permission. Reconnect the device, then retry."
        return
    }

    _firmwareTargetDevice.value = entry
    val chip = manualRecoveryTargetChip ?: "ESP32"
    _firmwareManualRecovery.value = false
    appendLog("Retrying with automatic DTR/RTS reset for $chip.", tag = "flasher")
    startFirmwareFlashImpl(
        targetChip = chip,
        skipReset = false,
        eraseBeforeFlash = false,
    )
}

internal fun MainViewModel.tryManualFirmwareFlashOnAttachImpl() {
    if (!_firmwareManualRecovery.value) return
    val entry = _devices.value.firstOrNull { usbManager.hasPermission(it.device) }
    if (entry != null) {
        _firmwareTargetDevice.value = entry
        appendLog("Bootloader device detected; starting manual flash.", tag = "flasher")
        flashManualFirmwareNowImpl()
    } else {
        _firmwareFlashStatus.value =
            "Bootloader device detected. Grant USB permission to continue flashing."
    }
}

/**
 * Clear a completed flash result when the user leaves the firmware flasher or
 * Settings screen. In-progress and failed states are intentionally preserved
 * because the flash may still be running in the background or the user may
 * need to read the failure details.
 */
internal fun MainViewModel.onFirmwareFlasherScreenClosedImpl() {
    if (!_firmwareFlashing.value && _firmwareFlashProgress.value >= 100) {
        _firmwareFlashProgress.value = 0
        _firmwareFlashStatus.value = null
    }
}

/**
 * Keep the foreground-service notification progress current without starting
 * the service thousands of times for a large firmware image.
 */
internal fun MainViewModel.updateFirmwareFlashNotificationImpl(force: Boolean = false) {
    if (!_firmwareFlashing.value) return
    val now = System.currentTimeMillis()
    val progress = _firmwareFlashProgress.value.coerceIn(0, 100)
    if (!force && progress == lastFirmwareNotificationProgress) return
    if (!force && now - lastFirmwareNotificationUpdateMs < FIRMWARE_NOTIFICATION_MIN_INTERVAL_MS) return
    lastFirmwareNotificationProgress = progress
    lastFirmwareNotificationUpdateMs = now
    updateForegroundService()
}

internal fun MainViewModel.startFirmwareFlashImpl(
    targetChip: String,
    skipReset: Boolean,
    eraseBeforeFlash: Boolean = false,
    manualResetFallback: Boolean = false,
) {
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

    val imageChip = firmwareChipFromFirmwareName(_firmwareFlashName.value)
    if (imageChip != null && !imageChip.equals(targetChip, ignoreCase = true)) {
        val message = "Firmware image targets $imageChip but the selected flash target is $targetChip."
        _firmwareFlashStatus.value = "Target mismatch: $message"
        appendLog(message, level = LogLevel.ERROR)
        return
    }

    _firmwareFlashing.value = true
    _firmwareFlashProgress.value = 0
    _firmwareFlashStatus.value = "Reading firmware image..."
    lastFirmwareNotificationProgress = -1
    lastFirmwareNotificationUpdateMs = 0L
    updateForegroundService()

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
            val targetFingerprint = deviceFingerprintImpl(entry.device)
            if (targetFingerprint == activeDeviceFingerprint) {
                stopActiveOperations()
            }
            // Tear down only the selected target's session. Other devices,
            // including sessions used for Wireless/BLE/etc., stay connected.
            disconnectSessionAndAwait(targetFingerprint, stopOperations = false)

            val automaticResetMode = recommendedEsp32ResetMode(
                vendorId = entry.device.vendorId,
                productId = entry.device.productId,
            )
            val resetMode = if (skipReset) Esp32Flasher.ResetMode.NONE else automaticResetMode
            val fallbackResetMode = automaticResetMode.takeIf { skipReset && manualResetFallback }

            val interfaceNames = runCatching {
                (0 until entry.device.interfaceCount).map { index ->
                    entry.device.getInterface(index)?.name
                }
            }.getOrDefault(emptyList())
            val tinyUsbFirmware = !skipReset && looksLikeTinyUsbFirmware(
                productName = entry.device.productName,
                interfaceNames = interfaceNames,
            )
            if (tinyUsbFirmware) {
                appendLog(
                    "Running TinyUSB firmware detected; using the Arduino CDC " +
                        "bootloader-restart sequence instead of esptool DTR/RTS reset.",
                    tag = "flasher",
                )
                armManualFirmwareRecoveryImpl(targetChip)
                _firmwareFlashStatus.value =
                    "Rebooting device into ROM bootloader. Grant USB permission if prompted; " +
                        "flashing will continue automatically."
                withContext(Dispatchers.IO) {
                    triggerTinyUsbBootloaderReboot(
                        UsbSerialFlasherTransport(
                            usbManager = usbManager,
                            driver = entry.driver,
                            onLog = { message -> appendLog(message, tag = "flasher") },
                        ),
                    )
                }
                appendLog(
                    "TinyUSB device entered ROM-bootloader restart; waiting for re-enumeration.",
                    tag = "flasher",
                )
                _firmwareFlashStatus.value =
                    "Waiting for the ROM bootloader device. Grant USB permission if prompted."
                // Release the UI busy state before the new ROM device can
                // appear. The USB attach callback, or the delayed fallback
                // below, will start the no-reset manual flash.
                _firmwareFlashing.value = false
                updateForegroundService()
                scope.launch {
                    delay(2_500)
                    if (_firmwareManualRecovery.value && !_firmwareFlashing.value) {
                        appendLog(
                            "Checking for the ROM bootloader device after TinyUSB restart.",
                            tag = "flasher",
                        )
                        tryManualFirmwareFlashOnAttachImpl()
                    }
                }
                return@launch
            }

            appendLog(
                "Flasher reset mode: $resetMode" +
                    (fallbackResetMode?.let { ", automatic fallback: $it" } ?: "") +
                    " (${entry.displayName}).",
                tag = "flasher",
            )

            val flasher = Esp32Flasher(
                transport = UsbSerialFlasherTransport(
                    usbManager = usbManager,
                    driver = entry.driver,
                    onLog = { message -> appendLog(message, tag = "flasher") },
                ),
                supportsEncryptedFlash = firmwareHardwareChip(targetChip) in setOf(
                    "ESP32-S2",
                    "ESP32-S3",
                    "ESP32-C3",
                    "ESP32-C5",
                ),
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
                    fallbackResetMode = fallbackResetMode,
                    eraseBeforeFlash = eraseBeforeFlash,
                    onStage = { stage -> _firmwareFlashStatus.value = stage },
                    onProgress = { percent ->
                        val written = (bytes.size.toLong() * percent / 100L).toInt()
                        _firmwareFlashProgress.value = percent
                        _firmwareFlashStatus.value = if (percent >= 100) {
                            "Firmware written — rebooting device..."
                        } else {
                            "Writing... $percent%  (${written / 1024} / ${bytes.size / 1024} KB)"
                        }
                        updateFirmwareFlashNotificationImpl()
                    },
                    onLog = { message -> appendLog(message, tag = "flasher") },
                )
            }

            _firmwareFlashProgress.value = 100
            _firmwareFlashStatus.value = "Flash complete. The device is rebooting; reconnect after it disappears."
            appendLog("Firmware flash complete (${bytes.size} bytes at 0x0).", level = LogLevel.SUCCESS)
            addHistory("firmware", "Flashed ${bytes.size} bytes at 0x0", HistoryLevel.SUCCESS)
            NrSuiteNotifications.notifyTaskAlert(
                app,
                "Firmware flash complete",
                "$targetChip flashed successfully. The device is rebooting.",
                smallIconRes = com.swp81x.nrsuite.R.drawable.ic_stat_firmware,
            )
            delay(2_000)
            refreshDevices()
        } catch (t: Throwable) {
            _firmwareFlashProgress.value = 0
            val reason = t.message ?: t.javaClass.simpleName
            _firmwareFlashStatus.value = "Flash failed: $reason"
            appendLog("Firmware flash failed: $reason", level = LogLevel.ERROR)
            addHistory("firmware", "Firmware flash failed", HistoryLevel.ERROR)
            NrSuiteNotifications.notifyTaskAlert(
                app,
                "Firmware flash failed",
                reason,
                smallIconRes = com.swp81x.nrsuite.R.drawable.ic_stat_firmware,
            )
        } finally {
            _firmwareFlashing.value = false
            updateForegroundService()
        }
    }
}

private const val FIRMWARE_NOTIFICATION_MIN_INTERVAL_MS = 750L
