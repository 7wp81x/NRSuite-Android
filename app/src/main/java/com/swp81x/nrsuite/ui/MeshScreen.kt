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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.mesh.MeshNodeStatus
import com.swp81x.nrsuite.ui.theme.CategoryBleBlue
import com.swp81x.nrsuite.ui.theme.NrAccent
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
fun MeshScreen(
    connected: Boolean,
    initialized: Boolean,
    role: String,
    sessionId: Long?,
    nodeId: String,
    peerCount: Int,
    active: Boolean,
    nodes: List<MeshNodeStatus>,
    lastError: String?,
    hasStoredCredentials: Boolean,
    storedKeyId: String?,
    onRefresh: () -> Unit,
    onDeactivate: () -> Unit,
    onAuthenticateStored: () -> Unit,
    onOpenSetup: () -> Unit,
    onClearNodes: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(connected) {
        if (connected) onRefresh()
    }

    val clipboard = LocalClipboardManager.current
    val statusSummary = remember(
        connected,
        initialized,
        role,
        sessionId,
        nodeId,
        peerCount,
        active,
        nodes,
    ) {
        buildString {
            appendLine("NRSuite Mesh Network")
            appendLine("connected=$connected")
            appendLine("initialized=$initialized")
            appendLine("role=$role")
            appendLine("active=$active")
            appendLine("node_id=${nodeId.ifBlank { "-" }}")
            appendLine("session_id=${sessionId ?: "-"}")
            appendLine("peer_count=$peerCount")
            if (nodes.isNotEmpty()) {
                appendLine("nodes=")
                nodes.forEach { node ->
                    appendLine("  ${node.nodeId} ${node.role} online=${node.online} rssi=${node.rssi ?: "-"}")
                }
            }
        }.trimEnd()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            MeshStatusCard(
                connected = connected,
                initialized = initialized,
                role = role,
                sessionId = sessionId,
                nodeId = nodeId,
                peerCount = peerCount,
                active = active,
                hasStoredCredentials = hasStoredCredentials,
                storedKeyId = storedKeyId,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { clipboard.setText(AnnotatedString(statusSummary)) },
                    enabled = connected,
                ) {
                    Text("Copy mesh status")
                }
                TextButton(
                    onClick = onRefresh,
                    enabled = connected,
                ) {
                    Text("Refresh")
                }
            }

            Spacer(Modifier.height(10.dp))

            if (!initialized) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = StatusAmber.copy(alpha = 0.10f)),
                    border = BorderStroke(0.5.dp, StatusAmber.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Mesh is not set up on this device",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusAmber,
                        )
                        Text(
                            text = "Open Mesh Setup to apply the shared mesh passphrase before using network controls.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                        Button(
                            onClick = onOpenSetup,
                            enabled = connected,
                            colors = ButtonDefaults.buttonColors(containerColor = NrAccent),
                        ) {
                            Text("Open Mesh Setup")
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = NrSurface),
                    border = BorderStroke(0.5.dp, NrOutline),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Network control",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = NrOnSurface,
                        )
                        Text(
                            text = "Activation is role-managed by election. The app controls whether this USB-connected node participates.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                        if (!active) {
                            if (hasStoredCredentials) {
                                Button(
                                    onClick = onAuthenticateStored,
                                    enabled = connected,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = CategoryBleBlue),
                                ) {
                                    Text("Authenticate & Activate")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = onOpenSetup,
                                    enabled = connected,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Open Mesh Setup to authenticate")
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = onDeactivate,
                            enabled = connected && active,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Deactivate mesh")
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurface),
                border = BorderStroke(0.5.dp, NrOutline),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Nodes (${nodes.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = NrOnSurface,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = onRefresh,
                            enabled = connected,
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh mesh status")
                        }
                    }

                    if (nodes.isEmpty()) {
                        Text(
                            text = "No mesh nodes observed yet. The elected master appears here after activation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    } else {
                        nodes.forEach { node ->
                            MeshNodeRow(node)
                        }
                        TextButton(
                            onClick = onClearNodes,
                            enabled = nodes.isNotEmpty(),
                        ) {
                            Text("Clear node list")
                        }
                    }
                }
            }

            if (!lastError.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = StatusRed.copy(alpha = 0.12f)),
                    border = BorderStroke(0.5.dp, StatusRed.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Mesh error",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusRed,
                        )
                        SelectionContainer {
                            Text(
                                text = lastError,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = NrOnSurface,
                            )
                        }
                        TextButton(onClick = onClearError) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun MeshStatusCard(
    connected: Boolean,
    initialized: Boolean,
    role: String,
    sessionId: Long?,
    nodeId: String,
    peerCount: Int,
    active: Boolean,
    hasStoredCredentials: Boolean,
    storedKeyId: String?,
) {
    val statusColor = when {
        !connected -> StatusNeutral
        !initialized -> StatusAmber
        role == "master" -> StatusGreen
        role == "client" -> CategoryBleBlue
        role == "candidate" -> StatusAmber
        active -> NrAccent
        else -> StatusNeutral
    }
    val statusText = when {
        !connected -> "Device needed"
        !initialized -> "Not provisioned"
        role == "disabled" -> "Provisioned / inactive"
        else -> role.replaceFirstChar { it.uppercase() }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SettingsInputAntenna,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Mesh Network",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NrOnSurface,
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(statusColor, RoundedCornerShape(50)),
                )
            }

            MeshStatusRow("Node ID", nodeId.ifBlank { "-" }, monospace = true)
            MeshStatusRow("Role", role)
            MeshStatusRow("Active", if (active) "yes" else "no")
            MeshStatusRow("Session", sessionId?.toString() ?: "-", monospace = true)
            MeshStatusRow("Peers", peerCount.toString(), monospace = true)
            MeshStatusRow(
                "App credentials",
                if (hasStoredCredentials) {
                    storedKeyId?.takeIf { it.isNotBlank() }?.let { "saved ($it)" } ?: "saved"
                } else {
                    "not saved"
                },
            )
        }
    }
}

@Composable
private fun MeshStatusRow(label: String, value: String, monospace: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = NrOnSurfaceVariant,
        )
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                color = NrOnSurface,
            )
        }
    }
}

@Composable
private fun MeshNodeRow(node: MeshNodeStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (node.online) StatusGreen else StatusNeutral, RoundedCornerShape(50)),
            )
            Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = node.nodeId,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = NrOnSurface,
                )
                Text(
                    text = "${node.role.ifBlank { "unknown" }}  •  " +
                        (node.rssi?.takeIf { it != 0 }?.let { "$it dBm" } ?: "RSSI -"),
                    style = MaterialTheme.typography.labelSmall,
                    color = NrOnSurfaceVariant,
                )
            }
            Text(
                text = if (node.online) "online" else "offline",
                style = MaterialTheme.typography.labelSmall,
                color = if (node.online) StatusGreen else StatusNeutral,
            )
        }
    }
}
