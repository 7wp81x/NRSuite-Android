package com.swp81x.nrsuite

import android.net.Uri
import android.util.Base64
import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.hid.PinAttemptRecord
import com.swp81x.nrsuite.core.hid.PinBruteforceMode
import com.swp81x.nrsuite.core.hid.PinBruteforceSource
import com.swp81x.nrsuite.core.hid.PinBruteforceUiState
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.session.ConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val PIN_WORDLIST_REMOTE_NAME = "/pin_wordlist.txt"
private const val PIN_WORDLIST_MAX_BYTES = 16 * 1024
private const val PIN_WORDLIST_MAX_LINES = 1000
private const val PIN_WORDLIST_CHUNK_SIZE = 512
private const val PIN_ATTEMPT_FEED_LIMIT = 200

internal fun MainViewModel.setPinBruteforceSourceImpl(value: String) {
    val source = PinBruteforceSource.entries.firstOrNull { it.wireName == value }
        ?: PinBruteforceSource.RANGE
    _pinBruteforce.update { it.copy(source = source) }
}

internal fun MainViewModel.setPinBruteforcePinLengthImpl(value: Int) {
    _pinBruteforce.update { it.copy(pinLength = value.coerceIn(4, 12)) }
}

internal fun MainViewModel.setPinBruteforceStartImpl(value: String) {
    _pinBruteforce.update { it.copy(start = value.filter { ch -> ch.isLetterOrDigit() }.take(12)) }
}

internal fun MainViewModel.setPinBruteforceEndImpl(value: String) {
    _pinBruteforce.update { it.copy(end = value.filter { ch -> ch.isLetterOrDigit() }.take(12)) }
}

internal fun MainViewModel.setPinBruteforceCharsetImpl(value: String) {
    _pinBruteforce.update { it.copy(charset = if (value == "custom") "custom" else "digits") }
}

internal fun MainViewModel.setPinBruteforceCustomCharsImpl(value: String) {
    _pinBruteforce.update { it.copy(customChars = value.take(62)) }
}

internal fun MainViewModel.setPinBruteforcePrefixImpl(value: String) {
    _pinBruteforce.update { it.copy(prefix = value.take(32)) }
}

internal fun MainViewModel.setPinBruteforceSuffixImpl(value: String) {
    _pinBruteforce.update { it.copy(suffix = value.take(32)) }
}

internal fun MainViewModel.setPinBruteforceTimeoutImpl(value: Int) {
    _pinBruteforce.update { it.copy(timeoutMs = value.coerceIn(50, 60_000)) }
}

internal fun MainViewModel.setPinBruteforceAttemptsBeforeTimeoutImpl(value: Int) {
    _pinBruteforce.update { it.copy(attemptsBeforeTimeout = value.coerceIn(0, 1000)) }
}

internal fun MainViewModel.setPinBruteforceCooldownImpl(value: Int) {
    _pinBruteforce.update { it.copy(cooldownMs = value.coerceIn(0, 600_000)) }
}

internal fun MainViewModel.setPinBruteforceInterAttemptImpl(value: Int) {
    _pinBruteforce.update { it.copy(interAttemptMs = value.coerceIn(0, 60_000)) }
}

internal fun MainViewModel.setPinBruteforceAutoResumeImpl(value: Boolean) {
    _pinBruteforce.update { it.copy(autoResume = value) }
}

internal fun MainViewModel.setPinBruteforceSessionKeyImpl(value: String) {
    _pinBruteforce.update { it.copy(sessionKey = value.take(32)) }
}

