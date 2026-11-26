package com.swp81x.nrsuite.core.log

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import com.swp81x.nrsuite.core.session.ConnectionState
import java.util.Locale

/**
 * Minimal lifecycle memory logger. Emits one line when a module opens and one
 * line when it closes, including Android heap/native heap and the latest
 * firmware heap snapshot when available.
 */
object NrSuiteMemoryLogger {
    private const val TAG = "NRSuiteMem"

    private var activeModule: String? = null
    private var startedAtMs: Long = 0L

    fun startModule(
        moduleId: String,
        connectionState: ConnectionState?,
        deviceLabel: String,
    ) {
        finishActiveModule(connectionState, deviceLabel)
        activeModule = moduleId
        startedAtMs = SystemClock.elapsedRealtime()
        log("module_start", moduleId, 0L, connectionState, deviceLabel)
    }

    fun endModule(connectionState: ConnectionState?, deviceLabel: String) {
        finishActiveModule(connectionState, deviceLabel)
    }

    /**
     * Logs a firmware lifecycle command such as START_SNIFF/STOP_SNIFF with
     * command timestamps/duration. Noise commands are filtered by the caller.
     */
    fun logOperation(
        deviceLabel: String,
        command: String,
        startedAtMs: Long,
        endedAtMs: Long,
        connectionState: ConnectionState?,
    ) {
        log(
            event = "operation",
            moduleId = command,
            durationMs = (endedAtMs - startedAtMs).coerceAtLeast(0L),
            connectionState = connectionState,
            deviceLabel = deviceLabel,
            operationStartedAtMs = startedAtMs,
            operationEndedAtMs = endedAtMs,
        )
    }

    private fun finishActiveModule(connectionState: ConnectionState?, deviceLabel: String) {
        val module = activeModule ?: return
        val durationMs = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
        log("module_end", module, durationMs, connectionState, deviceLabel)
        activeModule = null
        startedAtMs = 0L
    }

    private fun log(
        event: String,
        moduleId: String,
        durationMs: Long,
        connectionState: ConnectionState?,
        deviceLabel: String,
        operationStartedAtMs: Long? = null,
        operationEndedAtMs: Long? = null,
    ) {
        val runtime = Runtime.getRuntime()
        val javaUsed = runtime.totalMemory() - runtime.freeMemory()
        val nativeUsed = runCatching { Debug.getNativeHeapAllocatedSize() }.getOrDefault(0L)
        val firmware = connectionState as? ConnectionState.Connected
        Log.i(
            TAG,
            String.format(
                Locale.US,
                "event=%s device=%s module=%s duration_ms=%d " +
                    "op_start_ms=%s op_end_ms=%s android_uptime_ms=%d " +
                    "java_used=%d java_total=%d java_max=%d native_alloc=%d " +
                    "fw_heap_free=%s fw_heap_min=%s fw_heap_largest=%s fw_transport=%s fw_id=%s",
                event,
                deviceLabel,
                moduleId,
                durationMs,
                operationStartedAtMs?.toString() ?: "n/a",
                operationEndedAtMs?.toString() ?: "n/a",
                SystemClock.elapsedRealtime(),
                javaUsed,
                runtime.totalMemory(),
                runtime.maxMemory(),
                nativeUsed,
                firmware?.heapFree?.toString() ?: "n/a",
                firmware?.heapMinFree?.toString() ?: "n/a",
                firmware?.heapMaxAlloc?.toString() ?: "n/a",
                firmware?.transport ?: "n/a",
                firmware?.deviceId ?: "n/a",
            ),
        )
    }
}
