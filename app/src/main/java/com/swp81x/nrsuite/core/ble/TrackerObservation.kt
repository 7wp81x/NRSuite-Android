package com.swp81x.nrsuite.core.ble

data class TrackerObservation(
    val address: String,
    val name: String?,
    val rssi: Int,
    val manufacturerData: String?,
    val firstSeen: String,
    val lastSeen: String,
    val sightings: Int,
    val trackerType: String? = null,
)
