package com.swp81x.nrsuite.ui.components

import com.swp81x.nrsuite.core.wifi.bandForWifiChannel
import com.swp81x.nrsuite.core.wps.WpsLockState

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusRed
import com.swp81x.nrsuite.ui.util.ouiStatusColor
import com.swp81x.nrsuite.ui.util.securityColor
import com.swp81x.nrsuite.ui.util.signalQualityColor
import com.swp81x.nrsuite.ui.util.signalQualityLabel

enum class TargetSelectionControl {
    RADIO,
    CHECKBOX,
    NONE,
}

@Composable
fun NetworkTargetRow(
    ssid: String,
    bssid: String,
    channel: Int,
    rssi: Int,
    security: String,
    selected: Boolean,
    wps: Boolean = false,
    wpsColor: androidx.compose.ui.graphics.Color = StatusAmber,
    wpsLockState: WpsLockState? = null,
    vendor: String? = null,
    ouiWhitelisted: Boolean = false,
    ouiBlacklisted: Boolean = false,
    showSelectionIndicator: Boolean = true,
    selectionControl: TargetSelectionControl = TargetSelectionControl.RADIO,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val securityTint = securityColor(security)
    val ouiTint = ouiStatusColor(
        vendor = vendor,
        whitelisted = ouiWhitelisted,
        blacklisted = ouiBlacklisted,
    )
    val signalTint = signalQualityColor(rssi)
    val selectedAccent = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) selectedAccent.copy(alpha = 0.12f) else NrSurfaceVariant,
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = if (selected) 1.dp else 0.5.dp,
            color = if (selected) selectedAccent else NrOutline,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ssid.ifBlank { "(hidden)" },
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (wps) {
                        Spacer(Modifier.width(6.dp))
                        NetworkStatusBadge(text = "WPS", color = wpsColor)
                    }
                    if (wpsLockState != null) {
                        Spacer(Modifier.width(4.dp))
                        val (label, color) = when (wpsLockState) {
                            WpsLockState.LOCKED -> "WPS LOCKED" to StatusRed
                            WpsLockState.LIKELY_UNLOCKED -> "LIKELY UNLOCKED" to StatusGreen
                            WpsLockState.UNKNOWN -> "WPS UNKNOWN" to NrOnSurfaceVariant
                        }
                        NetworkStatusBadge(text = label, color = color)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = bssid,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = "ch $channel · ${bandForWifiChannel(channel).label} · $rssi dBm · ${signalQualityLabel(rssi)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = signalTint,
                    maxLines = 1,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = security,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = securityTint,
                        maxLines = 1,
                    )
                    if (!vendor.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "· $vendor",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = ouiTint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (showSelectionIndicator) {
                when (selectionControl) {
                    TargetSelectionControl.RADIO -> RadioButton(
                        selected = selected,
                        onClick = null,
                        enabled = enabled,
                    )
                    TargetSelectionControl.CHECKBOX -> Checkbox(
                        checked = selected,
                        onCheckedChange = null,
                        enabled = enabled,
                    )
                    TargetSelectionControl.NONE -> Unit
                }
            }
        }
    }
}
