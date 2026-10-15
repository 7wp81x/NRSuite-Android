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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.swp81x.nrsuite.core.defense.OuiRule
import com.swp81x.nrsuite.core.oui.OuiDatabaseStatus
import com.swp81x.nrsuite.core.defense.OuiRuleAction
import com.swp81x.nrsuite.core.defense.normalizeOuiPrefix
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.update.AppUpdateInfo
import com.swp81x.nrsuite.core.update.FirmwareAssetDownloadState
import com.swp81x.nrsuite.core.update.ReleaseAsset
import com.swp81x.nrsuite.core.update.AppUpdateDownloadState
import com.swp81x.nrsuite.core.update.FirmwareUpdateInfo
import com.swp81x.nrsuite.core.update.ReleaseInfo
import com.swp81x.nrsuite.core.usb.UsbSerialDevice
import com.swp81x.nrsuite.ui.components.ModuleCard
import com.swp81x.nrsuite.ui.components.ModuleCardSpec
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.components.SavedItemDropdown
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

private fun inferChipFromFirmwareName(name: String?): String? {
    val normalized = name?.lowercase() ?: return null
    return when {
        "esp32s3" in normalized || "esp32-s3" in normalized -> "ESP32-S3"
        "esp32c3" in normalized || "esp32-c3" in normalized -> "ESP32-C3"
        "esp32s2" in normalized || "esp32-s2" in normalized -> "ESP32-S2"
        "esp32-generic" in normalized || "esp32dev" in normalized || "esp32_dev" in normalized -> "ESP32"
        "esp32" in normalized -> "ESP32"
        else -> null
    }
}

private fun firmwareSuffixForChip(chip: String): String = when (chip) {
    "ESP32-C3" -> "esp32c3.bin"
    "ESP32-S3" -> "esp32s3.bin"
    "ESP32-S2" -> "esp32s2.bin"
    else -> "esp32-generic.bin"
}

private fun ReleaseInfo.compatibleFirmwareAssets(chip: String): List<ReleaseAsset> {
    val suffix = firmwareSuffixForChip(chip)
    return assets.filter { it.name.endsWith(suffix, ignoreCase = true) }
}

