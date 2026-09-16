package com.swp81x.nrsuite.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import com.swp81x.nrsuite.core.eapol.EapolHandshake
import org.json.JSONObject
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
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
import com.swp81x.nrsuite.core.sniff.SniffRequest
import com.swp81x.nrsuite.ui.components.NetworkTargetRow
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed

private val SNIFF_MAC_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

@Composable
fun SniffScreen(
    connected: Boolean,
    sniffing: Boolean,
    packetCount: Long,
    capturePath: String?,
    handshake: EapolHandshake,
    scanning: Boolean,
    networks: List<JSONObject>,
    exportDirectoryName: String,
    onChooseExportDirectory: () -> Unit,
    onScanWifi: () -> Unit,
    onStart: (SniffRequest) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var configExpanded by remember { mutableStateOf(true) }
    var fixedMode by remember { mutableStateOf(true) }
    var channel by remember { mutableStateOf(6) }
    var intervalMs by remember { mutableStateOf(300) }
    var deauthBeforeCapture by remember { mutableStateOf(false) }
    var eapolOnly by remember { mutableStateOf(false) }
    var targetNetworkOnly by remember { mutableStateOf(false) }
    var targetBssid by remember { mutableStateOf("") }
    var client by remember { mutableStateOf("FF:FF:FF:FF:FF:FF") }
    var deauthCount by remember { mutableStateOf(0) }
    var deauthIntervalMs by remember { mutableStateOf(80) }

    val validDeauthTarget = !deauthBeforeCapture || SNIFF_MAC_REGEX.matches(targetBssid.trim())
    val validDeauthClient = SNIFF_MAC_REGEX.matches(client.trim())

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            ConfigZone(
                connected = connected,
                sniffing = sniffing,
                expanded = configExpanded,
                fixedMode = fixedMode,
                channel = channel,
                intervalMs = intervalMs,
                exportDirectoryName = exportDirectoryName,
                onChooseExportDirectory = onChooseExportDirectory,
                deauthBeforeCapture = deauthBeforeCapture,
                eapolOnly = eapolOnly,
                targetNetworkOnly = targetNetworkOnly,
                targetBssid = targetBssid,
                client = client,
                deauthCount = deauthCount,
                deauthIntervalMs = deauthIntervalMs,
                validDeauthTarget = validDeauthTarget,
                validDeauthClient = validDeauthClient,
                onToggle = { configExpanded = !configExpanded },
                onModeChange = { fixedMode = it },
                onChannelChange = { channel = it.coerceIn(1, 13) },
                onIntervalChange = { intervalMs = it.coerceIn(50, 1000) },
                onDeauthBeforeCaptureChange = { deauthBeforeCapture = it },
                onEapolOnlyChange = { eapolOnly = it },
                onTargetNetworkOnlyChange = { targetNetworkOnly = it },
                scanning = scanning,
                networks = networks,
                onScanWifi = onScanWifi,
                onTargetBssidChange = { targetBssid = it },
                onClientChange = { client = it },
                onDeauthCountChange = { deauthCount = it.coerceAtLeast(0) },
                onDeauthIntervalChange = { deauthIntervalMs = it.coerceIn(10, 10_000) },
            )

            Spacer(Modifier.height(10.dp))

            ResultZone(
                connected = connected,
                sniffing = sniffing,
                packetCount = packetCount,
                capturePath = capturePath,
                handshake = handshake,
            )
        }

        if (connected) {
            FloatingActionButton(
                onClick = {
                    if (sniffing) {
                        onStop()
                    } else if (validDeauthTarget && validDeauthClient) {
                        onStart(
                            SniffRequest(
                                fixedMode = fixedMode,
                                channel = channel,
                                intervalMs = intervalMs,
                                deauthBeforeCapture = deauthBeforeCapture,
                                targetBssid = targetBssid.trim(),
                                eapolOnly = eapolOnly,
                                targetNetworkOnly = targetNetworkOnly,
                                client = client.trim(),
                                deauthCount = deauthCount,
                                deauthIntervalMs = deauthIntervalMs,
                            )
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = if (sniffing) StatusRed else NrAccent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = if (sniffing) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (sniffing) "Stop sniffing" else "Start sniffing",
                )
            }
        }
    }
}

