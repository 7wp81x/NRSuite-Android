package com.swp81x.nrsuite.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

private val MAC_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

@Composable
fun PortalScreen(
    connected: Boolean,
    running: Boolean,
    htmlSize: Int,
    htmlComplete: Boolean,
    activeSsid: String,
    activeChannel: Int,
    portalViews: Int,
    portalClients: Int,
    capturedData: Int,
    selectedHtmlName: String?,
    htmlUploading: Boolean,
    htmlProgress: Int,
    eventLog: List<String>,
    onChooseHtml: () -> Unit,
    onClearHtml: () -> Unit,
    onClearEventLog: () -> Unit,
    onStart: (ssid: String, channel: Int, targetBssid: String) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var configExpanded by remember { mutableStateOf(true) }
    var ssid by remember { mutableStateOf("Free WiFi") }
    var channel by remember { mutableStateOf(6) }
    var targetBssid by remember { mutableStateOf("") }
    var confirmStart by remember { mutableStateOf(false) }

    val validTargetBssid = targetBssid.isBlank() || MAC_REGEX.matches(targetBssid.trim())

    var selectedTab by remember { mutableIntStateOf(0) }
    val clipboardManager = LocalClipboardManager.current

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Overview") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Logs (${eventLog.size})") },
                )
            }

            Spacer(Modifier.height(10.dp))

            if (selectedTab == 0) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                ConfigZone(
                    connected = connected,
                    running = running,
                    expanded = configExpanded,
                    ssid = ssid,
                    channel = channel,
                    targetBssid = targetBssid,
                    validTargetBssid = validTargetBssid,
                    selectedHtmlName = selectedHtmlName,
                    htmlUploading = htmlUploading,
                    htmlProgress = htmlProgress,
                    onToggle = { configExpanded = !configExpanded },
                    onSsidChange = { ssid = it },
                    onChannelChange = { channel = it.coerceIn(1, 13) },
                    onTargetBssidChange = { targetBssid = it },
                    onChooseHtml = onChooseHtml,
                    onClearHtml = onClearHtml,
                )

                Spacer(Modifier.height(10.dp))

                ResultZone(
                    connected = connected,
                    running = running,
                    htmlSize = htmlSize,
                    htmlComplete = htmlComplete,
                    activeSsid = activeSsid,
                    activeChannel = activeChannel,
                    portalViews = portalViews,
                    portalClients = portalClients,
                    capturedData = capturedData,
                )
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.fillMaxSize().padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Portal event log",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(eventLog.joinToString("\n")))
                                },
                                enabled = eventLog.isNotEmpty(),
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy logs")
                            }
                            IconButton(
                                onClick = onClearEventLog,
                                enabled = eventLog.isNotEmpty(),
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear logs")
                            }
                        }
                        if (eventLog.isEmpty()) {
                            Text(
                                text = "No portal events yet. Page views, client associations, and POST data will appear here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NrOnSurfaceVariant,
                            )
                        } else {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                eventLog.reversed().forEach { line ->
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = NrOnSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (connected) {
            FloatingActionButton(
                onClick = {
                    if (running) {
                        onStop()
                    } else if (validTargetBssid) {
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
                    contentDescription = if (running) "Stop portal" else "Start portal",
                )
            }
        }
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("Start captive portal?") },
            text = {
                Text(
                    "SSID: $ssid\\n" +
                        "Channel: $channel\\n" +
                        if (targetBssid.isBlank()) {
                            "No auto-EAPOL target selected."
                        } else {
                            "Auto-EAPOL/deauth target: $targetBssid"
                        } +
                        "\\n\\nOnly deploy this on networks and devices you own or are authorized to test."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmStart = false
                        onStart(ssid.trim(), channel, targetBssid.trim())
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
private fun ConfigZone(
    connected: Boolean,
    running: Boolean,
    expanded: Boolean,
    ssid: String,
    channel: Int,
    targetBssid: String,
    validTargetBssid: Boolean,
    selectedHtmlName: String?,
    htmlUploading: Boolean,
    htmlProgress: Int,
    onToggle: () -> Unit,
    onSsidChange: (String) -> Unit,
    onChannelChange: (Int) -> Unit,
    onTargetBssidChange: (String) -> Unit,
    onChooseHtml: () -> Unit,
    onClearHtml: () -> Unit,
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
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = NrAccent,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Portal configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (running) "Portal running" else "Soft AP + DNS + HTTP server",
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
                        value = ssid,
                        onValueChange = onSsidChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !running,
                        label = { Text("SSID") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    NumberStepper(
                        label = "Channel",
                        valueText = channel.toString(),
                        enabled = !running,
                        onDecrease = { onChannelChange(channel - 1) },
                        onIncrease = { onChannelChange(channel + 1) },
                    )

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Custom HTML",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = selectedHtmlName ?: "No file selected (device placeholder page)",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = NrOnSurfaceVariant,
                    )
                    if (htmlUploading || htmlProgress > 0) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { htmlProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (htmlUploading) "Uploading HTML... $htmlProgress%" else "HTML upload complete.",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = NrOnSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onChooseHtml, enabled = !running) {
                            Text("Choose HTML")
                        }
                        OutlinedButton(onClick = onClearHtml, enabled = !running && selectedHtmlName != null) {
                            Text("Clear")
                        }
                    }

                    if (!connected) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Connect a device from the Device tab before starting the portal.",
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
private fun ResultZone(
    connected: Boolean,
    running: Boolean,
    htmlSize: Int,
    htmlComplete: Boolean,
    activeSsid: String,
    activeChannel: Int,
    portalViews: Int,
    portalClients: Int,
    capturedData: Int,
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
                text = "Portal status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            StatusIndicator(
                label = when {
                    !connected -> "No device connected"
                    running -> "Portal active"
                    activeSsid.isNotBlank() -> "Stopped"
                    else -> "Ready"
                },
                color = when {
                    !connected -> StatusNeutral
                    running -> StatusGreen
                    activeSsid.isNotBlank() -> StatusAmber
                    else -> StatusNeutral
                },
            )

            if (activeSsid.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "SSID: $activeSsid",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
                if (activeChannel > 0) {
                    Text(
                        text = "Channel: $activeChannel",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = NrOnSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            MetricRow("HTML size", "$htmlSize bytes")
            MetricRow("HTML complete", if (htmlComplete) "yes" else "no")
            MetricRow("Page views", portalViews.toString())
            MetricRow("Clients associated", portalClients.toString())
            MetricRow("Captured form posts", capturedData.toString())

            if (!connected) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Connect an ESP32 to start the captive portal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else if (!running) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Configure the AP, optionally choose an HTML file, then tap start.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = NrOnSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Medium,
        )
    }
}
