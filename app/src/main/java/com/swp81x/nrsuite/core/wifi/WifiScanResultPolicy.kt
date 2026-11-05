package com.swp81x.nrsuite.core.wifi

/**
 * A scan is retried when no asynchronous scan_ap events arrived, or when the
 * firmware reported more results than Android received. The caller keeps any
 * already-visible results and merges the next attempt into the same list.
 */
internal fun shouldRetryWifiScan(count: Int?, eventsReceived: Int): Boolean =
    eventsReceived == 0 || (count != null && count > eventsReceived)

internal fun shouldKeepPartialScanResults(
    eventsReceived: Int,
    currentResultCount: Int,
): Boolean = currentResultCount > 0 && eventsReceived > 0
