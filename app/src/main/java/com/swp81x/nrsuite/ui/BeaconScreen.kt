package com.swp81x.nrsuite.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.swp81x.nrsuite.core.wifi.WIFI_AUTO_CHANNELS
import com.swp81x.nrsuite.core.wifi.bandForWifiChannel
import com.swp81x.nrsuite.core.wifi.normalizeWifiChannel
import com.swp81x.nrsuite.core.wifi.normalizeWifiChannelForAllowed
import com.swp81x.nrsuite.ui.components.RoundedTabRow
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.components.WifiChannelStepper
import com.swp81x.nrsuite.ui.components.WifiChannelSummaryPill
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOnSurface
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusInfo
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed

private val RICKROLL_PRESET = listOf(
    "Never Gonna Give You Up",
    "Never Gonna Let You Down",
    "Never Gonna Run Around",
    "And Desert You",
)

@Composable
fun BeaconScreen(
    connected: Boolean,
    running: Boolean,
    sentFrames: Int,
    ssidCount: Int,
    activeChannel: Int,
    savedLists: Map<String, List<String>>,
    onSaveListAction: (String, List<String>) -> Unit,
    onRenameListAction: (String, String) -> Unit,
    onDeleteList: (String) -> Unit,
    onStart: (List<String>, Int, Int, Boolean, Boolean) -> Unit,
    onStop: () -> Unit,
    allowedChannels: List<Int> = emptyList(),
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var ssidsText by rememberSaveable {
        mutableStateOf(RICKROLL_PRESET.joinToString("\n"))
    }
    var channel by rememberSaveable { mutableIntStateOf(6) }
    var intervalMs by rememberSaveable { mutableIntStateOf(20) }
    var hidden by rememberSaveable { mutableStateOf(false) }
    var randomBssid by rememberSaveable { mutableStateOf(true) }
    var confirmStart by rememberSaveable { mutableStateOf(false) }
    var selectedListName by rememberSaveable { mutableStateOf<String?>("Rickroll") }
    var saveDialogVisible by rememberSaveable { mutableStateOf(false) }
    var saveName by rememberSaveable { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val effectiveAllowedChannels = allowedChannels.ifEmpty { WIFI_AUTO_CHANNELS }

    LaunchedEffect(effectiveAllowedChannels) {
        channel = normalizeWifiChannelForAllowed(channel, effectiveAllowedChannels)
    }
    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    ssidsText = stream.readBytes().toString(Charsets.UTF_8)
                }
            }
        }
    }

    val parsedSsids = remember(ssidsText) {
        ssidsText.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }
    val oversizedSsids = remember(parsedSsids) {
        parsedSsids.filter { it.toByteArray(Charsets.UTF_8).size > 32 }
    }
    val ssidValidationError = when {
        parsedSsids.isEmpty() -> "Add at least one SSID to start."
        parsedSsids.size > 32 -> "Firmware supports at most 32 SSIDs."
        oversizedSsids.isNotEmpty() -> "${oversizedSsids.size} SSID(s) exceed 32 bytes."
        else -> null
    }
    val sortedListNames = remember(savedLists) { savedLists.keys.sorted() }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RoundedTabRow(
                selectedIndex = selectedTab,
                tabs = listOf("Overview", "SSID Lists"),
                onSelect = { selectedTab = it },
            )

            if (selectedTab == 0) {
                if (running || sentFrames > 0) {
                    BroadcastStatusCard(
                        connected = connected,
                        running = running,
                        sentFrames = sentFrames,
                        ssidCount = ssidCount,
                        activeChannel = activeChannel,
                    )
                }

                CurrentSsidsCard(
                    ssidCount = parsedSsids.size,
                    ssids = parsedSsids.take(4),
                    selectedListName = selectedListName,
                    validationError = ssidValidationError,
                    onEdit = { selectedTab = 1 },
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = NrSurface),
                    border = BorderStroke(0.5.dp, NrOutline),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Channel & timing",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
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
                        WifiChannelStepper(
                            value = channel,
                            enabled = !running,
                            onChannelChange = {
                                channel = normalizeWifiChannel(
                                    it,
                                    allowedChannels.ifEmpty {
                                        com.swp81x.nrsuite.core.wifi.WIFI_AUTO_CHANNELS
                                    },
                                    fallback = channel,
                                )
                            },
                            allowedChannels = allowedChannels.ifEmpty {
                                com.swp81x.nrsuite.core.wifi.WIFI_AUTO_CHANNELS
                            },
                        )
                        NumberStepper(
                            label = "Interval",
                            value = intervalMs,
                            valueText = "$intervalMs ms",
                            enabled = !running,
                            onDecrease = { intervalMs = (intervalMs - 10).coerceIn(10, 2000) },
                            onIncrease = { intervalMs = (intervalMs + 10).coerceIn(10, 2000) },
                        )
                        SwitchRow(
                            label = "Hidden SSIDs",
                            checked = hidden,
                            enabled = !running,
                            onCheckedChange = { hidden = it },
                        )
                        SwitchRow(
                            label = "Stable BSSIDs",
                            checked = !randomBssid,
                            enabled = !running,
                            onCheckedChange = { randomBssid = !it },
                        )
                    }
                }
            } else {
                SsidCompositionCard(
                    ssidsText = ssidsText,
                    onSsidsChange = {
                        ssidsText = it
                        selectedListName = null
                    },
                    running = running,
                    parsedCount = parsedSsids.size,
                    validationError = ssidValidationError,
                    onSave = { saveDialogVisible = true },
                    onImport = { importPicker.launch(arrayOf("text/plain", "*/*")) },
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = NrSurface),
                    border = BorderStroke(0.5.dp, NrOutline),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Saved SSID lists",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "${sortedListNames.size} saved",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NrOnSurfaceVariant,
                                )
                            }
                            OutlinedButton(
                                onClick = { saveDialogVisible = true },
                                enabled = !running && parsedSsids.isNotEmpty(),
                            ) {
                                Text("Save current")
                            }
                        }
                        if (sortedListNames.isEmpty()) {
                            Text(
                                text = "No saved lists yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NrOnSurfaceVariant,
                            )
                        } else {
                            sortedListNames.forEach { name ->
                                val list = savedLists[name].orEmpty()
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    text = name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = "${list.size} SSIDs",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                                    color = NrOnSurfaceVariant,
                                                )
                                            }
                                            TextButton(
                                                onClick = {
                                                    ssidsText = list.joinToString("\n")
                                                    selectedListName = name
                                                    selectedTab = 0
                                                },
                                                enabled = !running,
                                            ) {
                                                Text("Load")
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                            TextButton(
                                                onClick = {
                                                    renameTarget = name
                                                    renameText = name
                                                },
                                                enabled = !running,
                                            ) {
                                                Text("Rename")
                                            }
                                            TextButton(
                                                onClick = { deleteTarget = name },
                                                enabled = !running,
                                            ) {
                                                Text("Delete")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                PresetCard(
                    running = running,
                    onLoadRickroll = {
                        ssidsText = RICKROLL_PRESET.joinToString("\n")
                        selectedListName = "Rickroll"
                        selectedTab = 0
                    },
                )
            }

            Spacer(Modifier.height(84.dp))
        }

        if (connected) {
            ModuleActionFab(
                onClick = {
                    when {
                        running -> onStop()
                        ssidValidationError != null -> selectedTab = 1
                        else -> confirmStart = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = if (running) StatusRed else MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (running) "Stop beacon broadcast" else "Start beacon broadcast",
                )
            }
        }
    }

    if (saveDialogVisible) {
        AlertDialog(
            onDismissRequest = { saveDialogVisible = false },
            title = { Text("Save SSID list") },
            text = {
                OutlinedTextField(
                    value = saveName,
                    onValueChange = { saveName = it },
                    label = { Text("List name") },
                    singleLine = true,
                    isError = saveName.isBlank(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clean = saveName.trim()
                        if (clean.isNotBlank()) {
                            onSaveListAction(clean, parsedSsids)
                            selectedListName = clean
                            saveName = ""
                            saveDialogVisible = false
                        }
                    },
                    enabled = saveName.isNotBlank(),
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { saveDialogVisible = false }) { Text("Cancel") }
            },
        )
    }

    renameTarget?.let { name ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename SSID list") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("List name") },
                    singleLine = true,
                    isError = renameText.isBlank(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clean = renameText.trim()
                        if (clean.isNotBlank()) {
                            onRenameListAction(name, clean)
                            if (selectedListName == name) selectedListName = clean
                            renameTarget = null
                        }
                    },
                    enabled = renameText.isNotBlank(),
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            },
        )
    }

    deleteTarget?.let { name ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete SSID list?") },
            text = { Text("Delete \"$name\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteList(name)
                        if (selectedListName == name) selectedListName = null
                        deleteTarget = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("Start beacon broadcast?") },
            text = {
                Text(
                    "${parsedSsids.size} SSIDs on CH $channel (${bandForWifiChannel(channel).label}) " +
                        "until stopped. Only use this on spectrum you own or are authorized to test."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmStart = false
                        onStart(parsedSsids, channel, intervalMs, hidden, randomBssid)
                    },
                    enabled = ssidValidationError == null,
                ) {
                    Text("Start")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmStart = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BroadcastStatusCard(
    connected: Boolean,
    running: Boolean,
    sentFrames: Int,
    ssidCount: Int,
    activeChannel: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusIndicator(
                label = when {
                    !connected -> "No device connected"
                    running -> "Broadcasting"
                    sentFrames > 0 -> "Stopped"
                    else -> "Idle"
                },
                color = when {
                    !connected -> StatusNeutral
                    running -> StatusGreen
                    sentFrames > 0 -> StatusInfo
                    else -> StatusNeutral
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                StatusMetric("Frames", sentFrames.toString())
                StatusMetric("SSIDs", ssidCount.toString())
                StatusMetric("Channel", activeChannel.takeIf { it > 0 }?.toString() ?: "—")
            }
        }
    }
}

@Composable
private fun CurrentSsidsCard(
    ssidCount: Int,
    ssids: List<String>,
    selectedListName: String?,
    validationError: String?,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = selectedListName ?: "Current SSIDs",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$ssidCount/32 SSIDs",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = if (validationError != null) StatusAmber else NrOnSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onEdit) { Text("Edit") }
            }
            if (ssids.isEmpty()) {
                Text(
                    text = "No SSIDs configured.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else {
                Text(
                    text = ssids.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SsidCompositionCard(
    ssidsText: String,
    onSsidsChange: (String) -> Unit,
    running: Boolean,
    parsedCount: Int,
    validationError: String?,
    onSave: () -> Unit,
    onImport: () -> Unit,
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
                    Icons.Default.Campaign,
                    contentDescription = null,
                    tint = NrAccent,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "SSID composition",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "$parsedCount/32",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    color = if (validationError != null) StatusAmber else NrOnSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = ssidsText,
                onValueChange = onSsidsChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !running,
                label = { Text("SSIDs, one per line") },
                placeholder = { Text("Free WiFi\nCoffeeShop") },
                minLines = 4,
                maxLines = 8,
                isError = validationError != null,
                supportingText = {
                    Text(
                        text = validationError ?: "Every SSID must fit in 32 UTF-8 bytes.",
                        color = if (validationError != null) StatusRed else NrOnSurfaceVariant,
                    )
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onSave,
                    enabled = !running && parsedCount > 0,
                ) {
                    Text("Save list")
                }
                OutlinedButton(onClick = onImport, enabled = !running) {
                    Icon(
                        Icons.Default.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Import")
                }
            }
        }
    }
}

@Composable
private fun StatusMetric(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NrOnSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.SemiBold,
            color = NrOnSurface,
        )
    }
}

@Composable
private fun PresetCard(
    running: Boolean,
    onLoadRickroll: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Presets",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Rickroll", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "${RICKROLL_PRESET.size} SSIDs · each ≤ 32 bytes",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = NrOnSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onLoadRickroll, enabled = !running) {
                    Text("Load")
                }
            }
        }
    }
}

@Composable
private fun NumberStepper(
    label: String,
    value: Int,
    valueText: String,
    enabled: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDecrease, enabled = enabled) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease $label")
        }
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        IconButton(onClick = onIncrease, enabled = enabled) {
            Icon(Icons.Default.Add, contentDescription = "Increase $label")
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
