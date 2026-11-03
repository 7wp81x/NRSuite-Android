package com.swp81x.nrsuite.ui

import com.swp81x.nrsuite.ui.components.NrAnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import org.json.JSONObject
import com.swp81x.nrsuite.core.wifi.NetworkTarget
import com.swp81x.nrsuite.core.wifi.WIFI_AUTO_CHANNELS
import com.swp81x.nrsuite.core.wifi.normalizeWifiChannel
import com.swp81x.nrsuite.core.wifi.normalizeWifiChannelForAllowed
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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.swp81x.nrsuite.ui.components.ScanButtonContent
import com.swp81x.nrsuite.ui.components.ScanTargetList
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.components.WifiChannelStepper
import com.swp81x.nrsuite.ui.components.WifiChannelSummaryPill
import com.swp81x.nrsuite.ui.components.NrStatusAction
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusInfo
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed

private val MAC_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

@Composable
fun DeauthScreen(
    connected: Boolean,
    running: Boolean,
    sentFrames: Int,
    failedFrames: Int,
    elapsedMs: Long,
    pps: Double,
    targetBssid: String,
    activeChannel: Int,
    scanning: Boolean,
    networks: List<JSONObject>,
    allowedChannels: List<Int> = emptyList(),
    onScanWifi: () -> Unit,
    onStart: (targets: List<NetworkTarget>, client: String, count: Int, intervalMs: Int, loopMode: Boolean) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var configExpanded by remember { mutableStateOf(true) }
    var bssid by remember { mutableStateOf("") }
    var client by remember { mutableStateOf("FF:FF:FF:FF:FF:FF") }
    var channel by remember { mutableStateOf(6) }
    var loopMode by remember { mutableStateOf(false) }
    var count by remember { mutableStateOf(20) }
    var intervalMs by remember { mutableStateOf(100) }
    var confirmStart by remember { mutableStateOf(false) }
    var selectedTargets by remember { mutableStateOf<Map<String, NetworkTarget>>(emptyMap()) }
    val context = LocalContext.current
    val effectiveAllowedChannels = allowedChannels.ifEmpty { WIFI_AUTO_CHANNELS }

    LaunchedEffect(effectiveAllowedChannels) {
        channel = normalizeWifiChannelForAllowed(channel, effectiveAllowedChannels)
    }

    LaunchedEffect(running) {
        if (running) configExpanded = false
    }

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
                loopMode = loopMode,
                count = count,
                intervalMs = intervalMs,
                validClient = validClient,
                scanning = scanning,
                sentFrames = sentFrames,
                networks = networks,
                allowedChannels = allowedChannels,
                onScanWifi = onScanWifi,
                onToggle = { configExpanded = !configExpanded },
                onBssidChange = { bssid = it },
                onClientChange = { client = it },
                onChannelChange = { channel = normalizeWifiChannel(it) },
                onLoopModeChange = { loopMode = it },
                onCountChange = { count = it.coerceIn(1, 2_000) },
                onIntervalChange = { intervalMs = it.coerceIn(10, 10_000) },
                selectedBssids = selectedTargets.keys,
                onToggleTarget = { network ->
                    val bssidValue = network.optString("bssid")
                    val target = NetworkTarget(
                        ssid = network.optString("ssid").ifBlank { "(hidden)" },
                        bssid = bssidValue,
                        channel = network.optInt("channel", 1),
                        rssi = network.optInt("rssi", -100),
                        security = network.optString("security", "?"),
                        wps = network.optBoolean("wps"),
                        vendor = network.optString("vendor").takeIf { it.isNotBlank() },
                        ouiWhitelisted = network.optBoolean("oui_whitelisted"),
                        ouiBlacklisted = network.optBoolean("oui_blacklisted"),
                    )
                    selectedTargets = if (selectedTargets.containsKey(bssidValue)) {
                        selectedTargets - bssidValue
                    } else {
                        selectedTargets + (bssidValue to target)
                    }
                    bssid = bssidValue
                    channel = target.channel
                },
                onClearTargets = {
                    selectedTargets = emptyMap()
                    bssid = ""
                },
            )

            if (running || sentFrames > 0 || failedFrames > 0 || elapsedMs > 0) {
                Spacer(Modifier.height(10.dp))
                ResultZone(
                    connected = connected,
                    running = running,
                    sentFrames = sentFrames,
                    failedFrames = failedFrames,
                    elapsedMs = elapsedMs,
                    pps = pps,
                    targetBssid = targetBssid,
                    activeChannel = activeChannel,
                )
            }
        }

        if (connected) {
            ModuleActionFab(
                onClick = {
                    when {
                        running -> onStop()
                        selectedTargets.isEmpty() -> {
                            configExpanded = true
                            Toast.makeText(
                                context,
                                "Scan and select at least one target first.",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
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
                statusAction = if (running) NrStatusAction.STOP else NrStatusAction.START,
            ) {
                Icon(
                    imageVector = if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (running) "Stop deauth" else "Send deauth",
                )
            }
        }
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("Send deauth frames?") },
            text = {
                val targets = selectedTargets.values.toList()
                val channels = targets.map { it.channel }.distinct().sorted()
                val modeDescription = if (loopMode) {
                    if (targets.size > 1) {
                        "Mode: Channel-hopping loop — $count frames/target, until stopped\n"
                    } else {
                        "Mode: Loop — until stopped\n"
                    }
                } else {
                    "Mode: Burst — $count frame(s) per target\n"
                }
                Text(
                    "Targets: ${targets.size}\n" +
                        targets.take(5).joinToString("\n") { "• ${it.ssid.ifBlank { "(hidden)" }} (${it.bssid})" } +
                        (if (targets.size > 5) "\n• +${targets.size - 5} more" else "") +
                        "\nChannels: ${channels.joinToString(", ")}\n" +
                        "Client: $client\n" +
                        modeDescription +
                        "Interval: $intervalMs ms\n\n" +
                        "This disrupts connectivity for affected clients. Only use this on networks you own or are authorized to test."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmStart = false
                        onStart(
                            selectedTargets.values.toList(),
                            client.trim(),
                            count,
                            intervalMs,
                            loopMode,
                        )
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
    loopMode: Boolean,
    count: Int,
    intervalMs: Int,
    validClient: Boolean,
    scanning: Boolean,
    sentFrames: Int,
    networks: List<JSONObject>,
    allowedChannels: List<Int>,
    onScanWifi: () -> Unit,
    onToggle: () -> Unit,
    onBssidChange: (String) -> Unit,
    onClientChange: (String) -> Unit,
    onChannelChange: (Int) -> Unit,
    onLoopModeChange: (Boolean) -> Unit,
    onCountChange: (Int) -> Unit,
    onIntervalChange: (Int) -> Unit,
    selectedBssids: Set<String>,
    onToggleTarget: (JSONObject) -> Unit,
    onClearTargets: () -> Unit,
) {
    val selectedTargetChannels = networks
        .filter { it.optString("bssid") in selectedBssids }
        .map { normalizeWifiChannel(it.optInt("channel", 1)) }
        .distinct()
        .sorted()
    val multiTargetSelected = selectedBssids.size > 1

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
                    tint = MaterialTheme.colorScheme.primary,
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
                            running -> "Deauth running"
                            sentFrames > 0 -> "$sentFrames frame(s) sent"
                            else -> "Select one or more AP targets"
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

            NrAnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onScanWifi,
                        enabled = connected && !scanning && !running,
                    ) {
                        ScanButtonContent(
                            scanning = scanning,
                            idleLabel = "Scan WiFi for targets",
                        )
                    }

                    if (networks.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${selectedBssids.size} selected",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (selectedBssids.isNotEmpty() && !running) {
                                TextButton(onClick = onClearTargets) {
                                    Text("Clear")
                                }
                            }
                        }
                        ScanTargetList(
                            networks = networks,
                            selectedBssids = selectedBssids,
                            onToggle = onToggleTarget,
                            enabled = !running,
                            multiSelect = true,
                        )
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (multiTargetSelected) "Channel schedule" else "Manual channel",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (multiTargetSelected) {
                            Text(
                                text = if (selectedTargetChannels.isEmpty()) {
                                    "AUTO"
                                } else {
                                    "AUTO · ${selectedTargetChannels.joinToString(", ")}"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            WifiChannelSummaryPill(channel)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (multiTargetSelected) {
                        Text(
                            text = "Manual channel is locked while multiple targets are selected. " +
                                "Each target uses its scanned channel and the firmware or fallback " +
                                "scheduler hops between them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    } else {
                        WifiChannelStepper(
                            value = channel,
                            enabled = !running,
                            onChannelChange = onChannelChange,
                            allowedChannels = allowedChannels.ifEmpty {
                                com.swp81x.nrsuite.core.wifi.WIFI_AUTO_CHANNELS
                            },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Mode", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NrFilterChip(
                            selected = !loopMode,
                            onClick = { onLoopModeChange(false) },
                            label = "Burst",
                            enabled = !running,
                        )
                        NrFilterChip(
                            selected = loopMode,
                            onClick = { onLoopModeChange(true) },
                            label = "Loop",
                            enabled = !running,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (!loopMode || selectedBssids.size > 1) {
                        NumberStepper(
                            label = if (loopMode) "Frames / target" else "Frame count",
                            valueText = count.toString(),
                            enabled = !running,
                            onDecrease = { onCountChange(count - 5) },
                            onIncrease = { onCountChange(count + 5) },
                        )
                    }
                    NumberStepper(
                        label = "Interval",
                        valueText = "$intervalMs ms",
                        enabled = !running,
                        onDecrease = { onIntervalChange(intervalMs - 10) },
                        onIncrease = { onIntervalChange(intervalMs + 10) },
                    )

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when {
                            loopMode && selectedBssids.size > 1 ->
                                "Channel-hopping loop sends one burst to each selected target, " +
                                    "then repeats until you press Stop."
                            loopMode ->
                                "Loop mode repeats deauth frames until you press Stop."
                            else ->
                                "Burst mode sends the selected frame count to each target once."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )

                    if (!connected) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Connect a device before starting deauth.",
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
    failedFrames: Int,
    elapsedMs: Long,
    pps: Double,
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
                    running -> "Deauth running"
                    sentFrames > 0 || failedFrames > 0 -> "Stopped"
                    else -> "Idle"
                },
                color = when {
                    !connected -> StatusNeutral
                    running -> StatusGreen
                    sentFrames > 0 || failedFrames > 0 -> StatusInfo
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

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column {
                    Text(
                        text = "Rate",
                        style = MaterialTheme.typography.labelSmall,
                        color = NrOnSurfaceVariant,
                    )
                    Text(
                        text = "%.1f pps".format(pps),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Column {
                    Text(
                        text = "Elapsed",
                        style = MaterialTheme.typography.labelSmall,
                        color = NrOnSurfaceVariant,
                    )
                    Text(
                        text = formatElapsed(elapsedMs),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Column {
                    Text(
                        text = "Failed",
                        style = MaterialTheme.typography.labelSmall,
                        color = NrOnSurfaceVariant,
                    )
                    Text(
                        text = failedFrames.toString(),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

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
                    text = "Connect a device before starting deauth.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }
    }
}


private fun formatElapsed(elapsedMs: Long): String {
    val totalSeconds = (elapsedMs / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes > 0) "%d:%02d".format(minutes, seconds) else "${seconds}s"
}
