package com.swp81x.nrsuite.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class NrThemeMode(
    val label: String,
    val description: String,
) {
    SYSTEM(
        label = "System",
        description = "Follow the Android system light/dark appearance setting.",
    ),
    DARK(
        label = "Obsidian",
        description = "Near-black layered surfaces with a configurable signal accent.",
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

enum class NrAccentColor(
    val label: String,
    val darkColor: Color,
    val lightColor: Color,
) {
    LIME("Lime", ObsidianAccent, DaylightAccent),
    CYAN("Cyan", Color(0xFF22D3EE), Color(0xFF0E7490)),
    BLUE("Blue", Color(0xFF60A5FA), Color(0xFF2563EB)),
    ORANGE("Orange", Color(0xFFFB923C), Color(0xFFC2410C)),
    MAGENTA("Magenta", Color(0xFFE879F9), Color(0xFFA21CAF)),
    TEAL("Teal", Color(0xFF2DD4BF), Color(0xFF0F766E)),
}

fun NrAccentColor.previewColor(isLight: Boolean): Color =
    if (isLight) lightColor else darkColor

private fun withAccent(scheme: ColorScheme, accent: Color): ColorScheme {
    val onAccent = if (accent.luminance() > 0.55f) Color(0xFF0A0D08) else Color.White
    val containerWeight = if (scheme.background.luminance() > 0.5f) 0.16f else 0.22f
    return scheme.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = lerp(scheme.surface, accent, containerWeight),
        onPrimaryContainer = accent,
    )
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
    surfaceContainerLowest = ObsidianBackground,
    surfaceContainerLow = Color(0xFF0D1115),
    surfaceContainer = ObsidianSurface,
    surfaceContainerHigh = ObsidianSurfaceVariant,
    surfaceContainerHighest = ObsidianSurfaceElevated,
    inverseSurface = ObsidianOnSurface,
    inverseOnSurface = ObsidianBackground,
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
    surfaceContainerLowest = BlackBackground,
    surfaceContainerLow = Color(0xFF070809),
    surfaceContainer = BlackSurface,
    surfaceContainerHigh = Color(0xFF111316),
    surfaceContainerHighest = BlackSurfaceVariant,
    inverseSurface = BlackOnSurface,
    inverseOnSurface = BlackBackground,
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
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFBFC),
    surfaceContainer = DaylightSurface,
    surfaceContainerHigh = Color(0xFFF1F4F7),
    surfaceContainerHighest = DaylightSurfaceVariant,
    inverseSurface = DaylightOnSurface,
    inverseOnSurface = Color.White,
    outline = DaylightOutline,
    outlineVariant = Color(0xFFDCE2EA),
    error = LightStatusRed,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF450A0A),
    scrim = Color.Black,
)

private fun paletteFor(mode: NrThemeMode): NrSuiteColors = when (mode) {
    NrThemeMode.SYSTEM,
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
    accentColor: NrAccentColor = NrAccentColor.LIME,
    content: @Composable () -> Unit,
) {
    val resolvedMode = when (mode) {
        NrThemeMode.SYSTEM -> if (isSystemInDarkTheme()) NrThemeMode.DARK else NrThemeMode.LIGHT
        else -> mode
    }
    val baseScheme = when (resolvedMode) {
        NrThemeMode.DARK,
        NrThemeMode.SYSTEM -> ObsidianColorScheme
        NrThemeMode.BLACK -> BlackColorScheme
        NrThemeMode.LIGHT -> DaylightColorScheme
    }
    val isLight = baseScheme.background.luminance() > 0.5f
    val colorScheme = withAccent(
        scheme = baseScheme,
        accent = accentColor.previewColor(isLight),
    )
    val colors = paletteFor(resolvedMode)
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
