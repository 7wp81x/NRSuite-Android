package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.sensors.Ld2450Target
import com.swp81x.nrsuite.core.session.ConnectionState
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.abs

internal fun MainViewModel.startLd2450Impl() {
    val activeSession = session ?: run {
        appendLog("Connect a device before starting mmWave radar.", level = LogLevel.ERROR)
        return
    }
    if (_ld2450.value.starting || _ld2450.value.active) return

    val connected = activeSession.state.value as? ConnectionState.Connected
    if (connected != null && connected.features.isNotEmpty() && "ld2450" !in connected.features) {
        appendLog(
            "The connected firmware does not advertise LD2450 support.",
            level = LogLevel.ERROR,
        )
        return
    }

    _ld2450.update {
        it.copy(starting = true, errorKind = null, errorMessage = null)
    }
    scope.launch {
        try {
            val response = activeSession.sendCommand(
                "LD2450_START",
                JSONObject().put("debug", false),
                timeoutMs = 6_000,
            )
            if (response?.optBoolean("ok") == true) {
                applyLd2450Status(response)
                appendLog("mmWave radar monitoring started.", tag = "ld2450")
                addHistory("ld2450", "mmWave radar started", HistoryLevel.SUCCESS)
            } else {
                val message = response?.optString("msg") ?: "timeout"
                _ld2450.update {
                    it.copy(
                        active = false,
                        errorKind = "start",
                        errorMessage = message,
                    )
                }
                appendLog("mmWave radar start failed: $message", level = LogLevel.ERROR)
            }
        } catch (t: Throwable) {
            _ld2450.update {
                it.copy(
                    active = false,
                    errorKind = "start",
                    errorMessage = t.message ?: "start failed",
                )
            }
            appendLog(
                "mmWave radar start failed: ${t.message ?: t.javaClass.simpleName}",
                level = LogLevel.ERROR,
            )
        } finally {
            _ld2450.update { it.copy(starting = false) }
        }
    }
}

internal fun MainViewModel.stopLd2450Impl() {
    val activeSession = session ?: return
    if (_ld2450.value.stopping) return
    _ld2450.update { it.copy(stopping = true) }
    scope.launch {
        try {
            val response = activeSession.sendCommand("LD2450_STOP", timeoutMs = 6_000)
            if (response?.optBoolean("ok") == true) {
                applyLd2450Status(response)
            }
            _ld2450.update {
                it.copy(
                    active = false,
                    targets = emptyList(),
                    targetCount = 0,
                    lastFrameAgeMs = null,
                )
            }
            appendLog("mmWave radar stopped.", tag = "ld2450")
        } finally {
            _ld2450.update { it.copy(stopping = false) }
        }
    }
}

internal fun MainViewModel.refreshLd2450Impl() {
    val activeSession = session ?: return
    scope.launch {
        val response = activeSession.sendCommand("LD2450_STATUS", timeoutMs = 5_000)
        if (response?.optBoolean("ok") == true) {
            applyLd2450Status(response)

            // Devkit bring-up builds boot with serial debug enabled. Once the
            // app is driving the sensor, restart it in protocol mode so debug
            // text cannot share the NRSuite protocol serial stream.
            if (response.optBoolean("active") && response.optBoolean("debug")) {
                val restarted = activeSession.sendCommand(
                    "LD2450_START",
                    JSONObject().put("debug", false),
                    timeoutMs = 6_000,
                )
                if (restarted?.optBoolean("ok") == true) {
                    applyLd2450Status(restarted)
                    appendLog("mmWave radar restarted with protocol-safe debug off.", tag = "ld2450")
                }
            }
        }
    }
}

internal fun MainViewModel.setLd2450MaxRangeImpl(valueCm: Int) {
    _ld2450.update { it.copy(maxRangeCm = valueCm.coerceIn(100, 800)) }
}

internal fun MainViewModel.setLd2450ZoneAlertImpl(enabled: Boolean) {
    _ld2450.update { it.copy(zoneAlertEnabled = enabled) }
}

internal fun MainViewModel.setLd2450MeshBroadcastImpl(enabled: Boolean) {
    // Mesh broadcast is intentionally disabled on feature/non-mesh.
    _ld2450.update { it.copy(meshBroadcastEnabled = enabled) }
}

internal fun MainViewModel.setLd2450HeartbeatEnabledImpl(enabled: Boolean) {
    _ld2450.update { it.copy(heartbeatEnabled = enabled) }
}

internal fun MainViewModel.setLd2450ScanDurationImpl(valueMs: Int) {
    _ld2450.update { it.copy(scanDurationMs = valueMs.coerceIn(500, 3000)) }
}

