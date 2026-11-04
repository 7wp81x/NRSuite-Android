package com.swp81x.nrsuite.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

@Composable
@ReadOnlyComposable
fun categoryColor(category: String): Color =
    categoryColor(category, NrTheme.colors.isLight)

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
    val accent = category?.let { categoryColor(it) }
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
