package com.swp81x.nrsuite.core.defense

data class HiddenApObservation(
    val bssid: String,
    val channel: Int,
    val rssi: Int,
    val resolvedSsid: String?,
    val resolutionSource: String?,
    val firstSeen: String,
    val lastSeen: String,
    val sightings: Int,
    val vendor: String?,
)

data class HiddenSsidCandidate(
    val ssid: String,
    val client: String?,
    val channel: Int,
    val rssi: Int,
    val firstSeen: String,
    val lastSeen: String,
    val sightings: Int,
)
