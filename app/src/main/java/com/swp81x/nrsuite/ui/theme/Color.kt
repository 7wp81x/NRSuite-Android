package com.swp81x.nrsuite.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// Base colors for the three switchable NRSuite palettes.
internal val ObsidianBackground = Color(0xFF08090B)
internal val ObsidianSurface = Color(0xFF111418)
internal val ObsidianSurfaceVariant = Color(0xFF171C22)
internal val ObsidianSurfaceElevated = Color(0xFF1C222A)
internal val ObsidianOutline = Color(0xFF2A3038)
internal val ObsidianOnSurface = Color(0xFFF2F5F7)
internal val ObsidianOnSurfaceVariant = Color(0xFF98A2AE)
internal val ObsidianAccent = Color(0xFFB6F34B)
internal val ObsidianOnAccent = Color(0xFF0A0D08)

internal val BlackBackground = Color(0xFF000000)
internal val BlackSurface = Color(0xFF0B0C0E)
internal val BlackSurfaceVariant = Color(0xFF141619)
internal val BlackSurfaceElevated = Color(0xFF111316)
internal val BlackOutline = Color(0xFF272B31)
internal val BlackOnSurface = Color(0xFFF4F6F8)
internal val BlackOnSurfaceVariant = Color(0xFF9AA4B0)

internal val DaylightBackground = Color(0xFFF3F5F8)
internal val DaylightSurface = Color(0xFFFFFFFF)
internal val DaylightSurfaceVariant = Color(0xFFE9EDF2)
internal val DaylightSurfaceElevated = Color(0xFFFFFFFF)
internal val DaylightOutline = Color(0xFFC9D1DB)
internal val DaylightOnSurface = Color(0xFF111827)
internal val DaylightOnSurfaceVariant = Color(0xFF4B5563)
internal val DaylightAccent = Color(0xFF4D7C0F)
internal val DaylightOnAccent = Color(0xFFFFFFFF)

internal val DarkStatusGreen = Color(0xFF4ADE80)
internal val DarkStatusAmber = Color(0xFFFBBF24)
internal val DarkStatusRed = Color(0xFFF87171)
internal val DarkStatusNeutral = Color(0xFF94A3B8)
internal val DarkStatusInfo = Color(0xFF60A5FA)

internal val LightStatusGreen = Color(0xFF15803D)
internal val LightStatusAmber = Color(0xFFB45309)
internal val LightStatusRed = Color(0xFFDC2626)
internal val LightStatusNeutral = Color(0xFF64748B)
internal val LightStatusInfo = Color(0xFF2563EB)

@Immutable
data class NrSuiteColors(
    val isLight: Boolean,
    val surfaceElevated: Color,
    val statusGreen: Color,
    val statusAmber: Color,
    val statusRed: Color,
    val statusNeutral: Color,
    val statusInfo: Color,
    val logInfo: Color,
    val logUsb: Color,
    val logSuccess: Color,
    val logError: Color,
    val logBgError: Color,
)

internal val LocalNrSuiteColors = compositionLocalOf {
    NrSuiteColors(
        isLight = false,
        surfaceElevated = ObsidianSurfaceElevated,
        statusGreen = DarkStatusGreen,
        statusAmber = DarkStatusAmber,
        statusRed = DarkStatusRed,
        statusNeutral = DarkStatusNeutral,
        statusInfo = DarkStatusInfo,
        logInfo = Color(0xFF8B949E),
        logUsb = Color(0xFF60A5FA),
        logSuccess = DarkStatusGreen,
        logError = DarkStatusRed,
        logBgError = Color(0xFF2A0A0A),
    )
}

object NrTheme {
    val colors: NrSuiteColors
        @Composable
        @ReadOnlyComposable
        get() = LocalNrSuiteColors.current
}

// Theme-aware aliases kept for compatibility with the existing screens.
val NrBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.background

val NrSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surface

val NrSurfaceVariant: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surfaceVariant

val NrSurfaceElevated: Color
    @Composable
    @ReadOnlyComposable
    get() = NrTheme.colors.surfaceElevated

val NrAccent: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.primary

val NrOnBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onBackground

val NrOnSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onSurface

val NrOnSurfaceVariant: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onSurfaceVariant

val NrOutline: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.outline

val StatusGreen: Color
    @Composable
    @ReadOnlyComposable
    get() = NrTheme.colors.statusGreen

