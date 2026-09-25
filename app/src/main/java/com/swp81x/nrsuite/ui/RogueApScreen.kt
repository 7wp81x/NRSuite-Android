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
import com.swp81x.nrsuite.core.defense.RogueApAlert
import com.swp81x.nrsuite.core.defense.RogueApCategory
import com.swp81x.nrsuite.core.defense.TrustedNetwork
import com.swp81x.nrsuite.ui.theme.NrAccent
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
    trustedNetworks: List<TrustedNetwork>,
    alerts: List<RogueApAlert>,
    lastScanAt: String?,
    onCaptureBaseline: () -> Unit,
    onClearBaseline: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClearAlerts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmStart by remember { mutableStateOf(false) }
    val canStart = connected && trustedNetworks.isNotEmpty()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            RogueApStatusCard(
                connected = connected,
                running = running,
                scanning = scanning,
                lastScanAt = lastScanAt,
            )
            Spacer(Modifier.height(10.dp))

            TrustedBaselineCard(
                connected = connected,
                running = running,
                scanning = scanning,
                trustedNetworks = trustedNetworks,
                onCaptureBaseline = onCaptureBaseline,
                onClearBaseline = onClearBaseline,
            )
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
                    if (running) {
                        onStop()
                    } else if (canStart) {
                        confirmStart = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .alpha(if (!canStart && !running) 0.4f else 1f),
                containerColor = if (running) StatusRed else NrAccent,
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
            title = { Text("Start Rogue AP monitoring?") },
            text = {
                Text(
                    "The ESP32 will repeatedly scan WiFi and compare visible APs " +
                        "against your trusted baseline. This uses the WiFi radio."
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
private fun TrustedBaselineCard(
    connected: Boolean,
    running: Boolean,
    scanning: Boolean,
    trustedNetworks: List<TrustedNetwork>,
    onCaptureBaseline: () -> Unit,
    onClearBaseline: () -> Unit,
) {
    val enabled = connected && !running && !scanning

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
                        text = "Trusted baseline",
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
                        enabled = enabled,
                    ) {
                        Text("Clear")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onCaptureBaseline,
                enabled = enabled,
            ) {
                Text(if (scanning) "Scanning..." else "Capture baseline")
            }

            if (trustedNetworks.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    trustedNetworks.take(8).forEach { network ->
                        Column {
                            Text(
                                text = network.ssid,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${network.bssid} · ch ${network.channel} · ${network.security}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = NrOnSurfaceVariant,
                            )
                        }
                    }
                }
                if (trustedNetworks.size > 8) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Showing first 8 of ${trustedNetworks.size}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
            }
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
        RogueApCategory.UNKNOWN_ROGUE -> StatusNeutral
    }
    val categoryLabel = when (alert.category) {
        RogueApCategory.EVIL_TWIN -> "Evil Twin"
        RogueApCategory.FAKE_PORTAL -> "Fake Portal"
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
                text = "${alert.bssid} · ch ${alert.channel} · ${alert.rssi} dBm",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
            )

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
