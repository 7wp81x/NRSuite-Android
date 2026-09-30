package com.swp81x.nrsuite.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

fun categoryColor(category: String): Color = when (category) {
    "Detection" -> CategoryDetectionBlue
    "Wireless" -> NrAccent
    "WiFi Recon" -> CategoryDetectionBlue
    "WiFi Attacks" -> CategoryAttackOrange
    "WiFi Defense" -> CategoryFirmwareGreen
    "Tools" -> StatusNeutral
    "Credentials" -> StatusAmber
    "HID" -> CategoryHidPurple
    "Storage" -> CategoryStorageTeal
    "Firmware" -> CategoryFirmwareGreen
    "BLE" -> CategoryBleBlue
    else -> StatusNeutral
}

/**
 * Applies the category's identity color as the Material primary/secondary color
 * for the subtree. Screens and shared components can then use
 * MaterialTheme.colorScheme.primary without hardcoding their category.
 */
@Composable
fun CategoryAccentTheme(
    category: String?,
    content: @Composable () -> Unit,
) {
    val accent = category?.let(::categoryColor)
    if (accent == null) {
        content()
        return
    }

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = accent,
            secondary = accent,
        ),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}
