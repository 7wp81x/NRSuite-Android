package com.swp81x.nrsuite.core.wifi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiScanResultPolicyTest {
    @Test
    fun partialEventLossTriggersOneRecoveryAttempt() {
        assertTrue(shouldRetryWifiScan(count = 6, eventsReceived = 4))
    }

    @Test
    fun completeEventCountDoesNotRetry() {
        assertFalse(shouldRetryWifiScan(count = 6, eventsReceived = 6))
    }

    @Test
    fun noEventsAndPositiveCountRetries() {
        assertTrue(shouldRetryWifiScan(count = 6, eventsReceived = 0))
    }

    @Test
    fun timeoutWithoutEventsRetries() {
        assertTrue(shouldRetryWifiScan(count = null, eventsReceived = 0))
    }

    @Test
    fun emptyScanWithoutEventsRetries() {
        assertTrue(shouldRetryWifiScan(count = 0, eventsReceived = 0))
    }

    @Test
    fun partialVisibleResultsArePreserved() {
        assertTrue(
            shouldKeepPartialScanResults(
                eventsReceived = 4,
                currentResultCount = 4,
            ),
        )
    }

    @Test
    fun emptyVisibleResultsAreNotPreserved() {
        assertFalse(
            shouldKeepPartialScanResults(
                eventsReceived = 4,
                currentResultCount = 0,
            ),
        )
    }

    @Test
    fun completeResultsAreStillKeptIfReportedCountIsInconsistent() {
        assertTrue(
            shouldKeepPartialScanResults(
                eventsReceived = 6,
                currentResultCount = 6,
            ),
        )
    }
}
