package com.swp81x.nrsuite.core.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleHidSessionPolicyTest {
    @Test
    fun `BLE HID remains active while connected even if advertising stopped`() {
        assertFalse(isBleHidSessionActive(advertising = false, connected = false))
        assertTrue(isBleHidSessionActive(advertising = true, connected = false))
        assertTrue(isBleHidSessionActive(advertising = false, connected = true))
        assertTrue(isBleHidSessionActive(advertising = true, connected = true))
    }
}
