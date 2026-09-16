package com.swp81x.nrsuite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.eapol.EapolHandshake
import com.swp81x.nrsuite.ui.components.NetworkTargetRow
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import org.json.JSONObject

@Composable
fun EvilTwinScreen(
    connected: Boolean,
    running: Boolean,
    scanning: Boolean,
    networks: List<JSONObject>,
    handshake: EapolHandshake,
    passwords: List<String>,
    selectedHtmlName: String?,
    onScanWifi: () -> Unit,
    onChooseHtml: () -> Unit,
    onStart: (ssid: String, channel: Int, targetBssid: String) -> Unit,
    onStop: () -> Unit,
    onClearPasswords: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var ssid by remember { mutableStateOf("Free WiFi") }
    var channel by remember { mutableStateOf(6) }
    var targetBssid by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = "Evil Twin",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Portal + deauth + EAPOL capture workflow (beta)",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                StatusIndicator(
                    label = when {
                        !connected -> "No device connected"
                        running -> "Evil Twin running"
                        else -> "Ready"
                    },
                    color = when {
                        !connected -> StatusNeutral
                        running -> StatusGreen
                        else -> StatusNeutral
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "EAPOL: M1 ${if (handshake.m1) "✓" else "—"}  M2 ${if (handshake.m2) "✓" else "—"}  " +
                        "M3 ${if (handshake.m3) "✓" else "—"}  M4 ${if (handshake.m4) "✓" else "—"}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = if (handshake.isComplete) StatusGreen else NrOnSurfaceVariant,
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Target network", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onScanWifi,
                    enabled = connected && !running && !scanning,
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = null)
                    Spacer(Modifier.height(2.dp))
                    Text(if (scanning) "Scanning..." else "Scan WiFi")
                }
                if (networks.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(
                        modifier = Modifier.height(180.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(networks.take(8), key = { it.optString("bssid", it.toString()) }) { network ->
                            val bssidValue = network.optString("bssid")
                            NetworkTargetRow(
                                ssid = network.optString("ssid").ifBlank { "(hidden)" },
                                bssid = bssidValue,
                                channel = network.optInt("channel", 1),
                                rssi = network.optInt("rssi", -100),
                                security = network.optString("security", "?"),
                                selected = targetBssid.equals(bssidValue, ignoreCase = true),
                                onClick = {
                                    targetBssid = bssidValue
                                    channel = network.optInt("channel", channel)
                                    val apSsid = network.optString("ssid")
                                    if (apSsid.isNotBlank()) ssid = apSsid
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Target BSSID: ${targetBssid.ifBlank { "none selected" }}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = if (targetBssid.isBlank()) StatusAmber else NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = ssid,
                    onValueChange = { ssid = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !running,
                    label = { Text("SSID") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Channel: $channel",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { channel = (channel - 1).coerceIn(1, 13) }, enabled = !running) {
                        Text("-", style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(onClick = { channel = (channel + 1).coerceIn(1, 13) }, enabled = !running) {
                        Text("+", style = MaterialTheme.typography.titleLarge)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = selectedHtmlName ?: "No custom HTML selected (placeholder page)",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onChooseHtml, enabled = !running) {
                        Text("Choose HTML")
                    }
                    if (running) {
                        Button(onClick = onStop) {
                            Text("Stop")
                        }
                    } else {
                        Button(
                            onClick = { onStart(ssid.trim(), channel, targetBssid.trim()) },
                            enabled = connected && targetBssid.isNotBlank(),
                        ) {
                            Text("Start Evil Twin")
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Captured passwords (${passwords.size})",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(passwords.joinToString("\n")))
                        },
                        enabled = passwords.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy passwords")
                    }
                    IconButton(onClick = onClearPasswords, enabled = passwords.isNotEmpty()) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear passwords")
                    }
                }
                if (passwords.isEmpty()) {
                    Text(
                        text = "Passwords submitted to the portal will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                } else {
                    passwords.reversed().forEach { password ->
                        Text(
                            text = "$password  ·  verification pending (beta)",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = NrOnSurfaceVariant,
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "EAPOL-based verification is not implemented yet. Captured passwords are stored for the next beta stage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusAmber,
                )
            }
        }
    }
}
