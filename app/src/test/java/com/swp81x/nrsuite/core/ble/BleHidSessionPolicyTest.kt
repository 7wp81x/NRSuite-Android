package com.swp81x.nrsuite.core.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleHidSessionPolicyTest {
    @Test
    fun `BLE HID remains active while connected even if advertising stopped`() {
        assertFalse(isBleHidSessionActive(sessionActive = false, advertising = false, connected = false))
        assertTrue(isBleHidSessionActive(sessionActive = true, advertising = false, connected = false))
        assertTrue(isBleHidSessionActive(sessionActive = false, advertising = true, connected = false))
        assertTrue(isBleHidSessionActive(sessionActive = false, advertising = false, connected = true))
        assertTrue(isBleHidSessionActive(sessionActive = true, advertising = true, connected = true))
    }
}