private fun firmwareChipFromAssetName(name: String): String? {
    val normalized = name.lowercase()
    return when {
        normalized.endsWith("esp32c3.bin") -> "ESP32-C3"
        normalized.endsWith("esp32s3.bin") -> "ESP32-S3"
        normalized.endsWith("esp32s2.bin") -> "ESP32-S2"
        normalized.endsWith("esp32-generic.bin") -> "ESP32"
        else -> null
    }
}

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
    onStartFirmwareFlash: (targetChip: String, skipReset: Boolean, eraseBeforeFlash: Boolean) -> Unit,
    firmwareManualRecovery: Boolean = false,
    onArmManualFirmwareRecovery: (targetChip: String) -> Unit = {},
    onFlashManualFirmwareNow: () -> Unit = {},
    onDisarmManualFirmwareRecovery: () -> Unit = {},
    firmwareReleases: List<ReleaseInfo> = emptyList(),
    firmwareAssetDownload: FirmwareAssetDownloadState = FirmwareAssetDownloadState.Idle,
    onDownloadFirmwareAsset: (releaseTag: String, assetName: String) -> Unit = { _, _ -> },
    ouiRules: List<OuiRule> = emptyList(),
    onAddOuiRule: (ouiPrefix: String, label: String, action: OuiRuleAction) -> Unit = { _, _, _ -> },
    onDeleteOuiRule: (id: String) -> Unit = {},
    ouiDatabaseStatus: OuiDatabaseStatus = OuiDatabaseStatus.NotDownloaded,
    ouiDatabaseProgress: Float? = null,
    onDownloadOuiDatabase: () -> Unit = {},
    autoUpdateCheck: Boolean = true,
    updateCheckInProgress: Boolean = false,
    appUpdate: AppUpdateInfo? = null,
    appUpdateDownload: AppUpdateDownloadState = AppUpdateDownloadState.Idle,
    firmwareUpdate: FirmwareUpdateInfo? = null,
    updateError: String? = null,
    onAutoUpdateCheckChange: (Boolean) -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onAutoCheckForUpdates: () -> Unit = {},
    onDownloadAppUpdate: () -> Unit = {},
    onInstallAppUpdate: () -> Unit = {},
    onDownloadFirmwareUpdate: () -> Unit = {},
    onOpenFirmwareFlasher: () -> Unit = {},
    onClearUpdateDownloads: () -> Unit = {},
    flasherOnly: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val currentFirmware = (connectionState as? ConnectionState.Connected)?.firmwareVersion
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentAppVersion = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"
    }
    val accent = MaterialTheme.colorScheme.primary
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
    var advancedExpanded by remember { mutableStateOf(false) }
    var showFlashConfirm by remember { mutableStateOf(false) }
    var selectedFirmwareReleaseTag by remember { mutableStateOf<String?>(null) }
    var selectedFirmwareAssetName by remember { mutableStateOf<String?>(null) }
    var firmwareSourceRemote by remember { mutableStateOf(true) }
    LaunchedEffect(firmwareFileName, chip) {
        val inferred = inferChipFromFirmwareName(firmwareFileName)
        when {
            inferred != null -> selectedTargetChip = inferred
            !chip.isNullOrBlank() -> selectedTargetChip = chip
        }
    }

    LaunchedEffect(firmwareAssetDownload) {
        if (firmwareAssetDownload is FirmwareAssetDownloadState.Downloaded) {
            firmwareSourceRemote = true
        }
    }
    LaunchedEffect(firmwareReleases, selectedTargetChip) {
        val compatibleReleases = firmwareReleases.filter {
            it.compatibleFirmwareAssets(selectedTargetChip).isNotEmpty()
        }
        val release = compatibleReleases.firstOrNull { it.tag == selectedFirmwareReleaseTag }
            ?: compatibleReleases.firstOrNull()
        selectedFirmwareReleaseTag = release?.tag
        val assets = release?.compatibleFirmwareAssets(selectedTargetChip).orEmpty()
        selectedFirmwareAssetName = assets.firstOrNull { it.name == selectedFirmwareAssetName }?.name
            ?: assets.firstOrNull()?.name
    }
    LaunchedEffect(devices) {
        if (devices.size == 1 &&
            selectedFlashTarget == null &&
            usbManager.hasPermission(devices[0].device)
        ) {
            onSelectFlashTarget(devices[0].device)
        }
    }

    LaunchedEffect(flasherOnly, autoUpdateCheck) {
        if (flasherOnly && autoUpdateCheck) {
            onAutoCheckForUpdates()
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
                Spacer(Modifier.height(12.dp))

                Text(
                    text = "1. Target device",
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Select the board to flash and grant USB permission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
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
                        val selected = hasPermission &&
                            selectedFlashTarget?.device?.deviceId == entry.device.deviceId
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
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
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
                Spacer(Modifier.height(12.dp))

                Text(
                    text = "2. Firmware image",
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Choose a cached GitHub release or a local .bin file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NrFilterChip(
                        selected = firmwareSourceRemote,
                        onClick = { firmwareSourceRemote = true },
                        enabled = !firmwareFlashing,
                        label = "Remote",
                    )
                    NrFilterChip(
                        selected = !firmwareSourceRemote,
                        onClick = { firmwareSourceRemote = false },
                        enabled = !firmwareFlashing,
                        label = "Local file",
                    )
                }
                Spacer(Modifier.height(10.dp))

                if (!firmwareSourceRemote) {
                    Text(
                        text = if (firmwareFileName != null && firmwareFlashSize > 0) {
                            "$firmwareFileName  (${firmwareFlashSize / 1024} KB)"
                        } else {
                            firmwareFileName ?: "No local firmware .bin selected"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = NrOnSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (firmwareFileName == null) {
                        Button(
                            onClick = onChooseFirmware,
                            enabled = targetHasPermission && !firmwareFlashing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text("Choose firmware .bin")
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onChooseFirmware,
                                enabled = targetHasPermission && !firmwareFlashing,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                            ) {
                                Text("Change")
                            }
                            TextButton(
                                onClick = onClearFirmware,
                                enabled = !firmwareFlashing,
                            ) {
                                Text("Clear")
                            }
                        }
                    }
                    if (!targetHasPermission) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Select a device above to choose a local firmware.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAmber,
                        )
                    }
                } else {
                    if (updateCheckInProgress) {
                        Text(
                            text = "Checking for updates…",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAmber,
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else if (firmwareReleases.isEmpty()) {
                        Text(
                            text = "No firmware releases cached yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAmber,
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = onCheckForUpdates,
                            enabled = autoUpdateCheck,
                        ) {
                            Text("Check now")
                        }
                        if (updateError != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = updateError,
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusRed,
                            )
                        }
                    } else {
                        val selectedRelease = firmwareReleases.firstOrNull {
                            it.tag == selectedFirmwareReleaseTag
                        }
                        val allAssets = selectedRelease?.assets.orEmpty()
                        val compatibleAssets = selectedRelease
                            ?.compatibleFirmwareAssets(selectedTargetChip)
                            .orEmpty()
                        val selectedAsset = allAssets.firstOrNull {
                            it.name == selectedFirmwareAssetName
                        } ?: compatibleAssets.firstOrNull()

                        SavedItemDropdown(
                            label = "Firmware version",
                            selectedName = selectedRelease?.tag,
                            names = firmwareReleases.map { it.tag },
                            enabled = !firmwareFlashing,
                            sortNames = false,
                            itemSupportingText = { tag ->
                                val release = firmwareReleases.firstOrNull { it.tag == tag }
                                val isLatest = tag == firmwareReleases.firstOrNull()?.tag
                                val compatible = release
                                    ?.compatibleFirmwareAssets(selectedTargetChip)
                                    ?.isNotEmpty() == true
                                when {
                                    isLatest && compatible -> "Latest · compatible with $selectedTargetChip"
                                    isLatest -> "Latest"
                                    compatible -> "Compatible with $selectedTargetChip"
                                    else -> "No $selectedTargetChip asset"
                                }
                            },
                            onSelect = { tag ->
                                selectedFirmwareReleaseTag = tag
                                selectedFirmwareAssetName = null
                            },
                        )

                        Spacer(Modifier.height(8.dp))
                        SavedItemDropdown(
                            label = "Firmware image",
                            selectedName = selectedAsset?.name,
                            names = allAssets.map { it.name },
                            enabled = !firmwareFlashing && allAssets.isNotEmpty(),
                            sortNames = false,
                            itemSupportingText = { name ->
                                firmwareChipFromAssetName(name)?.let { "Target: $it" }
                                    ?: "Unknown target chip"
                            },
                            onSelect = { name ->
                                selectedFirmwareAssetName = name
                                firmwareChipFromAssetName(name)?.let { selectedTargetChip = it }
                            },
                        )

                        if (compatibleAssets.isEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "This release has no asset for $selectedTargetChip. " +
                                    "Select a version/firmware that matches the target chip.",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusAmber,
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        val downloadState = firmwareAssetDownload
                        val downloaded = downloadState is FirmwareAssetDownloadState.Downloaded
                        Button(
                            onClick = {
                                val release = selectedRelease ?: return@Button
                                val asset = selectedAsset ?: return@Button
                                onDownloadFirmwareAsset(release.tag, asset.name)
                            },
                            enabled = selectedRelease != null &&
                                selectedAsset != null &&
                                downloadState !is FirmwareAssetDownloadState.Downloading &&
                                !downloaded &&
                                !firmwareFlashing,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            when (downloadState) {
                                is FirmwareAssetDownloadState.Downloading -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        downloadState.percent?.let {
                                            "Downloading ${it.toInt()}%"
                                        } ?: "Downloading…",
                                    )
                                }
                                is FirmwareAssetDownloadState.Downloaded -> Text("Downloaded")
                                is FirmwareAssetDownloadState.Failed -> Text("Retry download")
                                FirmwareAssetDownloadState.Idle -> Text("Download selected firmware")
                            }
                        }
                        if (downloadState is FirmwareAssetDownloadState.Failed) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = downloadState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusRed,
                            )
                        }
                        if (downloaded) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Firmware ready to flash. Use the button below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusGreen,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "3. Review",
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Target chip",
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
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
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Flash offset: 0x0",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { advancedExpanded = !advancedExpanded },
                    enabled = !firmwareFlashing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = if (advancedExpanded) {
                            Icons.Default.ExpandLess
                        } else {
                            Icons.Default.ExpandMore
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (advancedExpanded) "Hide advanced options" else "Advanced options")
                }
                if (advancedExpanded) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = manualBootloader,
                            onCheckedChange = { manualBootloader = it },
                            enabled = !firmwareFlashing,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Skip auto-reset (already in ROM bootloader)",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Use this only if the board is already in ROM bootloader mode. " +
                            "Otherwise hold BOOT, tap RESET, then enable this option and flash.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { showFlashConfirm = true },
                    enabled = selectedFlashTarget != null &&
                        targetHasPermission &&
                        firmwareFileName != null &&
                        !firmwareFlashing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(if (firmwareFlashing) "Flashing..." else "Flash firmware")
                }
                if (showFlashConfirm) {
                    AlertDialog(
                        onDismissRequest = { showFlashConfirm = false },
                        title = { Text("Flash firmware?") },
                        text = {
                            Text(
                                "This will write the selected firmware on " +
                                    "${selectedFlashTarget?.displayName ?: "the device"} " +
                                    "and reboot it. Do not unplug during the process.",
                                color = NrOnSurfaceVariant,
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showFlashConfirm = false
                                    onStartFirmwareFlash(selectedTargetChip, manualBootloader, false)
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

                if (firmwareManualRecovery) {
                    Spacer(Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
                        border = BorderStroke(0.5.dp, StatusAmber),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                text = "Manual bootloader recovery armed",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Hold BOOT, unplug/replug USB, release BOOT. " +
                                    "The flash will start when the device appears.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NrOnSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = onFlashManualFirmwareNow,
                                    enabled = !firmwareFlashing,
                                ) {
                                    Text("Flash now")
                                }
                                TextButton(
                                    onClick = onDisarmManualFirmwareRecovery,
                                    enabled = !firmwareFlashing,
                                ) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                } else if (!firmwareFlashing &&
                    firmwareFlashStatus?.startsWith("Flash failed") == true
                ) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { onArmManualFirmwareRecovery(selectedTargetChip) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Use manual bootloader")
                    }
                }
            }
        }
        }

        if (!flasherOnly) {

        OuiDatabaseCard(
            status = ouiDatabaseStatus,
            progress = ouiDatabaseProgress,
            onDownload = onDownloadOuiDatabase,
        )

        OuiRulesCard(
            ouiRules = ouiRules,
            onAddOuiRule = onAddOuiRule,
            onDeleteOuiRule = onDeleteOuiRule,
        )

        UpdatesCard(
            currentVersion = currentAppVersion,
            autoUpdateCheck = autoUpdateCheck,
            updateCheckInProgress = updateCheckInProgress,
            appUpdate = appUpdate,
            appUpdateDownload = appUpdateDownload,
            firmwareUpdate = firmwareUpdate,
            firmwareAssetDownload = firmwareAssetDownload,
            updateError = updateError,
            onAutoUpdateCheckChange = onAutoUpdateCheckChange,
            onCheckForUpdates = onCheckForUpdates,
            onDownloadAppUpdate = onDownloadAppUpdate,
            onInstallAppUpdate = onInstallAppUpdate,
            onDownloadFirmwareUpdate = onDownloadFirmwareUpdate,
            onOpenFirmwareFlasher = onOpenFirmwareFlasher,
            onClearUpdateDownloads = onClearUpdateDownloads,
        )

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

@Composable
private fun OuiRulesCard(
    ouiRules: List<OuiRule>,
    onAddOuiRule: (ouiPrefix: String, label: String, action: OuiRuleAction) -> Unit,
    onDeleteOuiRule: (id: String) -> Unit,
) {
    var showAddForm by remember { mutableStateOf(false) }
    var ouiInput by remember { mutableStateOf("") }
    var labelInput by remember { mutableStateOf("") }
    var actionInput by remember { mutableStateOf(OuiRuleAction.WHITELIST) }

    val normalizedOui = normalizeOuiPrefix(ouiInput)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Rogue AP Detector — OUI rules", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Whitelist common AP hardware in your area to reduce false alarms. " +
                    "Blacklist vendor prefixes known for rogue AP tools to flag them harder.",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            if (ouiRules.isEmpty()) {
                Text(
                    text = "No rules yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ouiRules.forEach { rule ->
                        OuiRuleRow(rule = rule, onDelete = { onDeleteOuiRule(rule.id) })
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (!showAddForm) {
                OutlinedButton(onClick = { showAddForm = true }) {
                    Text("Add rule")
                }
            } else {
                Column {
                    OutlinedTextField(
                        value = ouiInput,
                        onValueChange = { ouiInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("OUI prefix (AA:BB:CC)") },
                        isError = ouiInput.isNotBlank() && normalizedOui == null,
                        singleLine = true,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = labelInput,
                        onValueChange = { labelInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Label") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NrFilterChip(
                            selected = actionInput == OuiRuleAction.WHITELIST,
                            onClick = { actionInput = OuiRuleAction.WHITELIST },
                            label = "Whitelist",
                        )
                        NrFilterChip(
                            selected = actionInput == OuiRuleAction.BLACKLIST,
                            onClick = { actionInput = OuiRuleAction.BLACKLIST },
                            label = "Blacklist",
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val normalized = normalizeOuiPrefix(ouiInput) ?: return@Button
                                onAddOuiRule(normalized, labelInput.trim(), actionInput)
                                ouiInput = ""
                                labelInput = ""
                                actionInput = OuiRuleAction.WHITELIST
                                showAddForm = false
                            },
                            enabled = normalizedOui != null && labelInput.isNotBlank(),
                        ) {
                            Text("Save")
                        }
                        TextButton(onClick = { showAddForm = false }) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OuiRuleRow(
    rule: OuiRule,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = rule.ouiPrefix,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.SemiBold,
            )
            if (rule.label.isNotBlank()) {
                Text(
                    text = rule.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }

        val badgeColor = when (rule.action) {
            OuiRuleAction.WHITELIST -> NrAccent
            OuiRuleAction.BLACKLIST -> StatusRed
        }
        Row(
            modifier = Modifier
                .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                text = when (rule.action) {
                    OuiRuleAction.WHITELIST -> "Whitelist"
                    OuiRuleAction.BLACKLIST -> "Blacklist"
                },
                style = MaterialTheme.typography.labelSmall,
                color = badgeColor,
                maxLines = 1,
                softWrap = false,
            )
        }

        IconButton(onClick = { confirmDelete = true }) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete OUI rule",
                tint = NrOnSurfaceVariant,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete rule?") },
            text = { Text("Remove the rule for ${rule.ouiPrefix}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun UpdatesCard(
    currentVersion: String,
    autoUpdateCheck: Boolean,
    updateCheckInProgress: Boolean,
    appUpdate: AppUpdateInfo?,
    appUpdateDownload: AppUpdateDownloadState,
    firmwareUpdate: FirmwareUpdateInfo?,
    firmwareAssetDownload: FirmwareAssetDownloadState,
    updateError: String?,
    onAutoUpdateCheckChange: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadAppUpdate: () -> Unit,
    onInstallAppUpdate: () -> Unit,
    onDownloadFirmwareUpdate: () -> Unit,
    onOpenFirmwareFlasher: () -> Unit,
    onClearUpdateDownloads: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Updates", fontWeight = FontWeight.SemiBold)
            Text(
                text = "App version $currentVersion",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Check automatically", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Checks GitHub when the app opens, at most once per hour.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                Switch(
                    checked = autoUpdateCheck,
                    onCheckedChange = onAutoUpdateCheckChange,
                )
            }

            if (updateError != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = updateError,
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusRed,
                )
            }

            appUpdate?.let { update ->
                Spacer(Modifier.height(10.dp))
                val downloadState = appUpdateDownload
                UpdateInfoRow(
                    label = "App update available",
                    latest = update.latest,
                    buttonLabel = when (downloadState) {
                        is AppUpdateDownloadState.Downloading -> {
                            downloadState.percent?.let { "Downloading ${it.toInt()}%" }
                                ?: "Downloading…"
                        }
                        AppUpdateDownloadState.ReadyToInstall -> "Update"
                        is AppUpdateDownloadState.Failed -> "Retry"
                        AppUpdateDownloadState.Idle -> "Download"
                    },
                    buttonEnabled = downloadState !is AppUpdateDownloadState.Downloading,
                    onButtonClick = when (downloadState) {
                        AppUpdateDownloadState.ReadyToInstall -> onInstallAppUpdate
                        else -> onDownloadAppUpdate
                    },
                )
            }

            firmwareUpdate?.let { update ->
                Spacer(Modifier.height(10.dp))
                val downloadState = firmwareAssetDownload
                UpdateInfoRow(
                    label = "Firmware update available",
                    latest = update.latest,
                    buttonLabel = when (downloadState) {
                        is FirmwareAssetDownloadState.Downloading -> {
                            downloadState.percent?.let { "Downloading ${it.toInt()}%" }
                                ?: "Downloading…"
                        }
                        is FirmwareAssetDownloadState.Downloaded -> "Flash"
                        is FirmwareAssetDownloadState.Failed -> "Retry"
                        FirmwareAssetDownloadState.Idle -> "Download"
                    },
                    buttonEnabled = downloadState !is FirmwareAssetDownloadState.Downloading,
                    onButtonClick = when (downloadState) {
                        is FirmwareAssetDownloadState.Downloaded -> onOpenFirmwareFlasher
                        else -> onDownloadFirmwareUpdate
                    },
                )
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onCheckForUpdates,
                enabled = !updateCheckInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (updateCheckInProgress) {
                    Text("Checking...")
                } else {
                    Text("Check now")
                }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(
                onClick = onClearUpdateDownloads,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear downloaded updates")
            }
        }
    }
}

@Composable
private fun UpdateInfoRow(
    label: String,
    latest: ReleaseInfo,
    buttonLabel: String = "Download",
    buttonEnabled: Boolean = true,
    onButtonClick: (() -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = null,
            tint = StatusAmber,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = latest.tag,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(
            onClick = {
                if (onButtonClick != null) {
                    onButtonClick()
                } else {
                    val url = latest.downloadUrl ?: latest.htmlUrl
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
            },
            enabled = buttonEnabled,
            modifier = Modifier.widthIn(min = 112.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = buttonLabel,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OuiDatabaseCard(
    status: OuiDatabaseStatus,
    progress: Float?,
    onDownload: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("MAC vendor database", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Download the IEEE OUI registry for offline vendor/MAC lookup. " +
                    "No MAC lookup leaves the device.",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            when (status) {
                OuiDatabaseStatus.Checking -> {
                    Text(
                        text = "Checking local database…",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                OuiDatabaseStatus.NotDownloaded -> {
                    Text(
                        text = "Not downloaded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                OuiDatabaseStatus.Downloading -> {
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { (progress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Downloading… ${progress.toInt()}%",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = StatusAmber,
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Downloading…",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAmber,
                        )
                    }
                }
                is OuiDatabaseStatus.Ready -> {
                    Text(
                        text = "${status.vendorCount} OUI prefixes · updated ${status.updatedAt}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = NrOnSurfaceVariant,
                    )
                }
                is OuiDatabaseStatus.Error -> {
                    Text(
                        text = status.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusRed,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onDownload,
                enabled = status !is OuiDatabaseStatus.Downloading &&
                    status !is OuiDatabaseStatus.Checking,
            ) {
                Text(if (status is OuiDatabaseStatus.Ready) "Update database" else "Download database")
            }
        }
    }
}
