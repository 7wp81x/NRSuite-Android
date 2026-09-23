package com.swp81x.nrsuite

import android.net.Uri
import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.session.NrSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.update
import org.json.JSONObject

// BLE HID advertising, DuckyScript payloads, keyboard, mouse, and status polling.

internal fun MainViewModel.setBlePayloadImpl(uri: Uri, name: String?) {
    _blePayloadUri.value = uri
    _blePayloadName.value = name ?: uri.lastPathSegment ?: "payload.txt"
    appendLog("BLE payload selected: ${_blePayloadName.value}")
}

internal fun MainViewModel.clearBlePayloadImpl() {
    _blePayloadUri.value = null
    _blePayloadName.value = null
    _bleSavedScriptText.value = null
    appendLog("BLE payload cleared.")
}

internal fun MainViewModel.startBleImpl(advertiseName: String) {
    val activeSession = session
    if (activeSession == null) {
        appendLog("Connect to a device before starting BLE HID.")
        return
    }
    if (_bleAdvertising.value) return
    val activeWifi = activeRadioLabel(includeBle = false)
    if (activeWifi != null) {
        appendLog(
            "Cannot start BLE HID while $activeWifi is active. Stop $activeWifi first.",
            level = LogLevel.ERROR,
        )
        return
    }

    scope.launch {
        val name = advertiseName.trim().ifBlank { "NRSuite Keyboard" }
        appendLog("Starting BLE HID advertising as '$name'...")
        val response = activeSession.sendCommand(
            "BLE_START",
            JSONObject().put("name", name),
            timeoutMs = 8_000,
        )
        if (response?.optBoolean("ok") == true) {
            _bleAdvertising.value = true
            _bleConnected.value = false
            _blePeer.value = ""
            clearBleRealtimeStateImpl()
            startBleStatusPollingImpl(activeSession)
            updateForegroundService()
            appendLog("BLE HID advertising started.")
            addHistory("ble", "BLE HID advertising started as '$name'", HistoryLevel.SUCCESS)
        } else {
            appendLog("Failed to start BLE HID: ${response?.optString("msg") ?: "timeout or unsupported"}")
        }
    }
}

internal fun MainViewModel.stopBleImpl() {
    if (!_bleAdvertising.value && !_bleConnected.value) return
    bleStatusJob?.cancel()
    bleStatusJob = null
    _bleAdvertising.value = false
    _bleConnected.value = false
    _blePeer.value = ""
    clearBleRealtimeStateImpl()
    updateForegroundService()
    val activeSession = session
    scope.launch {
        runCatching { activeSession?.sendCommand("BLE_RELEASE_ALL", timeoutMs = 3_000) }
        runCatching { activeSession?.sendCommand("BLE_MOUSE_RELEASE", timeoutMs = 3_000) }
        val response = activeSession?.sendCommand("BLE_STOP", timeoutMs = 5_000)
        appendLog(
            if (response?.optBoolean("ok") == true) "BLE HID stopped."
            else "BLE stop request sent."
        )
    }
}

internal fun MainViewModel.runBlePayloadImpl() {
    val activeSession = session
    val payloadUri = _blePayloadUri.value
    val savedScript = _bleSavedScriptText.value
    if (activeSession == null) {
        appendLog("Connect to a device before running a BLE payload.")
        return
    }
    if (!_bleConnected.value) {
        appendLog("BLE host is not connected yet.")
        return
    }
    if (_bleScriptRunning.value) {
        appendLog("A BLE payload is already running.")
        return
    }
    if (payloadUri == null && savedScript == null) {
        appendLog("Choose a DuckyScript payload first.")
        return
    }
    scope.launch {
        val script = savedScript
            ?: withContext(Dispatchers.IO) {
                runCatching {
                    payloadUri?.let {
                        app.contentResolver
                            .openInputStream(it)
                            ?.use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
                    }
                }.getOrNull()
            }
        if (script.isNullOrBlank()) {
            appendLog("Could not read the selected BLE payload.")
            return@launch
        }
        runBleScriptImpl(activeSession, script)
    }
}

internal fun MainViewModel.sendBleKeyboardTextImpl(text: String) {
    val activeSession = session ?: run {
        appendLog("Connect to a device before sending keyboard input.")
        return
    }
    if (text.isBlank()) return
    val script = if (text.startsWith("STRING", ignoreCase = true) ||
        text.startsWith("DELAY", ignoreCase = true) ||
        text.startsWith("CTRL", ignoreCase = true) ||
        text.startsWith("ALT", ignoreCase = true) ||
        text.startsWith("GUI", ignoreCase = true) ||
        text.startsWith("SHIFT", ignoreCase = true)
    ) {
        text
    } else {
        "STRINGLN $text"
    }
    scope.launch { runBleScriptImpl(activeSession, script) }
}

internal fun MainViewModel.sendBleRealtimeInputImpl(inserted: String, backspaces: Int) {
    if (!_bleConnected.value || session == null) return
    if (inserted.isEmpty() && backspaces <= 0) return

    synchronized(bleTypeBuffer) {
        repeat(backspaces.coerceAtLeast(0)) { bleTypeBuffer.append('\b') }
        if (inserted.isNotEmpty()) bleTypeBuffer.append(inserted)
    }
    scheduleBleTypeFlushImpl()
}

