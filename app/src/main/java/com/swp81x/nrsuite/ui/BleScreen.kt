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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
fun BleScreen(
    connected: Boolean,
    advertising: Boolean,
    bleConnected: Boolean,
    peer: String,
    selectedPayloadName: String?,
    onChoosePayload: () -> Unit,
    onClearPayload: () -> Unit,
    onStartAdvertising: (String) -> Unit,
    onStop: () -> Unit,
    onRunPayload: () -> Unit,
    onSendText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var advertiseName by remember { mutableStateOf("NRSuite Keyboard") }
    var realtimeText by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = NrAccent,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "BLE HID",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "BadBLE payloads and realtime keyboard input",
                                style = MaterialTheme.typography.bodySmall,
                                color = NrOnSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = advertiseName,
                        onValueChange = { advertiseName = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !advertising,
                        label = { Text("Advertised name") },
                        singleLine = true,
                    )

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "DuckyScript payload",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = selectedPayloadName ?: "No payload selected",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = NrOnSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onChoosePayload, enabled = !advertising) {
                            Text("Choose payload")
                        }
                        OutlinedButton(
                            onClick = onClearPayload,
                            enabled = !advertising && selectedPayloadName != null,
                        ) {
                            Text("Clear")
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onRunPayload,
                        enabled = connected && bleConnected && selectedPayloadName != null,
                    ) {
                        Text("Run payload")
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Realtime keyboard",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = realtimeText,
                            onValueChange = { realtimeText = it },
                            modifier = Modifier.weight(1f),
                            enabled = connected && bleConnected,
                            label = { Text("Text or DuckyScript line") },
                            singleLine = true,
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                onSendText(realtimeText)
                                realtimeText = ""
                            },
                            enabled = connected && bleConnected && realtimeText.isNotBlank(),
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send")
                        }
                    }

                    if (!connected) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Connect a BLE-capable ESP32 (C3, S3, or classic) before using this module.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAmber,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
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
                        text = "BLE status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    StatusIndicator(
                        label = when {
                            !connected -> "No device connected"
                            bleConnected -> "Host connected"
                            advertising -> "Advertising / waiting for host"
                            else -> "Idle"
                        },
                        color = when {
                            !connected -> StatusNeutral
                            bleConnected -> StatusGreen
                            advertising -> StatusAmber
                            else -> StatusNeutral
                        },
                    )
                    if (peer.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Peer: $peer",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = NrOnSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Realtime input sends STRINGLN commands. Prefix with CTRL/ALT/GUI/SHIFT/DELAY to send raw DuckyScript.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
            }
        }

        if (connected && advertising) {
            FloatingActionButton(
                onClick = onStop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = StatusRed,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Stop, contentDescription = "Stop BLE")
            }
        } else if (connected) {
            FloatingActionButton(
                onClick = { onStartAdvertising(advertiseName) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = NrAccent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Start BLE")
            }
        }
    }
}
