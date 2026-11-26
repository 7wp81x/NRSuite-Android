package com.swp81x.nrsuite.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.swp81x.nrsuite.core.defense.isLikelyInfrastructureVendor
import com.swp81x.nrsuite.ui.theme.CategoryDetectionBlue
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed

/**
 * Scan-context signal quality. Stronger is better here, so this is not the
 * same color scale as threat proximity for deauth/rogue AP alerts.
 */
@Composable
fun signalQualityColor(rssi: Int): Color = when {
    rssi >= -50 -> NrAccent
    rssi >= -65 -> CategoryDetectionBlue
    rssi >= -75 -> StatusAmber
    else -> StatusNeutral
}

fun signalQualityLabel(rssi: Int): String = when {
    rssi >= -50 -> "Near"
    rssi >= -65 -> "Good"
    rssi >= -75 -> "Far"
    else -> "Weak"
}

/**
 * Threat proximity used by deauth/rogue AP detection: closer means more urgent.
 */
@Composable
fun threatProximityColor(rssi: Int): Color = when {
    rssi >= -50 -> StatusRed
    rssi >= -65 -> StatusAmber
    else -> StatusNeutral
}

@Composable
fun securityColor(security: String): Color {
    val normalized = security.uppercase()
    return when {
        "OPEN" in normalized -> StatusRed
        "WEP" in normalized -> StatusRed
        "WPS" in normalized -> StatusAmber
        "WPA3" in normalized -> NrAccent
        "WPA2" in normalized -> NrAccent
        "WPA" in normalized -> StatusAmber
        else -> StatusNeutral
    }
}

@Composable
fun ouiStatusColor(
    vendor: String?,
    whitelisted: Boolean,
    blacklisted: Boolean,
): Color = when {
    blacklisted -> StatusRed
    whitelisted -> NrAccent
    isLikelyInfrastructureVendor(vendor) -> CategoryDetectionBlue
    !vendor.isNullOrBlank() -> StatusNeutral
    else -> StatusNeutral
}
