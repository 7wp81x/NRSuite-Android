package com.swp81x.nrsuite.core.defense

enum class ClientPresenceMode { PASSIVE, ACTIVE }

data class ClientObservation(
    val mac: String,
    val bssid: String?,
    val ssid: String?,
    val subtype: String,
    val channel: Int,
    val rssi: Int,
    val firstSeen: String,
    val lastSeen: String,
    val sightings: Int,
    val vendor: String?,
)
