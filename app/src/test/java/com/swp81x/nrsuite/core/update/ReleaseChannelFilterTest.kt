package com.swp81x.nrsuite.core.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseChannelFilterTest {
    private fun release(tag: String) = ReleaseInfo(
        tag = tag,
        name = null,
        htmlUrl = "https://example.invalid",
        downloadUrl = null,
        apkUrl = null,
        publishedAt = null,
    )

    @Test
    fun `alpha and beta releases are filtered but RC and stable are retained`() {
        assertTrue(release("v1.0.0-alpha.1").isAlphaOrBetaRelease())
        assertTrue(release("v1.0.0-beta.3.2.0").isAlphaOrBetaRelease())
        assertFalse(release("v1.0.0-rc.1").isAlphaOrBetaRelease())
        assertFalse(release("v1.0.0").isAlphaOrBetaRelease())
    }
}
