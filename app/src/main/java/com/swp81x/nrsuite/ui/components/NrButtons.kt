package com.swp81x.nrsuite.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.ui.theme.NrTheme

/**
 * Semantic action components for NRSuite.
 *
 * Screens should choose an intent from this file rather than constructing one-off
 * Material button colors and sizes. CategoryAccentTheme maps MaterialTheme.primary
 * to the current module/category accent, so primary actions automatically inherit
 * the active category color.
 */

private val RegularButtonHeight = 40.dp
private val CompactButtonHeight = 32.dp
private val CompactButtonMinWidth = 96.dp

private val CompactContentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
private val RegularContentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)

private fun actionHeight(compact: Boolean) = if (compact) CompactButtonHeight else RegularButtonHeight

private fun contentPadding(compact: Boolean) =
    if (compact) CompactContentPadding else RegularContentPadding

@Composable
private fun labelStyle(compact: Boolean) =
    if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge

private fun iconSize(compact: Boolean) = if (compact) 16.dp else 18.dp

@Composable
private fun ActionIcon(
    icon: ImageVector,
    compact: Boolean,
    tint: Color?,
) {
    if (tint == null) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize(compact)),
        )
    } else {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize(compact)),
            tint = tint,
        )
    }
    Spacer(Modifier.width(if (compact) 5.dp else 7.dp))
}

@Composable
private fun ButtonLabel(
    text: String,
    compact: Boolean,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color? = null,
) {
    leadingIcon?.let { ActionIcon(it, compact, leadingIconTint) }
    Text(
        text = text,
        style = labelStyle(compact),
        maxLines = 1,
        softWrap = false,
    )
}

/** Filled category-accent action for the dominant action in a task area. */
@Composable
fun NrPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color? = null,
    compact: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(actionHeight(compact))
            .then(if (compact) Modifier.widthIn(min = CompactButtonMinWidth) else Modifier),
        enabled = enabled,
        contentPadding = contentPadding(compact),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
        ),
    ) {
        ButtonLabel(text = text, compact = compact, leadingIcon = leadingIcon, leadingIconTint = leadingIconTint)
    }
}

/** Outlined neutral action for supporting or alternate actions. */
@Composable
fun NrSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color? = null,
    compact: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .height(actionHeight(compact))
            .then(if (compact) Modifier.widthIn(min = CompactButtonMinWidth) else Modifier),
        enabled = enabled,
        contentPadding = contentPadding(compact),
        border = BorderStroke(
            1.dp,
            if (enabled) {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            },
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
        ),
    ) {
        ButtonLabel(text = text, compact = compact, leadingIcon = leadingIcon, leadingIconTint = leadingIconTint)
    }
}

/** Text-only tertiary action for low-emphasis inline actions. */
@Composable
fun NrQuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color? = null,
    compact: Boolean = false,
    edgeAligned: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(actionHeight(compact)),
        enabled = enabled,
        contentPadding = if (edgeAligned) {
            PaddingValues(horizontal = 0.dp, vertical = 0.dp)
        } else {
            contentPadding(compact)
        },
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
        ),
    ) {
        ButtonLabel(text = text, compact = compact, leadingIcon = leadingIcon, leadingIconTint = leadingIconTint)
    }
}

/**
 * Destructive action. Ordinary destructive actions are outlined in error red.
 * Set [filled] only for the final confirmation inside a confirmation dialog.
 */
@Composable
fun NrDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color? = null,
    compact: Boolean = false,
    filled: Boolean = false,
) {
    if (filled) {
        Button(
            onClick = onClick,
            modifier = modifier
                .height(actionHeight(compact))
                .then(if (compact) Modifier.widthIn(min = CompactButtonMinWidth) else Modifier),
            enabled = enabled,
            contentPadding = contentPadding(compact),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.38f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
            ),
        ) {
            ButtonLabel(text = text, compact = compact, leadingIcon = leadingIcon, leadingIconTint = leadingIconTint)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
                .height(actionHeight(compact))
                .then(if (compact) Modifier.widthIn(min = CompactButtonMinWidth) else Modifier),
            enabled = enabled,
            contentPadding = contentPadding(compact),
            border = BorderStroke(
                1.dp,
                if (enabled) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                },
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
            ),
        ) {
            ButtonLabel(text = text, compact = compact, leadingIcon = leadingIcon, leadingIconTint = leadingIconTint)
        }
    }
}

enum class NrStatusAction {
    START,
    STOP,
    PAUSE,
    RESUME,
}

/**
 * Status-aware action button.
 *
 * Start/resume use the category accent, stop is filled error red, and pause is
 * an amber tonal/outlined action. Resume is outlined to stay below a primary
 * start action when both appear in the same hierarchy.
 */
@Composable
fun NrStatusButton(
    text: String,
    status: NrStatusAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color? = null,
    compact: Boolean = false,
) {
    when (status) {
        NrStatusAction.START -> NrPrimaryButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            leadingIcon = leadingIcon,
            compact = compact,
        )
        NrStatusAction.STOP -> {
            Button(
                onClick = onClick,
                modifier = modifier
                    .height(actionHeight(compact))
                    .then(if (compact) Modifier.widthIn(min = CompactButtonMinWidth) else Modifier),
                enabled = enabled,
                contentPadding = contentPadding(compact),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.38f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
                ),
            ) {
                ButtonLabel(text = text, compact = compact, leadingIcon = leadingIcon, leadingIconTint = leadingIconTint)
            }
        }
        NrStatusAction.PAUSE -> {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier
                    .height(actionHeight(compact))
                    .then(if (compact) Modifier.widthIn(min = CompactButtonMinWidth) else Modifier),
                enabled = enabled,
                contentPadding = contentPadding(compact),
                border = BorderStroke(1.dp, NrTheme.colors.statusAmber.copy(alpha = 0.75f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = NrTheme.colors.statusAmber,
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
                ),
            ) {
                ButtonLabel(text = text, compact = compact, leadingIcon = leadingIcon, leadingIconTint = leadingIconTint)
            }
        }
        NrStatusAction.RESUME -> {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier
                    .height(actionHeight(compact))
                    .then(if (compact) Modifier.widthIn(min = CompactButtonMinWidth) else Modifier),
                enabled = enabled,
                contentPadding = contentPadding(compact),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
                ),
            ) {
                ButtonLabel(text = text, compact = compact, leadingIcon = leadingIcon, leadingIconTint = leadingIconTint)
            }
        }
    }
}

@Composable
fun NrCompactPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color? = null,
) = NrPrimaryButton(
    text = text,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    leadingIcon = leadingIcon,
    leadingIconTint = leadingIconTint,
    compact = true,
)

@Composable
fun NrCompactSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color? = null,
) = NrSecondaryButton(
    text = text,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    leadingIcon = leadingIcon,
    leadingIconTint = leadingIconTint,
    compact = true,
)

@Composable
fun NrCompactQuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color? = null,
) = NrQuietButton(
    text = text,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    leadingIcon = leadingIcon,
    leadingIconTint = leadingIconTint,
    compact = true,
)

@Composable
fun NrCompactDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color? = null,
    filled: Boolean = false,
) = NrDangerButton(
    text = text,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    leadingIcon = leadingIcon,
    leadingIconTint = leadingIconTint,
    compact = true,
    filled = filled,
)

@Composable
fun NrCompactStatusButton(
    text: String,
    status: NrStatusAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color? = null,
) = NrStatusButton(
    text = text,
    status = status,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    leadingIcon = leadingIcon,
    leadingIconTint = leadingIconTint,
    compact = true,
)
