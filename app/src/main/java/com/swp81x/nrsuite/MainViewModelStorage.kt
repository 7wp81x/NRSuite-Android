package com.swp81x.nrsuite

import android.net.Uri
import android.util.Base64
import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.storage.StorageFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.update
import org.json.JSONObject

private const val PREF_DUCKY_SCRIPTS = "ducky_scripts"
private const val BADUSB_RAW_CHUNK_SIZE = 693

// BadUSB, DuckyScript library, and mass-storage file operations.

internal fun MainViewModel.setBadUsbPayloadImpl(uri: Uri, name: String?) {
    _badUsbPayloadUri.value = uri
    _badUsbPayloadName.value = name ?: uri.lastPathSegment ?: "payload.txt"
    _badUsbProgress.value = 0
    appendLog("BadUSB payload selected: ${_badUsbPayloadName.value}")
}

internal fun MainViewModel.clearBadUsbPayloadImpl() {
    _badUsbPayloadUri.value = null
    _badUsbPayloadName.value = null
    _badUsbSavedScriptText.value = null
    _badUsbProgress.value = 0
    appendLog("BadUSB payload cleared.")
}

internal fun MainViewModel.saveDuckyScriptImpl(name: String, script: String) {
    val cleanName = name.trim()
    if (cleanName.isBlank()) return
    _duckyScriptMap.update { it + (cleanName to script) }
    persistDuckyScriptsImpl()
    appendLog("Saved DuckyScript '$cleanName'.")
}

internal fun MainViewModel.deleteDuckyScriptImpl(name: String) {
    _duckyScriptMap.update { it - name }
    persistDuckyScriptsImpl()
    appendLog("Deleted DuckyScript '$name'.")
}

internal fun MainViewModel.useBadUsbSavedScriptImpl(name: String) {
    val script = _duckyScriptMap.value[name] ?: return
    _badUsbPayloadUri.value = null
    _badUsbPayloadName.value = name
    _badUsbSavedScriptText.value = script
    appendLog("BadUSB script selected: $name")
}

internal fun MainViewModel.useBleSavedScriptImpl(name: String) {
    val script = _duckyScriptMap.value[name] ?: return
    _blePayloadUri.value = null
    _blePayloadName.value = name
    _bleSavedScriptText.value = script
    appendLog("BLE script selected: $name")
}

internal fun MainViewModel.loadDuckyScriptsImpl() {
    val raw = preferences.getString(PREF_DUCKY_SCRIPTS, null) ?: return
    runCatching {
        val json = JSONObject(raw)
        val map = mutableMapOf<String, String>()
        json.keys().forEach { key -> map[key] = json.optString(key, "") }
        _duckyScriptMap.value = map
    }
}

internal fun MainViewModel.persistDuckyScriptsImpl() {
    val json = JSONObject()
    _duckyScriptMap.value.forEach { (name, script) -> json.put(name, script) }
    preferences.edit().putString(PREF_DUCKY_SCRIPTS, json.toString()).apply()
}

