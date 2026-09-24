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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.swp81x.nrsuite.core.defense.DeauthAlert
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed

private val MAC_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

@Composable
fun DeauthDetectorScreen(
    connected: Boolean,
    running: Boolean,
    hopping: Boolean,
    activeChannel: Int,
    alerts: List<DeauthAlert>,
    onStart: (
        hop: Boolean,
        channel: Int,
        bssid: String,
        client: String,
        rssiMin: Int,
        intervalMs: Int,
    ) -> Unit,
    onStop: () -> Unit,
    onClearAlerts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var configExpanded by remember { mutableStateOf(true) }
    var hopMode by remember { mutableStateOf(false) }
    var channel by remember { mutableStateOf(6) }
    var bssid by remember { mutableStateOf("") }
    var client by remember { mutableStateOf("") }
    var rssiMin by remember { mutableStateOf(-90) }
    var intervalMs by remember { mutableStateOf(300) }
    var confirmStart by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(running) {
        if (running) configExpanded = false
    }

    val validBssid = bssid.isBlank() || MAC_REGEX.matches(bssid.trim())
    val validClient = client.isBlank() || MAC_REGEX.matches(client.trim())
    val validConfig = validBssid && validClient

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            StatusIndicator(
                label = when {
                    running && hopping -> "Detecting · hopping"
                    running -> "Detecting · channel ${activeChannel.coerceAtLeast(1)}"
                    connected -> "Ready"
                    else -> "Disconnected"
                },
                color = when {
                    running -> StatusGreen
                    connected -> StatusNeutral
                    else -> StatusRed
                },
            )

            Spacer(Modifier.height(10.dp))

            DetectorConfigZone(
                connected = connected,
                running = running,
                expanded = configExpanded,
                hopMode = hopMode,
                channel = channel,
                bssid = bssid,
                client = client,
                rssiMin = rssiMin,
                intervalMs = intervalMs,
                validBssid = validBssid,
                validClient = validClient,
                onToggle = { configExpanded = !configExpanded },
                onHopModeChange = { hopMode = it },
                onChannelChange = { channel = it.coerceIn(1, 13) },
                onBssidChange = { bssid = it.uppercase() },
                onClientChange = { client = it.uppercase() },
                onRssiMinChange = { rssiMin = it.coerceIn(-127, 0) },
                onIntervalChange = { intervalMs = it.coerceIn(50, 5_000) },
            )

            Spacer(Modifier.height(10.dp))

            DetectorResultsZone(
                alerts = alerts,
                running = running,
                onClearAlerts = onClearAlerts,
            )

            Spacer(Modifier.height(80.dp))
        }

        if (connected) {
            FloatingActionButton(
                onClick = {
                    when {
                        running -> onStop()
                        !validConfig -> Toast.makeText(
                            context,
                            "Fix the BSSID/client filter first.",
                            Toast.LENGTH_SHORT,
                        ).show()
                        else -> confirmStart = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = if (running) StatusRed else NrAccent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (running) "Stop detector" else "Start detector",
                )
            }
        }
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("Start deauth detector?") },
            text = {
                Text(
                    "Mode: ${if (hopMode) "channel hop" else "channel $channel"}\n" +
                        "BSSID filter: ${bssid.ifBlank { "any" }}\n" +
                        "Client filter: ${client.ifBlank { "any" }}\n" +
                        "RSSI floor: $rssiMin dBm\n\n" +
                        "The detector is passive and only reports deauth/disassoc frames."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmStart = false
                        onStart(hopMode, channel, bssid.trim(), client.trim(), rssiMin, intervalMs)
                    },
                ) {
                    Text("Start")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmStart = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun DetectorConfigZone(
    connected: Boolean,
    running: Boolean,
    expanded: Boolean,
    hopMode: Boolean,
    channel: Int,
    bssid: String,
    client: String,
    rssiMin: Int,
    intervalMs: Int,
    validBssid: Boolean,
    validClient: Boolean,
    onToggle: () -> Unit,
    onHopModeChange: (Boolean) -> Unit,
    onChannelChange: (Int) -> Unit,
    onBssidChange: (String) -> Unit,
    onClientChange: (String) -> Unit,
    onRssiMinChange: (Int) -> Unit,
    onIntervalChange: (Int) -> Unit,
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
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = NrAccent,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Detector configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (running) "Running" else "Passive deauth/disassoc monitor",
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
                        text = "Channel mode",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NrFilterChip(
                            selected = !hopMode,
                            onClick = { onHopModeChange(false) },
                            label = "Fixed",
                            enabled = !running,
                        )
                        NrFilterChip(
                            selected = hopMode,
                            onClick = { onHopModeChange(true) },
                            label = "Hop",
                            enabled = !running,
                        )
                    }

                    if (!hopMode) {
                        Spacer(Modifier.height(4.dp))
                        NumberStepper(
                            label = "Channel",
                            valueText = channel.toString(),
                            enabled = !running,
                            onDecrease = { onChannelChange(channel - 1) },
                            onIncrease = { onChannelChange(channel + 1) },
                        )
                    } else {
                        Spacer(Modifier.height(4.dp))
                        NumberStepper(
                            label = "Hop interval",
                            valueText = "$intervalMs ms",
                            enabled = !running,
                            onDecrease = { onIntervalChange(intervalMs - 50) },
                            onIncrease = { onIntervalChange(intervalMs + 50) },
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = bssid,
                        onValueChange = onBssidChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !running,
                        label = { Text("BSSID filter (optional)") },
                        placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                        isError = !validBssid,
                        singleLine = true,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = client,
                        onValueChange = onClientChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !running,
                        label = { Text("Client filter (optional)") },
                        placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                        isError = !validClient,
                        singleLine = true,
                    )

                    Spacer(Modifier.height(4.dp))
                    NumberStepper(
                        label = "RSSI floor",
                        valueText = "$rssiMin dBm",
                        enabled = !running,
                        onDecrease = { onRssiMinChange(rssiMin - 5) },
                        onIncrease = { onRssiMinChange(rssiMin + 5) },
                    )

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Leaving filters blank reports every detected deauth/disassoc frame. " +
                            "Use a fixed channel when you know the target AP channel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )

                    if (!connected) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Connect a device before starting the detector.",
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
private fun DetectorResultsZone(
    alerts: List<DeauthAlert>,
    running: Boolean,
    onClearAlerts: () -> Unit,
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
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Alerts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            alerts.isEmpty() && running -> "Listening..."
                            alerts.isEmpty() -> "No alerts yet"
                            else -> "${alerts.size} alert(s) captured"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                if (alerts.isNotEmpty()) {
                    OutlinedButton(onClick = onClearAlerts, enabled = !running) {
                        Text("Clear")
                    }
                }
            }

            if (alerts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                val sourceCounts = alerts.groupingBy { it.source }.eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .take(5)
                if (sourceCounts.isNotEmpty()) {
                    Text(
                        text = "Top sources",
                        style = MaterialTheme.typography.labelMedium,
                        color = NrOnSurfaceVariant,
                    )
                    sourceCounts.forEach { (source, count) ->
                        Text(
                            text = "$source  ×$count",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = NrOnSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        alerts.take(100).forEach { alert ->
                            AlertRow(alert)
                        }
                    }
                }
                if (alerts.size > 100) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Showing latest 100 of ${alerts.size}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertRow(alert: DeauthAlert) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = alert.subtype.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = StatusRed,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = alert.receivedAt,
                style = MaterialTheme.typography.labelSmall,
                color = NrOnSurfaceVariant,
            )
        }
        Text(
            text = "${alert.source} → ${alert.client}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
        Text(
            text = "BSSID ${alert.bssid} · ch ${alert.channel} · ${alert.rssi} dBm · reason ${alert.reason}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = NrOnSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(NrOutline),
        )
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
