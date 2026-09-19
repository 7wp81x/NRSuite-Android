package com.swp81x.nrsuite.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.credentials.CredentialSession
import com.swp81x.nrsuite.core.wifi.DetectedSsid
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOnSurface
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusRed
import java.io.File

@Composable
fun WpaCrackerScreen(
    sessions: List<CredentialSession>,
    selectedSession: CredentialSession?,
    customPcapName: String?,
    customPcapValid: Boolean,
    customPcapValidating: Boolean,
    customPcapMessage: String,
    customSsidOptions: List<DetectedSsid>,
    customSsidSelected: String?,
    customSsidManualEnabled: Boolean,
    customSsid: String,
    wordlistName: String?,
    running: Boolean,
    tested: Long,
    speed: Double,
    result: String?,
    status: String,
    onSelectSession: (CredentialSession?) -> Unit,
    onChooseCustomPcap: () -> Unit,
    onClearCustomPcap: () -> Unit,
    onSelectCustomSsid: (String) -> Unit,
    onUseManualCustomSsid: () -> Unit,
    onCustomSsidChange: (String) -> Unit,
    onChooseWordlist: () -> Unit,
    onClearWordlist: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val candidates = sessions.filter { path ->
        !path.pcapPath.isNullOrBlank() && File(path.pcapPath!!).exists()
    }
    val customSelected = customPcapName != null
    val canStart = !running && wordlistName != null && (
        if (customSelected) customPcapValid && customSsid.isNotBlank()
        else selectedSession != null
    )

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusIndicator(
                label = when {
                    running -> "Cracking..."
                    result != null -> "Password found"
                    else -> "Offline WPA/WPA2 cracker"
                },
                color = when {
                    running -> StatusAmber
                    result != null -> StatusGreen
                    else -> NrOnSurfaceVariant
                },
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurface),
                border = BorderStroke(0.5.dp, NrOutline),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = NrAccent,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Target handshake",
                                fontWeight = FontWeight.SemiBold,
                                color = NrOnSurface,
                            )
                            Text(
                                text = when {
                                    selectedSession != null -> {
                                        "${selectedSession.ssid.ifBlank { "(hidden)" }} · " +
                                            "${selectedSession.bssid.ifBlank { "unknown" }}"
                                    }
                                    customSelected -> "Custom PCAP: ${customPcapName ?: "file"}"
                                    else -> "Select a saved session or choose a custom PCAP"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = NrOnSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onChooseCustomPcap, enabled = !running) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (customSelected) "Change custom PCAP" else "Choose custom PCAP")
                        }
                        if (customSelected) {
                            OutlinedButton(onClick = onClearCustomPcap, enabled = !running) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Clear")
                            }
                        }
                    }

                    if (customSelected) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = customPcapName.orEmpty(),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = NrOnSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        if (customPcapValidating) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Validating EAPOL...",
                                style = MaterialTheme.typography.bodySmall,
                                color = NrOnSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = customPcapMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (customPcapValid) StatusGreen else StatusAmber,
                            )
                        }
                        Spacer(Modifier.height(8.dp))

                        if (customSsidOptions.isNotEmpty()) {
                            Text(
                                text = "Detected SSIDs",
                                style = MaterialTheme.typography.labelMedium,
                                color = NrOnSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                customSsidOptions.forEach { option ->
                                    NrFilterChip(
                                        selected = !customSsidManualEnabled &&
                                            customSsidSelected == option.ssid,
                                        onClick = { onSelectCustomSsid(option.ssid) },
                                        label = option.ssid,
                                        enabled = !running,
                                    )
                                }
                                NrFilterChip(
                                    selected = customSsidManualEnabled,
                                    onClick = onUseManualCustomSsid,
                                    label = "Manual",
                                    enabled = !running,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        OutlinedTextField(
                            value = customSsid,
                            onValueChange = onCustomSsidChange,
                            label = { Text(if (customSsidOptions.isEmpty()) "Target SSID" else "Selected SSID") },
                            placeholder = { Text("Network name") },
                            singleLine = true,
                            enabled = !running && customSsidManualEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                    }

                    Text(
                        text = "Saved sessions",
                        style = MaterialTheme.typography.labelMedium,
                        color = NrOnSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))

                    if (candidates.isEmpty()) {
                        Text(
                            text = "No saved sessions with a PCAP file. Run Evil Twin and capture a handshake first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    } else {
                        candidates.forEach { session ->
                            val selected = session.id == selectedSession?.id
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable(enabled = !running) { onSelectSession(session) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) NrSurfaceVariant else NrSurface,
                                ),
                                border = BorderStroke(
                                    width = if (selected) 1.dp else 0.5.dp,
                                    color = if (selected) NrAccent else NrOutline,
                                ),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(
                                        text = session.ssid.ifBlank { "(hidden)" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = NrOnSurface,
                                    )
                                    Text(
                                        text = session.bssid,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = NrOnSurfaceVariant,
                                    )
                                    Text(
                                        text = session.pcapPath?.substringAfterLast('/') ?: "no pcap",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = NrOnSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurface),
                border = BorderStroke(0.5.dp, NrOutline),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        text = "Wordlist",
                        fontWeight = FontWeight.SemiBold,
                        color = NrOnSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = wordlistName ?: "No wordlist selected",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = NrOnSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onChooseWordlist, enabled = !running) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (wordlistName == null) "Choose wordlist" else "Change wordlist")
                        }
                        if (wordlistName != null) {
                            OutlinedButton(onClick = onClearWordlist, enabled = !running) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Clear")
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurface),
                border = BorderStroke(0.5.dp, NrOutline),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        text = "Result",
                        fontWeight = FontWeight.SemiBold,
                        color = NrOnSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result != null) StatusGreen else NrOnSurfaceVariant,
                    )

                    if (running || tested > 0L) {
                        Spacer(Modifier.height(10.dp))
                        if (running) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(6.dp))
                        }
                        Text(
                            text = "Tested $tested candidate(s)  ·  ${"%.1f".format(speed)}/s",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = NrOnSurfaceVariant,
                        )
                    }

                    result?.let { password ->
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = password,
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                                color = StatusGreen,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = {
                                clipboard.setText(AnnotatedString(password))
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy recovered password",
                                    tint = NrOnSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }

        FloatingActionButton(
            onClick = { if (running) onStop() else onStart() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .alpha(if (running || canStart) 1f else 0.4f),
            containerColor = if (running) StatusRed else NrAccent,
            contentColor = if (running) NrOnSurface else NrSurface,
        ) {
            Icon(
                imageVector = if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (running) "Stop cracker" else "Start cracker",
            )
        }
    }
}
