package com.swp81x.nrsuite.core.portal

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class PortalHtmlRendererTest {
    @Test
    fun `replaces ssid token with html escaped ssid`() {
        val input = "<h1>Welcome to #SSID#</h1>".toByteArray()

        val rendered = PortalHtmlRenderer.render(input, "Lab <5G> & Test").toString(Charsets.UTF_8)

        assertEquals(
            "<h1>Welcome to Lab &lt;5G&gt; &amp; Test</h1>",
            rendered,
        )
    }

    @Test
    fun `leaves templates without the token unchanged`() {
        val input = "<h1>Static page</h1>".toByteArray()

        val rendered = PortalHtmlRenderer.render(input, "Any SSID")

        assertEquals("<h1>Static page</h1>", rendered.toString(Charsets.UTF_8))
    }

    @Test
    fun `streaming renderer handles a token split across read buffers`() {
        val prefix = "x".repeat(4093)
        val input = (prefix + "#SSID#" + "-tail").byteInputStream()
        val output = ByteArrayOutputStream()

        PortalHtmlRenderer.render(input, output, "SplitSSID")

        assertEquals(prefix + "SplitSSID-tail", output.toString(Charsets.UTF_8.name()))
    }
}
