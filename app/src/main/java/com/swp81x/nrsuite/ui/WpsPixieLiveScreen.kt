package com.swp81x.nrsuite.ui

import com.swp81x.nrsuite.ui.components.NrAnimatedVisibility
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.MainViewModel
import com.swp81x.nrsuite.WpsPixieLiveLogEntry
import com.swp81x.nrsuite.WpsPixieLiveUiState
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.wifi.NetworkTarget
import com.swp81x.nrsuite.ui.components.NetworkTargetRow
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.components.RoundedTabRow
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.NrOnSurface
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed

@Composable
fun WpsPixieLiveRoute(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.wpsPixieLive.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    WpsPixieLiveScreen(
        connected = connectionState is ConnectionState.Connected,
        state = state,
        onScan = viewModel::scanWpsTargets,
        onWpsOnlyChange = viewModel::setWpsOnlyFilter,
        onSelectTarget = viewModel::selectWpsTarget,
        onStart = { viewModel.startWpsPixieLiveAttempt() },
        onStop = viewModel::stopWpsPixieLiveAttempt,
        onClearLogs = viewModel::clearWpsPixieLiveLog,
        modifier = modifier,
    )
}

@Composable
fun WpsPixieLiveScreen(
    connected: Boolean,
    state: WpsPixieLiveUiState,
    onScan: () -> Unit,
    onWpsOnlyChange: (Boolean) -> Unit,
    onSelectTarget: (NetworkTarget) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val displayedTargets = state.targets.filter { !state.wpsOnly || it.wps }
    val selected = state.selectedTarget
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var configExpanded by rememberSaveable { mutableStateOf(true) }
    var confirmStart by remember { mutableStateOf(false) }
    val logListState = rememberLazyListState()

    LaunchedEffect(state.running) {
        if (state.running) configExpanded = false
    }
    LaunchedEffect(state.progressLog.size, selectedTab) {
        if (selectedTab == 1 && state.progressLog.isNotEmpty()) {
            logListState.animateScrollToItem(state.progressLog.lastIndex)
        }
    }

    val statusLabel = when {
        !connected -> "No device connected"
        state.locked -> "AP may be WPS locked"
        state.psk != null -> "PSK recovered"
        state.running && state.stage == "cracking" -> "Cracking PIN"
        state.running && state.stage == "retry-countdown" -> "Automatic retry countdown"
        state.running && state.stage == "continuing" -> "Continuing active WPS session"
        state.running && state.stage == "retrying" -> "Retrying with recovered PIN"
        state.running -> "WPS attempt running"
        state.recoveredPin != null -> "PIN recovered"
        state.error != null -> "Attempt failed"
        selected != null -> "Target selected"
        else -> "Select a WPS target"
    }
    val statusColor = when {
        !connected -> StatusNeutral
        state.locked || state.error != null -> StatusRed
        state.psk != null -> StatusGreen
        state.running -> StatusGreen
        selected != null -> StatusGreen
        else -> StatusNeutral
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RoundedTabRow(
                selectedIndex = selectedTab,
                tabs = listOf("Overview", "Logs (${state.progressLog.size})"),
                onSelect = { selectedTab = it },
            )

            if (selectedTab == 0) {
                TargetConfigCard(
                    connected = connected,
                    running = state.running,
                    scanning = state.scanning,
                    expanded = configExpanded,
                    wpsOnly = state.wpsOnly,
                    targets = displayedTargets,
                    selected = selected,
                    onToggleExpanded = { configExpanded = !configExpanded },
                    onScan = onScan,
                    onWpsOnlyChange = onWpsOnlyChange,
                    onSelectTarget = onSelectTarget,
                )

                if (state.running || state.psk != null || state.recoveredPin != null ||
                    state.error != null || state.locked || state.material != null
                ) {
                    ResultCard(
                        connected = connected,
                        state = state,
                        statusLabel = statusLabel,
                        statusColor = statusColor,
                        onCopy = { clipboard.setText(AnnotatedString(it)) },
                    )
                }
            } else {
                LogsCard(
                    logs = state.progressLog,
                    running = state.running,
                    listState = logListState,
                    onCopy = { clipboard.setText(AnnotatedString(it)) },
                    onClear = onClearLogs,
                )
            }
        }

        if (connected) {
            ModuleActionFab(
                onClick = {
                    when {
                        state.running -> onStop()
                        selected == null -> Toast.makeText(
                            context,
                            "Select a WPS target first.",
                            Toast.LENGTH_SHORT,
                        ).show()
                        state.locked -> Toast.makeText(
                            context,
                            "AP may be WPS locked. Wait before retrying.",
                            Toast.LENGTH_SHORT,
                        ).show()
                        else -> confirmStart = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = if (state.running) StatusRed else MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = if (state.running) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (state.running) "Stop WPS attempt" else "Run WPS attempt",
                )
            }
        }
    }

    if (confirmStart && selected != null) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("Run WPS Pixie Dust attempt?") },
            text = {
                Text(
                    "Target: ${selected.ssid.ifBlank { "(hidden)" }}\n" +
                        "BSSID: ${selected.bssid}\n" +
                        "Channel: ${selected.channel}\n" +
                        "Candidate PIN: ${state.candidatePin}\n" +
                        "PIN source: ${state.candidateSource?.name?.lowercase() ?: "unknown"}\n\n" +
                        if (state.candidateSource == com.swp81x.nrsuite.core.wps.WpsPinSource.GENERATED) {
                            "An unverified PIN will capture the AP's M3 response without sending M4. " +
                                "The app will crack the PIN offline and continue M4-M8 on the same " +
                                "active WPS session."
                        } else {
                            "The selected PIN will be verified through the full M4-M8 exchange. " +
                                "A wrong PIN or AP lockout can trigger a WPS cool-down."
                        },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmStart = false
                        onStart()
                    },
                ) {
                    Text("Run")
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
private fun TargetConfigCard(
    connected: Boolean,
    running: Boolean,
    scanning: Boolean,
    expanded: Boolean,
    wpsOnly: Boolean,
    targets: List<NetworkTarget>,
    selected: NetworkTarget?,
    onToggleExpanded: () -> Unit,
    onScan: () -> Unit,
    onWpsOnlyChange: (Boolean) -> Unit,
    onSelectTarget: (NetworkTarget) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.WifiFind,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "WPS Pixie Dust",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NrOnSurface,
                    )
                    Text(
                        text = selected?.let {
                            "${it.ssid.ifBlank { "(hidden)" }} · ${it.bssid} · ch ${it.channel}"
                        } ?: "Select a WPS-enabled target",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = NrOnSurfaceVariant,
                    )
                }
                IconButton(onClick = onToggleExpanded, enabled = !running) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse target selection" else "Expand target selection",
                        tint = NrOnSurfaceVariant,
                    )
                }
            }

            NrAnimatedVisibility(visible = expanded) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onScan, enabled = connected && !scanning && !running) {
                        if (scanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(if (scanning) "Scanning..." else "Scan WPS targets")
                    }
                    NrFilterChip(
                        selected = wpsOnly,
                        onClick = { onWpsOnlyChange(!wpsOnly) },
                        label = "WPS only",
                        enabled = !running,
                    )
                }

                if (targets.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(targets, key = { it.bssid }) { target ->
                            NetworkTargetRow(
                                ssid = target.ssid,
                                bssid = target.bssid,
                                channel = target.channel,
                                rssi = target.rssi,
                                security = target.security,
                                selected = selected?.bssid?.equals(target.bssid, ignoreCase = true) == true,
                                wps = target.wps,
                                wpsColor = StatusGreen,
                                wpsLockState = target.wpsLockState,
                                vendor = target.vendor,
                                ouiWhitelisted = target.ouiWhitelisted,
                                ouiBlacklisted = target.ouiBlacklisted,
                                enabled = !running && target.wps,
                                onClick = { onSelectTarget(target) },
                                modifier = Modifier.padding(vertical = 3.dp),
                            )
                        }
                    }
                } else if (!scanning) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "No matching networks yet. Run a scan to populate this list.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    connected: Boolean,
    state: WpsPixieLiveUiState,
    statusLabel: String,
    statusColor: androidx.compose.ui.graphics.Color,
    onCopy: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "WPS Pixie status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = NrOnSurface,
            )
            StatusIndicator(label = statusLabel, color = statusColor)
            state.selectedTarget?.let { target ->
                InfoRow("Target", target.ssid.ifBlank { "(hidden)" })
                InfoRow("BSSID", target.bssid)
                InfoRow("Channel", target.channel.toString())
            }
            if (state.stage.isNotBlank()) InfoRow("Stage", state.stage)
            if (state.candidatePin.isNotBlank()) {
                InfoRow("Candidate PIN", state.candidatePin)
                InfoRow("PIN source", state.candidateSource?.name?.lowercase() ?: "unknown")
            }
            state.firstHalfValid?.let {
                InfoRow("First PIN half", if (it) "valid" else "not confirmed")
            }
            if (state.message.isNotBlank()) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.error != null || state.locked) StatusRed else NrOnSurfaceVariant,
                )
            }

            state.recoveredPin?.let { pin ->
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Recovered PIN",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        color = NrOnSurfaceVariant,
                    )
                    IconButton(onClick = { onCopy(pin) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy PIN", tint = StatusGreen)
                    }
                }
                Text(
                    text = pin,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = StatusGreen,
                )
            }

            state.psk?.let { psk ->
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "WPA PSK",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        color = NrOnSurfaceVariant,
                    )
                    IconButton(onClick = { onCopy(psk) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy PSK", tint = StatusGreen)
                    }
                }
                Text(
                    text = psk,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = StatusGreen,
                )
                Text(
                    text = "Saved to Credential Manager.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }

            if (!connected) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Connect a device before starting WPS Pixie Dust.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
            if (state.locked) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "The AP may be WPS locked. Wait 5–10 minutes and do not auto-retry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusAmber,
                )
            }
            if (state.error != null &&
                state.error != state.message &&
                !state.locked
            ) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusRed,
                )
            }
        }
    }
}

