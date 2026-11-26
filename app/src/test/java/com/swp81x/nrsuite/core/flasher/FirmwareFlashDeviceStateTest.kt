package com.swp81x.nrsuite.core.flasher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirmwareFlashDeviceStateTest {
    @Test
    fun `matches by device id or stable fingerprint`() {
        val state = FirmwareFlashDeviceState(
            deviceId = 12,
            fingerprint = "serial|vid|pid",
            phase = FirmwareFlashDevicePhase.FLASHING,
        )

        assertTrue(state.matches(deviceId = 12, fingerprint = "different"))
        assertTrue(state.matches(deviceId = 99, fingerprint = "serial|vid|pid"))
        assertFalse(state.matches(deviceId = 99, fingerprint = "different"))
    }
}
