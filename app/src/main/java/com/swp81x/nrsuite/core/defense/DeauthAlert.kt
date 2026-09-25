package com.swp81x.nrsuite.core.defense

/**
 * UI state for a currently-active deauth attack alert.
 */
data class DeauthAlert(
    val sourceMac: String,
    val ssid: String,
    val channel: Int,
    val espDeviceLabel: String,
    val possiblySpoofed: Boolean,
)

/**
 * One deauth/disassoc frame displayed in the detector feed.
 */
data class DeauthFeedEntry(
    val timestamp: String,
    val sourceMac: String,
    val targetMac: String?,   // null = broadcast
    val reasonCode: Int,
    val rssi: Int,
)

enum class DeauthFeedFilter { ALL, BROADCAST, TARGETED }

/**
 * Detector scan mode: one selected target/channel vs. hopping across channels.
 */
enum class DeauthChannelMode { TARGETED, HOPPING }
