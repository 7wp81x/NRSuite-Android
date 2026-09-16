package com.swp81x.nrsuite.ui

import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed

@Composable
fun BeaconScreen(
    connected: Boolean,
    running: Boolean,
    sentFrames: Int,
    ssidCount: Int,
    activeChannel: Int,
    savedLists: Map<String, List<String>>,
    onSaveListAction: (String, List<String>) -> Unit,
    onDeleteList: (String) -> Unit,
    onStart: (List<String>, Int, Int, Boolean, Boolean) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var configExpanded by remember { mutableStateOf(true) }
    var ssidsText by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf(6) }
    var intervalMs by remember { mutableStateOf(20) }
    var hidden by remember { mutableStateOf(false) }
    var randomBssid by remember { mutableStateOf(true) }
    var confirmStart by remember { mutableStateOf(false) }
    var listMenuExpanded by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }
    val context = LocalContext.current
    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    ssidsText = stream.readBytes().toString(Charsets.UTF_8)
                }
            }.onFailure {
                // Reading failure is reported through the global log in the app shell.
            }
        }
    }

    val parsedSsids = remember(ssidsText) {
        ssidsText.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            ConfigZone(
                connected = connected,
                running = running,
                expanded = configExpanded,
                ssidsText = ssidsText,
                channel = channel,
                intervalMs = intervalMs,
                hidden = hidden,
                randomBssid = randomBssid,
                parsedCount = parsedSsids.size,
                savedLists = savedLists,
                listMenuExpanded = listMenuExpanded,
                onToggle = { configExpanded = !configExpanded },
                onListMenuChange = { listMenuExpanded = it },
                onLoadList = { ssidsText = it.joinToString("\n") },
                onSaveList = { showSaveDialog = true },
                onImportFile = { importPicker.launch(arrayOf("text/plain", "*/*")) },
                onSsidsChange = { ssidsText = it },
                onChannelChange = { channel = it.coerceIn(1, 13) },
                onIntervalChange = { intervalMs = it.coerceIn(10, 2000) },
                onHiddenChange = { hidden = it },
                onRandomBssidChange = { randomBssid = it },
            )

            Spacer(Modifier.height(10.dp))

            ResultZone(
                connected = connected,
                running = running,
                sentFrames = sentFrames,
                ssidCount = ssidCount,
                activeChannel = activeChannel,
                modifier = Modifier.weight(1f),
            )
        }

        if (connected) {
            FloatingActionButton(
                onClick = {
                    if (running) {
                        onStop()
                    } else {
                        confirmStart = true
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
                    contentDescription = if (running) "Stop beacon broadcast" else "Start beacon broadcast",
                )
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save SSID list") },
            text = {
                OutlinedTextField(
                    value = newListName,
                    onValueChange = { newListName = it },
                    label = { Text("List name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveListAction(newListName, parsedSsids)
                        newListName = ""
                        showSaveDialog = false
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("Start beacon broadcast?") },
            text = {
                Text(
                    "This will broadcast ${parsedSsids.size} SSID(s) on channel $channel until stopped. " +
                        "Only use this on spectrum you own or are authorized to test."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmStart = false
                        onStart(parsedSsids, channel, intervalMs, hidden, randomBssid)
                    },
                    enabled = parsedSsids.isNotEmpty(),
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
private fun ConfigZone(
    connected: Boolean,
    running: Boolean,
    expanded: Boolean,
    ssidsText: String,
    channel: Int,
    intervalMs: Int,
    hidden: Boolean,
    randomBssid: Boolean,
    parsedCount: Int,
    savedLists: Map<String, List<String>>,
    listMenuExpanded: Boolean,
    onToggle: () -> Unit,
    onListMenuChange: (Boolean) -> Unit,
    onLoadList: (List<String>) -> Unit,
    onSaveList: () -> Unit,
    onImportFile: () -> Unit,
    onSsidsChange: (String) -> Unit,
    onChannelChange: (Int) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onHiddenChange: (Boolean) -> Unit,
    onRandomBssidChange: (Boolean) -> Unit,
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
                    imageVector = Icons.Default.Campaign,
                    contentDescription = null,
                    tint = NrAccent,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Beacon configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (running) "Broadcast running" else "Custom SSIDs, fixed channel",
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
                    OutlinedTextField(
                        value = ssidsText,
                        onValueChange = onSsidsChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !running,
                        label = { Text("SSIDs (one per line)") },
                        placeholder = { Text("Free WiFi\\nCoffeeShop") },
                        minLines = 3,
                        maxLines = 6,
                        supportingText = {
                            Text("$parsedCount/32 SSIDs will be broadcast.")
                        },
                    )

                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box {
                            OutlinedButton(
                                onClick = { onListMenuChange(true) },
                                enabled = !running && savedLists.isNotEmpty(),
                            ) {
                                Text("Load list")
                            }
                            DropdownMenu(
                                expanded = listMenuExpanded,
                                onDismissRequest = { onListMenuChange(false) },
                            ) {
                                savedLists.keys.sorted().forEach { name ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            onLoadList(savedLists[name].orEmpty())
                                            onListMenuChange(false)
                                        },
                                    )
                                }
                            }
                        }
                        OutlinedButton(onClick = onSaveList, enabled = !running && parsedCount > 0) {
                            Text("Save list")
                        }
                        OutlinedButton(onClick = onImportFile, enabled = !running) {
                            Text("Import file")
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    NumberStepper(
                        label = "Channel",
                        value = channel,
                        valueText = channel.toString(),
                        enabled = !running,
                        onDecrease = { onChannelChange(channel - 1) },
                        onIncrease = { onChannelChange(channel + 1) },
                    )

                    Spacer(Modifier.height(6.dp))
                    NumberStepper(
                        label = "Interval",
                        value = intervalMs,
                        valueText = "$intervalMs ms",
                        enabled = !running,
                        onDecrease = { onIntervalChange(intervalMs - 10) },
                        onIncrease = { onIntervalChange(intervalMs + 10) },
                    )

                    Spacer(Modifier.height(10.dp))
                    SwitchRow(
                        label = "Hidden SSIDs",
                        checked = hidden,
                        enabled = !running,
                        onCheckedChange = onHiddenChange,
                    )
                    SwitchRow(
                        label = "Stable BSSIDs",
                        checked = !randomBssid,
                        enabled = !running,
                        onCheckedChange = { onRandomBssidChange(!it) },
                    )

                    if (!connected) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Connect a device from the Device tab before starting a broadcast.",
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
    value: Int,
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
            Text("-", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        IconButton(onClick = onIncrease, enabled = enabled) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun ResultZone(
    connected: Boolean,
    running: Boolean,
    sentFrames: Int,
    ssidCount: Int,
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
                text = "Broadcast status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            StatusIndicator(
                label = when {
                    !connected -> "No device connected"
                    running -> "Broadcasting"
                    sentFrames > 0 -> "Stopped"
                    else -> "Ready"
                },
                color = when {
                    !connected -> StatusNeutral
                    running -> StatusGreen
                    sentFrames > 0 -> StatusAmber
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

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Active SSIDs",
                style = MaterialTheme.typography.labelLarge,
                color = NrOnSurfaceVariant,
            )
            Text(
                text = ssidCount.toString(),
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
            )

            if (activeChannel > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Channel: $activeChannel",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
            }

            if (!connected) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Connect an ESP32 to configure and start beacon broadcast.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else if (!running && sentFrames == 0) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Enter one or more SSIDs, then tap the play button.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }
    }
}