internal fun MainViewModel.setLd2450SleepDurationImpl(valueMs: Int) {
    _ld2450.update { it.copy(sleepDurationMs = valueMs.coerceIn(500, 5000)) }
}

internal fun MainViewModel.setLd2450LimitDotCountImpl(enabled: Boolean) {
    _ld2450.update { it.copy(limitDotCount = enabled) }
}

internal fun MainViewModel.setLd2450MaxDotsImpl(value: Int) {
    _ld2450.update { it.copy(maxDots = value.coerceIn(1, 32)) }
}

internal fun MainViewModel.handleLd2450TargetsEvent(event: JSONObject) {
    val targets = buildList {
        val array = event.optJSONArray("targets") ?: return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val rawY = item.optInt("y_mm", 0)
            add(
                Ld2450Target(
                    slot = item.optInt("slot", index + 1),
                    xMm = item.optInt("x_mm", 0),
                    // The corrected firmware reports positive forward Y.
                    // abs() keeps the UI robust if an older build inverted it.
                    yMm = abs(rawY),
                    speedCmS = item.optInt("speed_cm_s", 0),
                    resolutionMm = item.optInt("resolution_mm", 0),
                ),
            )
        }
    }.sortedBy { it.slot }

    _ld2450.update {
        it.copy(
            active = true,
            hasData = true,
            targets = targets,
            targetCount = targets.size,
            validFrames = event.optInt("valid_frames", it.validFrames),
            errorKind = null,
            errorMessage = null,
            lastFrameAgeMs = 0L,
            lastUpdatedAt = System.currentTimeMillis(),
        )
    }
}

internal fun MainViewModel.handleLd2450PresenceEvent(event: JSONObject) {
    val present = event.optBoolean("present")
    val count = event.optInt("target_count", if (present) 1 else 0)
    val previousPresent = _ld2450.value.targetCount > 0
    _ld2450.update {
        it.copy(
            active = true,
            targetCount = count,
            targets = if (present) it.targets else emptyList(),
            hasData = it.hasData || present,
            lastUpdatedAt = System.currentTimeMillis(),
        )
    }
    if (present != previousPresent) {
        appendLog(
            if (present) "mmWave radar: target detected." else "mmWave radar: target lost.",
            tag = "ld2450",
        )
    }
}

internal fun MainViewModel.handleLd2450ErrorEvent(event: JSONObject) {
    val kind = event.optString("kind", "error")
    val message = event.optString("msg", "LD2450 error")
    val previousKind = _ld2450.value.errorKind
    _ld2450.update {
        it.copy(
            active = event.optBoolean("active", it.active),
            hasData = event.optInt("valid_frames", it.validFrames) > 0,
            validFrames = event.optInt("valid_frames", it.validFrames),
            invalidFrames = event.optInt("invalid_frames", it.invalidFrames),
            droppedBytes = event.optInt("dropped_bytes", it.droppedBytes),
            lastFrameAgeMs = event.optLong("last_frame_age_ms", it.lastFrameAgeMs ?: 0L),
            errorKind = kind,
            errorMessage = message,
            lastUpdatedAt = System.currentTimeMillis(),
        )
    }
    if (previousKind != kind) {
        appendLog("mmWave radar error: $message", level = LogLevel.ERROR, tag = "ld2450")
    }
}

private fun MainViewModel.applyLd2450Status(json: JSONObject) {
    val active = json.optBoolean("active", _ld2450.value.active)
    val hasData = json.optBoolean("has_data", _ld2450.value.hasData)
    val targetCount = json.optInt("target_count", _ld2450.value.targetCount)
    val noData = active && !hasData
    _ld2450.update {
        it.copy(
            active = active,
            hasData = hasData,
            targetCount = targetCount,
            targets = if (targetCount == 0) emptyList() else it.targets,
            validFrames = json.optInt("valid_frames", it.validFrames),
            invalidFrames = json.optInt("invalid_frames", it.invalidFrames),
            droppedBytes = json.optInt("dropped_bytes", it.droppedBytes),
            lastFrameAgeMs = if (json.has("last_frame_age_ms")) {
                json.optLong("last_frame_age_ms", it.lastFrameAgeMs ?: 0L)
            } else {
                it.lastFrameAgeMs
            },
            errorKind = when {
                noData -> "no_data"
                json.optString("kind").isNotBlank() -> json.optString("kind")
                else -> null
            },
            errorMessage = when {
                noData -> "No LD2450 frames received"
                json.optString("msg").isNotBlank() -> json.optString("msg")
                else -> null
            },
            lastUpdatedAt = System.currentTimeMillis(),
        )
    }
}
