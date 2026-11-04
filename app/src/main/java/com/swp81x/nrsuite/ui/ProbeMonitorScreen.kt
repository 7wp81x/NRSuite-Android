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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.ProbeObservation
import com.swp81x.nrsuite.core.wifi.WIFI_AUTO_CHANNELS
import com.swp81x.nrsuite.core.wifi.normalizeWifiChannelForAllowed
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.components.WifiChannelStepper
import com.swp81x.nrsuite.ui.components.WifiChannelSummaryPill
import com.swp81x.nrsuite.ui.components.NrStatusAction
import com.swp81x.nrsuite.ui.theme.NrOnSurface
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusInfo
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed

@Composable
fun ProbeMonitorScreen(
    connected: Boolean,
    running: Boolean,
    fixed: Boolean,
    channel: Int,
    entries: List<ProbeObservation>,
    frameCount: Long,
    allowedChannels: List<Int>,
    onStart: (fixed: Boolean, channel: Int) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var fixedMode by rememberSaveable { mutableStateOf(fixed) }
    var selectedChannel by rememberSaveable { mutableIntStateOf(channel) }
    val channels = allowedChannels.ifEmpty { WIFI_AUTO_CHANNELS }
    LaunchedEffect(channels) {
        selectedChannel = normalizeWifiChannelForAllowed(selectedChannel, channels)
    }
    val effectiveChannel = normalizeWifiChannelForAllowed(selectedChannel, channels)

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurface),
                border = BorderStroke(0.5.dp, NrOutline),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WifiFind, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Probe Request Monitor",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = NrOnSurface,
                            )
                            Text(
                                text = if (running) {
                                    "Observed $frameCount probe frame(s)"
                                } else {
                                    "Passive client probe-request discovery"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = NrOnSurfaceVariant,
                            )
                        }
                    }
                    if (running || entries.isNotEmpty() || !connected) {
                        StatusIndicator(
                            label = when {
                                !connected -> "No device connected"
                                running -> "Monitoring"
                                else -> "Stopped"
                            },
                            color = when {
                                !connected -> StatusNeutral
                                running -> StatusGreen
                                else -> StatusInfo
                            },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NrFilterChip(
                            selected = !fixedMode,
                            onClick = { if (!running) fixedMode = false },
                            label = "Hop channels",
                            enabled = !running,
                        )
                        NrFilterChip(
                            selected = fixedMode,
                            onClick = { if (!running) fixedMode = true },
                            label = "Fixed channel",
                            enabled = !running,
                        )
                    }
                    if (fixedMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Channel",
                                style = MaterialTheme.typography.labelLarge,
                                color = NrOnSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            WifiChannelSummaryPill(effectiveChannel)
                        }
                        WifiChannelStepper(
                            value = effectiveChannel,
                            enabled = !running,
                            onChannelChange = { selectedChannel = it },
                            allowedChannels = channels,
                        )
                    } else {
                        Text(
                            text = "Channel hopping across the allowed 2.4 / 5 GHz bands",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = NrSurface),
                border = BorderStroke(0.5.dp, NrOutline),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (entries.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "No probe requests yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = NrOnSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Start monitoring and let nearby clients probe for known networks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(entries, key = { "${it.mac}|${it.ssid}" }) { entry ->
                            ProbeObservationRow(entry)
                        }
                    }
                }
            }
        }

        if (connected) {
            ModuleActionFab(
                onClick = { if (running) onStop() else onStart(fixedMode, effectiveChannel) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                statusAction = if (running) NrStatusAction.STOP else NrStatusAction.START,
            ) {
                Icon(
                    imageVector = if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (running) "Stop probe monitor" else "Start probe monitor",
                )
            }
        }
    }
}

@Composable
private fun ProbeObservationRow(entry: ProbeObservation) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.ssid,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = NrOnSurface,
                )
                if (entry.randomizedMac) {
                    Text(
                        text = "RANDOM MAC",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusAmber,
                    )
                }
            }
            Text(
                text = entry.mac,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
            )
            Text(
                text = buildString {
                    append("${entry.rssi} dBm")
                    if (entry.channel > 0) append(" · ch ${entry.channel}")
                    append(" · ${entry.sightings} sighting(s)")
                    append(" · ${entry.lastSeen}")
                },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
            )
            entry.vendor?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }
    }
}