@Composable
private fun LogsCard(
    logs: List<WpsPixieLiveLogEntry>,
    running: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onCopy: (String) -> Unit,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "WPS Pixie logs (${logs.size})",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    color = NrOnSurface,
                )
                IconButton(
                    onClick = {
                        if (logs.isNotEmpty()) {
                            onCopy(logs.joinToString("\n") { "${it.time} ${it.message}" })
                        }
                    },
                    enabled = logs.isNotEmpty(),
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy logs")
                }
                IconButton(onClick = onClear, enabled = logs.isNotEmpty() && !running) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear logs")
                }
            }
            if (logs.isEmpty()) {
                Text(
                    text = "Scan, target selection, M1–M8 stages, PIN cracking, retries, and PSK recovery will appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    items(logs) { entry ->
                        ProgressLogRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressLogRow(entry: WpsPixieLiveLogEntry) {
    Row {
        Text(
            text = entry.time,
            modifier = Modifier.width(64.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = NrOnSurfaceVariant,
        )
        Text(
            text = entry.message,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = if (entry.message.startsWith("ERROR")) StatusRed else NrOnSurface,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(
            text = label,
            modifier = Modifier.width(112.dp),
            style = MaterialTheme.typography.labelMedium,
            color = NrOnSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = NrOnSurface,
        )
    }
}
