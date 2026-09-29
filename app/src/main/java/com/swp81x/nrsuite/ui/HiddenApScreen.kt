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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.defense.HiddenApObservation
import com.swp81x.nrsuite.core.defense.HiddenSsidCandidate
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed
import com.swp81x.nrsuite.ui.util.copyWithToast
import com.swp81x.nrsuite.ui.util.rssiToProximity
import com.swp81x.nrsuite.ui.util.threatProximityColor

@Composable
fun HiddenApScreen(
    connected: Boolean,
    running: Boolean,
    fixed: Boolean,
    channel: Int,
    currentHopChannel: Int?,
    observations: List<HiddenApObservation>,
    candidates: List<HiddenSsidCandidate>,
    eventCount: Long,
    deauthEnabled: Boolean,
    onFixedChange: (Boolean) -> Unit,
    onChannelChange: (Int) -> Unit,
    onDeauthToggle: (Boolean) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onForceReconnect: (HiddenApObservation) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var configExpanded by remember { mutableStateOf(true) }
    var confirmStart by remember { mutableStateOf(false) }

    LaunchedEffect(running) {
        if (running) configExpanded = false
    }

    val resolvedCount = observations.count { !it.resolvedSsid.isNullOrBlank() }
    val canStart = connected
    val copyText = remember(observations, candidates) {
        buildString {
            observations.forEach { observation ->
                appendLine(
                    "${observation.bssid}  ch ${observation.channel}  " +
                        "${observation.rssi} dBm  " +
                        (observation.resolvedSsid?.let { "resolved: $it" } ?: "not yet revealed")
                )
            }
            candidates.forEach { candidate ->
                appendLine("candidate: ${candidate.ssid}  ch ${candidate.channel}  ${candidate.rssi} dBm")
            }
        }.trim()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            HiddenApStatusCard(
                connected = connected,
                running = running,
                fixed = fixed,
                channel = channel,
                currentHopChannel = currentHopChannel,
                hiddenCount = observations.size,
                resolvedCount = resolvedCount,
                candidateCount = candidates.size,
                eventCount = eventCount,
            )

            Spacer(Modifier.height(10.dp))

            HiddenApConfigCard(
                running = running,
                expanded = configExpanded,
                fixed = fixed,
                channel = channel,
                deauthEnabled = deauthEnabled,
                onToggle = { configExpanded = !configExpanded },
                onFixedChange = onFixedChange,
                onChannelChange = onChannelChange,
                onDeauthToggle = onDeauthToggle,
            )

            Spacer(Modifier.height(10.dp))

            HiddenApCandidateCard(candidates)

            Spacer(Modifier.height(10.dp))

            HiddenApResultsCard(
                observations = observations,
                running = running,
                deauthEnabled = deauthEnabled,
                onForceReconnect = onForceReconnect,
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { clipboard.copyWithToast(context, copyText) },
                    enabled = copyText.isNotBlank(),
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }
                OutlinedButton(
                    onClick = onClear,
                    enabled = observations.isNotEmpty() || candidates.isNotEmpty(),
                ) {
                    Text("Clear")
                }
            }

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
                containerColor = if (running) StatusRed else MaterialTheme.colorScheme.primary,
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
            title = { Text("Start hidden AP detection?") },
            text = {
                Text(
                    "This passively monitors beacon, probe, association, and " +
                        "reassociation frames. It does not transmit by itself."
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
private fun HiddenApStatusCard(
    connected: Boolean,
    running: Boolean,
    fixed: Boolean,
    channel: Int,
    currentHopChannel: Int?,
    hiddenCount: Int,
    resolvedCount: Int,
    candidateCount: Int,
    eventCount: Long,
) {
    val color = when {
        !connected -> StatusNeutral
        running -> StatusGreen
        else -> StatusAmber
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, if (running) MaterialTheme.colorScheme.primary else NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
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
                            running -> buildString {
                                append(if (fixed) "Fixed ch $channel" else "Hopping")
                                currentHopChannel?.let { append(" · now ch $it") }
                            }
                            else -> "Passive hidden AP detection"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            StatusIndicator(
                label = if (running) "Hidden AP detection active" else "Hidden AP detection idle",
                color = color,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HiddenApStatTile("Candidates", candidateCount.toString(), StatusAmber, Modifier.weight(1f))
                HiddenApStatTile("Resolved", resolvedCount.toString(), StatusGreen, Modifier.weight(1f))
                HiddenApStatTile("Events", eventCount.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            }
            if (hiddenCount > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "$hiddenCount hidden AP(s) observed",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HiddenApStatTile(
    label: String,
    value: String,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = tint.copy(alpha = 0.12f),
        ),
        border = BorderStroke(0.5.dp, tint.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = tint,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HiddenApConfigCard(
    running: Boolean,
    expanded: Boolean,
    fixed: Boolean,
    channel: Int,
    deauthEnabled: Boolean,
    onToggle: () -> Unit,
    onFixedChange: (Boolean) -> Unit,
    onChannelChange: (Int) -> Unit,
    onDeauthToggle: (Boolean) -> Unit,
) {
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
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Hidden AP configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Passive scanning and optional reconnect trigger",
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
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NrFilterChip(
                        selected = fixed,
                        onClick = { onFixedChange(true) },
                        label = "Fixed",
                        enabled = !running,
                    )
                    NrFilterChip(
                        selected = !fixed,
                        onClick = { onFixedChange(false) },
                        label = "Hopping",
                        enabled = !running,
                    )
                }

                if (fixed) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Channel",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = { onChannelChange((channel - 1).coerceAtLeast(1)) },
                            enabled = !running && channel > 1,
                        ) {
                            Text("-")
                        }
                        Text(
                            text = "$channel",
                            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                        OutlinedButton(
                            onClick = { onChannelChange((channel + 1).coerceAtMost(14)) },
                            enabled = !running && channel < 14,
                        ) {
                            Text("+")
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Deauth reconnect trigger",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Allow sending broadcast deauth to a hidden BSSID. Authorized networks only.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAmber,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = deauthEnabled,
                        onCheckedChange = onDeauthToggle,
                    )
                }
            }
        }
    }
}

@Composable
private fun HiddenApCandidateCard(candidates: List<HiddenSsidCandidate>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "Hidden SSID candidates",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "SSIDs seen in client probe requests",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            if (candidates.isEmpty()) {
                Text(
                    text = "No probe-request candidates yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(candidates, key = { it.ssid }) { candidate ->
                        HiddenSsidCandidateRow(candidate)
                    }
                }
            }
        }
    }
}

