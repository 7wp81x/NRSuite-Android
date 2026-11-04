package com.swp81x.nrsuite.core.wifi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiScanResultPolicyTest {
    @Test
    fun partialResultsAreNotRetriedAfterTheyBecomeVisible() {
        assertFalse(shouldRetryWifiScan(eventsReceived = 4))
    }

    @Test
    fun noEventsAndPositiveCountRetries() {
        assertTrue(shouldRetryWifiScan(eventsReceived = 0))
    }

    @Test
    fun timeoutWithoutEventsRetries() {
        assertTrue(shouldRetryWifiScan(eventsReceived = 0))
    }

    @Test
    fun emptyScanWithoutEventsRetries() {
        assertTrue(shouldRetryWifiScan(eventsReceived = 0))
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
