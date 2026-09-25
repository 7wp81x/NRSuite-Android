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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WifiTethering
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
import com.swp81x.nrsuite.core.defense.AlertConfidence
import com.swp81x.nrsuite.core.defense.DeauthAlert
import com.swp81x.nrsuite.core.defense.DeauthChannelMode
import com.swp81x.nrsuite.core.defense.DeauthFeedEntry
import com.swp81x.nrsuite.core.defense.DeauthFeedFilter
import com.swp81x.nrsuite.core.wifi.NetworkTarget
import com.swp81x.nrsuite.ui.util.rssiToProximity
import com.swp81x.nrsuite.ui.components.NetworkTargetRow
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed

@Composable
fun DeauthDetectorScreen(
    connected: Boolean,
    running: Boolean,
    channelMode: DeauthChannelMode,
    onChannelModeChange: (DeauthChannelMode) -> Unit,
    hopIntervalMs: Int,
    onHopIntervalChange: (Int) -> Unit,
    currentHopChannel: Int? = null,
    framesPerSecond: Int,
    totalFrames: Int,
    uniqueSourceCount: Int,
    thresholdFramesPerSecond: Int,
    activeAlert: DeauthAlert?,
    feed: List<DeauthFeedEntry>,
    feedFilter: DeauthFeedFilter,
    scanResults: List<NetworkTarget>,
    selectedTarget: NetworkTarget?,
    channel: Int,
    onChannelChange: (Int) -> Unit,
    onSelectTarget: (NetworkTarget) -> Unit,
    onScanClick: () -> Unit,
    isScanning: Boolean,
    onFeedFilterChange: (DeauthFeedFilter) -> Unit,
    onClearFeed: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onLocateClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var configExpanded by remember { mutableStateOf(true) }
    var confirmStart by remember { mutableStateOf(false) }

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
            DetectorStatusCard(
                running = running,
                connected = connected,
                alert = activeAlert,
                onLocateClick = onLocateClick,
            )
            Spacer(Modifier.height(10.dp))

            if (running || activeAlert != null) {
                LiveStatsRow(
                    framesPerSecond = framesPerSecond,
                    totalFrames = totalFrames,
                    uniqueSourceCount = uniqueSourceCount,
                    thresholdFramesPerSecond = thresholdFramesPerSecond,
                )
                Spacer(Modifier.height(10.dp))
            }

            ConfigZone(
                connected = connected,
                running = running,
                expanded = configExpanded,
                channelMode = channelMode,
                hopIntervalMs = hopIntervalMs,
                currentHopChannel = currentHopChannel,
                scanResults = scanResults,
                selectedTarget = selectedTarget,
                channel = channel,
                isScanning = isScanning,
                onToggle = { configExpanded = !configExpanded },
                onChannelModeChange = onChannelModeChange,
                onHopIntervalChange = onHopIntervalChange,
                onScanClick = onScanClick,
                onSelectTarget = onSelectTarget,
                onChannelChange = onChannelChange,
            )

            Spacer(Modifier.height(10.dp))

            if (feed.isNotEmpty()) {
                DetectionFeedCard(
                    feed = feed,
                    feedFilter = feedFilter,
                    onFeedFilterChange = onFeedFilterChange,
                    onClearFeed = onClearFeed,
                )
            }

            Spacer(Modifier.height(80.dp))
        }

        val canStart = when (channelMode) {
            DeauthChannelMode.TARGETED -> selectedTarget != null
            DeauthChannelMode.HOPPING -> true
        }
        FloatingActionButton(
            onClick = {
                if (running) {
                    onStop()
                } else if (canStart) {
                    confirmStart = true
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .alpha(if (!connected || (!running && !canStart)) 0.4f else 1f),
            containerColor = if (running) StatusRed else NrAccent,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(
                imageVector = if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
            )
        }
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("Start monitoring?") },
            text = {
                Text(
                    if (channelMode == DeauthChannelMode.HOPPING) {
                        "The ESP32 will switch to monitor mode and hop across all channels " +
                            "every $hopIntervalMs ms."
                    } else {
                        "The ESP32 will switch to monitor mode on channel $channel " +
                            "to watch ${selectedTarget?.ssid ?: "the selected target"}."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onStart()
                        confirmStart = false
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
private fun DetectorStatusCard(
    running: Boolean,
    connected: Boolean,
    alert: DeauthAlert?,
    onLocateClick: () -> Unit,
) {
    val border = when {
        alert != null && alert.confidence == AlertConfidence.HIGH ->
            BorderStroke(1.dp, StatusRed)
        alert != null ->
            BorderStroke(0.5.dp, StatusAmber)
        else ->
            BorderStroke(0.5.dp, NrOutline)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = border,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            when {
                alert != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = StatusRed,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Deauth attack detected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusRed,
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${alert.ssid} · ch ${alert.channel}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = NrOnSurfaceVariant,
                    )

                    if (alert.possiblySpoofed) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = StatusAmber,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Source MAC may be spoofed",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusAmber,
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${rssiToProximity(alert.dominantSourceRssi).label} " +
                            "(${alert.dominantSourceRssi} dBm)",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = NrOnSurfaceVariant,
                    )

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = onLocateClick,
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = NrAccent,
                            ),
                        ) {
                            Text("Locate")
                        }
                        Spacer(Modifier.weight(1f))
                        ConfidenceBadge(alert.confidence)
                    }
                }

                running -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = NrAccent,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "No attack detected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Monitoring for deauth activity",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }

                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (!connected) StatusAmber else NrOnSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "No attack detected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = NrOnSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (!connected) {
                            "Connect a device to begin"
                        } else {
                            "Start monitoring to watch for deauth activity"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfidenceBadge(confidence: AlertConfidence) {
    val (label, color) = when (confidence) {
        AlertConfidence.HIGH -> "High confidence" to StatusRed
        AlertConfidence.MEDIUM -> "Medium confidence" to StatusAmber
        AlertConfidence.LOW -> "Possible false alarm" to StatusNeutral
    }
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun LiveStatsRow(
    framesPerSecond: Int,
    totalFrames: Int,
    uniqueSourceCount: Int,
    thresholdFramesPerSecond: Int,
) {
    val fpsColor = if (framesPerSecond > thresholdFramesPerSecond) StatusRed else NrAccent
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCell(
            label = "Frames/s",
            value = framesPerSecond.toString(),
            valueColor = fpsColor,
            modifier = Modifier.weight(1f),
        )
        StatCell(
            label = "Total",
            value = totalFrames.toString(),
            modifier = Modifier.weight(1f),
        )
        StatCell(
            label = "Sources",
            value = uniqueSourceCount.toString(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor,
            )
        }
    }
}

@Composable
private fun ConfigZone(
    connected: Boolean,
    running: Boolean,
    expanded: Boolean,
    channelMode: DeauthChannelMode,
    hopIntervalMs: Int,
    currentHopChannel: Int?,
    scanResults: List<NetworkTarget>,
    selectedTarget: NetworkTarget?,
    channel: Int,
    isScanning: Boolean,
    onToggle: () -> Unit,
    onChannelModeChange: (DeauthChannelMode) -> Unit,
    onHopIntervalChange: (Int) -> Unit,
    onScanClick: () -> Unit,
    onSelectTarget: (NetworkTarget) -> Unit,
    onChannelChange: (Int) -> Unit,
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
                    imageVector = Icons.Default.WifiTethering,
                    contentDescription = null,
                    tint = NrAccent,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Target & channel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            running && channelMode == DeauthChannelMode.HOPPING ->
                                "Hopping · ch ${currentHopChannel ?: "-"}"
                            running -> "Monitoring ${selectedTarget?.ssid ?: "target"}"
                            channelMode == DeauthChannelMode.TARGETED ->
                                selectedTarget?.ssid ?: "No target selected"
                            else -> "Hopping all channels"
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
                    Text(
                        text = "Mode",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NrFilterChip(
                            selected = channelMode == DeauthChannelMode.TARGETED,
                            onClick = { onChannelModeChange(DeauthChannelMode.TARGETED) },
                            label = "Targeted",
                            enabled = controlsEnabled,
                        )
                        NrFilterChip(
                            selected = channelMode == DeauthChannelMode.HOPPING,
                            onClick = { onChannelModeChange(DeauthChannelMode.HOPPING) },
                            label = "Hopping",
                            enabled = controlsEnabled,
                        )
                    }
                    Spacer(Modifier.height(10.dp))

                    if (channelMode == DeauthChannelMode.TARGETED) {
                        OutlinedButton(
                            onClick = onScanClick,
                            enabled = controlsEnabled && !isScanning,
                        ) {
                            Text(if (isScanning) "Scanning..." else "Scan")
                        }

                        if (scanResults.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                scanResults.forEach { target ->
                                    NetworkTargetRow(
                                        ssid = target.ssid,
                                        bssid = target.bssid,
                                        channel = target.channel,
                                        rssi = target.rssi,
                                        security = target.security,
                                        selected = selectedTarget?.bssid == target.bssid,
                                        enabled = controlsEnabled,
                                        onClick = { onSelectTarget(target) },
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        NumberStepper(
                            label = "Channel",
                            valueText = channel.toString(),
                            enabled = controlsEnabled,
                            onDecrease = { onChannelChange((channel - 1).coerceAtLeast(1)) },
                            onIncrease = { onChannelChange((channel + 1).coerceAtMost(14)) },
                        )
                    } else {
                        NumberStepper(
                            label = "Dwell time (ms)",
                            valueText = hopIntervalMs.toString(),
                            enabled = controlsEnabled,
                            onDecrease = {
                                onHopIntervalChange((hopIntervalMs - 100).coerceAtLeast(100))
                            },
                            onIncrease = {
                                onHopIntervalChange((hopIntervalMs + 100).coerceAtMost(2_000))
                            },
                        )

                        if (running) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Currently on ch ${currentHopChannel ?: "-"}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = NrOnSurfaceVariant,
                            )
                        }
                    }

                    if (!connected) {
                        Spacer(Modifier.height(8.dp))
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
private fun DetectionFeedCard(
    feed: List<DeauthFeedEntry>,
    feedFilter: DeauthFeedFilter,
    onFeedFilterChange: (DeauthFeedFilter) -> Unit,
    onClearFeed: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(feed.size) {
        if (feed.isNotEmpty()) {
            listState.animateScrollToItem(feed.lastIndex)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
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
                        text = "Detection feed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${feed.size} shown",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = onClearFeed,
                    enabled = feed.isNotEmpty(),
                ) {
                    Text("Clear")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeauthFeedFilter.entries.forEach { filter ->
                    NrFilterChip(
                        selected = feedFilter == filter,
                        onClick = { onFeedFilterChange(filter) },
                        label = when (filter) {
                            DeauthFeedFilter.ALL -> "All"
                            DeauthFeedFilter.BROADCAST -> "Broadcast"
                            DeauthFeedFilter.TARGETED -> "Directed"
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                ) {
                    itemsIndexed(feed) { index, entry ->
                        FeedRow(entry)
                        if (index != feed.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(0.5.dp)
                                    .background(NrOutline),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedRow(entry: DeauthFeedEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.sourceMac,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = entry.timestamp,
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
        }
        val targetAddress = entry.targetMac ?: "FF:FF:FF:FF:FF:FF"
        val targetKind = if (entry.targetMac == null) "broadcast" else "directed"
        Text(
            text = "to $targetAddress ($targetKind) · reason ${entry.reasonCode} · " +
                "${entry.rssi} dBm · ${rssiToProximity(entry.rssi).label}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = NrOnSurfaceVariant,
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
