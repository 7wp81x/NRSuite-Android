package com.swp81x.nrsuite.core.handshake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PwnWordlistSliceTest {
    @Test
    fun `caps large valid wordlists and reports truncation`() {
        val imported = slicePwnWordlist(
            lines = sequenceOf(
                "password1",
                "password2",
                "password2",
                "password3",
                "password4",
                "short",
                "password5",
            ),
            limit = 3,
        )

        assertEquals(listOf("password1", "password2", "password3"), imported.passwords)
        assertEquals(7, imported.sourceLines)
        assertEquals(1, imported.rejectedLines)
        assertEquals(1, imported.duplicateLines)
        assertTrue(imported.truncated)
    }

    @Test
    fun `rejects invalid lengths and does not report truncation for a small list`() {
        val imported = slicePwnWordlist(
            lines = sequenceOf("", "alpha", "valid-pass-1", "valid-pass-1", "valid-pass-2"),
            limit = 50,
        )

        assertEquals(listOf("valid-pass-1", "valid-pass-2"), imported.passwords)
        assertEquals(1, imported.rejectedLines)
        assertEquals(1, imported.duplicateLines)
        assertFalse(imported.truncated)
    }
}
