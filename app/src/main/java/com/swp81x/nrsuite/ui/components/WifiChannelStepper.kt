package com.swp81x.nrsuite.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.wifi.WIFI_AUTO_CHANNELS
import com.swp81x.nrsuite.core.wifi.bandForWifiChannel
import com.swp81x.nrsuite.core.wifi.nextWifiChannel

@Composable
fun WifiChannelStepper(
    value: Int,
    enabled: Boolean,
    onChannelChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    allowedChannels: List<Int> = WIFI_AUTO_CHANNELS,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onChannelChange(nextWifiChannel(value, false, allowedChannels)) },
            enabled = enabled && allowedChannels.size > 1,
        ) {
            Text("-", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = "ch $value · ${bandForWifiChannel(value).label}",
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        IconButton(
            onClick = { onChannelChange(nextWifiChannel(value, true, allowedChannels)) },
            enabled = enabled && allowedChannels.size > 1,
        ) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}
