package com.swp81x.nrsuite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import org.json.JSONObject

@Composable
fun ScanTargetList(
    networks: List<JSONObject>,
    selectedBssids: Set<String>,
    onToggle: (JSONObject) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    multiSelect: Boolean = false,
    maxHeight: Dp = 300.dp,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .background(NrSurfaceVariant, RoundedCornerShape(10.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (networks.isEmpty()) {
            Text(
                text = "No scan results.",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(networks, key = { it.optString("bssid") + it.optString("ssid") }) { network ->
                    val bssid = network.optString("bssid")
                    NetworkTargetRow(
                        ssid = network.optString("ssid").ifBlank { "(hidden)" },
                        bssid = bssid,
                        channel = network.optInt("channel", 1),
                        rssi = network.optInt("rssi", -100),
                        security = network.optString("security", "?"),
                        wps = network.optBoolean("wps"),
                        vendor = network.optString("vendor").takeIf { it.isNotBlank() },
                        ouiWhitelisted = network.optBoolean("oui_whitelisted"),
                        ouiBlacklisted = network.optBoolean("oui_blacklisted"),
                        selected = bssid in selectedBssids,
                        selectionControl = if (multiSelect) {
                            TargetSelectionControl.CHECKBOX
                        } else {
                            TargetSelectionControl.RADIO
                        },
                        enabled = enabled,
                        onClick = { onToggle(network) },
                    )
                }
            }
        }
    }
}
