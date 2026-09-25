package com.swp81x.nrsuite.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.ui.components.NetworkStatusBadge
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusRed
import com.swp81x.nrsuite.ui.util.ouiStatusColor
import com.swp81x.nrsuite.ui.util.securityColor
import com.swp81x.nrsuite.ui.util.signalQualityColor
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import org.json.JSONObject

@Composable
fun WifiScanScreen(
    scanning: Boolean,
    networks: List<JSONObject>,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            Text(
                text = if (scanning) {
                    "Scanning all 2.4 GHz channels..."
                } else {
                    "Results arrive as asynchronous scan_ap events and are sorted by RSSI."
                },
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Scan results (${networks.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(8.dp))

            if (networks.isEmpty()) {
                EmptyResults(scanning = scanning)
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(networks, key = { it.optString("bssid", it.toString()) }) { network ->
                        NetworkRow(network)
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }

        FloatingActionButton(
            onClick = onScan,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = NrAccent,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            if (scanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(Icons.Default.Search, contentDescription = "Scan WiFi")
            }
        }
    }
}

@Composable
private fun ConfigZone(
    expanded: Boolean,
    scanning: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = NrAccent,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Active scan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (scanning) "Scanning all channels..." else "All 2.4 GHz channels",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "The ESP32 performs the active scan. Results arrive as asynchronous scan_ap events and are sorted by RSSI.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyResults(scanning: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                tint = NrOnSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (scanning) "Waiting for scan results..." else "No scan results yet.",
                color = NrOnSurfaceVariant,
            )
            Text(
                text = "Tap the scan button to start.",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NetworkRow(network: JSONObject) {
    val ssid = network.optString("ssid").ifBlank { "(hidden)" }
    val bssid = network.optString("bssid")
    val channel = network.optInt("channel")
    val rssi = network.optInt("rssi")
    val security = network.optString("security").ifBlank { "?" }
    val wps = network.optBoolean("wps")
    val vendor = network.optString("vendor").takeIf { it.isNotBlank() }
    val ouiWhitelisted = network.optBoolean("oui_whitelisted")
    val ouiBlacklisted = network.optBoolean("oui_blacklisted")
    val securityTint = securityColor(security)
    val ouiTint = ouiStatusColor(vendor, ouiWhitelisted, ouiBlacklisted)
    val signalTint = signalQualityColor(rssi)
    val cardBorder = when {
        ouiBlacklisted -> StatusRed
        security.uppercase().contains("OPEN") -> StatusRed
        vendor != null -> ouiTint
        else -> NrOutline
    }
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, cardBorder),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ssid,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                SignalBars(rssi = rssi)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = bssid,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
                modifier = Modifier.clickable {
                    clipboardManager.setText(AnnotatedString(bssid))
                },
            )
            Text(
                text = "ch $channel  •  $rssi dBm",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = signalTint,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = security,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = securityTint,
                    maxLines = 1,
                )
                if (vendor != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "• $vendor",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = ouiTint,
                        maxLines = 1,
                    )
                }
                if (wps) {
                    Spacer(Modifier.width(6.dp))
                    NetworkStatusBadge(text = "WPS", color = StatusAmber)
                }
            }
        }
    }
}

@Composable
private fun SignalBars(rssi: Int) {
    val bars = when {
        rssi >= -50 -> 4
        rssi >= -65 -> 3
        rssi >= -75 -> 2
        rssi >= -85 -> 1
        else -> 0
    }
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        (1..4).forEach { index ->
            Spacer(
                modifier = Modifier
                    .width(4.dp)
                    .height((4 + index * 4).dp)
                    .background(
                        color = if (index <= bars) signalQualityColor(rssi) else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(1.dp),
                    ),
            )
        }
    }
}
