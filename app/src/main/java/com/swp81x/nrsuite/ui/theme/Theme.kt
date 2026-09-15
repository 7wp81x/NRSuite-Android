package com.swp81x.nrsuite.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NrAccent,
    onPrimary = NrBackground,
    secondary = NrAccent,
    onSecondary = NrBackground,
    background = NrBackground,
    onBackground = NrOnBackground,
    surface = NrSurface,
    onSurface = NrOnSurface,
    surfaceVariant = NrSurfaceVariant,
    onSurfaceVariant = NrOnSurfaceVariant,
    outline = NrOutline,
    error = StatusRed,
)

@Composable
fun NRSuiteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content,
    )
}
