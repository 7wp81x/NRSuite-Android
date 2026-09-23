package com.swp81x.nrsuite.ui

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.WindowManager
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.swp81x.nrsuite.ui.theme.LogColorError
import com.swp81x.nrsuite.ui.theme.LogColorInfo
import com.swp81x.nrsuite.ui.theme.LogColorSuccess
import com.swp81x.nrsuite.ui.theme.LogColorUsb
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.history.HistoryEntry
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.swp81x.nrsuite.MainViewModel
import com.swp81x.nrsuite.NrSuiteApplication
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.usb.UsbSerialDevice
import com.swp81x.nrsuite.ui.components.ModuleCard
import com.swp81x.nrsuite.ui.components.ModuleCardSpec
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrOnSurface
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.swp81x.nrsuite.core.log.LogEntry
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.ui.theme.LogBgError

@Composable
internal fun SettingsScreen(
    connectionState: ConnectionState,
    exportDirectoryName: String,
    firmwareFileName: String?,
    firmwareFlashSize: Long,
    firmwareFlashing: Boolean,
    firmwareFlashProgress: Int,
    firmwareFlashStatus: String?,
    devices: List<UsbSerialDevice>,
    usbManager: UsbManager,
    permissionRevision: Int,
    selectedFlashTarget: UsbSerialDevice?,
    onChooseExportDirectory: () -> Unit,
    onChooseFirmware: () -> Unit,
    onClearFirmware: () -> Unit,
    onRefreshDevices: () -> Unit,
    onRequestPermission: (UsbDevice) -> Unit,
    onSelectFlashTarget: (UsbDevice) -> Unit,
    onStartFirmwareFlash: (targetChip: String, skipReset: Boolean) -> Unit,
    flasherOnly: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val currentFirmware = (connectionState as? ConnectionState.Connected)?.firmwareVersion
    val context = androidx.compose.ui.platform.LocalContext.current
    val chip = (connectionState as? ConnectionState.Connected)?.chip
    val boardName = when (chip) {
        "ESP32-C3" -> "ESP32-C3"
        "ESP32-S3" -> "ESP32-S3"
        "ESP32-S2" -> "ESP32-S2"
        "ESP32" -> "Classic ESP32 devkit"
        else -> "Not connected / unknown"
    }
    var selectedTargetChip by remember { mutableStateOf(chip ?: "ESP32") }
    val targetHasPermission = selectedFlashTarget?.let { usbManager.hasPermission(it.device) } == true
    @Suppress("UNUSED_EXPRESSION")
    permissionRevision
    var manualBootloader by remember { mutableStateOf(false) }
    var showFlashConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(chip) {
        if (!chip.isNullOrBlank()) selectedTargetChip = chip
    }
    LaunchedEffect(devices) {
        if (devices.size == 1 && selectedFlashTarget == null) {
            onSelectFlashTarget(devices[0].device)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!flasherOnly) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("NRSuite root directory", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = exportDirectoryName,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = onChooseExportDirectory) {
                    Text("Choose root folder")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Captures are stored under Pcap/ inside this directory. Choose it once; the app creates the structure.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }
        }

        if (flasherOnly) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        ) {
            Column(Modifier.padding(14.dp)) {

                Text(
                    text = "Current version: ${currentFirmware ?: "not connected"}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
                Text(
                    text = "Detected board: $boardName",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
                Text(
                    text = "Flash offset: 0x0",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "This erases the target flash first, then writes the complete merged .bin image. It is not an OTA/incremental update.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusAmber,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (firmwareFileName != null && firmwareFlashSize > 0) {
                        "$firmwareFileName  (${firmwareFlashSize / 1024} KB)"
                    } else {
                        firmwareFileName ?: "No merged firmware .bin selected"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onChooseFirmware,
                        enabled = !firmwareFlashing,
                    ) {
                        Text("Choose firmware .bin")
                    }
                    if (firmwareFileName != null && !firmwareFlashing) {
                        OutlinedButton(onClick = onClearFirmware) {
                            Text("Clear")
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Target chip",
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("ESP32", "ESP32-S2", "ESP32-S3", "ESP32-C3").forEach { option ->
                        NrFilterChip(
                            selected = selectedTargetChip == option,
                            onClick = { selectedTargetChip = option },
                            enabled = !firmwareFlashing,
                            label = option,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Flash target",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRefreshDevices, enabled = !firmwareFlashing) {
                        Text("Refresh")
                    }
                }
                if (devices.isEmpty()) {
                    Text(
                        text = "No supported USB serial device found. Plug in the board and tap Refresh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusAmber,
                    )
                } else {
                    devices.forEach { entry ->
                        val hasPermission = usbManager.hasPermission(entry.device)
                        val selected = selectedFlashTarget?.device?.deviceId == entry.device.deviceId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = entry.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                Text(
                                    text = if (hasPermission) "Permission granted" else "Permission required",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NrOnSurfaceVariant,
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    if (hasPermission) {
                                        onSelectFlashTarget(entry.device)
                                    } else {
                                        onRequestPermission(entry.device)
                                    }
                                },
                                enabled = !firmwareFlashing && !selected,
                            ) {
                                Text(
                                    when {
                                        selected -> "Selected"
                                        !hasPermission -> "Grant permission"
                                        else -> "Select"
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = manualBootloader,
                        onCheckedChange = { manualBootloader = it },
                        enabled = !firmwareFlashing,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Device is already in ROM bootloader mode",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Automatic reset works on CP210x/CH340-style UART boards and some USB-JTAG boards. Otherwise hold BOOT, tap RESET, enable the switch above, then flash.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { showFlashConfirm = true },
                    enabled = selectedFlashTarget != null &&
                        targetHasPermission &&
                        firmwareFileName != null &&
                        !firmwareFlashing,
                ) {
                    Text(if (firmwareFlashing) "Flashing..." else "Flash firmware")
                }
                if (showFlashConfirm) {
                    AlertDialog(
                        onDismissRequest = { showFlashConfirm = false },
                        title = { Text("Flash firmware?") },
                        text = {
                            Text(
                                "This will erase the entire flash and write the firmware on " +
                                    "${selectedFlashTarget?.displayName ?: "the device"}. " +
                                    "The device will reboot. Do not unplug during the process.",
                                color = NrOnSurfaceVariant,
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showFlashConfirm = false
                                    onStartFirmwareFlash(selectedTargetChip, manualBootloader)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StatusAmber,
                                    contentColor = com.swp81x.nrsuite.ui.theme.NrBackground,
                                ),
                            ) { Text("Flash now") }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { showFlashConfirm = false }) {
                                Text("Cancel")
                            }
                        },
                    )
                }
                if (firmwareFlashing || firmwareFlashProgress > 0) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { firmwareFlashProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = firmwareFlashStatus ?: "Preparing...",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = if (firmwareFlashStatus?.startsWith("Flash failed") == true) {
                            MaterialTheme.colorScheme.error
                        } else {
                            NrOnSurfaceVariant
                        },
                    )
                }
            }
        }
        }

        if (!flasherOnly) {

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Appearance", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Dark technical theme is fixed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Developer", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "NRSuite is developed by @7wp81x.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/7wp81x/NRSuite"))
                        )
                    }
                }) {
                    Text("Open GitHub")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("About", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "NRSuite Android app. BLE, WiFi, HID, and storage operations are executed by the ESP32 companion.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }
        }

    }
}
