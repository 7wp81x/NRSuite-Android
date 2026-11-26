package com.swp81x.nrsuite.core.wps

/**
 * WPS target scans are complete only when the number of processed
 * `wps_lock_ap` events matches the firmware's reported target count.
 */
internal fun shouldRetryWpsTargetScan(
    reportedCount: Int?,
    receivedCount: Int,
): Boolean = reportedCount == null || receivedCount < reportedCount

internal fun shouldKeepWpsScanResults(
    receivedCount: Int,
    visibleTargetCount: Int,
): Boolean = receivedCount > 0 && visibleTargetCount > 0
