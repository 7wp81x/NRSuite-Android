package com.swp81x.nrsuite.ui.components

import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant

@Composable
fun NrFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = NrSurfaceVariant,
            labelColor = NrOnSurfaceVariant,
            selectedContainerColor = NrAccent.copy(alpha = 0.18f),
            selectedLabelColor = NrAccent,
            disabledContainerColor = NrSurfaceVariant.copy(alpha = 0.40f),
            disabledLabelColor = NrOnSurfaceVariant.copy(alpha = 0.50f),
        ),
    )
}
