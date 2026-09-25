package com.swp81x.nrsuite.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import android.widget.Toast
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.ui.components.NetworkTargetRow
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed

private val MAC_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

@Composable
fun DeauthScreen(
    connected: Boolean,
    running: Boolean,
    sentFrames: Int,
    targetBssid: String,
    activeChannel: Int,
    scanning: Boolean,
    networks: List<JSONObject>,
    onScanWifi: () -> Unit,
    onStart: (bssid: String, channel: Int, client: String, count: Int, duration: Int, intervalMs: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var configExpanded by remember { mutableStateOf(true) }
    var bssid by remember { mutableStateOf("") }
    var client by remember { mutableStateOf("FF:FF:FF:FF:FF:FF") }
    var channel by remember { mutableStateOf(6) }
    var count by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0) }
    var intervalMs by remember { mutableStateOf(100) }
    var confirmStart by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(running) {
        if (running) configExpanded = false
    }

    val validBssid = MAC_REGEX.matches(bssid.trim())
    val validClient = MAC_REGEX.matches(client.trim())

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            ConfigZone(
                connected = connected,
                running = running,
                expanded = configExpanded,
                bssid = bssid,
                client = client,
                channel = channel,
                count = count,
                duration = duration,
                intervalMs = intervalMs,
                validBssid = validBssid,
                validClient = validClient,
                scanning = scanning,
                sentFrames = sentFrames,
                networks = networks,
                onScanWifi = onScanWifi,
                onToggle = { configExpanded = !configExpanded },
                onBssidChange = { bssid = it },
                onClientChange = { client = it },
                onChannelChange = { channel = it.coerceIn(1, 13) },
                onCountChange = { count = it.coerceAtLeast(0) },
                onDurationChange = { duration = it.coerceAtLeast(0) },
                onIntervalChange = { intervalMs = it.coerceIn(10, 10_000) },
            )

            Spacer(Modifier.height(10.dp))

            ResultZone(
                connected = connected,
                running = running,
                sentFrames = sentFrames,
                targetBssid = targetBssid,
                activeChannel = activeChannel,
            )
        }

        if (connected) {
            FloatingActionButton(
                onClick = {
                    when {
                        running -> Unit
                        !validBssid -> Toast.makeText(
                            context,
                            "Select a valid target BSSID first.",
                            Toast.LENGTH_SHORT,
                        ).show()
                        !validClient -> Toast.makeText(
                            context,
                            "Enter a valid client MAC or use FF:FF:FF:FF:FF:FF.",
                            Toast.LENGTH_SHORT,
                        ).show()
                        else -> confirmStart = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = if (running) StatusAmber else NrAccent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                if (running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Send deauth")
                }
            }
        }
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("Send deauth frames?") },
            text = {
                Text(
                    "Target: $bssid\n" +
                        "Client: $client\n" +
                        "Channel: $channel\n" +
                        "Count: ${if (count <= 0) "firmware default" else count}\n\n" +
                        "This disrupts connectivity for affected clients. Only use this on networks you own or are authorized to test."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmStart = false
                        onStart(bssid.trim(), channel, client.trim(), count, duration, intervalMs)
                    },
                ) {
                    Text("Send")
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
private fun ConfigZone(
    connected: Boolean,
    running: Boolean,
    expanded: Boolean,
    bssid: String,
    client: String,
    channel: Int,
    count: Int,
    duration: Int,
    intervalMs: Int,
    validBssid: Boolean,
    validClient: Boolean,
    scanning: Boolean,
    sentFrames: Int,
    networks: List<JSONObject>,
    onScanWifi: () -> Unit,
    onToggle: () -> Unit,
    onBssidChange: (String) -> Unit,
    onClientChange: (String) -> Unit,
    onChannelChange: (Int) -> Unit,
    onCountChange: (Int) -> Unit,
    onDurationChange: (Int) -> Unit,
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
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = NrAccent,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Deauth configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            running -> "Burst running"
                            sentFrames > 0 -> "$sentFrames frame(s) sent"
                            else -> "Targeted deauthentication burst"
                        },
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
                    OutlinedButton(
                        onClick = onScanWifi,
                        enabled = connected && !scanning && !running,
                    ) {
                        Text(if (scanning) "Scanning..." else "Scan WiFi for targets")
                    }

                    if (networks.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Select target",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        networks.forEach { network ->
                            val ssid = network.optString("ssid").ifBlank { "(hidden)" }
                            val bssidValue = network.optString("bssid")
                            val channelValue = network.optInt("channel", 1)
                            NetworkTargetRow(
                                ssid = ssid,
                                bssid = bssidValue,
                                channel = channelValue,
                                rssi = network.optInt("rssi", -100),
                                security = network.optString("security", "?"),
                                vendor = network.optString("vendor").takeIf { it.isNotBlank() },
                                ouiWhitelisted = network.optBoolean("oui_whitelisted"),
                                ouiBlacklisted = network.optBoolean("oui_blacklisted"),
                                selected = bssid.trim().equals(bssidValue, ignoreCase = true),
                                enabled = !running,
                                onClick = {
                                    onBssidChange(bssidValue)
                                    onChannelChange(channelValue)
                                },
                                modifier = Modifier.padding(vertical = 3.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = client,
                        onValueChange = onClientChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !running,
                        label = { Text("Client MAC") },
                        isError = !validClient,
                        singleLine = true,
                    )

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Channel: $channel (auto from selected target)",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = NrOnSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    NumberStepper(
                        label = "Count",
                        valueText = if (count <= 0) "default" else count.toString(),
                        enabled = !running,
                        onDecrease = { onCountChange(count - 5) },
                        onIncrease = { onCountChange(count + 5) },
                    )
                    NumberStepper(
                        label = "Duration",
                        valueText = if (duration <= 0) "default" else "${duration}s",
                        enabled = !running,
                        onDecrease = { onDurationChange(duration - 5) },
                        onIncrease = { onDurationChange(duration + 5) },
                    )
                    NumberStepper(
                        label = "Interval",
                        valueText = "$intervalMs ms",
                        enabled = !running,
                        onDecrease = { onIntervalChange(intervalMs - 10) },
                        onIncrease = { onIntervalChange(intervalMs + 10) },
                    )

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Count 0 uses the firmware default. Duration is passed through for parity with the Termux CLI.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )

                    if (!connected) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Connect a device from the Device tab before sending deauth frames.",
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
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Decrease $label",
            )
        }
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        IconButton(onClick = onIncrease, enabled = enabled) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Increase $label",
            )
        }
    }
}

@Composable
private fun ResultZone(
    connected: Boolean,
    running: Boolean,
    sentFrames: Int,
    targetBssid: String,
    activeChannel: Int,
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
                text = "Deauth status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            StatusIndicator(
                label = when {
                    !connected -> "No device connected"
                    running -> "Sending burst"
                    sentFrames > 0 -> "Completed"
                    else -> "Ready"
                },
                color = when {
                    !connected -> StatusNeutral
                    running -> StatusAmber
                    sentFrames > 0 -> StatusGreen
                    else -> StatusNeutral
                },
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Frames sent",
                style = MaterialTheme.typography.labelLarge,
                color = NrOnSurfaceVariant,
            )
            Text(
                text = sentFrames.toString(),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.Bold,
            )

            if (targetBssid.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Target: $targetBssid",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
            }
            if (activeChannel > 0) {
                Text(
                    text = "Channel: $activeChannel",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
            }

            if (!connected) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Connect an ESP32 to send a deauth burst.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }
    }
}
