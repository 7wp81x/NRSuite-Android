package com.swp81x.nrsuite.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.defense.ClientObservation
import com.swp81x.nrsuite.core.defense.ClientPresenceMode
import com.swp81x.nrsuite.core.wifi.NetworkTarget
import com.swp81x.nrsuite.ui.components.NetworkStatusBadge
import com.swp81x.nrsuite.ui.components.NetworkTargetRow
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.theme.CategoryDetectionBlue
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed
import com.swp81x.nrsuite.ui.util.rssiToProximity
import com.swp81x.nrsuite.ui.util.signalQualityColor
import org.json.JSONObject

@Composable
fun ClientPresenceScreen(
    connected: Boolean,
    running: Boolean,
    mode: ClientPresenceMode,
    fixed: Boolean,
    channel: Int,
    targetBssid: String,
    scanning: Boolean,
    networks: List<JSONObject>,
    clients: List<ClientObservation>,
    frameCount: Long,
    lastTriggerAt: String?,
    onModeChange: (ClientPresenceMode) -> Unit,
    onFixedChange: (Boolean) -> Unit,
    onChannelChange: (Int) -> Unit,
    onScanWifi: () -> Unit,
    onSelectTarget: (NetworkTarget) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onTriggerReconnect: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var configExpanded by remember { mutableStateOf(true) }
    var confirmStart by remember { mutableStateOf(false) }
    val validTarget = targetBssid.isNotBlank()
    val canStart = connected && (mode == ClientPresenceMode.PASSIVE || validTarget)

    LaunchedEffect(running) {
        if (running) configExpanded = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            ClientPresenceStatusCard(
                connected = connected,
                running = running,
                mode = mode,
                frameCount = frameCount,
                lastTriggerAt = lastTriggerAt,
            )
            if (running && mode == ClientPresenceMode.ACTIVE) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onTriggerReconnect,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Force reconnect burst")
                }
            }
            Spacer(Modifier.height(10.dp))

            ConfigZone(
                connected = connected,
                running = running,
                expanded = configExpanded,
                mode = mode,
                fixed = fixed,
                channel = channel,
                targetBssid = targetBssid,
                scanning = scanning,
                networks = networks,
                onToggle = { configExpanded = !configExpanded },
                onModeChange = onModeChange,
                onFixedChange = onFixedChange,
                onChannelChange = onChannelChange,
                onScanWifi = onScanWifi,
                onSelectTarget = onSelectTarget,
            )
            Spacer(Modifier.height(10.dp))

            ClientListCard(
                clients = clients,
                running = running,
                onClear = onClear,
            )

            Spacer(Modifier.height(80.dp))
        }

        if (connected) {
            FloatingActionButton(
                onClick = {
                    if (running) onStop() else if (canStart) confirmStart = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .alpha(if (!canStart && !running) 0.4f else 1f),
                containerColor = if (running) StatusRed else NrAccent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                )
            }
        }
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("Start client detection?") },
            text = {
                Text(
                    if (mode == ClientPresenceMode.ACTIVE) {
                        "Active mode sends a short deauth burst to $targetBssid " +
                            "to force clients to re-announce, then monitors their frames. " +
                            "Only use this on networks you own or are authorized to test."
                    } else {
                        "Passive mode monitors probe, association, reassociation, " +
                            "and authentication frames without transmitting."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmStart = false
                        onStart()
                    },
                ) { Text("Start") }
            },
            dismissButton = {
                TextButton(onClick = { confirmStart = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ClientPresenceStatusCard(
    connected: Boolean,
    running: Boolean,
    mode: ClientPresenceMode,
    frameCount: Long,
    lastTriggerAt: String?,
) {
    val color = when {
        !connected -> StatusNeutral
        running -> StatusGreen
        else -> StatusAmber
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, if (running) NrAccent else NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PersonSearch,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = when {
                            !connected -> "Disconnected"
                            running -> "Monitoring"
                            else -> "Ready"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            !connected -> "Connect a device to begin"
                            running -> "${mode.name.lowercase().replaceFirstChar { it.uppercase() }} mode · $frameCount frames"
                            else -> "Passive or active client detection"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                    if (running && lastTriggerAt != null) {
                        Text(
                            text = "Last force trigger: $lastTriggerAt",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = NrOnSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigZone(
    connected: Boolean,
    running: Boolean,
    expanded: Boolean,
    mode: ClientPresenceMode,
    fixed: Boolean,
    channel: Int,
    targetBssid: String,
    scanning: Boolean,
    networks: List<JSONObject>,
    onToggle: () -> Unit,
    onModeChange: (ClientPresenceMode) -> Unit,
    onFixedChange: (Boolean) -> Unit,
    onChannelChange: (Int) -> Unit,
    onScanWifi: () -> Unit,
    onSelectTarget: (NetworkTarget) -> Unit,
) {
    val controlsEnabled = connected && !running

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
                    imageVector = Icons.Default.PersonSearch,
                    contentDescription = null,
                    tint = CategoryDetectionBlue,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Client/Presence configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (running) "Detection running" else "Passive or active mode",
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

            if (expanded) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Text("Mode", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NrFilterChip(
                            selected = mode == ClientPresenceMode.PASSIVE,
                            onClick = { onModeChange(ClientPresenceMode.PASSIVE) },
                            label = "Passive",
                            enabled = controlsEnabled,
                        )
                        NrFilterChip(
                            selected = mode == ClientPresenceMode.ACTIVE,
                            onClick = { onModeChange(ClientPresenceMode.ACTIVE) },
                            label = "Active (deauth)",
                            enabled = controlsEnabled,
                        )
                    }

                    if (mode == ClientPresenceMode.ACTIVE) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Active mode sends a short deauth burst to force clients to reconnect " +
                                "and reveal themselves. Use only on authorized networks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAmber,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onScanWifi,
                            enabled = controlsEnabled && !scanning,
                        ) {
                            Text(if (scanning) "Scanning..." else "Scan WiFi for targets")
                        }
                        if (networks.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                networks.forEach { network ->
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
                                        selected = targetBssid.equals(bssid, ignoreCase = true),
                                        enabled = controlsEnabled,
                                        onClick = {
                                            onSelectTarget(
                                                NetworkTarget(
                                                    ssid = network.optString("ssid").ifBlank { "(hidden)" },
                                                    bssid = bssid,
                                                    channel = network.optInt("channel", 1),
                                                    rssi = network.optInt("rssi", -100),
                                                    security = network.optString("security", "?"),
                                                    wps = network.optBoolean("wps"),
                                                    vendor = network.optString("vendor").takeIf { it.isNotBlank() },
                                                    ouiWhitelisted = network.optBoolean("oui_whitelisted"),
                                                    ouiBlacklisted = network.optBoolean("oui_blacklisted"),
                                                )
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.height(10.dp))
                        Text("Channel mode", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NrFilterChip(
                                selected = fixed,
                                onClick = { onFixedChange(true) },
                                label = "Fixed",
                                enabled = controlsEnabled,
                            )
                            NrFilterChip(
                                selected = !fixed,
                                onClick = { onFixedChange(false) },
                                label = "Hop",
                                enabled = controlsEnabled,
                            )
                        }
                        if (fixed) {
                            Spacer(Modifier.height(4.dp))
                            NumberStepper(
                                label = "Channel",
                                valueText = channel.toString(),
                                enabled = controlsEnabled,
                                onDecrease = { onChannelChange((channel - 1).coerceAtLeast(1)) },
                                onIncrease = { onChannelChange((channel + 1).coerceAtMost(14)) },
                            )
                        }
                    }

                    if (!connected) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Connect a device before starting client detection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusRed,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientListCard(
    clients: List<ClientObservation>,
    running: Boolean,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Detected clients",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (clients.isEmpty()) "No clients detected yet" else "${clients.size} unique client(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                if (clients.isNotEmpty()) {
                    OutlinedButton(onClick = onClear, enabled = !running) {
                        Text("Clear")
                    }
                }
            }

            if (clients.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(clients, key = { it.mac }) { client ->
                        ClientRow(client)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientRow(client: ClientObservation) {
    val signalColor = signalQualityColor(client.rssi)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = client.mac,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                NetworkStatusBadge(text = client.subtype.replace('_', ' '), color = signalColor)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "ch ${client.channel} · ${client.rssi} dBm · ${rssiToProximity(client.rssi).label} · " +
                    "${client.sightings} sighting(s)",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = signalColor,
            )
            if (!client.vendor.isNullOrBlank()) {
                Text(
                    text = "Vendor: ${client.vendor}",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
            if (!client.ssid.isNullOrBlank()) {
                Text(
                    text = "SSID: ${client.ssid}",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
            Text(
                text = "Last seen: ${client.lastSeen}",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NumberStepper(
    label: String,
    valueText: String,
    enabled: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDecrease, enabled = enabled) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease $label")
        }
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        IconButton(onClick = onIncrease, enabled = enabled) {
            Icon(Icons.Default.Add, contentDescription = "Increase $label")
        }
    }
}
