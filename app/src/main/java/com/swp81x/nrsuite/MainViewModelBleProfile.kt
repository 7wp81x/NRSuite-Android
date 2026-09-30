package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.ble.BleCharacteristicProfile
import com.swp81x.nrsuite.core.ble.BleDeviceObservation
import com.swp81x.nrsuite.core.ble.BleServiceProfile
import com.swp81x.nrsuite.core.history.HistoryLevel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

// Read-only BLE GATT profiling for a selected discovered device.

internal fun MainViewModel.selectBleProfileTargetImpl(device: BleDeviceObservation?) {
    _bleProfileTarget.value = device
    if (device != null) {
        _bleProfileStatus.value = "Target selected: ${device.name ?: device.address}"
    }
}

internal fun MainViewModel.startBleProfileImpl() {
    val activeSession = session
    if (activeSession == null) {
        appendLog("Connect to a device before starting BLE profile.")
        return
    }
    val target = _bleProfileTarget.value
    if (target == null) {
        appendLog("Select a discovered BLE device before profiling.")
        return
    }
    if (_bleProfileRunning.value) return
    if (!ensureRadioIdle("BLE GATT Profile")) return

    _bleProfileServices.value = emptyList()
    _bleProfileStatus.value = "Starting profile..."
    _bleProfileRunning.value = true
    updateForegroundService()

    scope.launch {
        val response = activeSession.sendCommand(
            "BLE_PROFILE_START",
            JSONObject().apply {
                put("address", target.address)
                put("address_type", target.addressType)
            },
            timeoutMs = 10_000,
        )
        if (response?.optBoolean("ok") == true) {
            _bleProfileStatus.value = "Connecting..."
            appendLog("BLE profile started for ${target.name ?: target.address}.")
            addHistory("ble_profile", "BLE profile started: ${target.name ?: target.address}", HistoryLevel.INFO)
        } else {
            _bleProfileRunning.value = false
            _bleProfileStatus.value = response?.optString("msg") ?: "Failed to start"
            updateForegroundService()
            appendLog("Failed to start BLE profile: ${response?.optString("msg") ?: "timeout"}")
        }
    }
}

internal fun MainViewModel.stopBleProfileImpl() {
    if (!_bleProfileRunning.value) return
    _bleProfileRunning.value = false
    _bleProfileStatus.value = "Stopping..."
    updateForegroundService()

    val activeSession = session
    scope.launch {
        activeSession?.sendCommand("BLE_PROFILE_STOP", timeoutMs = 5_000)
    }
}

internal fun MainViewModel.clearBleProfileImpl() {
    _bleProfileServices.value = emptyList()
    _bleProfileStatus.value = _bleProfileTarget.value?.let {
        "Target selected: ${it.name ?: it.address}"
    } ?: "Select a discovered BLE device"
}

internal fun MainViewModel.recordBleProfileEvent(event: JSONObject) {
    val status = event.optString("status").ifBlank { "unknown" }
    _bleProfileStatus.value = when (status) {
        "connecting" -> "Connecting..."
        "connected" -> "Connected; enumerating services..."
        "done" -> "Profile complete"
        "failed" -> event.optString("msg").ifBlank { "Profile failed" }
        else -> status
    }
    if (status == "done" || status == "failed") {
        _bleProfileRunning.value = false
        updateForegroundService()
        addHistory(
            "ble_profile",
            if (status == "done") "BLE profile complete" else "BLE profile failed",
            if (status == "done") HistoryLevel.SUCCESS else HistoryLevel.ERROR,
        )
    }
}

internal fun MainViewModel.recordBleServiceEvent(event: JSONObject) {
    val uuid = event.optString("service_uuid").uppercase().takeIf { it.isNotBlank() } ?: return
    _bleProfileServices.update { current ->
        if (current.any { it.uuid == uuid }) current
        else current + BleServiceProfile(uuid = uuid)
    }
}

internal fun MainViewModel.recordBleCharacteristicEvent(event: JSONObject) {
    val serviceUuid = event.optString("service_uuid").uppercase().takeIf { it.isNotBlank() } ?: return
    val uuid = event.optString("uuid").uppercase().takeIf { it.isNotBlank() } ?: return
    val props = event.optJSONObject("properties")
    val characteristic = BleCharacteristicProfile(
        uuid = uuid,
        read = props?.optBoolean("read") == true,
        write = props?.optBoolean("write") == true,
        writeNoResponse = props?.optBoolean("write_no_response") == true,
        notify = props?.optBoolean("notify") == true,
        indicate = props?.optBoolean("indicate") == true,
        broadcast = props?.optBoolean("broadcast") == true,
    )

    _bleProfileServices.update { current ->
        val existing = current.firstOrNull { it.uuid == serviceUuid }
        val withService = existing?.copy(
            characteristics = existing.characteristics
                .filterNot { it.uuid == uuid } + characteristic,
        ) ?: BleServiceProfile(serviceUuid, listOf(characteristic))

        (current.filterNot { it.uuid == serviceUuid } + withService)
            .sortedBy { it.uuid }
    }
}
