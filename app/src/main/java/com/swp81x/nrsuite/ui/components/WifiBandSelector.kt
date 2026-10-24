package com.swp81x.nrsuite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.wifi.WifiBand
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant

@Composable
fun WifiBandSelector(
    selectedBand: WifiBand,
    fiveGhzAvailable: Boolean,
    onBandSelected: (WifiBand) -> Unit,
    onUnavailableBandClicked: (WifiBand) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BandChip(
            label = WifiBand.AUTO.label,
            selected = selectedBand == WifiBand.AUTO,
            enabled = enabled,
            clickable = enabled,
            onClick = { onBandSelected(WifiBand.AUTO) },
        )
        BandChip(
            label = WifiBand.BAND_2_4.label,
            selected = selectedBand == WifiBand.BAND_2_4,
            enabled = enabled,
            clickable = enabled,
            onClick = { onBandSelected(WifiBand.BAND_2_4) },
        )
        BandChip(
            label = WifiBand.BAND_5.label,
            selected = selectedBand == WifiBand.BAND_5,
            enabled = enabled && fiveGhzAvailable,
            clickable = enabled,
            onClick = {
                if (fiveGhzAvailable) {
                    onBandSelected(WifiBand.BAND_5)
                } else {
                    onUnavailableBandClicked(WifiBand.BAND_5)
                }
            },
        )
    }
}

@Composable
private fun BandChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    clickable: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val containerColor = when {
        selected -> accent.copy(alpha = 0.18f)
        enabled -> NrSurfaceVariant
        else -> NrSurfaceVariant.copy(alpha = 0.40f)
    }
    val contentColor = when {
        selected -> accent
        enabled -> NrOnSurfaceVariant
        else -> NrOnSurfaceVariant.copy(alpha = 0.50f)
    }
    val borderColor = when {
        selected -> accent.copy(alpha = 0.75f)
        enabled -> NrOutline
        else -> NrOutline.copy(alpha = 0.40f)
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = contentColor,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .border(0.5.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(enabled = clickable, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
