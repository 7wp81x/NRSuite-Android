package com.swp81x.nrsuite

import org.junit.Assert.assertEquals
import org.junit.Test

class CrackerDocumentNameTest {
    @Test
    fun `prefers queried display name over opaque document id`() {
        assertEquals(
            "rockyou.txt",
            selectDocumentDisplayName(
                suppliedName = null,
                queriedName = "rockyou.txt",
                uriLastPathSegment = "msf:1000",
                fallback = "wordlist.txt",
            ),
        )
    }

    @Test
    fun `rejects opaque provider ids from every candidate`() {
        assertEquals(
            "wordlist.txt",
            selectDocumentDisplayName(
                suppliedName = "msf:1000",
                queriedName = "primary:Download",
                uriLastPathSegment = "raw:/storage/emulated/0",
                fallback = "wordlist.txt",
            ),
        )
    }

    @Test
    fun `uses last path segment when it is a normal filename`() {
        assertEquals(
            "custom-wordlist.txt",
            selectDocumentDisplayName(
                suppliedName = null,
                queriedName = null,
                uriLastPathSegment = "Download/custom-wordlist.txt",
                fallback = "wordlist.txt",
            ),
        )
    }
}
