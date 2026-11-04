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

    fun startModule(moduleId: String, connectionState: ConnectionState?) {
        finishActiveModule(connectionState)
        activeModule = moduleId
        startedAtMs = SystemClock.elapsedRealtime()
        log("module_start", moduleId, 0L, connectionState)
    }

    fun endModule(connectionState: ConnectionState?) {
        finishActiveModule(connectionState)
    }

    private fun finishActiveModule(connectionState: ConnectionState?) {
        val module = activeModule ?: return
        val durationMs = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
        log("module_end", module, durationMs, connectionState)
        activeModule = null
        startedAtMs = 0L
    }

    private fun log(
        event: String,
        moduleId: String,
        durationMs: Long,
        connectionState: ConnectionState?,
    ) {
        val runtime = Runtime.getRuntime()
        val javaUsed = runtime.totalMemory() - runtime.freeMemory()
        val nativeUsed = runCatching { Debug.getNativeHeapAllocatedSize() }.getOrDefault(0L)
        val firmware = connectionState as? ConnectionState.Connected
        Log.i(
            TAG,
            String.format(
                Locale.US,
                "event=%s module=%s duration_ms=%d android_uptime_ms=%d " +
                    "java_used=%d java_total=%d java_max=%d native_alloc=%d " +
                    "fw_heap_free=%s fw_heap_min=%s fw_heap_largest=%s fw_transport=%s",
                event,
                moduleId,
                durationMs,
                SystemClock.elapsedRealtime(),
                javaUsed,
                runtime.totalMemory(),
                runtime.maxMemory(),
                nativeUsed,
                firmware?.heapFree?.toString() ?: "n/a",
                firmware?.heapMinFree?.toString() ?: "n/a",
                firmware?.heapMaxAlloc?.toString() ?: "n/a",
                firmware?.transport ?: "n/a",
            ),
        )
    }
}
