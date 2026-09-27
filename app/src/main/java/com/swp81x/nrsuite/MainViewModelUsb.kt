package com.swp81x.nrsuite

import android.hardware.usb.UsbDevice
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.session.NrSession
import com.swp81x.nrsuite.core.usb.UsbSerialDeviceCatalog
import com.swp81x.nrsuite.core.usb.UsbSerialTransport
import com.swp81x.nrsuite.core.usb.buildDeviceFingerprintKey
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

// USB discovery, permission handling, connection lifecycle, and per-device sessions.

internal fun MainViewModel.refreshDevicesImpl() {
    val found = UsbSerialDeviceCatalog.list(usbManager)
    _devices.value = found
    appendLog("Found ${found.size} supported USB serial device(s).")

    // Reconcile active sessions against Android's authoritative attached-device
    // list. The serial prober can transiently miss an already-open device; only
    // close a session when the USB device itself is no longer attached.
    val attachedDeviceIds = usbManager.deviceList.values
        .map { it.deviceId }
        .toSet()
    (deviceSessions + idleDeviceSessions).entries
        .filter { (_, entry) ->
            entry.openedOnce && entry.device.device.deviceId !in attachedDeviceIds
        }
        .map { it.key }
        .toList()
        .forEach { staleFingerprint ->
            appendLog("Closing stale session for a detached USB device.", level = LogLevel.USB)
            disconnectSession(staleFingerprint, stopOperations = staleFingerprint == activeDeviceFingerprint)
        }

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
    if (deviceSessions.isEmpty() && _connectionState.value is ConnectionState.Failed) {
        _connectionState.value = ConnectionState.Disconnected
    }
    refreshDevices()
    appendLog("USB device attached.", level = LogLevel.USB)
    appendLog("Tap Connect to open a session when ready.", level = LogLevel.USB)
}

internal fun MainViewModel.deviceFingerprintImpl(device: UsbDevice): String {
    val serial = runCatching { device.serialNumber }.getOrNull().orEmpty()
    return buildDeviceFingerprintKey(
        serial = serial,
        vendorId = device.vendorId,
        productId = device.productId,
        manufacturer = device.manufacturerName,
        productName = device.productName,
        deviceName = device.deviceName,
        deviceId = device.deviceId,
    )
}

internal fun MainViewModel.onUsbDeviceDetachedImpl(device: UsbDevice) {
    clearPermissionRequestImpl(device.deviceId)
    refreshDevices()
    appendLog("USB device detached: ${device.deviceName}", level = LogLevel.USB)

    val detachedFingerprint = runCatching { deviceFingerprintImpl(device) }.getOrNull()
    val allSessions = deviceSessions + idleDeviceSessions
    val matchedFingerprint = detachedFingerprint
        ?.takeIf { allSessions.containsKey(it) }
        ?: allSessions.entries.firstOrNull { (_, session) ->
            session.device.device.deviceId == device.deviceId ||
                session.device.device.deviceName == device.deviceName
        }?.key

    if (matchedFingerprint == null) {
        appendLog("Detached device had no open session.", level = LogLevel.USB)
        return
    }

    if (_firmwareTargetDevice.value?.device?.deviceId == device.deviceId) {
        _firmwareTargetDevice.value = null
    }

    // Only the detached device's session is torn down. Every other session,
    // its connection state, and its active operation remain untouched.
    disconnectSession(matchedFingerprint, stopOperations = true)
}

internal fun MainViewModel.hasPermissionImpl(device: UsbDevice): Boolean =
    usbManager.hasPermission(device)