internal fun MainViewModel.armBadUsbImpl(mscMode: Boolean) {
    val activeSession = session
    if (activeSession == null) {
        appendLog("Connect to a device before arming a BadUSB payload.")
        return
    }
    if (_badUsbUploading.value) return

    val chip = (_connectionState.value as? ConnectionState.Connected)?.chip
    if (chip !in setOf("ESP32-S2", "ESP32-S3")) {
        appendLog("BadUSB requires ESP32-S2 or ESP32-S3 (connected chip: ${chip ?: "unknown"}).")
        return
    }

    val payloadUri = _badUsbPayloadUri.value
    val savedScript = _badUsbSavedScriptText.value
    if (payloadUri == null && savedScript == null) {
        appendLog("Choose a DuckyScript payload first.")
        return
    }

    scope.launch {
        _badUsbUploading.value = true
        _badUsbProgress.value = 0
        try {
            val bytes = savedScript?.toByteArray(Charsets.UTF_8)
                ?: withContext(Dispatchers.IO) {
                    runCatching {
                        payloadUri?.let {
                            app.contentResolver
                                .openInputStream(it)
                                ?.use { stream -> stream.readBytes() }
                        }
                    }.getOrNull()
                }
            if (bytes == null) {
                appendLog("Could not read the selected BadUSB payload.")
                return@launch
            }
            if (bytes.isEmpty()) {
                appendLog("BadUSB payload is empty.")
                return@launch
            }

            val remoteFilename = "ducky.txt"
            var offset = 0
            while (offset < bytes.size) {
                val end = minOf(offset + BADUSB_RAW_CHUNK_SIZE, bytes.size)
                val chunk = bytes.copyOfRange(offset, end)
                val encoded = android.util.Base64.encodeToString(chunk, android.util.Base64.NO_WRAP)
                val isLast = end == bytes.size

                var success = false
                for (attempt in 1..3) {
                    val response = activeSession.sendCommand(
                        "SET_FILE_CHUNK",
                        JSONObject().apply {
                            put("filename", remoteFilename)
                            put("data", encoded)
                            put("last", isLast)
                        },
                        timeoutMs = 6_000,
                    )
                    if (response?.optBoolean("ok") == true) {
                        success = true
                        break
                    }
                    appendLog("BadUSB chunk attempt $attempt failed; retrying...")
                    delay(300)
                }
                if (!success) {
                    appendLog("BadUSB upload failed at byte $offset.")
                    return@launch
                }

                offset = end
                _badUsbProgress.value = ((offset * 100) / bytes.size)
            }

            val response = activeSession.sendCommand(
                "START_BADUSB",
                JSONObject().apply {
                    put("filename", remoteFilename)
                    put("msc", mscMode)
                },
                timeoutMs = 10_000,
            )
            if (response?.optBoolean("ok") == true) {
                appendLog("BadUSB payload armed. Unplug and re-plug the device to execute it once.")
                addHistory("badusb", "Payload armed for next boot", HistoryLevel.SUCCESS)
            } else {
                appendLog("Failed to arm BadUSB payload: ${response?.optString("msg") ?: "timeout"}")
            }
        } finally {
            _badUsbUploading.value = false
        }
    }
}

internal fun MainViewModel.refreshStorageImpl() {
    val activeSession = session
    if (activeSession == null) {
        appendLog("Connect to a device before browsing storage.")
        return
    }
    if (_storageLoading.value) return

    _storageLoading.value = true
    scope.launch {
        val response = activeSession.sendCommand("MSC_LIST", timeoutMs = 8_000)
        _storageLoading.value = false
        if (response?.optBoolean("ok") != true) {
            appendLog("Failed to list storage: ${response?.optString("msg") ?: "timeout"}")
            return@launch
        }

        _storageTotal.value = response.optLong("total", 0L)
        _storageUsed.value = response.optLong("used", 0L)
        _storageFree.value = response.optLong("free", 0L)

        val files = mutableListOf<StorageFile>()
        val jsonFiles = response.optJSONArray("files")
        if (jsonFiles != null) {
            for (index in 0 until jsonFiles.length()) {
                val item = jsonFiles.optJSONObject(index) ?: continue
                files += StorageFile(
                    name = item.optString("name"),
                    size = item.optInt("size", 0),
                )
            }
        }
        _storageFiles.value = files
        appendLog("Storage: ${files.size} file(s), ${_storageFree.value} bytes free.")
    }
}

internal fun MainViewModel.deleteStorageFileImpl(name: String) {
    val activeSession = session
    if (activeSession == null) {
        appendLog("Connect to a device before deleting files.")
        return
    }
    scope.launch {
        val response = activeSession.sendCommand(
            "MSC_DELETE",
            JSONObject().put("path", name),
            timeoutMs = 8_000,
        )
        if (response?.optBoolean("ok") == true) {
            appendLog("Deleted $name.")
            refreshStorageImpl()
        } else {
            appendLog("Failed to delete $name: ${response?.optString("msg") ?: "timeout"}")
        }
    }
}

internal fun MainViewModel.startMassStorageImpl() {
    val activeSession = session
    if (activeSession == null) {
        appendLog("Connect to a device before entering mass storage mode.")
        return
    }
    val chip = (_connectionState.value as? ConnectionState.Connected)?.chip
    if (chip !in setOf("ESP32-S2", "ESP32-S3")) {
        appendLog("Mass storage start requires ESP32-S2 or ESP32-S3 (connected chip: ${chip ?: "unknown"}).")
        return
    }

    scope.launch {
        appendLog("Switching device to USB mass storage mode...")
        val response = activeSession.sendCommand("START_MSC", timeoutMs = 5_000)
        if (response?.optBoolean("ok") == true) {
            appendLog("Device is rebooting into mass storage mode; the USB bridge will disappear.")
        } else if (response == null) {
            appendLog("No response, expected: USB is re-enumerating as mass storage.")
        } else {
            appendLog("Device refused mass storage mode: ${response.optString("msg")}")
        }
    }
}
