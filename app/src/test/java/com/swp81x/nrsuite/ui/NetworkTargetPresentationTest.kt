package com.swp81x.nrsuite.ui

import com.swp81x.nrsuite.ui.util.signalQualityLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkTargetPresentationTest {
    @Test
    fun `signal proximity labels use stable RSSI thresholds`() {
        assertEquals("Near", signalQualityLabel(-40))
        assertEquals("Near", signalQualityLabel(-50))
        assertEquals("Good", signalQualityLabel(-51))
        assertEquals("Good", signalQualityLabel(-65))
        assertEquals("Far", signalQualityLabel(-66))
        assertEquals("Far", signalQualityLabel(-75))
        assertEquals("Weak", signalQualityLabel(-76))
        assertEquals("Weak", signalQualityLabel(-95))
    }
}
