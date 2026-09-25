package com.swp81x.nrsuite.ui.util

enum class ProximityTier(val label: String) {
    VERY_CLOSE("Very close"),
    NEARBY("Nearby"),
    FAR("Far"),
    VERY_FAR("Very far"),
}

fun rssiToProximity(rssi: Int): ProximityTier = when {
    rssi >= -50 -> ProximityTier.VERY_CLOSE
    rssi >= -65 -> ProximityTier.NEARBY
    rssi >= -80 -> ProximityTier.FAR
    else -> ProximityTier.VERY_FAR
}
