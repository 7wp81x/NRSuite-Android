package com.swp81x.nrsuite.core.sniff

data class SniffRequest(
    val fixedMode: Boolean,
    val channel: Int,
    val intervalMs: Int,
    val deauthBeforeCapture: Boolean = false,
    val targetBssid: String = "",
    val client: String = "FF:FF:FF:FF:FF:FF",
    val deauthCount: Int = 0,
    val deauthIntervalMs: Int = 80,
    val eapolOnly: Boolean = false,
    val targetNetworkOnly: Boolean = false,
)