internal fun MainViewModel.setPinBruteforceWordlistImpl(uri: Uri, name: String?) {
    _pinBruteforce.update {
        it.copy(
            wordlistUri = uri,
            wordlistName = name ?: uri.lastPathSegment ?: "pin_wordlist.txt",
            wordlistLines = 0,
            wordlistBytes = 0L,
            wordlistUploadedToDevice = false,
            wordlistError = null,
            wordlistUploadProgress = 0,
        )
    }
    appendLog("PIN wordlist selected: ${_pinBruteforce.value.wordlistName}")

    val activeSession = session ?: run {
        _pinBruteforce.update { it.copy(wordlistError = "Connect a device first.") }
        return
    }
    scope.launch {
        val bytes = withContext(Dispatchers.IO) {
            runCatching {
                app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
        }
        if (bytes == null || bytes.isEmpty()) {
            _pinBruteforce.update { it.copy(wordlistError = "Could not read the selected wordlist.") }
            return@launch
        }
        if (bytes.size > PIN_WORDLIST_MAX_BYTES) {
            _pinBruteforce.update {
                it.copy(wordlistError = "Wordlist is ${bytes.size} bytes; limit is 16 KB.")
            }
            return@launch
        }
        val lines = bytes.toString(Charsets.UTF_8).lineSequence().count { line -> line.trim().isNotEmpty() }
        if (lines > PIN_WORDLIST_MAX_LINES) {
            _pinBruteforce.update {
                it.copy(wordlistError = "Wordlist has $lines lines; limit is 1000.")
            }
            return@launch
        }

        _pinBruteforce.update {
            it.copy(
                wordlistLines = lines,
                wordlistBytes = bytes.size.toLong(),
                wordlistUploading = true,
                wordlistUploadProgress = 0,
                wordlistError = null,
            )
        }
        val ok = uploadPinWordlistImpl(activeSession, bytes)
        _pinBruteforce.update {
            it.copy(
                wordlistUploading = false,
                wordlistUploadProgress = if (ok) 100 else it.wordlistUploadProgress,
                wordlistUploadedToDevice = ok,
                wordlistError = if (ok) null else "Wordlist upload failed.",
            )
        }
        if (ok) {
            appendLog("PIN wordlist uploaded: $lines line(s), ${bytes.size} bytes.")
        }
    }
}

internal fun MainViewModel.clearPinBruteforceWordlistImpl() {
    _pinBruteforce.update {
        it.copy(
            wordlistUri = null,
            wordlistName = null,
            wordlistLines = 0,
            wordlistBytes = 0L,
            wordlistUploadedToDevice = false,
            wordlistUploadProgress = 0,
            wordlistError = null,
        )
    }
}

private suspend fun MainViewModel.uploadPinWordlistImpl(
    activeSession: com.swp81x.nrsuite.core.session.NrSession,
    bytes: ByteArray,
): Boolean {
    val reset = activeSession.sendCommand("PIN_WORDLIST_RESET", JSONObject(), timeoutMs = 5_000)
    if (reset?.optBoolean("ok") != true) {
        appendLog("Device rejected PIN_WORDLIST_RESET.")
        return false
    }

    var offset = 0
    while (offset < bytes.size) {
        val end = minOf(offset + PIN_WORDLIST_CHUNK_SIZE, bytes.size)
        val chunk = bytes.copyOfRange(offset, end)
        val encoded = Base64.encodeToString(chunk, Base64.NO_WRAP)
        val isLast = end == bytes.size

        var success = false
        for (attempt in 1..3) {
            val response = activeSession.sendCommand(
                "PIN_WORDLIST_CHUNK",
                JSONObject().apply {
                    put("data", encoded)
                    put("last", isLast)
                },
                timeoutMs = 6_000,
            )
            if (response?.optBoolean("ok") == true) {
                success = true
                break
            }
            delay(250)
        }
        if (!success) {
            appendLog("PIN wordlist upload failed at byte $offset.")
            return false
        }

        offset = end
        _pinBruteforce.update {
            it.copy(wordlistUploadProgress = ((offset * 100) / bytes.size))
        }
    }
    return true
}

internal fun MainViewModel.startPinBruteforceImpl() {
    val activeSession = session
    if (activeSession == null) {
        appendLog("Connect a device before starting PIN bruteforce.")
        return
    }
    val state = _pinBruteforce.value
    if (state.running || state.starting) return
    if (state.source == null) {
        appendLog("Select range or wordlist before arming PIN bruteforce.", level = LogLevel.ERROR)
        return
    }

    val connected = activeSession.state.value as? ConnectionState.Connected
    if (connected != null && connected.features.isNotEmpty() &&
        "pin_bruteforce" !in connected.features
    ) {
        appendLog("The connected firmware does not advertise PIN bruteforce support.", level = LogLevel.ERROR)
        return
    }
    if (state.mode == PinBruteforceMode.STANDALONE_NATIVE_HID &&
        connected?.chip !in setOf("ESP32-S2", "ESP32-S3")
    ) {
        appendLog("standalone_native_hid requires ESP32-S2 or ESP32-S3.", level = LogLevel.ERROR)
        return
    }
    if (state.source == PinBruteforceSource.WORDLIST && !state.wordlistUploadedToDevice) {
        appendLog("Upload a wordlist before starting wordlist mode.", level = LogLevel.ERROR)
        return
    }

    _pinBruteforce.update { it.copy(starting = true, lastError = "", state = "starting") }
    scope.launch {
        val args = JSONObject().apply {
            put("mode", PinBruteforceMode.STANDALONE_NATIVE_HID.wireName)
            put("source", state.source?.wireName ?: PinBruteforceSource.RANGE.wireName)
            put("pin_length", state.pinLength)
            put("start", state.start)
            put("end", state.end)
            put("charset", if (state.charset == "custom") "custom" else "digits")
            put("custom_chars", state.customChars)
            put("prefix", state.prefix)
            put("suffix", state.suffix)
            put("timeout_ms", state.timeoutMs)
            put("attempts_before_cooldown", state.attemptsBeforeTimeout)
            put("attempts_before_timeout", state.attemptsBeforeTimeout)
            put("cooldown_ms", state.cooldownMs)
            put("inter_attempt_ms", state.interAttemptMs)
            put("auto_resume", state.autoResume)
            put("session_key", state.sessionKey.ifBlank { "default" })
            if (state.source == PinBruteforceSource.WORDLIST) {
                put("wordlist_file", PIN_WORDLIST_REMOTE_NAME)
            }
        }
        try {
            val response = activeSession.sendCommand("PIN_RANGE_START", args, timeoutMs = 10_000)
            if (response?.optBoolean("ok") == true) {
                applyPinStatus(response)
                if (_pinBruteforce.value.state == "starting") {
                    _pinBruteforce.update { it.copy(state = "armed") }
                }
                if (_pinBruteforce.value.state == "armed") {
                    appendLog(
                        "PIN bruteforce armed. Unplug and re-plug to execute; it reboots to normal when done.",
                        tag = "pin",
                    )
                    addHistory("pin_bruteforce", "PIN bruteforce armed", HistoryLevel.SUCCESS)
                } else {
                    appendLog("PIN bruteforce started (${state.mode.wireName}, ${state.source?.wireName ?: "range"}).")
                    addHistory("pin_bruteforce", "PIN bruteforce started", HistoryLevel.SUCCESS)
                }
            } else {
                val message = response?.optString("msg") ?: "timeout"
                _pinBruteforce.update { it.copy(state = "error", lastError = message) }
                appendLog("PIN bruteforce start failed: $message", level = LogLevel.ERROR)
            }
        } catch (t: Throwable) {
            _pinBruteforce.update { it.copy(state = "error", lastError = t.message ?: "arm failed") }
            appendLog("PIN bruteforce arm failed: ${t.message ?: t.javaClass.simpleName}", level = LogLevel.ERROR)
        } finally {
            _pinBruteforce.update { it.copy(starting = false) }
        }
    }
}

internal fun MainViewModel.stopPinBruteforceImpl() {
    val activeSession = session ?: return
    scope.launch {
        val response = activeSession.sendCommand("PIN_RANGE_STOP", timeoutMs = 8_000)
        if (response?.optBoolean("ok") == true) {
            applyPinStatus(response)
        } else {
            _pinBruteforce.update { it.copy(state = "paused") }
        }
        appendLog("PIN bruteforce stopped.")
    }
}

internal fun MainViewModel.resumePinBruteforceImpl() {
    val activeSession = session ?: return
    scope.launch {
        val response = activeSession.sendCommand("PIN_RANGE_RESUME", timeoutMs = 8_000)
        if (response?.optBoolean("ok") == true) {
            applyPinStatus(response)
            appendLog("PIN bruteforce resumed.")
        } else {
            appendLog("PIN bruteforce resume failed: ${response?.optString("msg") ?: "timeout"}")
        }
    }
}

internal fun MainViewModel.clearPinBruteforceImpl() {
    val activeSession = session ?: return
    scope.launch {
        activeSession.sendCommand(
            "PIN_RANGE_CLEAR",
            JSONObject().put("wordlist", false),
            timeoutMs = 8_000,
        )
        _pinAttempts.value = emptyList()
        _pinBruteforce.update {
            it.copy(
                state = "idle",
                currentSequence = 0,
                attemptsTotal = 0,
                attemptsInCycle = 0,
                lastPin = "",
                lastError = "",
                startedAt = 0,
                updatedAt = 0,
                persistedInNvs = false,
            )
        }
        appendLog("PIN bruteforce session cleared.")
    }
}

internal fun MainViewModel.refreshPinBruteforceImpl() {
    val activeSession = session ?: return
    scope.launch {
        val response = activeSession.sendCommand("PIN_RANGE_STATUS", timeoutMs = 6_000)
        if (response?.optBoolean("ok") == true) applyPinStatus(response)
    }
}

internal fun MainViewModel.handlePinSessionStatusEvent(event: JSONObject) {
    applyPinStatus(event)
    event.optString("reason").takeIf { it.isNotBlank() }?.let {
        appendLog("PIN session: $it", tag = "pin")
    }
}

internal fun MainViewModel.handlePinAttemptEvent(event: JSONObject) {
    val sequence = event.optInt("current_sequence", _pinBruteforce.value.currentSequence)
    val pin = event.optString("pin", _pinBruteforce.value.lastPin)
    val attempts = event.optInt("attempts_total", _pinBruteforce.value.attemptsTotal)
    _pinBruteforce.update {
        it.copy(
            state = "running",
            currentSequence = sequence,
            lastPin = pin,
            attemptsTotal = attempts,
            attemptsInCycle = event.optInt("attempts_in_cycle", it.attemptsInCycle),
            startedAt = event.optInt("started_at", it.startedAt),
            updatedAt = event.optInt("updated_at", it.updatedAt),
        )
    }
    _pinAttempts.update { current ->
        (current + PinAttemptRecord(
            sequence = sequence,
            pin = pin,
            attemptsTotal = attempts,
            timestamp = timeHmNow(),
        )).takeLast(PIN_ATTEMPT_FEED_LIMIT)
    }
}

internal fun MainViewModel.handlePinRangeDoneEvent(event: JSONObject) {
    _pinBruteforce.update {
        it.copy(
            state = "done",
            attemptsTotal = event.optInt("attempts_total", it.attemptsTotal),
            lastPin = event.optString("last_pin", it.lastPin),
            updatedAt = event.optInt("updated_at", it.updatedAt),
        )
    }
    appendLog("PIN bruteforce range completed after ${_pinBruteforce.value.attemptsTotal} attempt(s).")
    addHistory("pin_bruteforce", "PIN range completed", HistoryLevel.SUCCESS)
}

internal fun MainViewModel.handlePinRangeErrorEvent(event: JSONObject) {
    _pinBruteforce.update {
        it.copy(
            state = "error",
            lastError = event.optString("msg", "PIN bruteforce error"),
        )
    }
    appendLog("PIN bruteforce error: ${_pinBruteforce.value.lastError}", level = LogLevel.ERROR)
}

private fun MainViewModel.applyPinStatus(json: JSONObject) {
    _pinBruteforce.update { current ->
        current.copy(
            state = json.optString("state", current.state),
            mode = PinBruteforceMode.entries.firstOrNull {
                it.wireName == json.optString("mode", current.mode.wireName)
            } ?: current.mode,
            source = PinBruteforceSource.entries.firstOrNull {
                it.wireName == json.optString("source", current.source?.wireName ?: "")
            } ?: current.source,
            pinLength = json.optInt("pin_length", current.pinLength),
            start = json.optString("start", current.start),
            end = json.optString("end", current.end),
            charset = json.optString(
                "charset_mode",
                if (json.optString("charset") == "0123456789") "digits" else current.charset,
            ),
            customChars = if (json.optString("charset_mode", "") == "custom") {
                json.optString("charset", current.customChars)
            } else {
                current.customChars
            },
            prefix = json.optString("prefix", current.prefix),
            suffix = json.optString("suffix", current.suffix),
            timeoutMs = json.optInt("timeout_ms", current.timeoutMs),
            attemptsBeforeTimeout = json.optInt(
                "attempts_before_cooldown",
                json.optInt("attempts_before_timeout", current.attemptsBeforeTimeout),
            ),
            cooldownMs = json.optInt("cooldown_ms", current.cooldownMs),
            interAttemptMs = json.optInt("inter_attempt_ms", current.interAttemptMs),
            autoResume = json.optBoolean("auto_resume", current.autoResume),
            sessionKey = json.optString("session_key", current.sessionKey),
            currentSequence = json.optInt("current_sequence", current.currentSequence),
            attemptsTotal = json.optInt("attempts_total", current.attemptsTotal),
            attemptsInCycle = json.optInt("attempts_in_cycle", current.attemptsInCycle),
            lastPin = json.optString("last_pin", current.lastPin),
            lastError = json.optString("last_error", current.lastError),
            startedAt = json.optInt("started_at", current.startedAt),
            updatedAt = json.optInt("updated_at", current.updatedAt),
            persistedInNvs = json.optBoolean("persisted_in_nvs", current.persistedInNvs),
            wordlistName = if (json.optString("source") == "wordlist" &&
                json.optBoolean("wordlist_present", false)
            ) {
                json.optString("wordlist_file", "/pin_wordlist.txt").substringAfterLast('/')
            } else {
                current.wordlistName
            },
            wordlistLines = json.optInt("wordlist_lines", current.wordlistLines),
            wordlistBytes = json.optLong("wordlist_bytes", current.wordlistBytes),
            wordlistUploadedToDevice = if (json.optString("source") == "wordlist") {
                json.optBoolean("wordlist_present", current.wordlistUploadedToDevice)
            } else {
                current.wordlistUploadedToDevice
            },
        )
    }
}