internal fun MainViewModel.sendBleSpecialKeyImpl(key: String) {
    val activeSession = session ?: return
    if (!_bleConnected.value || key.isBlank()) return
    scope.launch {
        runCatching {
            activeSession.sendCommandNoWait(
                "BLE_KEY_TAP",
                JSONObject().put("key", key),
            )
        }
        releaseMomentaryModifiersImpl(activeSession)
    }
}

internal fun MainViewModel.setBleModifierHoldImpl(enabled: Boolean) {
    _bleModifierHold.value = enabled
}

internal fun MainViewModel.sendBleMouseMoveImpl(dx: Int, dy: Int) {
    val activeSession = session ?: return
    if (!_bleConnected.value || (dx == 0 && dy == 0)) return
    scope.launch {
        runCatching {
            activeSession.sendCommandNoWait(
                "BLE_MOUSE_MOVE",
                JSONObject().put("dx", dx).put("dy", dy),
            )
        }
    }
}

internal fun MainViewModel.sendBleMouseScrollImpl(wheel: Int) {
    val activeSession = session ?: return
    if (!_bleConnected.value || wheel == 0) return
    scope.launch {
        runCatching {
            activeSession.sendCommandNoWait(
                "BLE_MOUSE_SCROLL",
                JSONObject().put("wheel", wheel),
            )
        }
    }
}

internal fun MainViewModel.sendBleMouseButtonImpl(button: String, down: Boolean) {
    val activeSession = session ?: return
    if (!_bleConnected.value || button.isBlank()) return
    scope.launch {
        runCatching {
            activeSession.sendCommandNoWait(
                "BLE_MOUSE_BUTTON",
                JSONObject().put("button", button).put("down", down),
            )
        }
    }
}

internal fun MainViewModel.releaseBleMouseButtonsImpl() {
    val activeSession = session ?: return
    if (!_bleConnected.value) return
    scope.launch {
        runCatching { activeSession.sendCommandNoWait("BLE_MOUSE_RELEASE") }
    }
}

internal suspend fun MainViewModel.releaseMomentaryModifiersImpl(activeSession: NrSession?) {
    if (_bleModifierHold.value) return
    val active = _bleModifiers.value
    if (active.isEmpty()) return
    _bleModifiers.value = emptySet()
    active.forEach { key ->
        runCatching {
            activeSession?.sendCommandNoWait(
                "BLE_KEY_UP",
                JSONObject().put("key", key),
            )
        }
    }
}

internal fun MainViewModel.setBleModifierImpl(key: String, down: Boolean) {
    val activeSession = session ?: return
    if (!_bleConnected.value || key.isBlank()) return

    _bleModifiers.update { current ->
        if (down) current + key else current - key
    }
    val cmd = if (down) "BLE_KEY_DOWN" else "BLE_KEY_UP"
    scope.launch {
        runCatching {
            activeSession.sendCommandNoWait(cmd, JSONObject().put("key", key))
        }
    }
}

internal fun MainViewModel.scheduleBleTypeFlushImpl() {
    if (bleTypeJob?.isActive == true) return
    bleTypeJob = scope.launch {
        try {
            while (true) {
                delay(25L)
                val payload: String? = synchronized(bleTypeBuffer) {
                    if (bleTypeBuffer.isEmpty()) {
                        null
                    } else {
                        bleTypeBuffer.toString().also { bleTypeBuffer.setLength(0) }
                    }
                }
                if (payload == null) return@launch

                val activeSession = session ?: return@launch
                if (!_bleConnected.value) return@launch
                runCatching {
                    activeSession.sendCommandNoWait(
                        "BLE_TYPE_TEXT",
                        JSONObject().put("text", payload),
                    )
                }
                releaseMomentaryModifiersImpl(activeSession)
            }
        } finally {
            bleTypeJob = null
        }
    }
}

internal fun MainViewModel.clearBleRealtimeStateImpl() {
    _bleModifiers.value = emptySet()
    synchronized(bleTypeBuffer) {
        bleTypeBuffer.setLength(0)
    }
    bleTypeJob?.cancel()
    bleTypeJob = null
}

internal suspend fun MainViewModel.runBleScriptImpl(activeSession: NrSession, script: String) {
    if (!_bleConnected.value) {
        appendLog("BLE host is not connected yet.")
        return
    }
    if (_bleScriptRunning.value) {
        appendLog("A BLE payload is already running.")
        return
    }

    _bleScriptRunning.value = true
    try {
        val response = runCatching {
            activeSession.sendCommand(
                "BLE_RUN_SCRIPT",
                JSONObject().put("script", script),
                timeoutMs = maxOf(10_000, script.length / 20L),
            )
        }.getOrNull()
        if (response?.optBoolean("ok") == true) {
            appendLog("BLE script finished (${response.optInt("lines")} lines).")
        } else {
            appendLog("BLE script failed: ${response?.optString("msg") ?: "timeout or disconnected"}")
        }
    } finally {
        _bleScriptRunning.value = false
    }
}

internal fun MainViewModel.startBleStatusPollingImpl(activeSession: NrSession) {
    bleStatusJob?.cancel()
    bleStatusJob = scope.launch {
        while (isActive && (_bleAdvertising.value || _bleConnected.value)) {
            delay(2_000)
            val response = runCatching {
                activeSession.sendCommand("BLE_STATUS", timeoutMs = 4_000)
            }.getOrNull() ?: continue
            if (response.optBoolean("ok")) {
                _bleAdvertising.value = response.optBoolean("advertising", _bleAdvertising.value)
                _bleConnected.value = response.optBoolean("connected", _bleConnected.value)
                _blePeer.value = response.optString("peer", "")
            }
        }
    }
}
