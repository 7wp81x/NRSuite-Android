package com.swp81x.nrsuite.core.wifi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeauthMultiPlannerTest {

    @Test
    fun `uses native multi only for advertised firmware and multiple targets`() {
        val features = setOf("wifi", "deauth", "deauth_multi")
        assertTrue(shouldUseNativeDeauthMulti(features, 2))
        assertTrue(shouldUseNativeDeauthMulti(features, 32))
        assertFalse(shouldUseNativeDeauthMulti(features, 1))
        assertFalse(shouldUseNativeDeauthMulti(features, 33))
        assertFalse(shouldUseNativeDeauthMulti(setOf("wifi", "deauth"), 2))
    }

    @Test
    fun `burst timeout scales with requested frame schedule`() {
        assertEquals(34_000L, deauthMultiCommandTimeoutMs(2, 20, 100, loopMode = false))
        assertEquals(
            45_000L,
            deauthMultiCommandTimeoutMs(50, 100, 3, loopMode = false),
        )
        assertEquals(600_000L, deauthMultiCommandTimeoutMs(32, 2_000, 10, loopMode = false))
    }

    @Test
    fun `loop start uses a short command timeout`() {
        assertEquals(30_000L, deauthMultiCommandTimeoutMs(32, 2_000, 10, loopMode = true))
    }
}
