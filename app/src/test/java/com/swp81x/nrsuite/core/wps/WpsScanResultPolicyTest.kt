package com.swp81x.nrsuite.core.wps

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WpsScanResultPolicyTest {
    @Test
    fun retriesWhenFewerEventsArrivedThanReported() {
        assertTrue(shouldRetryWpsTargetScan(reportedCount = 2, receivedCount = 1))
    }

    @Test
    fun completesWhenAllEventsArrived() {
        assertFalse(shouldRetryWpsTargetScan(reportedCount = 2, receivedCount = 2))
    }

    @Test
    fun retriesWhenResponseIsMissing() {
        assertTrue(shouldRetryWpsTargetScan(reportedCount = null, receivedCount = 0))
    }

    @Test
    fun keepsVisiblePartialTargets() {
        assertTrue(shouldKeepWpsScanResults(receivedCount = 1, visibleTargetCount = 1))
    }

    @Test
    fun doesNotKeepEmptyResults() {
        assertFalse(shouldKeepWpsScanResults(receivedCount = 0, visibleTargetCount = 0))
    }
}
