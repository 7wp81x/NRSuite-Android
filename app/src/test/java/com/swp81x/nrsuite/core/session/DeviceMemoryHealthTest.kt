package com.swp81x.nrsuite.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceMemoryHealthTest {
    @Test
    fun normalHeapDoesNotShowBanner() {
        val health = DeviceMemoryHealth.evaluate(
            freeHeap = 100 * 1024L,
            largestBlock = 64 * 1024L,
        )

        assertEquals(DeviceMemoryPressure.NORMAL, health.pressure)
        assertFalse(health.shouldShowBanner)
    }

    @Test
    fun lowFreeHeapShowsWarning() {
        val health = DeviceMemoryHealth.evaluate(
            freeHeap = 47 * 1024L,
            largestBlock = 48 * 1024L,
        )

        assertEquals(DeviceMemoryPressure.WARNING, health.pressure)
        assertTrue(health.shouldShowBanner)
    }

    @Test
    fun lowLargestBlockShowsWarning() {
        val health = DeviceMemoryHealth.evaluate(
            freeHeap = 80 * 1024L,
            largestBlock = 27 * 1024L,
        )

        assertEquals(DeviceMemoryPressure.WARNING, health.pressure)
    }

    @Test
    fun criticallyLowFreeHeapTakesPrecedence() {
        val health = DeviceMemoryHealth.evaluate(
            freeHeap = 19 * 1024L,
            largestBlock = 64 * 1024L,
        )

        assertEquals(DeviceMemoryPressure.CRITICAL, health.pressure)
    }

    @Test
    fun criticallyLowLargestBlockTakesPrecedence() {
        val health = DeviceMemoryHealth.evaluate(
            freeHeap = 80 * 1024L,
            largestBlock = 11 * 1024L,
        )

        assertEquals(DeviceMemoryPressure.CRITICAL, health.pressure)
    }

    @Test
    fun exactThresholdsRemainNormal() {
        val health = DeviceMemoryHealth.evaluate(
            freeHeap = DeviceMemoryHealth.WARNING_FREE_HEAP_BYTES,
            largestBlock = DeviceMemoryHealth.WARNING_LARGEST_BLOCK_BYTES,
        )

        assertEquals(DeviceMemoryPressure.NORMAL, health.pressure)
    }

    @Test
    fun missingTelemetryDoesNotWarn() {
        val health = DeviceMemoryHealth.evaluate(freeHeap = null, largestBlock = null)

        assertEquals(DeviceMemoryPressure.NORMAL, health.pressure)
    }

    @Test
    fun parsesBleHidHeapFailureValues() {
        val failure = parseBleHidHeapFailure(
            "insufficient heap for BLE HID (free=70344 largest=628)",
        )

        assertEquals(70344L, failure?.freeHeap)
        assertEquals(628L, failure?.largestBlock)
    }

    @Test
    fun ignoresUnrelatedFirmwareErrors() {
        assertNull(parseBleHidHeapFailure("stop BLE scan before starting BLE HID"))
        assertNull(parseBleHidHeapFailure(null))
    }
}
