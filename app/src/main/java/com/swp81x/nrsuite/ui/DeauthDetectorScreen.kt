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
import androidx.compose.material.icons.filled.Check
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
import com.swp81x.nrsuite.core.defense.DeauthDetectedChannel
import com.swp81x.nrsuite.core.defense.DeauthDistributedMode
import com.swp81x.nrsuite.core.defense.DeauthFeedEntry
import com.swp81x.nrsuite.core.defense.DeauthFeedFilter
import com.swp81x.nrsuite.core.defense.DeauthSourceFilter
import com.swp81x.nrsuite.core.wifi.NetworkTarget
import com.swp81x.nrsuite.ui.util.rssiToProximity
import com.swp81x.nrsuite.ui.util.threatProximityColor
import com.swp81x.nrsuite.ui.components.NetworkTargetRow
import com.swp81x.nrsuite.ui.components.ChannelModeToggle
import com.swp81x.nrsuite.ui.components.ModuleStatusCard
import com.swp81x.nrsuite.ui.components.ModuleStatusState
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed

@Composable
fun DeauthDetectorScreen(
    connected: Boolean,
    running: Boolean,
    starting: Boolean,
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
    sourceFilter: DeauthSourceFilter,
    meshActive: Boolean,
    meshPeerCount: Int,
    meshChannel: Int,
    distributed: Boolean,
    onDistributedChange: (Boolean) -> Unit,
    detectorChannel: Int,
    onDetectorChannelChange: (Int) -> Unit,
    onApplyDetectorChannel: (Int) -> Unit,
    detectorChannelApplyInProgress: Boolean,
    detectorChannelApplySuccess: Boolean,
    recentChannels: List<DeauthDetectedChannel>,
    focusChannels: List<Int>,
    onFocusChannel: (Int) -> Unit,
    onFocusChannels: (List<Int>) -> Unit,
    distributedMode: DeauthDistributedMode,
    onDistributedModeChange: (DeauthDistributedMode) -> Unit,
    meshWindowMs: Int,
    onMeshWindowChange: (Int) -> Unit,
    detectorWindowMs: Int,
    onDetectorWindowChange: (Int) -> Unit,
    hopDwellMs: Int,
    onHopDwellChange: (Int) -> Unit,
    scanResults: List<NetworkTarget>,
    selectedTarget: NetworkTarget?,
    channel: Int,
    onSelectTarget: (NetworkTarget) -> Unit,
    onScanClick: () -> Unit,
    isScanning: Boolean,
    onFeedFilterChange: (DeauthFeedFilter) -> Unit,
    onSourceFilterChange: (DeauthSourceFilter) -> Unit,
    onClearFeed: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onLocateClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var configExpanded by remember { mutableStateOf(true) }
    var confirmStart by remember { mutableStateOf(false) }
    var showMeshRequired by remember { mutableStateOf(false) }

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
                onFocusChannel = if (distributed && distributedMode == DeauthDistributedMode.HOP) {
                    onFocusChannel
                } else {
                    null
                },
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
                isScanning = isScanning,
                onToggle = { configExpanded = !configExpanded },
                distributed = distributed,
                onChannelModeChange = onChannelModeChange,
                onHopIntervalChange = onHopIntervalChange,
                onScanClick = onScanClick,
                onSelectTarget = onSelectTarget,
            )

            Spacer(Modifier.height(10.dp))

            DistributedDetectorCard(
                connected = connected,
                running = running,
                meshActive = meshActive,
                meshPeerCount = meshPeerCount,
                distributed = distributed,
                onMeshRequired = { showMeshRequired = true },
                onDistributedChange = onDistributedChange,
                distributedMode = distributedMode,
                onDistributedModeChange = onDistributedModeChange,
                detectorChannel = detectorChannel,
                onDetectorChannelChange = onDetectorChannelChange,
                onApplyDetectorChannel = onApplyDetectorChannel,
                detectorChannelApplyInProgress = detectorChannelApplyInProgress,
                detectorChannelApplySuccess = detectorChannelApplySuccess,
                recentChannels = recentChannels,
                focusChannels = focusChannels,
                onFocusChannel = onFocusChannel,
                onFocusChannels = onFocusChannels,
                meshChannel = meshChannel,
                meshWindowMs = meshWindowMs,
                onMeshWindowChange = onMeshWindowChange,
                detectorWindowMs = detectorWindowMs,
                onDetectorWindowChange = onDetectorWindowChange,
                hopDwellMs = hopDwellMs,
                onHopDwellChange = onHopDwellChange,
            )

            Spacer(Modifier.height(10.dp))

            DetectionFeedCard(
                feed = feed,
                feedFilter = feedFilter,
                sourceFilter = sourceFilter,
                onFeedFilterChange = onFeedFilterChange,
                onSourceFilterChange = onSourceFilterChange,
                onClearFeed = onClearFeed,
            )

            Spacer(Modifier.height(80.dp))
        }

        val canStart = when {
            distributed -> true
            channelMode == DeauthChannelMode.TARGETED -> selectedTarget != null
            channelMode == DeauthChannelMode.HOPPING -> true
            else -> false
        }
        ModuleActionFab(
            onClick = {
                if (running) {
                    onStop()
                } else if (canStart) {
                    confirmStart = true
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            enabled = connected && !starting && (running || canStart),
            containerColor = if (running) StatusRed else MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            if (starting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
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
            title = { Text("Start monitoring?") },
            text = {
                Text(
                    if (distributed) {
                        "The mesh master will broadcast a distributed deauth detector start. " +
                            "Clients will time-slice a ${distributedMode.name.lowercase()} detector " +
                            "against the mesh channel, and reports will appear as Mesh rows."
                    } else if (channelMode == DeauthChannelMode.HOPPING) {
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

    if (showMeshRequired) {
        AlertDialog(
            onDismissRequest = { showMeshRequired = false },
            title = { Text("Mesh is not active") },
            text = {
                Text(
                    "Activate mesh from the Mesh screen before enabling distributed detection."
                )
            },
            confirmButton = {
                TextButton(onClick = { showMeshRequired = false }) {
                    Text("OK")
                }
            },
        )
    }
}

@Composable
private fun DistributedDetectorCard(
    connected: Boolean,
    running: Boolean,
    meshActive: Boolean,
    meshPeerCount: Int,
    meshChannel: Int,
    distributed: Boolean,
    onDistributedChange: (Boolean) -> Unit,
    onMeshRequired: () -> Unit,
    distributedMode: DeauthDistributedMode,
    onDistributedModeChange: (DeauthDistributedMode) -> Unit,
    detectorChannel: Int,
    onDetectorChannelChange: (Int) -> Unit,
    onApplyDetectorChannel: (Int) -> Unit,
    detectorChannelApplyInProgress: Boolean,
    detectorChannelApplySuccess: Boolean,
    recentChannels: List<DeauthDetectedChannel>,
    focusChannels: List<Int>,
    onFocusChannel: (Int) -> Unit,
    onFocusChannels: (List<Int>) -> Unit,
    meshWindowMs: Int,
    onMeshWindowChange: (Int) -> Unit,
    detectorWindowMs: Int,
    onDetectorWindowChange: (Int) -> Unit,
    hopDwellMs: Int,
    onHopDwellChange: (Int) -> Unit,
) {
    val controlsEnabled = connected && !running
    val channelChanged = detectorChannel != meshChannel

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Detector source",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Choose whether this detector runs locally over USB only, or " +
                    "distributed across mesh clients.",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NrFilterChip(
                    selected = !distributed,
                    onClick = { if (controlsEnabled) onDistributedChange(false) },
                    label = "Local only",
                )
                NrFilterChip(
                    selected = distributed,
                    onClick = {
                        if (!controlsEnabled) return@NrFilterChip
                        if (!meshActive) {
                            onMeshRequired()
                        } else {
                            onDistributedChange(true)
                        }
                    },
                    label = "Distributed mesh",
                )
            }

            if (!meshActive) {
                Text(
                    text = "Mesh is not active. Activate mesh first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusAmber,
                )
            }

            if (distributed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(
                                if (meshPeerCount > 0) StatusGreen else StatusNeutral,
                                RoundedCornerShape(50),
                            ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Mesh clients online: $meshPeerCount",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (meshPeerCount > 0) StatusGreen else NrOnSurfaceVariant,
                    )
                }

                Text(
                    text = "Detector channel mode",
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Distributed HOP is intentionally removed from the UI for
                    // now. The existing FIXED path applies the selected mesh
                    // channel before the distributed detector starts.
                    listOf(DeauthDistributedMode.FIXED).forEach { mode ->
                        NrFilterChip(
                            selected = distributedMode == mode,
                            onClick = { if (controlsEnabled) onDistributedModeChange(mode) },
                            enabled = controlsEnabled,
                            label = "Fixed",
                        )
                    }
                }

                when (distributedMode) {
                    DeauthDistributedMode.FIXED -> {
                        NumberStepper(
                            label = "Mesh channel",
                            valueText = detectorChannel.toString(),
                            enabled = connected && !detectorChannelApplyInProgress,
                            onDecrease = { onDetectorChannelChange((detectorChannel - 1).coerceAtLeast(1)) },
                            onIncrease = { onDetectorChannelChange((detectorChannel + 1).coerceAtMost(13)) },
                        )
                        Text(
                            text = if (channelChanged) {
                                "Current mesh channel: $meshChannel · applying will switch the " +
                                    "whole mesh to $detectorChannel."
                            } else {
                                "Mesh and detector share channel $meshChannel."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (channelChanged) StatusAmber else NrOnSurfaceVariant,
                        )
                        if (channelChanged || detectorChannelApplyInProgress ||
                            detectorChannelApplySuccess
                        ) {
                            OutlinedButton(
                                onClick = { onApplyDetectorChannel(detectorChannel) },
                                enabled = connected && !detectorChannelApplyInProgress &&
                                    !detectorChannelApplySuccess,
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (detectorChannelApplySuccess) {
                                        StatusGreen
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                ),
                            ) {
                                when {
                                    detectorChannelApplyInProgress -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Applying...")
                                    }
                                    detectorChannelApplySuccess -> {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Success")
                                    }
                                    else -> Text("Apply mesh channel")
                                }
                            }
                            TextButton(
                                onClick = { onDetectorChannelChange(meshChannel) },
                                enabled = connected && !detectorChannelApplyInProgress,
                            ) {
                                Text("Use mesh channel")
                            }
                        }
                    }

                    DeauthDistributedMode.HOP -> {
                        Text(
                            text = "Experimental: clients hop only inside the detector window.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAmber,
                        )
                        NumberStepper(
                            label = "Hop dwell (ms)",
                            valueText = hopDwellMs.toString(),
                            enabled = controlsEnabled,
                            onDecrease = { onHopDwellChange((hopDwellMs - 50).coerceAtLeast(200)) },
                            onIncrease = { onHopDwellChange((hopDwellMs + 50).coerceAtMost(1_000)) },
                        )
                        if (focusChannels.isNotEmpty()) {
                            Text(
                                text = "Focused: ${focusChannels.joinToString { "ch $it" }}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = StatusAmber,
                            )
                            TextButton(
                                onClick = { onFocusChannels(emptyList()) },
                                enabled = controlsEnabled,
                            ) {
                                Text("Hop all channels")
                            }
                        }
                        if (recentChannels.isNotEmpty()) {
                            Text(
                                text = "Detected channels",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                recentChannels.forEach { detected ->
                                    NrFilterChip(
                                        selected = false,
                                        onClick = { onFocusChannel(detected.channel) },
                                        label = "ch ${detected.channel} (${detected.count})",
                                    )
                                }
                            }
                            if (recentChannels.size > 1) {
                                OutlinedButton(
                                    onClick = {
                                        onFocusChannels(recentChannels.map { it.channel })
                                    },
                                    enabled = controlsEnabled,
                                ) {
                                    Text("Focus detected channels")
                                }
                            }
                        }
                    }

                    DeauthDistributedMode.SAME_CHANNEL -> Unit
                }

                NumberStepper(
                    label = "Mesh window (ms)",
                    valueText = meshWindowMs.toString(),
                    enabled = controlsEnabled,
                    onDecrease = { onMeshWindowChange((meshWindowMs - 500).coerceAtLeast(2_000)) },
                    onIncrease = { onMeshWindowChange((meshWindowMs + 500).coerceAtMost(10_000)) },
                )
                NumberStepper(
                    label = "Detector window (ms)",
                    valueText = detectorWindowMs.toString(),
                    enabled = controlsEnabled,
                    onDecrease = { onDetectorWindowChange((detectorWindowMs - 100).coerceAtLeast(300)) },
                    onIncrease = { onDetectorWindowChange((detectorWindowMs + 100).coerceAtMost(2_500)) },
                )
            }
        }
    }
}

@Composable
private fun DetectorStatusCard(
    running: Boolean,
    connected: Boolean,
    alert: DeauthAlert?,
    onLocateClick: () -> Unit,
    onFocusChannel: ((Int) -> Unit)? = null,
) {
    val state = when {
        alert != null && alert.confidence == AlertConfidence.HIGH -> ModuleStatusState.ALERT
        alert != null -> ModuleStatusState.RUNNING
        !connected -> ModuleStatusState.DISCONNECTED
        running -> ModuleStatusState.RUNNING
        else -> ModuleStatusState.READY
    }
    ModuleStatusCard(
        icon = Icons.Default.Shield,
        title = when {
            alert != null -> "Deauth attack detected"
            !connected -> "Disconnected"
            running -> "No attack detected"
            else -> "Ready"
        },
        subtitle = when {
            alert != null -> "${alert.ssid} · ch ${alert.channel}"
            !connected -> "Connect a device before starting the detector."
            running -> "Monitoring for deauth activity"
            else -> "Start monitoring to watch for deauth activity"
        },
        state = state,
    ) {
        if (alert != null) {
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
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = threatProximityColor(alert.dominantSourceRssi),
            )

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onFocusChannel != null) {
                    OutlinedButton(
                        onClick = { onFocusChannel(alert.channel) },
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = StatusAmber,
                        ),
                    ) {
                        Text("Focus ch ${alert.channel}")
                    }
                    Spacer(Modifier.width(8.dp))
                }
                OutlinedButton(
                    onClick = onLocateClick,
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text("Locate")
                }
                Spacer(Modifier.weight(1f))
                ConfidenceBadge(alert.confidence)
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
    val fpsColor = if (framesPerSecond > thresholdFramesPerSecond) StatusRed else MaterialTheme.colorScheme.primary
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
    isScanning: Boolean,
    onToggle: () -> Unit,
    distributed: Boolean,
    onChannelModeChange: (DeauthChannelMode) -> Unit,
    onHopIntervalChange: (Int) -> Unit,
    onScanClick: () -> Unit,
    onSelectTarget: (NetworkTarget) -> Unit,
) {
    val controlsEnabled = connected && !running
    val localControlsEnabled = controlsEnabled && !distributed

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
                    tint = MaterialTheme.colorScheme.primary,
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
                    if (distributed) {
                        Text(
                            text = "Hopping and target scanning are disabled while distributed " +
                                "mesh is active.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAmber,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    ChannelModeToggle(
                        fixed = if (distributed) true else channelMode == DeauthChannelMode.TARGETED,
                        enabled = localControlsEnabled,
                        onFixedChange = { fixed ->
                            onChannelModeChange(
                                if (fixed) DeauthChannelMode.TARGETED else DeauthChannelMode.HOPPING,
                            )
                        },
                    )
                    Spacer(Modifier.height(10.dp))

                    if (!distributed && channelMode == DeauthChannelMode.TARGETED) {
                        OutlinedButton(
                            onClick = onScanClick,
                            enabled = controlsEnabled && !isScanning,
                        ) {
                            Text(if (isScanning) "Scanning..." else "Scan WiFi for targets")
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
                                        wps = target.wps,
                                        vendor = target.vendor,
                                        ouiWhitelisted = target.ouiWhitelisted,
                                        ouiBlacklisted = target.ouiBlacklisted,
                                        selected = selectedTarget?.bssid == target.bssid,
                                        enabled = controlsEnabled,
                                        onClick = { onSelectTarget(target) },
                                    )
                                }
                            }
                        }

                    } else if (!distributed) {
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
    sourceFilter: DeauthSourceFilter,
    onFeedFilterChange: (DeauthFeedFilter) -> Unit,
    onSourceFilterChange: (DeauthSourceFilter) -> Unit,
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
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeauthSourceFilter.entries.forEach { filter ->
                    NrFilterChip(
                        selected = sourceFilter == filter,
                        onClick = { onSourceFilterChange(filter) },
                        label = when (filter) {
                            DeauthSourceFilter.ALL -> "All sources"
                            DeauthSourceFilter.LOCAL -> "Local"
                            DeauthSourceFilter.MESH -> "Mesh"
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (feed.isEmpty()) {
                Text(
                    text = "No observations match the current source/frame filter yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else {
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
                text = buildString {
                    if (entry.origin == "mesh") {
                        append("[MESH] ")
                        entry.nodeId?.takeIf { it.isNotBlank() }?.let {
                            append(it)
                            append(" ")
                        }
                    }
                    append(entry.sourceMac)
                },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = buildString {
                    entry.chip?.takeIf { it.isNotBlank() }?.let {
                        append(it)
                        append(" · ")
                    }
                    append(entry.timestamp)
                },
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
        }
        val targetAddress = entry.targetMac ?: "FF:FF:FF:FF:FF:FF"
        val targetKind = if (entry.targetMac == null) "broadcast" else "directed"
        val channelText = entry.channel?.let { "ch $it · " } ?: ""
        val staleText = if (entry.stale) " · stale" else ""
        Text(
            text = "to $targetAddress ($targetKind) · ${channelText}reason ${entry.reasonCode} · " +
                "${entry.rssi} dBm · ${rssiToProximity(entry.rssi).label}$staleText",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = if (entry.stale) StatusNeutral else threatProximityColor(entry.rssi),
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