@Composable
private fun HiddenSsidCandidateRow(candidate: HiddenSsidCandidate) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                text = candidate.ssid,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "ch ${candidate.channel} · ${candidate.rssi} dBm · " +
                    "${rssiToProximity(candidate.rssi).label} · ${candidate.sightings} sighting(s)",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = threatProximityColor(candidate.rssi),
            )
            candidate.client?.let { client ->
                Text(
                    text = "Client: $client",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
            Text(
                text = "Last seen: ${candidate.lastSeen}",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HiddenApResultsCard(
    observations: List<HiddenApObservation>,
    running: Boolean,
    deauthEnabled: Boolean,
    onForceReconnect: (HiddenApObservation) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "Detected Hidden APs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Yellow = hidden. Green = SSID revealed.",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            if (observations.isEmpty()) {
                Text(
                    text = "No hidden APs detected yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 330.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(observations, key = { it.bssid }) { observation ->
                        HiddenApObservationRow(
                            observation = observation,
                            running = running,
                            deauthEnabled = deauthEnabled,
                            onForceReconnect = onForceReconnect,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HiddenApObservationRow(
    observation: HiddenApObservation,
    running: Boolean,
    deauthEnabled: Boolean,
    onForceReconnect: (HiddenApObservation) -> Unit,
) {
    val resolved = !observation.resolvedSsid.isNullOrBlank()
    val stateColor = if (resolved) StatusGreen else StatusAmber
    val stateLabel = if (resolved) "Revealed" else "Hidden"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = stateColor.copy(alpha = 0.08f)),
        border = BorderStroke(0.5.dp, stateColor.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .background(stateColor.copy(alpha = 0.16f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = stateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = stateColor,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = observation.lastSeen,
                    style = MaterialTheme.typography.labelSmall,
                    color = NrOnSurfaceVariant,
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = observation.resolvedSsid ?: "Not yet revealed",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${observation.bssid} · ch ${observation.channel} · " +
                    "${observation.rssi} dBm · ${rssiToProximity(observation.rssi).label}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = threatProximityColor(observation.rssi),
            )
            observation.vendor?.let { vendor ->
                Text(
                    text = "Vendor: $vendor",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
            Text(
                text = "${observation.sightings} sighting(s)",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            if (resolved) {
                Text(
                    text = observation.resolutionSource ?: "Resolved",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusGreen,
                )
            } else if (running && deauthEnabled) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onForceReconnect(observation) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Force reconnect")
                }
            }
        }
    }
}
