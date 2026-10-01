package com.swp81x.nrsuite.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChannelModeToggle(
    fixed: Boolean,
    enabled: Boolean,
    onFixedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NrFilterChip(
            selected = fixed,
            onClick = { onFixedChange(true) },
            label = "Fixed",
            enabled = enabled,
        )
        NrFilterChip(
            selected = !fixed,
            onClick = { onFixedChange(false) },
            label = "Hopping",
            enabled = enabled,
        )
    }
}
