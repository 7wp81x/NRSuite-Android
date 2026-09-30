package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.ble.BleDeviceObservation
import com.swp81x.nrsuite.core.history.HistoryLevel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

// BLE Scanner: passive/active BLE advertisement discovery over NimBLE.

internal fun MainViewModel.startBleScanImpl(active: Boolean) {
    val activeSession = session
    if (activeSession == null) {
        appendLog("Connect to a device before starting BLE scan.")
        return
    }
    if (_bleScanRunning.value) return
    if (!ensureRadioIdle("BLE Scanner")) return

    _bleScanActive.value = active
    scope.launch {
        val response = activeSession.sendCommand(
            "BLE_SCAN_START",
            JSONObject().apply {
                put("active", active)
                put("interval_ms", 100)
                put("window_ms", 99)
                put("min_emit_ms", 1000)
            },
            timeoutMs = 10_000,
        )
        if (response?.optBoolean("ok") == true) {
            _bleScanDevices.value = emptyList()
            _bleScanRunning.value = true
            updateForegroundService()
            appendLog("BLE scan started (${if (active) "active" else "passive"} mode).")
            addHistory("ble_scanner", "BLE scan started", HistoryLevel.SUCCESS)
        } else {
            appendLog("Failed to start BLE scan: ${response?.optString("msg") ?: "timeout"}")
        }
    }
}

internal fun MainViewModel.stopBleScanImpl() {
    if (!_bleScanRunning.value) return
    _bleScanRunning.value = false
    updateForegroundService()

    val activeSession = session
    scope.launch {
        val response = activeSession?.sendCommand("BLE_SCAN_STOP", timeoutMs = 6_000)
        if (response?.optBoolean("ok") == true) {
            appendLog("BLE scan stopped.")
            addHistory("ble_scanner", "BLE scan stopped", HistoryLevel.INFO)
        } else {
            appendLog("BLE scan stop request sent without confirmation.")
        }
    }
}

internal fun MainViewModel.clearBleScanImpl() {
    _bleScanDevices.value = emptyList()
}

internal fun MainViewModel.recordBleDeviceEvent(event: JSONObject) {
    val address = event.optString("address").uppercase().takeIf { it.isNotBlank() } ?: return
    val now = timeHmNow()
    val name = event.optString("name").takeIf { it.isNotBlank() }
    val rssi = event.optInt("rssi", -127)
    val reconnectable = event.optBoolean("connectable", false)
    val addressType = event.optInt("address_type", -1)
    val txPower = event.optInt("tx_power", 0)
    val appearance = event.optInt("appearance", 0)
    val manufacturerData = event.optString("manufacturer_data").takeIf { it.isNotBlank() }
    val services = mutableListOf<String>()
    event.optJSONArray("services")?.let { array ->
        for (i in 0 until array.length()) {
            val service = array.optString(i)
            if (service.isNotBlank()) services += service.uppercase()
        }
    }

    val existing = _bleScanDevices.value.firstOrNull { it.address == address }
    val updated = existing?.copy(
        addressType = addressType,
        name = name ?: existing.name,
        rssi = rssi,
        connectable = reconnectable,
        txPower = txPower,
        appearance = appearance,
        manufacturerData = manufacturerData ?: existing.manufacturerData,
        services = if (services.isEmpty()) existing.services else services,
        lastSeen = now,
        sightings = existing.sightings + 1,
    ) ?: BleDeviceObservation(
        address = address,
        addressType = addressType,
        name = name,
        rssi = rssi,
        connectable = reconnectable,
        txPower = txPower,
        appearance = appearance,
        manufacturerData = manufacturerData,
        services = services,
        firstSeen = now,
        lastSeen = now,
        sightings = 1,
    )

    _bleScanDevices.update { current ->
        (listOf(updated) + current.filterNot { it.address == address })
            .sortedByDescending { it.rssi }
            .take(300)
    }

    detectTrackerCandidate(
        address = address,
        name = updated.name,
        rssi = rssi,
        manufacturerData = updated.manufacturerData,
    )
}