val StatusAmber: Color
    @Composable
    @ReadOnlyComposable
    get() = NrTheme.colors.statusAmber

val StatusRed: Color
    @Composable
    @ReadOnlyComposable
    get() = NrTheme.colors.statusRed

val StatusNeutral: Color
    @Composable
    @ReadOnlyComposable
    get() = NrTheme.colors.statusNeutral

val StatusInfo: Color
    @Composable
    @ReadOnlyComposable
    get() = NrTheme.colors.statusInfo

val LogColorInfo: Color
    @Composable
    @ReadOnlyComposable
    get() = NrTheme.colors.logInfo

val LogColorUsb: Color
    @Composable
    @ReadOnlyComposable
    get() = NrTheme.colors.logUsb

val LogColorSuccess: Color
    @Composable
    @ReadOnlyComposable
    get() = NrTheme.colors.logSuccess

val LogColorError: Color
    @Composable
    @ReadOnlyComposable
    get() = NrTheme.colors.logError

val LogBgError: Color
    @Composable
    @ReadOnlyComposable
    get() = NrTheme.colors.logBgError

val CategoryDetectionBlue: Color
    @Composable
    @ReadOnlyComposable
    get() = categoryColor("Detection")

val CategoryAttackOrange: Color
    @Composable
    @ReadOnlyComposable
    get() = categoryColor("WiFi Attacks")

val CategoryHidPurple: Color
    @Composable
    @ReadOnlyComposable
    get() = categoryColor("HID")

val CategoryStorageTeal: Color
    @Composable
    @ReadOnlyComposable
    get() = categoryColor("Storage")

val CategoryFirmwareGreen: Color
    @Composable
    @ReadOnlyComposable
    get() = categoryColor("Firmware")

val CategoryBleBlue: Color
    @Composable
    @ReadOnlyComposable
    get() = categoryColor("BLE")

val CategoryMeshIndigo: Color
    @Composable
    @ReadOnlyComposable
    get() = categoryColor("Mesh")

val CategorySensorsBlue: Color
    @Composable
    @ReadOnlyComposable
    get() = categoryColor("Sensors")

val CategoryWifiViolet: Color
    @Composable
    @ReadOnlyComposable
    get() = categoryColor("WiFi")

val CategoryIrRose: Color
    @Composable
    @ReadOnlyComposable
    get() = categoryColor("IR")

// Static category accents for components that resolve categories outside a
// composition (for example a remember block). The composable overload applies
// the light-theme variants automatically.
internal fun categoryColor(category: String, isLight: Boolean): Color = if (isLight) {
    when (category) {
        "Detection" -> Color(0xFF2563EB)
        "Wireless" -> Color(0xFF4D7C0F)
        "WiFi" -> Color(0xFF6D28D9)
        "WiFi Recon" -> Color(0xFF2563EB)
        "WiFi Attacks" -> Color(0xFFC2410C)
        "WiFi Defense" -> Color(0xFF15803D)
        "Tools" -> Color(0xFF64748B)
        "Credentials" -> Color(0xFFB45309)
        "HID" -> Color(0xFF7C3AED)
        "Storage" -> Color(0xFF0F766E)
        "Firmware" -> Color(0xFF15803D)
        "BLE" -> Color(0xFF0891B2)
        "Sensors" -> Color(0xFF0284C7)
        "Mesh" -> Color(0xFF4F46E5)
        "IR" -> Color(0xFFBE123C)
        else -> Color(0xFF64748B)
    }
} else {
    when (category) {
        "Detection" -> Color(0xFF60A5FA)
        "Wireless" -> ObsidianAccent
        "WiFi" -> Color(0xFF8B5CF6)
        "WiFi Recon" -> Color(0xFF60A5FA)
        "WiFi Attacks" -> Color(0xFFFB923C)
        "WiFi Defense" -> Color(0xFF22C55E)
        "Tools" -> Color(0xFF8B949E)
        "Credentials" -> Color(0xFFF59E0B)
        "HID" -> Color(0xFFA78BFA)
        "Storage" -> Color(0xFF14B8A6)
        "Firmware" -> Color(0xFF22C55E)
        "BLE" -> Color(0xFF22D3EE)
        "Sensors" -> Color(0xFF38BDF8)
        "Mesh" -> Color(0xFF7C6FF0)
        "IR" -> Color(0xFFFB7185)
        else -> Color(0xFF8B949E)
    }
}
