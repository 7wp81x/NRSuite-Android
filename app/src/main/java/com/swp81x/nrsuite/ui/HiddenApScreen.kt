package com.swp81x.nrsuite.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed
import com.swp81x.nrsuite.ui.util.copyWithToast
import com.swp81x.nrsuite.ui.util.rssiToProximity

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
    onFixedChange: (Boolean) -> Unit,
    onChannelChange: (Int) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
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
                observations = observations,
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
                onToggle = { configExpanded = !configExpanded },
                onFixedChange = onFixedChange,
                onChannelChange = onChannelChange,
            )

            Spacer(Modifier.height(10.dp))

            HiddenApResultsCard(
                observations = observations,
                candidates = candidates,
                onClear = onClear,
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
                        "reassociation frames. It does not transmit."
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
    observations: List<HiddenApObservation>,
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
                label = "${observations.size} hidden AP(s) · $resolvedCount resolved · $candidateCount candidate(s) · $eventCount events",
                color = color,
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
    onToggle: () -> Unit,
    onFixedChange: (Boolean) -> Unit,
    onChannelChange: (Int) -> Unit,
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
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Detection mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Fixed channel or channel hopping",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onToggle) {
                    Text(if (expanded) "Collapse" else "Configure")
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
            }
        }
    }
}

@Composable
private fun HiddenApResultsCard(
    observations: List<HiddenApObservation>,
    candidates: List<HiddenSsidCandidate>,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "Detected hidden APs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))

            if (observations.isEmpty()) {
                Text(
                    text = "No hidden APs detected yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else {
                observations.take(50).forEach { observation ->
                    HiddenApRow(observation)
                    Spacer(Modifier.height(8.dp))
                }
                if (observations.size > 50) {
                    Text(
                        text = "Showing 50 of ${observations.size}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
            }

            if (candidates.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Probe request candidates",
                    style = MaterialTheme.typography.labelLarge,
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                candidates.take(20).forEach { candidate ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = candidate.ssid,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "Candidate from probe request · ch ${candidate.channel} · " +
                                "${candidate.rssi} dBm · ${candidate.sightings} sighting(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                }
            }

            if (observations.isEmpty() && candidates.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onClear,
                    enabled = false,
                ) {
                    Text("Clear")
                }
            }
        }
    }
}

@Composable
private fun HiddenApRow(observation: HiddenApObservation) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = observation.resolvedSsid ?: "Not yet revealed",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = rssiToProximity(observation.rssi).label,
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${observation.bssid} · ch ${observation.channel} · " +
                "${observation.rssi} dBm · ${observation.sightings} sighting(s)",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = NrOnSurfaceVariant,
        )
        if (observation.resolvedSsid != null) {
            Text(
                text = observation.resolutionSource ?: "Resolved",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        observation.vendor?.let { vendor ->
            Text(
                text = vendor,
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
        }
    }
}