@Composable
private fun ConfigZone(
    connected: Boolean,
    sniffing: Boolean,
    expanded: Boolean,
    fixedMode: Boolean,
    channel: Int,
    intervalMs: Int,
    exportDirectoryName: String,
    onChooseExportDirectory: () -> Unit,
    deauthBeforeCapture: Boolean,
    eapolOnly: Boolean,
    targetNetworkOnly: Boolean,
    targetBssid: String,
    client: String,
    deauthCount: Int,
    deauthIntervalMs: Int,
    validDeauthTarget: Boolean,
    validDeauthClient: Boolean,
    onToggle: () -> Unit,
    onModeChange: (Boolean) -> Unit,
    onChannelChange: (Int) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onDeauthBeforeCaptureChange: (Boolean) -> Unit,
    onEapolOnlyChange: (Boolean) -> Unit,
    onTargetNetworkOnlyChange: (Boolean) -> Unit,
    scanning: Boolean,
    networks: List<JSONObject>,
    onScanWifi: () -> Unit,
    onTargetBssidChange: (String) -> Unit,
    onClientChange: (String) -> Unit,
    onDeauthCountChange: (Int) -> Unit,
    onDeauthIntervalChange: (Int) -> Unit,
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
                        text = "Capture configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (sniffing) "Capture running" else "Fixed channel or channel hopping",
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
                    Text(
                        text = "Mode",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = fixedMode,
                            onClick = { onModeChange(true) },
                            label = { Text("Fixed") },
                            enabled = !sniffing,
                        )
                        FilterChip(
                            selected = !fixedMode,
                            onClick = { onModeChange(false) },
                            label = { Text("Channel hop") },
                            enabled = !sniffing,
                        )
                    }

                    if (fixedMode) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Channel: $channel",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onChannelChange(channel - 1) },
                                enabled = !sniffing && channel > 1,
                            ) {
                                Text("-", style = MaterialTheme.typography.titleLarge)
                            }
                            Text(
                                text = channel.toString(),
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                            IconButton(
                                onClick = { onChannelChange(channel + 1) },
                                enabled = !sniffing && channel < 13,
                            ) {
                                Text("+", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    } else {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Hop interval: ${intervalMs} ms",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onIntervalChange(intervalMs - 50) },
                                enabled = !sniffing && intervalMs > 50,
                            ) {
                                Text("-", style = MaterialTheme.typography.titleLarge)
                            }
                            Text(
                                text = "$intervalMs ms",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                            IconButton(
                                onClick = { onIntervalChange(intervalMs + 50) },
                                enabled = !sniffing && intervalMs < 1000,
                            ) {
                                Text("+", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Target specific network",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = targetNetworkOnly,
                            onCheckedChange = onTargetNetworkOnlyChange,
                            enabled = !sniffing,
                        )
                    }

                    if (targetNetworkOnly) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = onScanWifi,
                            enabled = connected && !sniffing && !scanning,
                        ) {
                            Text(if (scanning) "Scanning..." else "Scan WiFi for targets")
                        }
                        if (networks.isNotEmpty()) {
                            networks.take(6).forEach { network ->
                                val ssid = network.optString("ssid").ifBlank { "(hidden)" }
                                val bssidValue = network.optString("bssid")
                                val channelValue = network.optInt("channel", 1)
                                NetworkTargetRow(
                                    ssid = ssid,
                                    bssid = bssidValue,
                                    channel = channelValue,
                                    rssi = network.optInt("rssi", -100),
                                    security = network.optString("security", "?"),
                                    selected = targetBssid.trim().equals(bssidValue, ignoreCase = true),
                                    onClick = {
                                        onTargetBssidChange(bssidValue)
                                        onChannelChange(channelValue)
                                    },
                                    modifier = Modifier.padding(vertical = 3.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "EAPOL-only capture",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = eapolOnly,
                            onCheckedChange = onEapolOnlyChange,
                            enabled = !sniffing,
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Send deauth before capture",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = deauthBeforeCapture,
                            onCheckedChange = onDeauthBeforeCaptureChange,
                            enabled = !sniffing,
                        )
                    }

                    if (eapolOnly || deauthBeforeCapture || targetNetworkOnly) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = targetBssid,
                            onValueChange = onTargetBssidChange,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !sniffing,
                            label = { Text("Target BSSID") },
                            placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                            isError = targetBssid.isNotBlank() && !validDeauthTarget,
                            singleLine = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = client,
                            onValueChange = onClientChange,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !sniffing,
                            label = { Text("Client MAC") },
                            isError = !validDeauthClient,
                            singleLine = true,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Count",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { onDeauthCountChange(deauthCount - 5) },
                                enabled = !sniffing && deauthCount > 0,
                            ) { Text("-", style = MaterialTheme.typography.titleLarge) }
                            Text(
                                text = if (deauthCount <= 0) "default" else deauthCount.toString(),
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                            IconButton(
                                onClick = { onDeauthCountChange(deauthCount + 5) },
                                enabled = !sniffing,
                            ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Interval",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { onDeauthIntervalChange(deauthIntervalMs - 10) },
                                enabled = !sniffing && deauthIntervalMs > 10,
                            ) { Text("-", style = MaterialTheme.typography.titleLarge) }
                            Text(
                                text = "$deauthIntervalMs ms",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                            IconButton(
                                onClick = { onDeauthIntervalChange(deauthIntervalMs + 10) },
                                enabled = !sniffing && deauthIntervalMs < 10_000,
                            ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Output",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = exportDirectoryName,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = NrOnSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onChooseExportDirectory) {
                            Text("Change")
                        }
                    }

                    if (!connected) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Connect a device from the Device tab before starting a capture.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAmber,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultZone(
    connected: Boolean,
    sniffing: Boolean,
    packetCount: Long,
    capturePath: String?,
    handshake: EapolHandshake,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text(
                text = "Live capture",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))

            StatusIndicator(
                label = when {
                    !connected -> "No device connected"
                    sniffing -> "Capturing"
                    packetCount > 0 -> "Stopped"
                    else -> "Ready"
                },
                color = when {
                    !connected -> StatusNeutral
                    sniffing -> StatusGreen
                    packetCount > 0 -> StatusAmber
                    else -> StatusNeutral
                },
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Packets",
                style = MaterialTheme.typography.labelLarge,
                color = NrOnSurfaceVariant,
            )
            Text(
                text = packetCount.toString(),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Capture file",
                style = MaterialTheme.typography.labelLarge,
                color = NrOnSurfaceVariant,
            )
            Text(
                text = capturePath ?: "Not started",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "EAPOL handshake",
                style = MaterialTheme.typography.labelLarge,
                color = NrOnSurfaceVariant,
            )
            Text(
                text = buildString {
                    append("M1 ${if (handshake.m1) "✓" else "—"}  ")
                    append("M2 ${if (handshake.m2) "✓" else "—"}  ")
                    append("M3 ${if (handshake.m3) "✓" else "—"}  ")
                    append("M4 ${if (handshake.m4) "✓" else "—"}")
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = if (handshake.isComplete) StatusGreen else NrOnSurfaceVariant,
            )

            if (!connected) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Connect an ESP32 to start packet capture.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else if (!sniffing && packetCount == 0L && capturePath == null) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Tap the play button to start a capture.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }
    }
}
