package com.swp81x.nrsuite.ui.components

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusRed
import com.swp81x.nrsuite.ui.util.ouiStatusColor
import com.swp81x.nrsuite.ui.util.securityColor
import com.swp81x.nrsuite.ui.util.signalQualityColor

@Composable
fun NetworkTargetRow(
    ssid: String,
    bssid: String,
    channel: Int,
    rssi: Int,
    security: String,
    selected: Boolean,
    vendor: String? = null,
    ouiWhitelisted: Boolean = false,
    ouiBlacklisted: Boolean = false,
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
    val riskTint = when {
        ouiBlacklisted -> StatusRed
        security.uppercase().contains("OPEN") -> StatusRed
        vendor != null -> ouiTint
        else -> NrOutline
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) NrAccent.copy(alpha = 0.12f) else NrSurfaceVariant,
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = if (selected) 1.dp else 0.5.dp,
            color = if (selected) NrAccent else riskTint,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = ssid.ifBlank { "(hidden)" },
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = bssid,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = "ch $channel  ·  $rssi dBm",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = signalTint,
                    maxLines = 1,
                )
                Row {
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
                        )
                    }
                }
            }
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
            )
        }
    }
}
