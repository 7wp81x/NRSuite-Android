package com.swp81x.nrsuite.core.portal

/** Per-module portal metrics so Captive Portal and Evil Twin never share counters. */
data class PortalSessionMetrics(
    val ssid: String = "",
    val channel: Int = 0,
    val pageViews: Int = 0,
    val clientsAssociated: Int = 0,
    val capturedPosts: Int = 0,
) {
    val hasActivity: Boolean
        get() = ssid.isNotBlank() ||
            pageViews > 0 ||
            clientsAssociated > 0 ||
            capturedPosts > 0
}
