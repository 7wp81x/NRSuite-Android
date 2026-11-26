package com.swp81x.nrsuite.core.portal

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptiveCredentialParserTest {
    @Test
    fun `extracts common password aliases case-insensitively`() {
        assertEquals("secret1", extractCaptivePassword(JSONObject().put("password", "secret1")))
        assertEquals("secret2", extractCaptivePassword(JSONObject().put("Passwd", "secret2")))
        assertEquals("secret3", extractCaptivePassword(JSONObject().put("pwd", "secret3")))
        assertEquals("secret4", extractCaptivePassword(JSONObject().put("WPA_KEY", "secret4")))
        assertEquals(
            "secret5",
            extractCaptivePassword(JSONObject().put("wifi_password", "secret5")),
        )
    }

    @Test
    fun `prefers password over later generic aliases`() {
        val data = JSONObject()
            .put("key", "csrf-token")
            .put("password", "real-passphrase")
        assertEquals("real-passphrase", extractCaptivePassword(data))
    }

    @Test
    fun `ignores blank and missing password fields`() {
        assertEquals("", extractCaptivePassword(null))
        assertEquals("", extractCaptivePassword(JSONObject().put("password", "")))
        assertEquals("", extractCaptivePassword(JSONObject().put("username", "user")))
    }
}
