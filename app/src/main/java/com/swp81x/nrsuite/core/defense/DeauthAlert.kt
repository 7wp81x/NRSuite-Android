package com.swp81x.nrsuite.core.defense

/**
 * UI state for a currently-active deauth attack alert.
 */
data class DeauthAlert(
    val sourceMac: String,
    val ssid: String,
    val channel: Int,
    val possiblySpoofed: Boolean,
    val confidence: AlertConfidence,
    val sustainedSeconds: Int,
    val targetConcentrationPct: Int,
    val reasonCodeConsistencyPct: Int,
    val dominantSourceRssi: Int,
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
    val origin: String = "local",
    val nodeId: String? = null,
    val chip: String? = null,
    val seq: Long? = null,
    val channel: Int? = null,
    val stale: Boolean = false,
)

enum class DeauthFeedFilter { ALL, BROADCAST, TARGETED }

/** Distributed detector channel strategy sent to the mesh master. */
enum class DeauthDistributedMode { SAME_CHANNEL, FIXED, HOP }

enum class DeauthSourceFilter { ALL, LOCAL, MESH }

/**
 * Detector scan mode: one selected target/channel vs. hopping across channels.
 */
enum class DeauthChannelMode { TARGETED, HOPPING }

/**
 * Heuristic confidence that the detector is seeing a real deauth burst rather
 * than a single stray frame. Scored upstream; rendered by the UI.
 */
enum class AlertConfidence { LOW, MEDIUM, HIGH }
