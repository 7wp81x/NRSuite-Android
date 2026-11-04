package com.swp81x.nrsuite.core.wifi

/**
 * A scan is retried only when no asynchronous scan_ap events arrived. Once at
 * least one result has reached the UI, retrying would leave the scan button in
 * a loading state while results are already visible, and a later transport
 * mismatch could replace those visible results with the previous list.
 */
internal fun shouldRetryWifiScan(eventsReceived: Int): Boolean = eventsReceived == 0

internal fun shouldKeepPartialScanResults(
    eventsReceived: Int,
    currentResultCount: Int,
): Boolean = currentResultCount > 0 && eventsReceived > 0
