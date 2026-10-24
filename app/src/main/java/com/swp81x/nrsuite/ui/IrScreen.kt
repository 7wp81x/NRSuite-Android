package com.swp81x.nrsuite.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.ir.IrEncodeResult
import com.swp81x.nrsuite.core.ir.IrPhoneCapabilities
import com.swp81x.nrsuite.core.ir.IrProtocolEncoder
import com.swp81x.nrsuite.core.ir.IrRemote
import com.swp81x.nrsuite.core.ir.IrRemoteSource
import com.swp81x.nrsuite.core.ir.IrRemoteSummary
import com.swp81x.nrsuite.core.ir.IrScanProgress
import com.swp81x.nrsuite.core.ir.IrSignal
import com.swp81x.nrsuite.core.ir.IrTransmitBackend
import com.swp81x.nrsuite.ui.components.RoundedTabRow
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen

@Composable
fun IrScreen(
    remotes: List<IrRemoteSummary>,
    selectedRemote: IrRemote?,
    loadingRemote: Boolean,
    importing: Boolean,
    transmitting: Boolean,
    capabilities: IrPhoneCapabilities,
    esp32IrReady: Boolean,
    txAvailable: Boolean,
    backend: IrTransmitBackend,
    loopMode: Boolean,
    activeLoopSignalId: String?,
    loopIntervalMs: Int,
    scanProgress: IrScanProgress?,
    errorMessage: String?,
    onImport: (Uri) -> Unit,
    onRefreshLibrary: () -> Unit,
    onOpenRemote: (String) -> Unit,
    openedFromFinder: Boolean,
    onToggleFavorite: (String) -> Unit,
    onDeleteRemote: (String) -> Unit,
    onSendSignal: (IrSignal) -> Unit,
    onToggleSignal: (IrSignal) -> Unit,
    onSetLoopMode: (Boolean) -> Unit,
    onStopTransmission: () -> Unit,
    onClearMessages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("All categories") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<IrRemoteSummary?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri)
    }

    if (selectedRemote != null) {
        IrRemoteDetail(
            remote = selectedRemote,
            transmitting = transmitting,
            capabilities = capabilities,
            txAvailable = txAvailable,
            loopMode = loopMode,
            activeLoopSignalId = activeLoopSignalId,
            loopIntervalMs = loopIntervalMs,
            errorMessage = errorMessage,
            openedFromFinder = openedFromFinder,
            onSendSignal = onSendSignal,
            onToggleSignal = onToggleSignal,
            onSetLoopMode = onSetLoopMode,
            onStopTransmission = onStopTransmission,
            modifier = modifier,
        )
        return
    }

    val tabRemotes = remember(remotes, selectedTab) {
        when (selectedTab) {
            2 -> remotes.filter { it.source == IrRemoteSource.RECEIVED }
            0 -> remotes.filter { it.source != IrRemoteSource.RECEIVED }
            else -> remotes
        }
    }
    val categories = remember(tabRemotes) {
        listOf("All categories") + tabRemotes.map { it.category }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    val filtered = remember(remotes, selectedTab, query, selectedCategory) {
        val needle = query.trim()
        remotes.asSequence()
            .filter {
                when (selectedTab) {
                    1 -> it.favorite
                    2 -> it.source == IrRemoteSource.RECEIVED
                    else -> it.source != IrRemoteSource.RECEIVED
                }
            }
            .filter { selectedCategory == "All categories" || it.category == selectedCategory }
            .filter {
                needle.isEmpty() ||
                    it.name.contains(needle, ignoreCase = true) ||
                    it.fileName.contains(needle, ignoreCase = true) ||
                    it.category.contains(needle, ignoreCase = true) ||
                    it.brand.contains(needle, ignoreCase = true)
            }
            .toList()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "IR Remotes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = scanProgress?.let {
                        if (it.discovering) {
                            "Discovering IR files · ${it.discoveredCount} entries · ${it.currentFile.orEmpty()}"
                        } else {
                            "Scanning ${it.scanned}/${it.total} · ${it.currentFile.orEmpty()}"
                        }
                    } ?: "Browse, import, and transmit IR remotes.",
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        scanProgress != null -> StatusAmber
                        txAvailable -> StatusGreen
                        else -> StatusAmber
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                enabled = !importing,
            ) {
                if (importing && scanProgress == null) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Add, contentDescription = "Import .ir file")
                }
            }
            IconButton(onClick = onRefreshLibrary, enabled = !importing) {
                Icon(Icons.Default.Refresh, contentDescription = "Rescan IR library")
            }
        }

        if (scanProgress != null) {
            if (scanProgress.discovering) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { scanProgress.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        RoundedTabRow(
            selectedIndex = selectedTab,
            tabs = listOf("Library", "Favorites", "IR Received"),
            onSelect = { selectedTab = it },
        )

        OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search remotes, brands, files, or categories") },
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { categoryExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = selectedCategory,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose category")
                    }
                    DropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                        modifier = Modifier.heightIn(max = 380.dp),
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (category == "All categories") category else {
                                            "$category (${remotes.count { it.category == category }})"
                                        },
                                    )
                                },
                                onClick = {
                                    selectedCategory = category
                                    categoryExpanded = false
                                },
                            )
                        }
                    }
                }
                Text(
                    text = "${filtered.size} remote(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }

        IrMessages(
            errorMessage = errorMessage,
            onClearMessages = onClearMessages,
        )

        if (loadingRemote) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Opening IR remote...")
            }
        } else if (filtered.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
                border = BorderStroke(0.5.dp, NrOutline),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = when {
                            selectedTab == 2 -> "No received IR remotes yet"
                            remotes.isEmpty() -> "No IR remotes yet"
                            else -> "No matching remotes"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when {
                            selectedTab == 2 ->
                                "Save a captured remote from IR Receiver to see it here."
                            remotes.isEmpty() -> "Import a Flipper .ir file to begin."
                            else -> "Try another search, category, or favorites filter."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.id }) { remote ->
                    IrRemoteCard(
                        remote = remote,
                        onOpen = { onOpenRemote(remote.id) },
                        onToggleFavorite = { onToggleFavorite(remote.id) },
                        onDelete = { pendingDelete = remote },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    pendingDelete?.let { remote ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove IR remote?") },
            text = { Text("${remote.name} and its stored .ir file will be removed from NRSuite.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDeleteRemote(remote.id)
                    },
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun IrRemoteCard(
    remote: IrRemoteSummary,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(
            0.5.dp,
            if (remote.favorite) StatusGreen.copy(alpha = 0.65f) else NrOutline,
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = remote.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${remote.category}${if (remote.brand.isNotBlank()) " · ${remote.brand}" else ""} · " +
                        "${remote.buttonCount} buttons · " +
                        remote.protocols.ifEmpty { listOf("Unknown") }.joinToString(" / "),
                    style = MaterialTheme.typography.labelSmall,
                    color = NrOnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (remote.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (remote.favorite) "Remove from favorites" else "Add to favorites",
                    tint = if (remote.favorite) StatusAmber else NrOnSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Remove IR remote")
            }
        }
    }
}

@Composable
private fun IrRemoteDetail(
    remote: IrRemote,
    transmitting: Boolean,
    capabilities: IrPhoneCapabilities,
    txAvailable: Boolean,
    loopMode: Boolean,
    activeLoopSignalId: String?,
    loopIntervalMs: Int,
    errorMessage: String?,
    openedFromFinder: Boolean,
    onSendSignal: (IrSignal) -> Unit,
    onToggleSignal: (IrSignal) -> Unit,
    onSetLoopMode: (Boolean) -> Unit,
    onStopTransmission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val common = remember(remote.signals) { remote.signals.filter { isCommonIrButton(it.name) } }
    val other = remember(remote.signals) { remote.signals.filterNot { isCommonIrButton(it.name) } }

    Column(
        modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = remote.summary.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${remote.signals.size} buttons · loop interval ${loopIntervalMs} ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = NrOnSurfaceVariant,
                )
                if (openedFromFinder) {
                    Text(
                        text = "Opened from IR Finder · Back returns to paused Finder",
                        style = MaterialTheme.typography.labelSmall,
                        color = NrOnSurfaceVariant,
                    )
                }
            }
            Text("Loop", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(6.dp))
            Switch(checked = loopMode, onCheckedChange = onSetLoopMode)
        }

        val activeLoopName = remote.signals.firstOrNull { it.id == activeLoopSignalId }
            ?.let { displayIrButtonName(it.name) }
        IrRemoteStaticStatus(
            errorMessage = errorMessage,
            transmitting = transmitting,
            activeLoopName = activeLoopName,
            loopMode = loopMode,
            onStopTransmission = onStopTransmission,
        )

        if (remote.signals.isEmpty()) {
            Text(
                text = "This remote has no valid buttons.",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 112.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (common.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        IrSectionTitle("Common")
                    }
                    items(common, key = { it.id }) { signal ->
                        IrButtonTile(
                            signal = signal,
                            enabled = txAvailable,
                            activeLoop = activeLoopSignalId == signal.id,
                            loopMode = loopMode,
                            onClick = {
                                if (loopMode) onToggleSignal(signal) else onSendSignal(signal)
                            },
                        )
                    }
                }
                if (other.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        IrSectionTitle(if (common.isEmpty()) "Buttons" else "Other")
                    }
                    items(other, key = { it.id }) { signal ->
                        IrButtonTile(
                            signal = signal,
                            enabled = txAvailable,
                            activeLoop = activeLoopSignalId == signal.id,
                            loopMode = loopMode,
                            onClick = {
                                if (loopMode) onToggleSignal(signal) else onSendSignal(signal)
                            },
                        )
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun IrButtonTile(
    signal: IrSignal,
    enabled: Boolean,
    activeLoop: Boolean,
    loopMode: Boolean,
    onClick: () -> Unit,
) {
    val encoding = remember(signal) { IrProtocolEncoder.encode(signal) }
    val encodable = encoding is IrEncodeResult.Success
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && encodable, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = when {
                activeLoop -> MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
                else -> NrSurfaceVariant
            },
        ),
        border = BorderStroke(
            if (activeLoop) 1.5.dp else 0.5.dp,
            if (activeLoop) StatusGreen else NrOutline,
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = displayIrButtonName(signal.name),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    !encodable -> "Unsupported"
                    activeLoop -> if (loopMode) "Looping · tap to stop" else "Active"
                    loopMode -> "Tap to loop"
                    else -> signal.protocol ?: "Raw"
                },
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    !encodable -> StatusAmber
                    activeLoop -> StatusGreen
                    else -> NrOnSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun IrSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun IrMessages(
    errorMessage: String?,
    onClearMessages: () -> Unit,
) {
    if (errorMessage == null) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.error,
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClearMessages) { Text("Dismiss") }
        }
    }
}

private fun irBackendSummary(
    backend: IrTransmitBackend,
    capabilities: IrPhoneCapabilities,
    esp32IrReady: Boolean,
): String {
    return when (backend) {
        IrTransmitBackend.AUTO -> when {
            esp32IrReady -> "Backend: Auto · ESP32 IR ready"
            capabilities.hasEmitter -> "Backend: Auto · Phone IR ready"
            else -> "Backend: Auto · no IR transmitter ready"
        }
        IrTransmitBackend.PHONE -> if (capabilities.hasEmitter) {
            "Backend: Phone IR"
        } else {
            "Backend: Phone IR · no emitter detected"
        }
        IrTransmitBackend.ESP32 -> if (esp32IrReady) {
            "Backend: ESP32 IR"
        } else {
            "Backend: ESP32 IR · pin configuration required"
        }
    }
}

private fun displayIrButtonName(value: String): String = value.replace('_', ' ').trim()

private val COMMON_IR_WORDS = setOf(
    "power", "on", "off", "up", "down", "left", "right", "ok", "enter", "select",
    "menu", "home", "back", "exit", "mute", "volume", "vol", "channel", "ch",
    "play", "pause", "stop", "source", "input", "hdmi", "guide", "info",
    "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
)

private fun isCommonIrButton(name: String): Boolean {
    val normalized = name.lowercase().replace('_', ' ').replace('-', ' ')
    val words = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
    return words.any { word ->
        word in COMMON_IR_WORDS ||
            word.startsWith("vol") ||
            word.startsWith("ch")
    }
}


@Composable
private fun IrRemoteStaticStatus(
    errorMessage: String?,
    transmitting: Boolean,
    activeLoopName: String?,
    loopMode: Boolean,
    onStopTransmission: () -> Unit,
) {
    val text = when {
        errorMessage != null -> errorMessage
        activeLoopName != null -> "Looping: $activeLoopName"
        transmitting -> "Transmitting..."
        else -> if (loopMode) {
            "Loop mode on · tap a button to start or stop looping"
        } else {
            "Ready · loop mode off"
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
        border = BorderStroke(
            0.5.dp,
            when {
                errorMessage != null -> MaterialTheme.colorScheme.error
                activeLoopName != null -> StatusGreen
                else -> NrOutline
            },
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (errorMessage != null) MaterialTheme.colorScheme.error else NrOnSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (activeLoopName != null) {
                TextButton(onClick = onStopTransmission) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Stop")
                }
            }
        }
    }
}