internal fun MainViewModel.onPermissionResultImpl(device: UsbDevice, granted: Boolean) {
    clearPermissionRequestImpl(device.deviceId)
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

    refreshDevicesImpl()
    if (usbManager.deviceList.values.none { it.deviceId == device.deviceId }) {
        appendLog("USB device ${device.deviceName} is no longer attached.")
        return
    }

    val entry = _devices.value.firstOrNull { it.device.deviceId == device.deviceId }
        ?: UsbSerialDeviceCatalog.find(usbManager, device)
    if (entry == null) {
        appendLog("No supported USB serial driver for ${device.deviceName}.")
        return
    }

    val fingerprint = deviceFingerprintImpl(entry.device)
    val previousPrimary = activeDeviceFingerprint

    if (fingerprint in _disconnectingFingerprints.value) {
        appendLog("${entry.displayName} is still disconnecting; try again in a moment.", tag = "USB")
        return
    }

    // Adopt an idle-but-still-open session instead of closing/reopening the
    // USB port. This is the workaround for TinyUSB S2 devices that stop
    // answering after a host-side close/reopen cycle.
    val idle = idleDeviceSessions.remove(fingerprint)
    if (idle != null && idle.session.state.value is ConnectionState.Connected) {
        val reactivated = DeviceSession(
            fingerprint = fingerprint,
            device = entry,
            session = idle.session,
            opening = true,
        )
        deviceSessions[fingerprint] = reactivated
        _deviceConnectionStates.update { it + (fingerprint to ConnectionState.Connecting) }

        val shouldBecomePrimary = previousPrimary == null || previousPrimary == fingerprint
        if (shouldBecomePrimary) {
            _primaryFingerprint.value = fingerprint
            _connectionState.value = ConnectionState.Connecting
            if (_firmwareTargetDevice.value == null) {
                _firmwareTargetDevice.value = entry
            }
        }

        observe(fingerprint, idle.session)
        // observe() seeds from the session's existing Connected state; keep the
        // UI in Connecting until the PING validation completes.
        _deviceConnectionStates.update { it + (fingerprint to ConnectionState.Connecting) }
        if (fingerprint == activeDeviceFingerprint) {
            _connectionState.value = ConnectionState.Connecting
        }

        scope.launch {
            appendLog("Reactivating ${entry.displayName} [$fingerprint]...", tag = "USB")
            val pong = idle.session.sendCommand("PING", timeoutMs = 2_000)
            if (pong?.optBoolean("ok") == true) {
                val connected = idle.session.state.value
                reactivated.opening = false
                reactivated.openedOnce = true
                _deviceConnectionStates.update { it + (fingerprint to connected) }
                if (fingerprint == activeDeviceFingerprint) {
                    _connectionState.value = connected
                }
                appendLog("Connected to ${(connected as? ConnectionState.Connected)?.chip ?: "NRSuite device"} (reused idle session).", tag = "USB")
            } else {
                appendLog("Idle session did not respond to PING; reopening.", tag = "USB")
                deviceSessions.remove(fingerprint)
                reactivated.observers.forEach { it.cancel() }
                _deviceConnectionStates.update { it - fingerprint }
                if (fingerprint == activeDeviceFingerprint) {
                    val next = deviceSessions.keys.firstOrNull()
                    _primaryFingerprint.value = next
                    _connectionState.value = next
                        ?.let { _deviceConnectionStates.value[it] }
                        ?: ConnectionState.Disconnected
                }
                usbOperationMutex.withLock {
                    runCatching { idle.session.disconnect() }
                }
            }
        }
        return
    }
    if (idle != null) {
        // Stale idle transport; close it before opening a fresh session.
        scope.launch {
            usbOperationMutex.withLock {
                runCatching { idle.session.disconnect() }
            }
        }
    }

    val anotherDeviceOpening = deviceSessions.values.any {
        it.opening && it.fingerprint != fingerprint
    }
    if (anotherDeviceOpening) {
        appendLog("Another USB device is still connecting; try again in a moment.", tag = "USB")
        return
    }

    val replacing = deviceSessions[fingerprint]
    val replacingState = replacing?.session?.state?.value
    if (replacing?.opening == true ||
        replacingState is ConnectionState.Connecting ||
        replacingState is ConnectionState.Connected
    ) {
        appendLog("${entry.displayName} is already connected or connecting.")
        return
    }

    // Tear down only this fingerprint's prior session. The actual USB close is
    // serialized below and completes before the replacement session opens.
    if (replacing != null) {
        val replacingPrimary = previousPrimary == fingerprint
        if (replacingPrimary) {
            stopActiveOperations()
        }
        deviceSessions.remove(fingerprint)
        replacing.observers.forEach { it.cancel() }
        _deviceConnectionStates.update { it - fingerprint }
        if (replacingPrimary) {
            val next = deviceSessions.keys.firstOrNull()
            _primaryFingerprint.value = next
            _connectionState.value = next
                ?.let { _deviceConnectionStates.value[it] }
                ?: ConnectionState.Disconnected
        }
    }

    val newSession = NrSession(
        transport = UsbSerialTransport(usbManager, entry.driver),
        scope = scope,
    )
    deviceSessions[fingerprint] = DeviceSession(
        fingerprint = fingerprint,
        device = entry,
        session = newSession,
        opening = true,
    )
    _deviceConnectionStates.update { it + (fingerprint to ConnectionState.Connecting) }

    // Preserve the existing primary unless this is the first session or the
    // same device is being replaced/reconnected.
    val shouldBecomePrimary = previousPrimary == null || previousPrimary == fingerprint
    if (shouldBecomePrimary) {
        _primaryFingerprint.value = fingerprint
        _connectionState.value = ConnectionState.Connecting
        if (_firmwareTargetDevice.value == null) {
            _firmwareTargetDevice.value = entry
        }
    }

    observe(fingerprint, newSession)
    scope.launch {
        appendLog("Opening ${entry.displayName} [$fingerprint]...", tag = "USB")
        usbOperationMutex.withLock {
            // Close any prior session for this same fingerprint before opening.
            replacing?.let { old ->
                if (old.session.state.value is ConnectionState.Connected) {
                    runCatching { old.session.sendCommand("STOP_ALL", timeoutMs = 1_500) }
                }
                runCatching { old.session.disconnect() }
                kotlinx.coroutines.delay(250)
            }
            newSession.connect()
        }
    }
}

internal fun MainViewModel.markPermissionRequestedImpl(device: UsbDevice) {
    _pendingPermissionRequests.update { it + device.deviceId }
}

internal fun MainViewModel.clearPermissionRequestImpl(deviceId: Int) {
    _pendingPermissionRequests.update { it - deviceId }
}

internal fun MainViewModel.disconnectDeviceImpl(device: UsbDevice) {
    val exactFingerprint = runCatching { deviceFingerprintImpl(device) }.getOrNull()
    val fingerprint = exactFingerprint
        ?.takeIf { deviceSessions.containsKey(it) }
        ?: deviceSessions.entries.firstOrNull { (_, session) ->
            session.device.device.deviceId == device.deviceId ||
                session.device.device.deviceName == device.deviceName
        }?.key
        ?: return
    idleSession(
        fingerprint = fingerprint,
        stopOperations = fingerprint == activeDeviceFingerprint,
    )
}
