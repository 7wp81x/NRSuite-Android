package com.swp81x.nrsuite.core.wifi

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiChannelsTest {
    @Test
    fun bandSelectionUsesOnlyAllowedChannels() {
        val allowed2g = listOf(1, 6, 11)
        val allowed5g = listOf(36, 40, 44, 48)

        assertEquals(allowed2g, channelsForBand(WifiBand.BAND_2_4, allowed2g, allowed5g))
        assertEquals(allowed5g, channelsForBand(WifiBand.BAND_5, allowed2g, allowed5g))
        assertEquals(
            listOf(1, 6, 11, 36, 40, 44, 48),
            channelsForBand(WifiBand.AUTO, allowed2g, allowed5g),
        )
    }

    @Test
    fun nextChannelStaysInsideAllowedListAndWraps() {
        val allowed = listOf(36, 40, 44, 48)

        assertEquals(40, nextWifiChannel(36, forward = true, allowed))
        assertEquals(48, nextWifiChannel(36, forward = false, allowed))
        assertEquals(36, nextWifiChannel(48, forward = true, allowed))
    }

    @Test
    fun normalizationFallsBackToFirstAllowedChannel() {
        assertEquals(36, normalizeWifiChannel(149, listOf(36, 40, 44, 48), fallback = 36))
        assertEquals(44, normalizeWifiChannel(44, listOf(36, 40, 44, 48), fallback = 36))
    }
}
