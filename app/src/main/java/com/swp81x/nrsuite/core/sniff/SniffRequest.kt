package com.swp81x.nrsuite.core.sniff

data class SniffRequest(
    val fixedMode: Boolean,
    val channel: Int,
    val intervalMs: Int,
    val deauthBeforeCapture: Boolean = false,
    val targetBssid: String = "",
    val client: String = "FF:FF:FF:FF:FF:FF",
    val deauthCount: Int = 8,
    val deauthIntervalMs: Int = 50,
    val deauthRetryDuringCapture: Boolean = true,
    val deauthRetryIntervalMs: Int = 5_000,
    val eapolOnly: Boolean = false,
    val targetNetworkOnly: Boolean = false,
    val band: String = "auto",
)
