package com.swp81x.nrsuite

import android.hardware.usb.UsbDevice
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.session.NrSession
import com.swp81x.nrsuite.core.usb.UsbSerialDeviceCatalog
import com.swp81x.nrsuite.core.usb.UsbSerialTransport
import kotlinx.coroutines.launch

// USB discovery, permission handling, connection lifecycle, and device selection.

internal fun MainViewModel.refreshDevicesImpl() {
    val found = UsbSerialDeviceCatalog.list(usbManager)
    _devices.value = found
    appendLog("Found ${found.size} supported USB serial device(s).")

    // If the selected flash target vanished or lost permission, clear it so
    // the flasher asks for permission instead of showing a stale "Selected".
    val selectedTarget = _firmwareTargetDevice.value
    if (selectedTarget != null) {
        val stillPresent = found.firstOrNull {
            it.device.deviceId == selectedTarget.device.deviceId
        }
        if (stillPresent == null || !usbManager.hasPermission(stillPresent.device)) {
            _firmwareTargetDevice.value = null
        } else {
            _firmwareTargetDevice.value = stillPresent
        }
    }
}

internal fun MainViewModel.onUsbDeviceAttachedImpl() {
    if (_connectionState.value is ConnectionState.Failed) {
        _connectionState.value = ConnectionState.Disconnected
    }
    refreshDevices()
    appendLog("USB device attached.", level = LogLevel.USB)
    appendLog("Tap Connect to reconnect when ready.", level = LogLevel.USB)
}

internal fun MainViewModel.deviceFingerprintImpl(device: UsbDevice): String {
    val serial = runCatching { device.serialNumber }.getOrNull().orEmpty()
    return listOf(
        serial,
        device.vendorId.toString(),
        device.productId.toString(),
        device.manufacturerName.orEmpty(),
        device.productName.orEmpty(),
    ).joinToString("|")
}

internal fun MainViewModel.onUsbDeviceDetachedImpl(device: UsbDevice) {
    refreshDevices()
    appendLog("USB device detached: ${device.deviceName}", level = LogLevel.USB)

    val detachedFingerprint = runCatching { deviceFingerprintImpl(device) }.getOrNull()
    if (activeDeviceFingerprint == null || detachedFingerprint == null ||
        activeDeviceFingerprint != detachedFingerprint
    ) {
        appendLog("Detached device is not the active session; keeping connection.", level = LogLevel.USB)
        return
    }

    activeDeviceFingerprint = null
    activeSerialDevice = null
    if (_firmwareTargetDevice.value?.device?.deviceId == device.deviceId) {
        _firmwareTargetDevice.value = null
    }

    // Set clean state BEFORE disconnecting so UI never shows Failed
    _connectionState.value = ConnectionState.Disconnected
    stopActiveOperations()

    val currentSession = session
    sessionObservers.forEach { it.cancel() }
    sessionObservers = emptyList()
    session = null
    if (currentSession != null) {
        scope.launch {
            runCatching { currentSession.disconnect() }
        }
    }
}

internal fun MainViewModel.hasPermissionImpl(device: UsbDevice): Boolean = usbManager.hasPermission(device)

internal fun MainViewModel.onPermissionResultImpl(device: UsbDevice, granted: Boolean) {
    val reallyGranted = granted || usbManager.hasPermission(device)
    appendLog(
        if (reallyGranted) "USB permission granted for ${device.deviceName}."
        else "USB permission denied for ${device.deviceName}.",
        level = LogLevel.USB,
    )
    if (reallyGranted) {
        refreshDevices()
        connect(device)
    }
}

internal fun MainViewModel.connectImpl(device: UsbDevice) {
    if (!usbManager.hasPermission(device)) {
        appendLog("USB permission is required before connecting.")
        return
    }

    val entry = _devices.value.firstOrNull { it.device.deviceId == device.deviceId }
        ?: UsbSerialDeviceCatalog.find(usbManager, device)
    if (entry == null) {
        appendLog("No supported USB serial driver for ${device.deviceName}.")
        return
    }

    // Replacing the session must also clear any stale active operation.
    disconnectInternal()

    val newSession = NrSession(
        transport = UsbSerialTransport(usbManager, entry.driver),
        scope = scope,
    )
    session = newSession
    activeSerialDevice = entry
    _firmwareTargetDevice.value = entry
    activeDeviceFingerprint = deviceFingerprintImpl(entry.device)
    observe(newSession)
    scope.launch {
        appendLog("Opening ${entry.displayName}...")
        newSession.connect()
    }
}
