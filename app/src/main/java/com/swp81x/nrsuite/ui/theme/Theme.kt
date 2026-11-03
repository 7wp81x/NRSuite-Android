package com.swp81x.nrsuite.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class NrThemeMode(
    val label: String,
    val description: String,
) {
    DARK(
        label = "Obsidian",
        description = "Near-black layered surfaces with a lime signal accent.",
    ),
    BLACK(
        label = "Black",
        description = "True-black OLED surfaces for maximum contrast and battery savings.",
    ),
    LIGHT(
        label = "Daylight",
        description = "High-contrast light surfaces for bright environments and outdoor use.",
    ),
}

private val NrShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val ObsidianColorScheme = darkColorScheme(
    primary = ObsidianAccent,
    onPrimary = ObsidianOnAccent,
    primaryContainer = Color(0xFF263A12),
    onPrimaryContainer = Color(0xFFD8FF9C),
    secondary = Color(0xFF60A5FA),
    onSecondary = Color(0xFF06121F),
    secondaryContainer = Color(0xFF172A44),
    onSecondaryContainer = Color(0xFFCFE6FF),
    tertiary = Color(0xFF22D3EE),
    onTertiary = Color(0xFF00181D),
    background = ObsidianBackground,
    onBackground = ObsidianOnSurface,
    surface = ObsidianSurface,
    onSurface = ObsidianOnSurface,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = ObsidianOnSurfaceVariant,
    outline = ObsidianOutline,
    outlineVariant = Color(0xFF20262D),
    error = DarkStatusRed,
    onError = Color(0xFF240404),
    errorContainer = Color(0xFF3A1111),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black,
)

private val BlackColorScheme = darkColorScheme(
    primary = ObsidianAccent,
    onPrimary = ObsidianOnAccent,
    primaryContainer = Color(0xFF1B2A0D),
    onPrimaryContainer = Color(0xFFD8FF9C),
    secondary = Color(0xFF60A5FA),
    onSecondary = Color(0xFF06121F),
    secondaryContainer = Color(0xFF122238),
    onSecondaryContainer = Color(0xFFCFE6FF),
    tertiary = Color(0xFF22D3EE),
    onTertiary = Color(0xFF00181D),
    background = BlackBackground,
    onBackground = BlackOnSurface,
    surface = BlackSurface,
    onSurface = BlackOnSurface,
    surfaceVariant = BlackSurfaceVariant,
    onSurfaceVariant = BlackOnSurfaceVariant,
    outline = BlackOutline,
    outlineVariant = Color(0xFF1C2025),
    error = DarkStatusRed,
    onError = Color(0xFF240404),
    errorContainer = Color(0xFF3A1111),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black,
)

private val DaylightColorScheme = lightColorScheme(
    primary = DaylightAccent,
    onPrimary = DaylightOnAccent,
    primaryContainer = Color(0xFFDDF5B8),
    onPrimaryContainer = Color(0xFF172600),
    secondary = Color(0xFF2563EB),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE8FF),
    onSecondaryContainer = Color(0xFF001A41),
    tertiary = Color(0xFF0E7490),
    onTertiary = Color.White,
    background = DaylightBackground,
    onBackground = DaylightOnSurface,
    surface = DaylightSurface,
    onSurface = DaylightOnSurface,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = DaylightOnSurfaceVariant,
    outline = DaylightOutline,
    outlineVariant = Color(0xFFDCE2EA),
    error = LightStatusRed,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF450A0A),
    scrim = Color.Black,
)

private fun paletteFor(mode: NrThemeMode): NrSuiteColors = when (mode) {
    NrThemeMode.DARK -> NrSuiteColors(
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
    NrThemeMode.BLACK -> NrSuiteColors(
        isLight = false,
        surfaceElevated = BlackSurfaceElevated,
        statusGreen = DarkStatusGreen,
        statusAmber = DarkStatusAmber,
        statusRed = DarkStatusRed,
        statusNeutral = DarkStatusNeutral,
        statusInfo = DarkStatusInfo,
        logInfo = Color(0xFF8B949E),
        logUsb = Color(0xFF60A5FA),
        logSuccess = DarkStatusGreen,
        logError = DarkStatusRed,
        logBgError = Color(0xFF1E0707),
    )
    NrThemeMode.LIGHT -> NrSuiteColors(
        isLight = true,
        surfaceElevated = DaylightSurfaceElevated,
        statusGreen = LightStatusGreen,
        statusAmber = LightStatusAmber,
        statusRed = LightStatusRed,
        statusNeutral = LightStatusNeutral,
        statusInfo = LightStatusInfo,
        logInfo = Color(0xFF4B5563),
        logUsb = Color(0xFF2563EB),
        logSuccess = LightStatusGreen,
        logError = LightStatusRed,
        logBgError = Color(0xFFFEE2E2),
    )
}

@Composable
fun NRSuiteTheme(
    mode: NrThemeMode = NrThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (mode) {
        NrThemeMode.DARK -> ObsidianColorScheme
        NrThemeMode.BLACK -> BlackColorScheme
        NrThemeMode.LIGHT -> DaylightColorScheme
    }
    val colors = paletteFor(mode)
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = colors.isLight
                isAppearanceLightNavigationBars = colors.isLight
            }
        }
    }

    CompositionLocalProvider(LocalNrSuiteColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = NrShapes,
            content = content,
        )
    }
}
