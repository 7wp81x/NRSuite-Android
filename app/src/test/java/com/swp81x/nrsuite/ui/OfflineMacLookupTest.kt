package com.swp81x.nrsuite.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineMacLookupTest {
    @Test
    fun acceptsColonHyphenAndBareHexFormats() {
        assertEquals("AA:BB:CC:DD:EE:FF", normalizeMacQuery("aa:bb:cc:dd:ee:ff"))
        assertEquals("AA:BB:CC:DD:EE:FF", normalizeMacQuery("AA-BB-CC-DD-EE-FF"))
        assertEquals("AA:BB:CC:DD:EE:FF", normalizeMacQuery("AABB.CCDD.EEFF"))
        assertEquals("AA:BB:CC:DD:EE:FF", normalizeMacQuery("AABBCCDDEEFF"))
    }

    @Test
    fun rejectsInvalidLengthsAndCharacters() {
        assertNull(normalizeMacQuery("AA:BB:CC:DD:EE"))
        assertNull(normalizeMacQuery("AA:BB:CC:DD:EE:GG"))
        assertNull(normalizeMacQuery(""))
    }
}
