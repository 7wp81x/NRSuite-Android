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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.defense.AlertConfidence
import com.swp81x.nrsuite.core.defense.TrustedNetwork
import com.swp81x.nrsuite.core.oui.OuiDatabaseStatus
import com.swp81x.nrsuite.core.defense.NearbyAp
import com.swp81x.nrsuite.core.defense.RogueApAlert
import com.swp81x.nrsuite.core.defense.RogueApCategory
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.util.rssiToProximity
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed

@Composable
fun RogueApScreen(
    connected: Boolean,
    running: Boolean,
    scanning: Boolean,
    nearbyNetworks: List<NearbyAp>,
    alerts: List<RogueApAlert>,
    lastScanAt: String?,
    trustedNetworks: List<TrustedNetwork>,
    onCaptureBaseline: () -> Unit,
    onClearBaseline: () -> Unit,
    ouiDatabaseStatus: OuiDatabaseStatus,
    onDownloadOuiDatabase: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClearAlerts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmStart by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            if (ouiDatabaseStatus !is OuiDatabaseStatus.Ready) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.5.dp, StatusAmber),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            tint = StatusAmber,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Vendor database not downloaded",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Vendor-based signals (unrecognized hardware, vendor mismatch) " +
                                    "will be unavailable until this is downloaded.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NrOnSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = onDownloadOuiDatabase) {
                            Text("Download")
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            RogueApStatusCard(
                connected = connected,
                running = running,
                scanning = scanning,
                lastScanAt = lastScanAt,
            )
            Spacer(Modifier.height(10.dp))

            KnownGoodBaselineCard(
                connected = connected,
                running = running,
                scanning = scanning,
                trustedNetworks = trustedNetworks,
                onCaptureBaseline = onCaptureBaseline,
                onClearBaseline = onClearBaseline,
            )
            Spacer(Modifier.height(10.dp))

            NearbyNetworksCard(nearbyNetworks = nearbyNetworks)

            Spacer(Modifier.height(10.dp))

            RogueApAlertsCard(
                alerts = alerts,
                running = running,
                onClearAlerts = onClearAlerts,
            )

            Spacer(Modifier.height(80.dp))
        }

        if (connected) {
            FloatingActionButton(
                onClick = {
                    if (running) onStop() else confirmStart = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .alpha(if (!connected) 0.4f else 1f),
                containerColor = if (running) StatusRed else NrAccent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (running) {
                        "Stop Rogue AP detector"
                    } else {
                        "Start Rogue AP detector"
                    },
                )
            }
        }
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("Start Rogue AP monitoring?") },
            text = {
                Text(
                    "The ESP32 will repeatedly scan WiFi and compare nearby APs " +
                        "sharing the same SSID for OUI and security mismatches. " +
                        "This uses the WiFi radio."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmStart = false
                        onStart()
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
private fun RogueApStatusCard(
    connected: Boolean,
    running: Boolean,
    scanning: Boolean,
    lastScanAt: String?,
) {
    val border = if (running) BorderStroke(1.dp, NrAccent) else BorderStroke(0.5.dp, NrOutline)
    val title = when {
        !connected -> "Disconnected"
        running -> "Monitoring"
        else -> "Ready"
    }
    val subtitle = when {
        !connected -> "Connect a device to begin"
        running && scanning -> "Scanning for rogue APs..."
        running -> "Last scan ${lastScanAt ?: "pending"}"
        else -> "Capture a baseline, then start monitoring"
    }
    val iconColor = when {
        !connected -> StatusAmber
        running -> NrAccent
        else -> NrOnSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = border,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Router,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun KnownGoodBaselineCard(
    connected: Boolean,
    running: Boolean,
    scanning: Boolean,
    trustedNetworks: List<TrustedNetwork>,
    onCaptureBaseline: () -> Unit,
    onClearBaseline: () -> Unit,
) {
    var confirmRecapture by remember { mutableStateOf(false) }
    val captureEnabled = connected && !running && !scanning

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Known-good baseline (optional)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${trustedNetworks.size} trusted AP(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                if (trustedNetworks.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onClearBaseline,
                        enabled = captureEnabled,
                    ) {
                        Text("Clear")
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "Optional. Improves matching for APs you already trust; " +
                    "the autonomous nearby comparison works without it.",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    if (trustedNetworks.isNotEmpty()) {
                        confirmRecapture = true
                    } else {
                        onCaptureBaseline()
                    }
                },
                enabled = captureEnabled,
            ) {
                Text(if (scanning) "Scanning..." else "Capture baseline")
            }
        }
    }

    if (confirmRecapture) {
        AlertDialog(
            onDismissRequest = { confirmRecapture = false },
            title = { Text("Replace trusted baseline?") },
            text = {
                Text(
                    "This replaces your current ${trustedNetworks.size} trusted " +
                        "AP(s) with what's visible right now. Networks not currently " +
                        "in range will be removed from the trusted list."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCaptureBaseline()
                        confirmRecapture = false
                    },
                ) {
                    Text("Replace")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRecapture = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun NearbyNetworksCard(
    nearbyNetworks: List<NearbyAp>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "Nearby networks",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${nearbyNetworks.size} AP(s) seen in the latest scan",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )

            if (nearbyNetworks.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(nearbyNetworks, key = { it.bssid }) { ap ->
                        NearbyApRow(ap)
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbyApRow(ap: NearbyAp) {
    val statusLabel = when {
        ap.suspicious && ap.category != null -> when (ap.category) {
            RogueApCategory.EVIL_TWIN -> "Evil Twin"
            RogueApCategory.FAKE_PORTAL -> "Fake Portal"
            RogueApCategory.SECURITY_DOWNGRADE -> "Security downgrade"
            RogueApCategory.UNKNOWN_ROGUE -> "Unknown Rogue"
        }
        ap.likelyInfrastructureVendor -> "Likely legit"
        else -> "Unclassified"
    }
    val statusColor = when {
        ap.suspicious && ap.category == RogueApCategory.EVIL_TWIN -> StatusRed
        ap.suspicious && ap.category == RogueApCategory.FAKE_PORTAL -> StatusAmber
        ap.suspicious && ap.category == RogueApCategory.SECURITY_DOWNGRADE -> StatusAmber
        ap.suspicious -> StatusNeutral
        ap.likelyInfrastructureVendor -> NrAccent
        else -> StatusNeutral
    }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = ap.ssid.ifBlank { "(hidden)" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier
                    .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        Text(
            text = ap.bssid,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = NrOnSurfaceVariant,
        )
        Text(
            text = "ch ${ap.channel} · ${ap.rssi} dBm · ${rssiToProximity(ap.rssi).label} · ${ap.security}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = NrOnSurfaceVariant,
        )
        if (!ap.vendor.isNullOrBlank()) {
            Text(
                text = "Vendor: ${ap.vendor}",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RogueApAlertsCard(
    alerts: List<RogueApAlert>,
    running: Boolean,
    onClearAlerts: () -> Unit,
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
                        text = "Suspicious APs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            alerts.isEmpty() && running -> "Waiting for suspicious observations..."
                            alerts.isEmpty() -> "No suspicious APs yet"
                            else -> "${alerts.size} alert(s)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                if (alerts.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onClearAlerts,
                        enabled = !running,
                    ) {
                        Text("Clear")
                    }
                }
            }

            if (alerts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(alerts, key = { it.id }) { alert ->
                        RogueApAlertRow(alert)
                    }
                }
            }
        }
    }
}

@Composable
private fun RogueApAlertRow(alert: RogueApAlert) {
    val categoryColor = when (alert.category) {
        RogueApCategory.EVIL_TWIN -> StatusRed
        RogueApCategory.FAKE_PORTAL -> StatusAmber
        RogueApCategory.SECURITY_DOWNGRADE -> StatusAmber
        RogueApCategory.UNKNOWN_ROGUE -> StatusNeutral
    }
    val categoryLabel = when (alert.category) {
        RogueApCategory.EVIL_TWIN -> "Evil Twin"
        RogueApCategory.FAKE_PORTAL -> "Fake Portal"
        RogueApCategory.SECURITY_DOWNGRADE -> "Security downgrade"
        RogueApCategory.UNKNOWN_ROGUE -> "Unknown Rogue"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .background(categoryColor.copy(alpha = 0.15f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = categoryLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = categoryColor,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = alert.detectedAt,
                    style = MaterialTheme.typography.labelSmall,
                    color = NrOnSurfaceVariant,
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = alert.ssid,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${alert.bssid} · ch ${alert.channel} · ${alert.rssi} dBm · " +
                    rssiToProximity(alert.rssi).label,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
            )
            if (!alert.vendor.isNullOrBlank()) {
                Text(
                    text = "Vendor: ${alert.vendor}",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }

            if (alert.reasons.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                alert.reasons.forEach { reason ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = categoryColor,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "Confidence: ${alert.confidence.name.lowercase().replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.labelSmall,
                color = when (alert.confidence) {
                    AlertConfidence.HIGH -> StatusRed
                    AlertConfidence.MEDIUM -> StatusAmber
                    AlertConfidence.LOW -> StatusNeutral
                },
            )
        }
    }
}
