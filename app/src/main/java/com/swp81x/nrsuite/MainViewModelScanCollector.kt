package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.session.NrSession
import com.swp81x.nrsuite.core.wifi.NetworkTarget
import com.swp81x.nrsuite.core.wifi.normalizeWifiChannel
import com.swp81x.nrsuite.core.wifi.shouldRetryWifiScan
import com.swp81x.nrsuite.core.wps.shouldRetryWpsTargetScan
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

internal data class CollectedWifiScan(
    val commandResult: Int?,
    val eventsReceived: Int,
    val aps: List<NetworkTarget>,
)

/**
 * Collects scan_ap events independently of the main UI destination and retries
 * once when firmware reports more APs than the app received. This is used by
 * modules that need a scan snapshot without changing the WiFi Scan screen.
 */
internal suspend fun MainViewModel.collectWifiScanWithRecovery(
    activeSession: NrSession,
    timeoutMs: Long,
    maxAttempts: Int = 2,
): CollectedWifiScan {
    val found = ConcurrentHashMap<String, NetworkTarget>()
    var lastResult: Int? = null
    var lastEvents = 0
    var attempts = 0

    wifiScanDestination = null
    while (attempts < maxAttempts) {
        attempts++
        val attemptEvents = AtomicInteger(0)
        val collector = scope.launch {
            activeSession.events.collect { event ->
                if (event.optString("type") != "scan_ap") return@collect
                val bssid = event.optString("bssid").trim().uppercase()
                if (bssid.isBlank()) return@collect
                attemptEvents.incrementAndGet()
                found[bssid] = NetworkTarget(
                    ssid = event.optString("ssid"),
                    bssid = bssid,
                    channel = normalizeWifiChannel(event.optInt("channel", 1), fallback = 1),
                    rssi = event.optInt("rssi", -100),
                    security = event.optString("security", "UNKNOWN"),
                    wps = event.optBoolean("wps", false),
                )
            }
        }
        try {
            lastResult = activeSession.scanWifi(timeoutMs = timeoutMs)
            if (lastResult != null && lastResult > 0) {
                val drainDeadlineMs = System.currentTimeMillis() + 750
                while (attemptEvents.get() < lastResult &&
                    System.currentTimeMillis() < drainDeadlineMs
                ) {
                    delay(25)
                }
            }
            lastEvents = attemptEvents.get()
        } finally {
            delay(150)
            collector.cancelAndJoin()
        }

        if (!shouldRetryWifiScan(lastResult, lastEvents) || attempts >= maxAttempts) {
            break
        }
        delay(1_200)
    }

    return CollectedWifiScan(
        commandResult = lastResult,
        eventsReceived = lastEvents,
        aps = found.values.sortedByDescending { it.rssi },
    )
}

internal data class WpsLockScanResult(
    val response: JSONObject?,
    val reportedCount: Int,
    val receivedCount: Int,
)

/**
 * Runs WPS_LOCK_SCAN with a single recovery attempt when target events are
 * missing. The module-specific event handler merges delivered targets into its
 * existing visible list.
 */
internal suspend fun MainViewModel.runWpsLockScanWithRecovery(
    activeSession: NrSession,
    maxAttempts: Int = 2,
): WpsLockScanResult {
    var response: JSONObject? = null
    var reportedCount = 0
    var receivedCount = 0
    var attempts = 0

    while (attempts < maxAttempts) {
        attempts++
        wpsLockEventsReceived.set(0)
        response = runCatching {
            activeSession.sendCommand(
                "WPS_LOCK_SCAN",
                JSONObject().apply {
                    put("band", "auto")
                    put("dwell_ms", 120)
                },
                timeoutMs = 45_000,
            )
        }.getOrNull()
        reportedCount = response?.optInt("count", 0) ?: 0

        if (reportedCount > 0) {
            val drainDeadlineMs = System.currentTimeMillis() + 750
            while (wpsLockEventsReceived.get() < reportedCount &&
                System.currentTimeMillis() < drainDeadlineMs
            ) {
                delay(25)
            }
        }
        receivedCount = wpsLockEventsReceived.get()
        val responseOk = response?.optBoolean("ok") == true
        if (responseOk &&
            !shouldRetryWpsTargetScan(reportedCount, receivedCount)
        ) {
            break
        }
        if (attempts >= maxAttempts) break
        delay(1_200)
    }

    return WpsLockScanResult(
        response = response,
        reportedCount = reportedCount,
        receivedCount = receivedCount,
    )
}
