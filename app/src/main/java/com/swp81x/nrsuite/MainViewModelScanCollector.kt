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

internal fun JSONObject.toNetworkTargetOrNull(): NetworkTarget? {
    val bssid = optString("bssid").trim().uppercase()
    if (bssid.isBlank()) return null
    return NetworkTarget(
        ssid = optString("ssid"),
        bssid = bssid,
        channel = normalizeWifiChannel(optInt("channel", 1), fallback = 1),
        rssi = optInt("rssi", -100),
        security = optString("security", "UNKNOWN"),
        wps = optBoolean("wps", false),
    )
}

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
                val target = event.toNetworkTargetOrNull() ?: return@collect
                attemptEvents.incrementAndGet()
                found[target.bssid] = target
            }
        }
        try {
            val snapshot = activeSession.scanWifiSnapshot(timeoutMs = timeoutMs)
            lastResult = when {
                snapshot.response == null -> null
                !snapshot.response.optBoolean("ok") -> -1
                else -> snapshot.expectedCount
            }
            if (snapshot.pulled && snapshot.response?.optBoolean("ok") == true) {
                snapshot.results.forEach { result ->
                    val target = result.toNetworkTargetOrNull() ?: return@forEach
                    found[target.bssid] = target
                }
                lastEvents = snapshot.receivedCount
                // Pull mode already retried each failed/corrupt chunk.
                break
            }
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
    val transportError: String? = null,
)

/**
 * Runs WPS_LOCK_SCAN with a single recovery attempt when target events are
 * missing. The module-specific event handler merges delivered targets into its
 * existing visible list.
 */
internal suspend fun MainViewModel.runWpsLockScanWithRecovery(
    activeSession: NrSession,
    destination: WifiScanDestination?,
    maxAttempts: Int = 2,
): WpsLockScanResult {
    var response: JSONObject? = null
    var reportedCount = 0
    var receivedCount = 0
    var attempts = 0

    var transportError: String? = null
    while (attempts < maxAttempts) {
        attempts++
        wpsLockEventsReceived.set(0)
        val snapshot = runCatching {
            activeSession.scanWpsLockSnapshot(timeoutMs = 45_000, dwellMs = 120)
        }.getOrNull()
        response = snapshot?.response
        reportedCount = snapshot?.expectedCount ?: 0
        transportError = snapshot?.error

        if (snapshot?.pulled == true) {
            snapshot.results.forEach { event ->
                wpsLockEventsReceived.incrementAndGet()
                handleWpsLockApEventImpl(event, destination)
            }
            receivedCount = snapshot.receivedCount
            if (response?.optBoolean("ok") == true) break
            if (attempts >= maxAttempts) break
            delay(1_200)
            continue
        }

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
        transportError = transportError,
    )
}
