package com.swp81x.nrsuite.core.ble

data class BleDeviceObservation(
    val address: String,
    val addressType: Int,
    val name: String?,
    val rssi: Int,
    val connectable: Boolean,
    val txPower: Int?,
    val appearance: Int?,
    val manufacturerData: String?,
    val services: List<String>,
    val firstSeen: String,
    val lastSeen: String,
    val sightings: Int,
)
