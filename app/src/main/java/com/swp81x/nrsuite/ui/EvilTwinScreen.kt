package com.swp81x.nrsuite.ui

import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swp81x.nrsuite.core.eapol.EapolHandshake
import com.swp81x.nrsuite.core.wpa.EvilTwinResult
import com.swp81x.nrsuite.ui.components.HtmlUploadSection
import com.swp81x.nrsuite.ui.components.NetworkTargetRow
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusRed
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import org.json.JSONObject

@Composable
fun EvilTwinScreen(
    connected: Boolean,
    running: Boolean,
    scanning: Boolean,
    networks: List<JSONObject>,
    handshake: EapolHandshake,
    results: List<EvilTwinResult>,
    eventLog: List<String>,
    selectedHtmlName: String?,
    htmlUploading: Boolean,
    htmlProgress: Int,
    htmlComplete: Boolean,
    onClearEventLog: () -> Unit,
    onScanWifi: () -> Unit,
    onChooseHtml: () -> Unit,
    onClearHtml: () -> Unit,
    onStart: (ssid: String, channel: Int, targetBssid: String) -> Unit,
    onStop: () -> Unit,
    onClearPasswords: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var ssid by rememberSaveable { mutableStateOf("Free WiFi") }
    var channel by rememberSaveable { mutableStateOf(6) }
    var targetBssid by rememberSaveable { mutableStateOf("") }
    var confirmStart by rememberSaveable { mutableStateOf(false) }
    var confirmStop by rememberSaveable { mutableStateOf(false) }
    var targetExpanded by rememberSaveable { mutableStateOf(true) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(running) {
        if (running) targetExpanded = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Overview") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Logs (${eventLog.size})") })
        }

        if (selectedTab == 0) {
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
                    text = "Portal + deauth + EAPOL capture workflow",
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EapolBadge("M1", handshake.m1)
                    EapolBadge("M2", handshake.m2)
                    EapolBadge("M3", handshake.m3)
                    EapolBadge("M4", handshake.m4)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = NrAccent,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Target network", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = targetBssid.ifBlank { "Scan and select an access point" },
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = NrOnSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { targetExpanded = !targetExpanded }) {
                        Icon(
                            imageVector = if (targetExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (targetExpanded) "Collapse target selection" else "Expand target selection",
                        )
                    }
                }
                if (targetExpanded && !running) {
                    OutlinedButton(
                        onClick = onScanWifi,
                        enabled = connected && !scanning,
                    ) {
                        Text(if (scanning) "Scanning..." else "Scan WiFi for targets")
                    }
                    if (networks.isEmpty() && !scanning) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Tap \"Scan WiFi for targets\" to find nearby access points.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                    if (networks.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = NrSurfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            networks.forEach { network ->
                                val bssidValue = network.optString("bssid")
                                NetworkTargetRow(
                                    ssid = network.optString("ssid").ifBlank { "(hidden)" },
                                    bssid = bssidValue,
                                    channel = network.optInt("channel", 1),
                                    rssi = network.optInt("rssi", -100),
                                    security = network.optString("security", "?"),
                                    selected = targetBssid.equals(bssidValue, ignoreCase = true),
                                    enabled = true,
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
                }
                if (targetExpanded) {
                    Spacer(Modifier.height(8.dp))
                    HtmlUploadSection(
                        selectedName = selectedHtmlName,
                        uploading = htmlUploading,
                        progress = htmlProgress,
                        completed = htmlComplete,
                        required = true,
                        enabled = !running,
                        onChoose = onChooseHtml,
                        onClear = onClearHtml,
                    )
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
                        text = "Captured passwords (${results.size})",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(results.joinToString("\n") { it.password }))
                        },
                        enabled = results.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy passwords")
                    }
                    IconButton(onClick = onClearPasswords, enabled = results.isNotEmpty()) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear passwords")
                    }
                }
                if (results.isEmpty()) {
                    Text(
                        text = "No password captured yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                } else {
                    results.reversed().forEach { result ->
                        val statusSymbol = when (result.status) {
                            EvilTwinResult.Status.CORRECT -> "✓"
                            EvilTwinResult.Status.INCORRECT -> "✗"
                            EvilTwinResult.Status.PENDING -> "–"
                            EvilTwinResult.Status.INVALID_LENGTH -> "!"
                        }
                        val statusColor = when (result.status) {
                            EvilTwinResult.Status.CORRECT -> StatusGreen
                            EvilTwinResult.Status.INCORRECT -> MaterialTheme.colorScheme.error
                            EvilTwinResult.Status.PENDING -> Color(0xFF3D9BFF)
                            EvilTwinResult.Status.INVALID_LENGTH -> StatusAmber
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "[${result.timestamp}] ${result.password}  :  $statusSymbol",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = statusColor,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { clipboard.setText(AnnotatedString(result.password)) },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy password",
                                    tint = NrOnSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                val verificationText = when {
                    results.any { it.status == EvilTwinResult.Status.CORRECT } ->
                        "A submitted password matched the captured WPA2 handshake."
                    results.any { it.status == EvilTwinResult.Status.INCORRECT } ->
                        "Submitted passwords were checked against the captured WPA2 handshake."
                    results.any { it.status == EvilTwinResult.Status.PENDING } ->
                        "Waiting for a complete M1 + M2 handshake with matching BSSID/STA before verification."
                    results.any { it.status == EvilTwinResult.Status.INVALID_LENGTH } ->
                        "One or more submitted passwords are outside the WPA2 passphrase length range (8-63 characters)."
                    else ->
                        "WPA2 verification runs automatically after a password is submitted and matching M1 + M2 material is captured."
                }
                Text(
                    text = verificationText,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        results.any { it.status == EvilTwinResult.Status.CORRECT } -> StatusGreen
                        results.any { it.status == EvilTwinResult.Status.INCORRECT } -> MaterialTheme.colorScheme.error
                        results.any { it.status == EvilTwinResult.Status.PENDING } -> StatusAmber
                        results.any { it.status == EvilTwinResult.Status.INVALID_LENGTH } -> StatusAmber
                        else -> NrOnSurfaceVariant
                    },
                )
            }
        }
        } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Evil Twin logs (${eventLog.size})",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(eventLog.joinToString("\n")))
                        },
                        enabled = eventLog.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy logs")
                    }
                    IconButton(onClick = onClearEventLog, enabled = eventLog.isNotEmpty()) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear logs")
                    }
                }
                if (eventLog.isEmpty()) {
                    Text(
                        text = "Portal page views, client associations, and POST data will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                } else {
                    eventLog.reversed().forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = NrOnSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
        }

        if (confirmStart) {
            AlertDialog(
                onDismissRequest = { confirmStart = false },
                title = { Text("Start Evil Twin?") },
                text = {
                    Text(
                        "This starts an AP, optionally deauths the selected target, " +
                            "and captures EAPOL/password attempts.\n\n" +
                            "SSID: $ssid\n" +
                            "Channel: $channel\n" +
                            "Target: ${targetBssid.ifBlank { "none selected" }}\n\n" +
                            "Only use this on networks you own or are authorized to test.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmStart = false
                            onStart(ssid.trim(), channel, targetBssid.trim())
                        },
                    ) { Text("Start") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmStart = false }) { Text("Cancel") }
                },
            )
        }

        if (confirmStop) {
            AlertDialog(
                onDismissRequest = { confirmStop = false },
                title = { Text("Stop Evil Twin?") },
                text = { Text("This stops the portal, deauth workflow, and EAPOL capture.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmStop = false
                            onStop()
                        },
                    ) { Text("Stop") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmStop = false }) { Text("Cancel") }
                },
            )
        }

        Spacer(Modifier.height(80.dp))
        }

        if (connected) {
            FloatingActionButton(
                onClick = {
                    when {
                        running -> confirmStop = true
                        targetBssid.isBlank() -> Toast.makeText(
                            context,
                            "Select a target network first.",
                            Toast.LENGTH_SHORT,
                        ).show()
                        selectedHtmlName == null -> Toast.makeText(
                            context,
                            "Choose an HTML file first.",
                            Toast.LENGTH_SHORT,
                        ).show()
                        htmlUploading -> Toast.makeText(
                            context,
                            "Wait for the HTML upload to finish.",
                            Toast.LENGTH_SHORT,
                        ).show()
                        else -> confirmStart = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = if (running) StatusRed else NrAccent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (running) "Stop Evil Twin" else "Start Evil Twin",
                )
            }
        }
    }
}


@Composable
private fun EapolBadge(label: String, captured: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (captured) NrAccent.copy(alpha = 0.16f) else NrSurfaceVariant,
        ),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.5.dp, if (captured) NrAccent else NrOutline),
    ) {
        Text(
            text = if (captured) "$label ✓" else label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = if (captured) NrAccent else NrOnSurfaceVariant,
        )
    }
}
