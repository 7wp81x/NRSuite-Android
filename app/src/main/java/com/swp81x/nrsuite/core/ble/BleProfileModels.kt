package com.swp81x.nrsuite.core.ble

data class BleCharacteristicProfile(
    val uuid: String,
    val read: Boolean = false,
    val write: Boolean = false,
    val writeNoResponse: Boolean = false,
    val notify: Boolean = false,
    val indicate: Boolean = false,
    val broadcast: Boolean = false,
)

data class BleServiceProfile(
    val uuid: String,
    val characteristics: List<BleCharacteristicProfile> = emptyList(),
)
