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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed

@Composable
fun BadUsbScreen(
    connected: Boolean,
    uploading: Boolean,
    progress: Int,
    selectedPayloadName: String?,
    onChoosePayload: () -> Unit,
    onClearPayload: () -> Unit,
    onArm: (mscMode: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var configExpanded by remember { mutableStateOf(true) }
    var mscMode by remember { mutableStateOf(false) }
    var confirmArm by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            ConfigZone(
                connected = connected,
                uploading = uploading,
                expanded = configExpanded,
                selectedPayloadName = selectedPayloadName,
                mscMode = mscMode,
                onToggle = { configExpanded = !configExpanded },
                onChoosePayload = onChoosePayload,
                onClearPayload = onClearPayload,
                onMscChange = { mscMode = it },
            )

            Spacer(Modifier.height(10.dp))

            ResultZone(
                connected = connected,
                uploading = uploading,
                progress = progress,
                selectedPayloadName = selectedPayloadName,
                modifier = Modifier.weight(1f),
            )
        }

        if (connected && selectedPayloadName != null) {
            FloatingActionButton(
                onClick = { if (!uploading) confirmArm = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = if (uploading) StatusAmber else NrAccent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = if (uploading) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (uploading) "Uploading" else "Arm payload",
                )
            }
        }
    }

    if (confirmArm) {
        AlertDialog(
            onDismissRequest = { confirmArm = false },
            title = { Text("Arm BadUSB payload?") },
            text = {
                Text(
                    "The payload will be uploaded to the device and armed. It executes once on the next " +
                        "power-on/re-plug" + if (mscMode) " and also exposes mass storage." else "." +
                        " Only use payloads on devices you own or are authorized to test."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmArm = false
                        onArm(mscMode)
                    },
                ) {
                    Text("Arm")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmArm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ConfigZone(
    connected: Boolean,
    uploading: Boolean,
    expanded: Boolean,
    selectedPayloadName: String?,
    mscMode: Boolean,
    onToggle: () -> Unit,
    onChoosePayload: () -> Unit,
    onClearPayload: () -> Unit,
    onMscChange: (Boolean) -> Unit,
) {
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
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = null,
                    tint = NrAccent,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "BadUSB configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Native USB HID DuckyScript",
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
                        text = "DuckyScript payload",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = selectedPayloadName ?: "No payload selected",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = NrOnSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onChoosePayload, enabled = !uploading) {
                            Text("Choose payload")
                        }
                        OutlinedButton(onClick = onClearPayload, enabled = !uploading && selectedPayloadName != null) {
                            Text("Clear")
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Also expose mass storage",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = mscMode,
                            onCheckedChange = onMscChange,
                            enabled = !uploading,
                        )
                    }

                    if (!connected) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Connect an ESP32-S2 or ESP32-S3 before arming a payload.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAmber,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultZone(
    connected: Boolean,
    uploading: Boolean,
    progress: Int,
    selectedPayloadName: String?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text(
                text = "Payload status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            StatusIndicator(
                label = when {
                    !connected -> "No device connected"
                    uploading -> "Uploading payload"
                    selectedPayloadName != null -> "Ready to arm"
                    else -> "No payload selected"
                },
                color = when {
                    !connected -> StatusNeutral
                    uploading -> StatusAmber
                    selectedPayloadName != null -> StatusGreen
                    else -> StatusNeutral
                },
            )

            if (uploading || progress > 0) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Upload progress",
                    style = MaterialTheme.typography.labelLarge,
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$progress%",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
            }

            if (!connected) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Connect an ESP32-S2/S3 to use BadUSB.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else if (selectedPayloadName == null) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Choose a .txt or .conf DuckyScript file, then arm it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }
    }
}
