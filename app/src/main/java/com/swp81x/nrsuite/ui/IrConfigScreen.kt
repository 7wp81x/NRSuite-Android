package com.swp81x.nrsuite.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.ir.IrDatabaseMetadata
import com.swp81x.nrsuite.core.ir.IrDatabasePhase
import com.swp81x.nrsuite.core.ir.IrDatabaseUiState
import com.swp81x.nrsuite.core.ir.IrPhoneCapabilities
import com.swp81x.nrsuite.core.ir.IrScanProgress
import com.swp81x.nrsuite.core.ir.IrTransmitBackend
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun IrConfigScreen(
    capabilities: IrPhoneCapabilities,
    backend: IrTransmitBackend,
    loopIntervalMs: Int,
    exportDirectoryName: String,
    remoteCount: Int,
    importing: Boolean,
    scanProgress: IrScanProgress?,
    irDatabaseState: IrDatabaseUiState,
    irDatabaseMetadata: IrDatabaseMetadata?,
    esp32: ConnectionState.Connected?,
    errorMessage: String?,
    onSetBackend: (IrTransmitBackend) -> Unit,
    onSetLoopInterval: (Int) -> Unit,
    onRefreshCapabilities: () -> Unit,
    onRescanLibrary: () -> Unit,
    onDownloadIrDatabase: () -> Unit,
    onRetryIrDatabaseOperation: () -> Unit,
    onCancelIrDatabaseOperation: () -> Unit,
    onImportIrZip: (Uri) -> Unit,
    onDeleteIrDatabase: () -> Unit,
    onRefreshEsp32Ir: () -> Unit,
    onSetIrEsp32Pins: (txGpio: Int, rxGpio: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var backendMenu by remember { mutableStateOf(false) }
    var intervalMenu by remember { mutableStateOf(false) }
    var deleteDatabaseDialog by remember { mutableStateOf(false) }
    var txPinMenu by remember { mutableStateOf(false) }
    var rxPinMenu by remember { mutableStateOf(false) }
    var selectedTxGpio by rememberSaveable { mutableIntStateOf(-1) }
    var selectedRxGpio by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(esp32?.deviceId, esp32?.irTxGpio, esp32?.irRxGpio) {
        selectedTxGpio = esp32?.irTxGpio ?: -1
        selectedRxGpio = esp32?.irRxGpio ?: -1
    }
    val zipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(onImportIrZip)
    }
    val databaseRunning = irDatabaseState.running

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NrSurface),
            border = BorderStroke(0.5.dp, NrOutline),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = "IR transmit backend",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Auto uses ESP32 IR when firmware support is available, otherwise Phone IR.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Box {
                    OutlinedButton(
                        onClick = { backendMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = when (backend) {
                                IrTransmitBackend.AUTO -> "Auto"
                                IrTransmitBackend.PHONE -> "Phone IR"
                                IrTransmitBackend.ESP32 -> "ESP32 IR"
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose IR backend")
                    }
                    DropdownMenu(
                        expanded = backendMenu,
                        onDismissRequest = { backendMenu = false },
                    ) {
                        IrTransmitBackend.entries.forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            when (item) {
                                                IrTransmitBackend.AUTO -> "Auto"
                                                IrTransmitBackend.PHONE -> "Phone IR"
                                                IrTransmitBackend.ESP32 -> "ESP32 IR"
                                            },
                                        )
                                        if (item == IrTransmitBackend.ESP32) {
                                            Text(
                                                text = "Firmware IR TX pending",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = StatusAmber,
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onSetBackend(item)
                                    backendMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }

        if (errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
                border = BorderStroke(0.5.dp, StatusRed),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusRed,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NrSurface),
            border = BorderStroke(0.5.dp, NrOutline),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Phone IR",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = onRefreshCapabilities) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Refresh")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (capabilities.hasEmitter) "Emitter detected" else "No emitter detected",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (capabilities.hasEmitter) StatusGreen else StatusAmber,
                )
                if (capabilities.carrierRanges.isNotEmpty()) {
                    Text(
                        text = capabilities.carrierRanges.joinToString { "${it.first}-${it.last} Hz" },
                        style = MaterialTheme.typography.labelSmall,
                        color = NrOnSurfaceVariant,
                    )
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ESP32 IR",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = onRefreshEsp32Ir) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Refresh")
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (esp32 == null) {
                    Text(
                        text = "No ESP32 connected. Connect a supported device to configure IR RX/TX GPIOs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusAmber,
                    )
                } else {
                    val hasIrTx = "ir_tx" in esp32.features
                    val hasIrRx = "ir_rx" in esp32.features
                    Text(
                        text = "Connected: ${esp32.chip ?: "ESP32"} · ${esp32.firmwareVersion ?: "unknown firmware"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusGreen,
                    )
                    Text(
                        text = if (hasIrTx && hasIrRx) {
                            "Firmware advertises IR RX/TX support."
                        } else {
                            "Firmware does not advertise both IR RX/TX features. Update the firmware."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasIrTx && hasIrRx) StatusGreen else StatusRed,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("IR TX GPIO", style = MaterialTheme.typography.labelMedium)
                    Box {
                        OutlinedButton(
                            onClick = { txPinMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = hasIrTx && esp32.irTxGpios.isNotEmpty(),
                        ) {
                            Text(
                                text = if (selectedTxGpio >= 0) {
                                    "GPIO $selectedTxGpio"
                                } else {
                                    "Select TX GPIO"
                                },
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose IR TX GPIO")
                        }
                        DropdownMenu(expanded = txPinMenu, onDismissRequest = { txPinMenu = false }) {
                            esp32.irTxGpios.forEach { gpio ->
                                DropdownMenuItem(
                                    text = { Text("GPIO $gpio") },
                                    onClick = {
                                        selectedTxGpio = gpio
                                        txPinMenu = false
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("IR RX GPIO", style = MaterialTheme.typography.labelMedium)
                    Box {
                        OutlinedButton(
                            onClick = { rxPinMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = hasIrRx && esp32.irRxGpios.isNotEmpty(),
                        ) {
                            Text(
                                text = if (selectedRxGpio >= 0) {
                                    "GPIO $selectedRxGpio"
                                } else {
                                    "Select RX GPIO"
                                },
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose IR RX GPIO")
                        }
                        DropdownMenu(expanded = rxPinMenu, onDismissRequest = { rxPinMenu = false }) {
                            esp32.irRxGpios.forEach { gpio ->
                                DropdownMenuItem(
                                    text = { Text("GPIO $gpio") },
                                    onClick = {
                                        selectedRxGpio = gpio
                                        rxPinMenu = false
                                    },
                                )
                            }
                        }
                    }
                    if (selectedTxGpio >= 0 && selectedTxGpio == selectedRxGpio) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "TX and RX must use different GPIOs.",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusRed,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onSetIrEsp32Pins(selectedTxGpio, selectedRxGpio) },
                        enabled = hasIrTx && hasIrRx &&
                            selectedTxGpio >= 0 && selectedRxGpio >= 0 &&
                            selectedTxGpio != selectedRxGpio,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Save IR GPIO configuration")
                    }
                    Text(
                        text = "RX state: ${if (esp32.irRxActive) "active" else "stopped"} · " +
                            "safe GPIOs: ${esp32.irTxGpios.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NrOnSurfaceVariant,
                    )
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
                    text = "Flipper-IRDB",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Source: github.com/7wp81x/Flipper-IRDB",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
                Text(
                    text = "NRSuite root: $exportDirectoryName",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
                Text(
                    text = "Storage: NRSuite/IR/Database/Flipper-IRDB",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
                Text(
                    text = "$remoteCount indexed remote(s) total",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusGreen,
                )

                if (irDatabaseMetadata != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Archive version: ${irDatabaseMetadata.archiveVersion.ifBlank { "unknown" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NrOnSurfaceVariant,
                    )
                    Text(
                        text = "Downloaded: ${formatIrDatabaseTimestamp(irDatabaseMetadata.downloadedAtMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NrOnSurfaceVariant,
                    )
                    Text(
                        text = "Indexed: ${formatIrDatabaseTimestamp(irDatabaseMetadata.indexedAtMs)} · " +
                            "${irDatabaseMetadata.extractedFiles} files · " +
                            "${irDatabaseMetadata.indexedRemotes} remotes",
                        style = MaterialTheme.typography.labelSmall,
                        color = NrOnSurfaceVariant,
                    )
                }

                if (irDatabaseState.running || irDatabaseState.phase == IrDatabasePhase.FAILED ||
                    irDatabaseState.phase == IrDatabasePhase.CANCELLED ||
                    irDatabaseState.phase == IrDatabasePhase.COMPLETED
                ) {
                    Spacer(Modifier.height(10.dp))
                    val statusText = irDatabaseState.errorMessage
                        ?: irDatabaseState.message
                        ?: when (irDatabaseState.phase) {
                            IrDatabasePhase.DOWNLOADING -> "Downloading Flipper-IRDB..."
                            IrDatabasePhase.EXTRACTING -> "Extracting Flipper-IRDB..."
                            IrDatabasePhase.INDEXING -> "Indexing Flipper-IRDB..."
                            IrDatabasePhase.COMPLETED -> "Flipper-IRDB operation completed."
                            IrDatabasePhase.CANCELLED -> "Flipper-IRDB operation cancelled."
                            IrDatabasePhase.FAILED -> "Flipper-IRDB operation failed."
                            IrDatabasePhase.IDLE -> ""
                        }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (irDatabaseState.phase) {
                            IrDatabasePhase.FAILED -> StatusRed
                            IrDatabasePhase.CANCELLED -> StatusAmber
                            IrDatabasePhase.COMPLETED -> StatusGreen
                            else -> NrOnSurfaceVariant
                        },
                    )
                    if (irDatabaseState.retryAttempt > 0 && irDatabaseState.running) {
                        Text(
                            text = "Retry ${irDatabaseState.retryAttempt}/" +
                                "${irDatabaseState.maxRetryAttempts} after a connection interruption.",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusAmber,
                        )
                    }
                    if (irDatabaseState.running) {
                        Spacer(Modifier.height(6.dp))
                        when (irDatabaseState.phase) {
                            IrDatabasePhase.DOWNLOADING -> {
                                val percent = irDatabaseState.downloadPercent
                                if (percent == null) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    Text(
                                        text = "Downloaded ${formatIrDatabaseBytes(irDatabaseState.downloadedBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NrOnSurfaceVariant,
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        progress = { percent / 100f },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        text = "${formatIrDatabaseBytes(irDatabaseState.downloadedBytes)} / " +
                                            formatIrDatabaseBytes(irDatabaseState.totalBytes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NrOnSurfaceVariant,
                                    )
                                }
                            }
                            IrDatabasePhase.EXTRACTING -> {
                                val percent = irDatabaseState.extractionPercent
                                if (percent == null) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                } else {
                                    LinearProgressIndicator(
                                        progress = { percent / 100f },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        text = "${irDatabaseState.extractedFiles}/" +
                                            "${irDatabaseState.totalFiles} files · ${percent}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NrOnSurfaceVariant,
                                    )
                                }
                            }
                            else -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        irDatabaseState.currentItem?.takeIf { it.isNotBlank() }?.let { item ->
                            Text(
                                text = item,
                                style = MaterialTheme.typography.labelSmall,
                                color = NrOnSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }

                scanProgress?.let { progress ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (progress.discovering) {
                            "Discovering IR files · ${progress.discoveredCount} entries · " +
                                (progress.currentFile ?: "")
                        } else {
                            "Scanning ${progress.scanned}/${progress.total} · ${progress.percent}% " +
                                (progress.currentFile ?: "")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusAmber,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (progress.discovering) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { progress.percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                if (databaseRunning) {
                    Button(
                        onClick = onCancelIrDatabaseOperation,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Cancel IR database operation")
                    }
                } else {
                    if (irDatabaseState.canRetry) {
                        Button(
                            onClick = onRetryIrDatabaseOperation,
                            enabled = !importing,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Retry / resume IR database operation")
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Button(
                        onClick = onDownloadIrDatabase,
                        enabled = !importing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (irDatabaseMetadata == null) {
                                "Download Flipper-IRDB"
                            } else {
                                "Update Flipper-IRDB"
                            },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        zipPicker.launch(
                            arrayOf("application/zip", "application/octet-stream", "*/*"),
                        )
                    },
                    enabled = !databaseRunning && !importing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Import IR ZIP")
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onRescanLibrary,
                    enabled = !databaseRunning && !importing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (importing) "Scanning..." else "Rescan IR library")
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { deleteDatabaseDialog = true },
                    enabled = !databaseRunning && !importing &&
                        (irDatabaseMetadata != null || irDatabaseState.phase == IrDatabasePhase.COMPLETED),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Delete Flipper-IRDB")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
            border = BorderStroke(0.5.dp, NrOutline),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = "IR remote loop settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Box {
                    OutlinedButton(
                        onClick = { intervalMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Loop interval: $loopIntervalMs ms", modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose loop interval")
                    }
                    DropdownMenu(expanded = intervalMenu, onDismissRequest = { intervalMenu = false }) {
                        listOf(100, 250, 500, 1000, 1500, 2000).forEach { interval ->
                            DropdownMenuItem(
                                text = { Text("$interval ms") },
                                onClick = {
                                    onSetLoopInterval(interval)
                                    intervalMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }

    if (deleteDatabaseDialog) {
        AlertDialog(
            onDismissRequest = { deleteDatabaseDialog = false },
            title = { Text("Delete Flipper-IRDB?") },
            text = {
                Text(
                    "This removes NRSuite/IR/Database/Flipper-IRDB and its indexed remotes. " +
                        "Other imported IR files are not affected.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteDatabaseDialog = false
                        onDeleteIrDatabase()
                    },
                ) {
                    Text("Delete", color = StatusRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDatabaseDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun formatIrDatabaseTimestamp(value: Long): String {
    if (value <= 0L) return "unknown"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))
}

private fun formatIrDatabaseBytes(value: Long): String {
    if (value <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var scaled = value.toDouble()
    var unit = 0
    while (scaled >= 1024.0 && unit < units.lastIndex) {
        scaled /= 1024.0
        unit++
    }
    return if (unit == 0) {
        "${value} B"
    } else {
        String.format(Locale.getDefault(), "%.1f %s", scaled, units[unit])
    }
}
