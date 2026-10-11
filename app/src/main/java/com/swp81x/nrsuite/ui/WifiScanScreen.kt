package com.swp81x.nrsuite.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.util.copyWithToast
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.StatusAmber
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
    meshActive: Boolean,
    meshRole: String,
    meshPeerCount: Int,
    meshChannel: Int,
    useMesh: Boolean,
    onUseMeshChange: (Boolean) -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val meshScanAvailable = meshActive && meshRole == "master"
    val meshScanBlocked = meshActive && meshRole != "master"
    val activeMeshSource = meshScanAvailable && useMesh

    LaunchedEffect(meshScanAvailable, meshActive) {
        if (meshScanAvailable && !useMesh) onUseMeshChange(true)
        if (!meshActive && useMesh) onUseMeshChange(false)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            ConfigZone(
                meshActive = meshActive,
                meshScanAvailable = meshScanAvailable,
                meshScanBlocked = meshScanBlocked,
                meshPeerCount = meshPeerCount,
                meshChannel = meshChannel,
                useMesh = useMesh,
                scanning = scanning,
                onUseMeshChange = onUseMeshChange,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = when {
                    scanning && activeMeshSource ->
                        "Mesh scan: waiting for reports from online mesh nodes..."
                    scanning ->
                        "Scanning all 2.4 GHz channels..."
                    meshScanBlocked ->
                        "Mesh is active, but this device is not the master. Connect to the master to run a Mesh Scan."
                    meshScanAvailable && !useMesh ->
                        "Mesh is active. Select Mesh clients as the scan source."
                    activeMeshSource ->
                        "Mesh scan: distributes a same-channel scan across online mesh nodes."
                    else ->
                        "Local scan: results arrive as asynchronous scan_ap events and are sorted by RSSI."
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

        ModuleActionFab(
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
                Icon(
                    Icons.Default.Search,
                    contentDescription = if (activeMeshSource) "Mesh Scan" else "Scan WiFi",
                )
            }
        }
    }
}

@Composable
private fun ConfigZone(
    meshActive: Boolean,
    meshScanAvailable: Boolean,
    meshScanBlocked: Boolean,
    meshPeerCount: Int,
    meshChannel: Int,
    useMesh: Boolean,
    scanning: Boolean,
    onUseMeshChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = NrAccent,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Scan source",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            meshScanBlocked -> "Connect to the mesh master to enable Mesh Scan."
                            meshScanAvailable -> "Mesh clients online: $meshPeerCount"
                            else -> "Local scan uses the connected USB device."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NrFilterChip(
                    selected = !useMesh,
                    onClick = { if (!scanning) onUseMeshChange(false) },
                    enabled = !scanning && !meshActive,
                    label = "This device",
                )
                NrFilterChip(
                    selected = useMesh,
                    onClick = { if (!scanning && meshScanAvailable) onUseMeshChange(true) },
                    enabled = !scanning && meshScanAvailable,
                    label = "Mesh clients",
                )
            }

            if (meshActive) {
                Text(
                    text = "Same-channel mesh scan · current mesh ch $meshChannel",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
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
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
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
                    clipboardManager.copyWithToast(context, bssid)
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
            val meshNodes = network.optJSONArray("nodes")
            if (meshNodes != null && meshNodes.length() > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Mesh: ${meshNodes.length()} node(s) · best $rssi dBm",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
                for (i in 0 until meshNodes.length()) {
                    val node = meshNodes.optJSONObject(i) ?: continue
                    val nodeId = node.optString("node_id")
                    val nodeRssi = node.optInt("rssi", -127)
                    Text(
                        text = "  $nodeId: $nodeRssi dBm",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = signalQualityColor(nodeRssi),
                    )
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
