package com.swp81x.nrsuite.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.wifi.NetworkTarget
import com.swp81x.nrsuite.core.wifi.bandForWifiChannel
import com.swp81x.nrsuite.core.wps.WpsAttemptDetectorUiState
import com.swp81x.nrsuite.core.wps.WpsAttemptRisk
import com.swp81x.nrsuite.core.wps.WpsAttemptSession
import com.swp81x.nrsuite.ui.components.NetworkStatusBadge
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.components.ScanButtonContent
import com.swp81x.nrsuite.ui.components.ScanTargetList
import com.swp81x.nrsuite.ui.theme.NrOnSurface
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed
import com.swp81x.nrsuite.ui.util.signalQualityColor
import org.json.JSONObject

@Composable
fun WpsAttemptDetectorScreen(
    connected: Boolean,
    state: WpsAttemptDetectorUiState,
    scanning: Boolean,
    networks: List<JSONObject>,
    onScanWpsTargets: () -> Unit,
    onStart: (targets: List<NetworkTarget>, allChannels: Boolean, intervalMs: Int) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var selectedBssids by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    var autoSelectPending by rememberSaveable { mutableStateOf(true) }
    var allChannels by rememberSaveable { mutableStateOf(false) }
    var intervalMs by rememberSaveable { mutableIntStateOf(300) }
    var riskFilter by rememberSaveable { mutableIntStateOf(0) }

    val wpsNetworks = remember(networks) {
        networks.filter { network ->
            val security = network.optString("security").uppercase()
            network.optBoolean("wps") &&
                !security.startsWith("OPEN") &&
                !security.startsWith("NONE")
        }.sortedByDescending { it.optInt("rssi", -999) }
    }
    val currentBssids = remember(wpsNetworks) {
        wpsNetworks.mapNotNull { it.optString("bssid").takeIf { bssid -> bssid.isNotBlank() } }.toSet()
    }
    LaunchedEffect(wpsNetworks, autoSelectPending) {
        if (autoSelectPending && wpsNetworks.isNotEmpty()) {
            selectedBssids = currentBssids
        }
    }
    LaunchedEffect(currentBssids) {
        selectedBssids = selectedBssids.intersect(currentBssids)
    }

    val selectedTargets = remember(wpsNetworks, selectedBssids) {
        wpsNetworks.filter { it.optString("bssid") in selectedBssids }.map { network ->
            NetworkTarget(
                ssid = network.optString("ssid").ifBlank { "(hidden)" },
                bssid = network.optString("bssid"),
                channel = network.optInt("channel", 1),
                rssi = network.optInt("rssi", -100),
                security = network.optString("security", "?"),
                wps = true,
                vendor = network.optString("vendor").takeIf { it.isNotBlank() },
                ouiWhitelisted = network.optBoolean("oui_whitelisted"),
                ouiBlacklisted = network.optBoolean("oui_blacklisted"),
            )
        }
    }
    val selectedChannels = selectedTargets.map { it.channel }.distinct().sorted()
    val filtered = state.sessions.filter { session ->
        when (riskFilter) {
            1 -> session.risk == WpsAttemptRisk.LOW
            2 -> session.risk == WpsAttemptRisk.MEDIUM
            3 -> session.risk == WpsAttemptRisk.HIGH
            else -> true
        }
    }
    val mediumOrHigh = state.sessions.count { it.risk != WpsAttemptRisk.LOW }
    val highRisk = state.sessions.count { it.risk == WpsAttemptRisk.HIGH }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
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
                        Icon(
                            Icons.Default.WifiFind,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "WPS Attempt Detector",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = NrOnSurface,
                            )
                            Text(
                                text = when {
                                    state.starting -> "Starting passive monitor..."
                                    state.running && state.allChannels -> "Monitoring all allowed channels"
                                    state.running -> "Monitoring ${state.selectedTargetCount} selected WPS AP(s)"
                                    state.sessions.isEmpty() -> "Scan WPS APs, select targets, then monitor"
                                    else -> "${state.sessions.size} attempt session(s)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = NrOnSurfaceVariant,
                            )
                        }
                    }
                    if (!connected) {
                        Text(
                            text = "Connect a device before scanning or monitoring.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAmber,
                        )
                    }
                    if (state.running || state.sessions.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CountCell("Attempts", state.sessions.size.toString(), StatusNeutral, Modifier.weight(1f))
                            CountCell("Medium/High", mediumOrHigh.toString(), StatusAmber, Modifier.weight(1f))
                            CountCell("High", highRisk.toString(), StatusRed, Modifier.weight(1f))
                        }
                    }
                    state.error?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusRed,
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurface),
                border = BorderStroke(0.5.dp, NrOutline),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Targets & monitoring",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )

                    SwitchRow(
                        label = "Monitor all allowed channels",
                        checked = allChannels,
                        enabled = !state.running && !state.starting,
                        onCheckedChange = {
                            allChannels = it
                            if (it) autoSelectPending = false
                        },
                    )

                    if (!allChannels) {
                        OutlinedButton(
                            onClick = {
                                autoSelectPending = true
                                selectedBssids = emptySet()
                                onScanWpsTargets()
                            },
                            enabled = connected && !scanning && !state.running && !state.starting,
                        ) {
                            ScanButtonContent(
                                scanning = scanning,
                                idleLabel = "Scan WPS targets",
                            )
                        }

                        if (wpsNetworks.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${selectedTargets.size} selected",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                )
                                TextButton(
                                    onClick = {
                                        autoSelectPending = false
                                        selectedBssids = currentBssids
                                    },
                                    enabled = !state.running && !state.starting,
                                ) {
                                    Text("Select all")
                                }
                                TextButton(
                                    onClick = {
                                        autoSelectPending = false
                                        selectedBssids = emptySet()
                                    },
                                    enabled = !state.running && !state.starting,
                                ) {
                                    Text("Clear")
                                }
                            }
                            Text(
                                text = if (selectedChannels.isEmpty()) {
                                    "Channels: none"
                                } else {
                                    "Channels: ${selectedChannels.joinToString(", ")}"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = NrOnSurfaceVariant,
                                maxLines = 2,
                            )
                            ScanTargetList(
                                networks = wpsNetworks,
                                selectedBssids = selectedBssids,
                                onToggle = { network ->
                                    autoSelectPending = false
                                    val bssid = network.optString("bssid")
                                    selectedBssids = if (bssid in selectedBssids) {
                                        selectedBssids - bssid
                                    } else {
                                        selectedBssids + bssid
                                    }
                                },
                                enabled = !state.running && !state.starting,
                                multiSelect = true,
                                maxHeight = 300.dp,
                            )
                        } else {
                            Text(
                                text = if (scanning) {
                                    "Scanning for WPS-enabled access points..."
                                } else {
                                    "No WPS targets selected. Scan nearby WPS APs first."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = NrOnSurfaceVariant,
                            )
                        }
                    } else {
                        Text(
                            text = "Advanced mode monitors all allowed 2.4/5 GHz channels. " +
                                "Use only when the target AP is not visible in a scan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAmber,
                        )
                    }

                    Text(
                        text = "Hop interval",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(100, 300, 600, 1000).forEach { value ->
                            NrFilterChip(
                                selected = intervalMs == value,
                                onClick = { intervalMs = value },
                                label = "${value}ms",
                                enabled = !state.running && !state.starting,
                            )
                        }
                    }
                    Text(
                        text = "Passive only: parses EAP Identity/WSC M1–M8 activity. " +
                            "No WPS frames are transmitted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
            }

            if (state.running || state.sessions.isNotEmpty()) {
                val channelsText = if (state.allChannels) {
                    "all allowed channels"
                } else {
                    state.monitoredChannels.joinToString(",").ifBlank { "—" }
                }
                Text(
                    text = "Monitoring ${if (state.allChannels) "all channels" else "${state.selectedTargetCount} AP(s)"} · $channelsText",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            if (state.sessions.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = NrSurface),
                    border = BorderStroke(0.5.dp, NrOutline),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Observed attempts",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${filtered.size}/${state.sessions.size}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = NrOnSurfaceVariant,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            NrFilterChip(
                                selected = riskFilter == 0,
                                onClick = { riskFilter = 0 },
                                label = "All",
                            )
                            NrFilterChip(
                                selected = riskFilter == 1,
                                onClick = { riskFilter = 1 },
                                label = "Low",
                                selectedColor = StatusGreen,
                            )
                            NrFilterChip(
                                selected = riskFilter == 2,
                                onClick = { riskFilter = 2 },
                                label = "Medium",
                                selectedColor = StatusAmber,
                            )
                            NrFilterChip(
                                selected = riskFilter == 3,
                                onClick = { riskFilter = 3 },
                                label = "High",
                                selectedColor = StatusRed,
                            )
                        }
                        if (filtered.isEmpty()) {
                            Text(
                                text = "No attempts match this risk filter.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NrOnSurfaceVariant,
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(filtered, key = { it.sessionId }) { session ->
                                    WpsAttemptCard(session)
                                }
                            }
                        }
                    }
                }
            } else if (state.running) {
                Text(
                    text = "Monitoring... waiting for WPS Identity/M1–M8 activity.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            Spacer(Modifier.height(84.dp))
        }

        if (connected) {
            ModuleActionFab(
                onClick = {
                    when {
                        state.running || state.starting -> onStop()
                        !allChannels && selectedTargets.isEmpty() -> Toast.makeText(
                            context,
                            "Select at least one WPS target or enable all-channel monitoring.",
                            Toast.LENGTH_SHORT,
                        ).show()
                        else -> onStart(selectedTargets, allChannels, intervalMs)
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                containerColor = if (state.running) StatusRed else MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                when {
                    state.starting -> CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    state.running -> Icon(Icons.Default.Stop, contentDescription = "Stop WPS attempt monitor")
                    else -> Icon(Icons.Default.PlayArrow, contentDescription = "Start WPS attempt monitor")
                }
            }
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
private fun CountCell(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(Modifier.padding(9.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = NrOnSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        }
    }
}

@Composable
private fun WpsAttemptCard(session: WpsAttemptSession) {
    val riskColor = when (session.risk) {
        WpsAttemptRisk.LOW -> StatusGreen
        WpsAttemptRisk.MEDIUM -> StatusAmber
        WpsAttemptRisk.HIGH -> StatusRed
    }
    val riskLabel = when (session.risk) {
        WpsAttemptRisk.LOW -> "LOW"
        WpsAttemptRisk.MEDIUM -> "MEDIUM"
        WpsAttemptRisk.HIGH -> "HIGH"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = session.client,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.SemiBold,
                    color = NrOnSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                NetworkStatusBadge(text = riskLabel, color = riskColor)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = session.vendor ?: "Unknown vendor",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (session.randomizedMac) {
                    NetworkStatusBadge(text = "RANDOM MAC", color = StatusAmber)
                }
            }
            Text(
                text = "→ ${session.ssid} · ${session.bssid}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "ch ${session.channel} · ${bandForWifiChannel(session.channel).label} · " +
                    "${session.rssi} dBm · stage ${session.stage} · ${session.frames} frame(s)",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = signalQualityColor(session.rssi),
            )
            Text(
                text = "${(session.durationMs / 1000L).coerceAtLeast(0L)}s session · " +
                    "session #${session.sessionId}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
            )
            session.riskReasons.forEach { reason ->
                Text(
                    text = "• $reason",
                    style = MaterialTheme.typography.labelSmall,
                    color = riskColor.copy(alpha = 0.92f),
                )
            }
        }
    }
}
