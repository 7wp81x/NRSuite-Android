package com.swp81x.nrsuite.core.wps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WpsPinGeneratorTest {
    @Test
    fun returnsNoSuggestionForUnknownOui() {
        assertNull(WpsPinGenerator.getLikely("B0:4E:26:CC:2E:F8"))
    }

    @Test
    fun matchesOneShotExtendedKnownVectors() {
        assertEquals("11930464", WpsPinGenerator.getLikely("14:D6:4D:12:34:56"))
        assertEquals("11891963", WpsPinGenerator.getLikely("04:BF:6D:AA:BB:CC"))
        assertEquals("95508993", WpsPinGenerator.getLikely("D8:FE:E3:11:22:33"))
    }

    @Test
    fun exposesAllSuggestedAlgorithmsInReferenceOrder() {
        val suggestions = WpsPinGenerator.getSuggestedPins("14:D6:4D:12:34:56")
        assertEquals(
            listOf("pin24", "pinDLink", "pinDLink1", "pinBrcm2", "pinBrcm3", "pinBrcm4", "pinBrcm5", "pinBrcm6"),
            suggestions.map { it.algorithmId },
        )
        assertEquals("76465154", suggestions.first { it.algorithmId == "pinDLink" }.pin)
        assertEquals("46264848", suggestions.first { it.algorithmId == "pinBrcm2" }.pin)
    }

    @Test
    fun emptyPinAlgorithmProducesEmptyPin() {
        assertEquals("", WpsPinGenerator.generatePin("pinEmpty", "14:D6:4D:12:34:56"))
    }

    @Test
    fun checksumMatchesKnownWpsPin() {
        assertEquals(8, WpsPinGenerator.checksum(4_592_200))
    }
}
