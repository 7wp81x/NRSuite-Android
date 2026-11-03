package com.swp81x.nrsuite.ui

import com.swp81x.nrsuite.ui.components.NrAnimatedVisibility
import com.swp81x.nrsuite.core.wifi.WIFI_AUTO_CHANNELS
import com.swp81x.nrsuite.core.wifi.normalizeWifiChannel
import com.swp81x.nrsuite.core.wifi.normalizeWifiChannelForAllowed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.ui.platform.LocalClipboardManager
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.credentials.CapturedCredential
import com.swp81x.nrsuite.core.portal.PortalVisitor
import com.swp81x.nrsuite.ui.util.copyWithToast
import com.swp81x.nrsuite.ui.components.HtmlTemplatesTab
import com.swp81x.nrsuite.ui.components.HtmlUploadSection
import com.swp81x.nrsuite.ui.components.PortalFirmwareNoticeToggle
import com.swp81x.nrsuite.ui.components.PortalVisitorsTab
import com.swp81x.nrsuite.ui.components.RoundedTabRow
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.components.WifiChannelStepper
import com.swp81x.nrsuite.ui.components.WifiChannelSummaryPill
import com.swp81x.nrsuite.ui.components.NrStatusAction
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusInfo
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed

private val MAC_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

@Composable
fun PortalScreen(
    connected: Boolean,
    running: Boolean,
    starting: Boolean = false,
    stopping: Boolean = false,
    htmlSize: Int,
    htmlComplete: Boolean,
    activeSsid: String,
    activeChannel: Int,
    portalViews: Int,
    portalClients: Int,
    capturedData: Int,
    credentials: List<CapturedCredential>,
    selectedHtmlName: String?,
    selectedHtmlSize: Long?,
    htmlUploading: Boolean,
    htmlProgress: Int,
    eventLog: List<String>,
    visitors: List<PortalVisitor> = emptyList(),
    onChooseHtml: () -> Unit,
    htmlTemplates: List<com.swp81x.nrsuite.core.portal.HtmlTemplate> = emptyList(),
    htmlTemplateMessage: String? = null,
    htmlTemplateMessageIsError: Boolean = false,
    onImportHtmlTemplate: () -> Unit = {},
    onUseHtmlTemplate: (String) -> Unit = {},
    onRenameHtmlTemplate: (String, String) -> Unit = { _, _ -> },
    onDeleteHtmlTemplate: (String) -> Unit = {},
    onClearEventLog: () -> Unit,
    onClearVisitors: () -> Unit = {},
    onStart: (ssid: String, channel: Int, targetBssid: String) -> Unit,
    onStop: () -> Unit,
    allowedChannels: List<Int> = emptyList(),
    firmwareNoticeEnabled: Boolean = true,
    onFirmwareNoticeEnabledChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var configExpanded by rememberSaveable { mutableStateOf(true) }
    var ssid by rememberSaveable { mutableStateOf("Free WiFi") }
    var channel by rememberSaveable { mutableStateOf(6) }
    var targetBssid by rememberSaveable { mutableStateOf("") }
    var confirmStart by rememberSaveable { mutableStateOf(false) }

    val effectiveAllowedChannels = allowedChannels.ifEmpty { WIFI_AUTO_CHANNELS }
    LaunchedEffect(effectiveAllowedChannels) {
        channel = normalizeWifiChannelForAllowed(channel, effectiveAllowedChannels)
    }

    LaunchedEffect(running) {
        if (running) configExpanded = false
    }

    val validTargetBssid = targetBssid.isBlank() || MAC_REGEX.matches(targetBssid.trim())

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            PortalFirmwareNoticeToggle(
                enabled = firmwareNoticeEnabled,
                onEnabledChange = onFirmwareNoticeEnabledChange,
            )
            Spacer(Modifier.height(6.dp))
            RoundedTabRow(
                selectedIndex = selectedTab,
                tabs = listOf(
                    "Overview",
                    "Logs (${eventLog.size})",
                    "Visitors (${visitors.size})",
                    "HTML Templates",
                ),
                onSelect = { selectedTab = it },
            )

            Spacer(Modifier.height(10.dp))

            if (selectedTab == 0) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                ConfigZone(
                    connected = connected,
                    running = running,
                    expanded = configExpanded,
                    ssid = ssid,
                    channel = channel,
                    targetBssid = targetBssid,
                    validTargetBssid = validTargetBssid,
                    allowedChannels = allowedChannels,
                    selectedHtmlName = selectedHtmlName,
                    selectedHtmlSize = selectedHtmlSize,
                    htmlUploading = htmlUploading,
                    htmlProgress = htmlProgress,
                    htmlComplete = htmlComplete,
                    templates = htmlTemplates,
                    onSelectTemplate = onUseHtmlTemplate,
                    onToggle = { configExpanded = !configExpanded },
                    onSsidChange = { ssid = it },
                    onChannelChange = {
                        channel = normalizeWifiChannel(
                            it,
                            allowedChannels.ifEmpty { com.swp81x.nrsuite.core.wifi.WIFI_AUTO_CHANNELS },
                            fallback = channel,
                        )
                    },
                    onTargetBssidChange = { targetBssid = it },
                    onChooseHtml = onChooseHtml,
                )

                if (running || activeSsid.isNotBlank() || portalViews > 0 ||
                    portalClients > 0 || capturedData > 0
                ) {
                    Spacer(Modifier.height(10.dp))
                    ResultZone(
                        connected = connected,
                        running = running,
                        htmlSize = htmlSize,
                        htmlComplete = htmlComplete,
                        activeSsid = activeSsid,
                        activeChannel = activeChannel,
                        portalViews = portalViews,
                        portalClients = portalClients,
                        capturedData = capturedData,
                    )
                }

                if (credentials.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    CapturedCredentialsZone(credentials)
                }
                }
            } else if (selectedTab == 1) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.fillMaxSize().padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Portal event log",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.copyWithToast(
                                        context,
                                        eventLog.joinToString("\n"),
                                        "Logs copied",
                                    )
                                },
                                enabled = eventLog.isNotEmpty(),
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy logs")
                            }
                            IconButton(
                                onClick = onClearEventLog,
                                enabled = eventLog.isNotEmpty(),
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear logs")
                            }
                        }
                        if (eventLog.isEmpty()) {
                            Text(
                                text = "No portal events yet. Page views, client associations, and POST data will appear here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NrOnSurfaceVariant,
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(eventLog.reversed()) { line ->
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = NrOnSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (selectedTab == 2) {
                PortalVisitorsTab(
                    visitors = visitors,
                    onClearVisitors = onClearVisitors,
                    modifier = Modifier.weight(1f),
                    emptyText = "No portal visitors yet. Page views, form posts, MAC, IP, and User-Agent will appear here.",
                )
            } else {
                HtmlTemplatesTab(
                    templates = htmlTemplates,
                    message = htmlTemplateMessage,
                    messageIsError = htmlTemplateMessageIsError,
                    enabled = !running && !htmlUploading,
                    selectedName = selectedHtmlName,
                    onImport = onImportHtmlTemplate,
                    onUse = {
                        onUseHtmlTemplate(it.id)
                        selectedTab = 0
                    },
                    onRename = { template, name -> onRenameHtmlTemplate(template.id, name) },
                    onDelete = { onDeleteHtmlTemplate(it.id) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (connected) {
            ModuleActionFab(
                onClick = {
                    when {
                        running -> onStop()
                        selectedHtmlName == null -> {
                            configExpanded = true
                            Toast.makeText(context, "Choose an HTML template or file first.", Toast.LENGTH_SHORT).show()
                        }
                        htmlUploading -> {
                            configExpanded = true
                            Toast.makeText(context, "Wait for the HTML upload to finish.", Toast.LENGTH_SHORT).show()
                        }
                        validTargetBssid -> confirmStart = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                enabled = !starting && !stopping,
                statusAction = if (running) NrStatusAction.STOP else NrStatusAction.START,
            ) {
                if (starting || stopping) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        imageVector = if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (running) "Stop portal" else "Start portal",
                    )
                }
            }
        }
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("Start captive portal?") },
            text = {
                Text(
                    "SSID: $ssid\n" +
                        "Channel: $channel\n" +
                        if (targetBssid.isBlank()) {
                            "No auto-EAPOL target selected."
                        } else {
                            "Auto-EAPOL/deauth target: $targetBssid"
                        } +
                        "\n\nOnly deploy this on networks and devices you own or are authorized to test."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmStart = false
                        onStart(ssid.trim(), channel, targetBssid.trim())
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
private fun ConfigZone(
    connected: Boolean,
    running: Boolean,
    expanded: Boolean,
    ssid: String,
    channel: Int,
    targetBssid: String,
    validTargetBssid: Boolean,
    allowedChannels: List<Int>,
    selectedHtmlName: String?,
    selectedHtmlSize: Long?,
    htmlUploading: Boolean,
    htmlProgress: Int,
    htmlComplete: Boolean,
    templates: List<com.swp81x.nrsuite.core.portal.HtmlTemplate>,
    onSelectTemplate: (String) -> Unit,
    onToggle: () -> Unit,
    onSsidChange: (String) -> Unit,
    onChannelChange: (Int) -> Unit,
    onTargetBssidChange: (String) -> Unit,
    onChooseHtml: () -> Unit,
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
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Portal configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (running) "Portal running" else "Soft AP + DNS + HTTP server",
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

            NrAnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = ssid,
                        onValueChange = onSsidChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !running,
                        label = { Text("SSID") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Channel",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        WifiChannelSummaryPill(channel)
                    }
                    Spacer(Modifier.height(6.dp))
                    WifiChannelStepper(
                        value = channel,
                        enabled = !running,
                        onChannelChange = onChannelChange,
                        allowedChannels = allowedChannels.ifEmpty {
                            com.swp81x.nrsuite.core.wifi.WIFI_AUTO_CHANNELS
                        },
                    )

                    Spacer(Modifier.height(12.dp))
                    HtmlUploadSection(
                        selectedName = selectedHtmlName,
                        selectedSizeBytes = selectedHtmlSize,
                        uploading = htmlUploading,
                        progress = htmlProgress,
                        completed = htmlComplete,
                        enabled = !running,
                        templates = templates,
                        onSelectTemplate = onSelectTemplate,
                        onChoose = onChooseHtml,
                    )

                }
            }
        }
    }
}

@Composable
private fun CapturedCredentialsZone(credentials: List<CapturedCredential>) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val latest = credentials.lastOrNull() ?: return
    val shownFields = latest.knownPortalFields()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Captured credentials (${credentials.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Latest submission: ${latest.capturedAt}  ·  ${latest.details.size} field(s)",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
            )

            Spacer(Modifier.height(6.dp))
            shownFields.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$label:",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                        modifier = Modifier.width(78.dp),
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = if (label == "Password") StatusGreen else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { clipboard.copyWithToast(context, value) }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy $label",
                            tint = NrOnSurfaceVariant,
                        )
                    }
                }
            }

            val extraFields = (latest.details.size - shownFields.size).coerceAtLeast(0)
            if (extraFields > 0) {
                Text(
                    text = "+$extraFields more field(s) in Credential Manager · Portal filter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else {
                Text(
                    text = "Full submissions are available in Credential Manager · Portal filter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }
    }
}

private fun CapturedCredential.knownPortalFields(): List<Pair<String, String>> {
    val known = listOf(
        "Username" to listOf("username", "user", "login"),
        "Email" to listOf("email", "mail"),
        "Password" to listOf("password", "pass"),
        "Phone" to listOf("phone", "mobile"),
    )
    val fields = linkedMapOf<String, String>()
    known.forEach { (label, keys) ->
        keys.forEach { key ->
            val value = details.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
            if (!value.isNullOrBlank()) {
                fields.putIfAbsent(label, value)
                return@forEach
            }
        }
    }

    if (fields.isEmpty()) {
        fields["Value"] = value
    }
    return fields.entries.map { it.key to it.value }
}

@Composable
private fun ResultZone(
    connected: Boolean,
    running: Boolean,
    starting: Boolean = false,
    stopping: Boolean = false,
    htmlSize: Int,
    htmlComplete: Boolean,
    activeSsid: String,
    activeChannel: Int,
    portalViews: Int,
    portalClients: Int,
    capturedData: Int,
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
                text = "Portal status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            StatusIndicator(
                label = when {
                    !connected -> "No device connected"
                    running -> "Portal active"
                    activeSsid.isNotBlank() -> "Stopped"
                    else -> "Idle"
                },
                color = when {
                    !connected -> StatusNeutral
                    running -> StatusGreen
                    activeSsid.isNotBlank() -> StatusInfo
                    else -> StatusNeutral
                },
            )

            if (activeSsid.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "SSID: $activeSsid",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
                if (activeChannel > 0) {
                    Text(
                        text = "Channel: $activeChannel",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = NrOnSurfaceVariant,
                    )
                }
            }

            if (running || activeSsid.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                MetricRow("Page views", portalViews.toString())
                MetricRow("Clients associated", portalClients.toString())
                MetricRow("Captured form posts", capturedData.toString())
            }

            if (connected && !running) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Configure the AP, choose an HTML template or file, then tap start.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = NrOnSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Medium,
        )
    }
}
