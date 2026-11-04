package com.swp81x.nrsuite.ui

import android.widget.Toast
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
import androidx.compose.foundation.border
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
import com.swp81x.nrsuite.ui.components.IrModuleConfigRow
import com.swp81x.nrsuite.core.terminal.TerminalShortcut
import com.swp81x.nrsuite.core.terminal.terminalShortcutParameterNames
import com.swp81x.nrsuite.ui.components.NrCompactPrimaryButton
import com.swp81x.nrsuite.ui.components.NrPrimaryButton
import com.swp81x.nrsuite.ui.components.NrQuietButton
import com.swp81x.nrsuite.ui.components.NrSecondaryButton
import com.swp81x.nrsuite.ui.components.RfidModuleConfigRow
import com.swp81x.nrsuite.ui.components.RoundedTabRow
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeveloperBoard
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.history.HistoryEntry
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.swp81x.nrsuite.MainViewModel
import com.swp81x.nrsuite.NrSuiteApplication
import com.swp81x.nrsuite.core.oui.OuiDatabaseStatus
import com.swp81x.nrsuite.core.ir.IrDatabaseMetadata
import com.swp81x.nrsuite.core.ir.IrDatabasePhase
import com.swp81x.nrsuite.core.ir.IrDatabaseUiState
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.update.AppUpdateInfo
import com.swp81x.nrsuite.core.update.AppUpdateDownloadState
import com.swp81x.nrsuite.core.update.FirmwareUpdateInfo
import com.swp81x.nrsuite.core.update.FirmwareAssetDownloadState
import com.swp81x.nrsuite.core.update.ReleaseInfo
import com.swp81x.nrsuite.core.usb.UsbSerialDevice
import com.swp81x.nrsuite.ui.components.ConnectionDiagnosticSummary
import com.swp81x.nrsuite.ui.components.FirmwareUpdateInlineNotice
import com.swp81x.nrsuite.ui.components.ModuleCard
import com.swp81x.nrsuite.ui.components.ModuleCardSpec
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.CategoryDetectionBlue
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrOnSurface
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.categoryColor
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
internal fun ConnectionStatusIndicator(
    state: ConnectionState,
    hasDevices: Boolean,
    hasPermission: Boolean,
    hasPendingPermissions: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val (label, color) = when (state) {
        ConnectionState.Disconnected -> when {
            hasPendingPermissions -> "Connect" to StatusAmber
            hasDevices && hasPermission -> "Connect" to CategoryDetectionBlue
            hasDevices -> "Unpaired" to CategoryDetectionBlue
            else -> "Offline" to StatusNeutral
        }
        ConnectionState.Connecting -> "Connecting…" to StatusAmber
        ConnectionState.Diagnosing -> "Diagnosing…" to StatusAmber
        is ConnectionState.Connected -> (state.chip ?: "Online") to StatusGreen
        is ConnectionState.Failed -> "Error" to StatusRed
    }

    Row(
        modifier = Modifier
            .padding(end = 8.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
                shape = RoundedCornerShape(50),
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(start = 10.dp, end = if (onClick != null) 6.dp else 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusIndicator(label = label, color = color)
        if (onClick != null) {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Switch active device",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
internal fun HomeTabContent(
    connectionState: ConnectionState,
    permissionRevision: Int,
    devices: List<UsbSerialDevice>,
    deviceConnectionStates: Map<String, ConnectionState>,
    deviceFingerprint: (UsbDevice) -> String,
    pendingPermissionRequests: Set<Int>,
    disconnectingFingerprints: Set<String>,
    usbManager: UsbManager,
    modules: List<ModuleCardSpec>,
    listState: LazyListState,
    recentModuleIds: List<String>,
    ouiDatabaseStatus: OuiDatabaseStatus,
    ouiDatabaseProgress: Float?,
    appUpdate: AppUpdateInfo?,
    appUpdateDownload: AppUpdateDownloadState,
    firmwareUpdate: FirmwareUpdateInfo?,
    firmwareAssetDownload: FirmwareAssetDownloadState,
    terminalShortcuts: List<TerminalShortcut>,
    showTerminalLauncherCard: Boolean,
    onOpenShortcuts: () -> Unit,
    onManageTerminalShortcuts: () -> Unit,
    onRunTerminalShortcut: (TerminalShortcut) -> Unit,
    onDownloadOuiDatabase: () -> Unit,
    onDownloadAppUpdate: () -> Unit,
    onInstallAppUpdate: () -> Unit,
    onDownloadFirmwareUpdate: () -> Unit,
    onOpenFirmwareFlasher: () -> Unit,
    onOpenFirmwareFlasherForDiagnostic: (String?) -> Unit,
    onOpenModule: (String) -> Unit,
    onOpenCategory: (String) -> Unit,
    onBrowseAllModules: () -> Unit,
    onRefreshDevices: () -> Unit,
    onConnect: (UsbDevice) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeScreen(
        connectionState = connectionState,
        permissionRevision = permissionRevision,
        devices = devices,
        deviceConnectionStates = deviceConnectionStates,
        deviceFingerprint = deviceFingerprint,
        pendingPermissionRequests = pendingPermissionRequests,
        disconnectingFingerprints = disconnectingFingerprints,
        usbManager = usbManager,
        modules = modules,
        listState = listState,
        recentModuleIds = recentModuleIds,
        ouiDatabaseStatus = ouiDatabaseStatus,
        ouiDatabaseProgress = ouiDatabaseProgress,
        appUpdate = appUpdate,
        appUpdateDownload = appUpdateDownload,
        firmwareUpdate = firmwareUpdate,
        firmwareAssetDownload = firmwareAssetDownload,
        terminalShortcuts = terminalShortcuts,
        showTerminalLauncherCard = showTerminalLauncherCard,
        onOpenShortcuts = onOpenShortcuts,
        onManageTerminalShortcuts = onManageTerminalShortcuts,
        onRunTerminalShortcut = onRunTerminalShortcut,
        onDownloadOuiDatabase = onDownloadOuiDatabase,
        onDownloadAppUpdate = onDownloadAppUpdate,
        onInstallAppUpdate = onInstallAppUpdate,
        onDownloadFirmwareUpdate = onDownloadFirmwareUpdate,
        onOpenFirmwareFlasher = onOpenFirmwareFlasher,
        onOpenFirmwareFlasherForDiagnostic = onOpenFirmwareFlasherForDiagnostic,
        onOpenModule = onOpenModule,
        onOpenCategory = onOpenCategory,
        onBrowseAllModules = onBrowseAllModules,
        onRefreshDevices = onRefreshDevices,
        onConnect = onConnect,
        onDisconnect = onDisconnect,
        modifier = modifier,
    )
}

@Composable
internal fun HomeScreen(
    connectionState: ConnectionState,
    permissionRevision: Int,
    devices: List<UsbSerialDevice>,
    deviceConnectionStates: Map<String, ConnectionState>,
    deviceFingerprint: (UsbDevice) -> String,
    pendingPermissionRequests: Set<Int>,
    disconnectingFingerprints: Set<String>,
    usbManager: UsbManager,
    modules: List<ModuleCardSpec>,
    listState: LazyListState,
    recentModuleIds: List<String>,
    ouiDatabaseStatus: OuiDatabaseStatus,
    ouiDatabaseProgress: Float?,
    appUpdate: AppUpdateInfo?,
    appUpdateDownload: AppUpdateDownloadState,
    firmwareUpdate: FirmwareUpdateInfo?,
    firmwareAssetDownload: FirmwareAssetDownloadState,
    terminalShortcuts: List<TerminalShortcut> = emptyList(),
    showTerminalLauncherCard: Boolean = true,
    onOpenShortcuts: () -> Unit = {},
    onManageTerminalShortcuts: () -> Unit = {},
    onRunTerminalShortcut: (TerminalShortcut) -> Unit = {},
    onDownloadOuiDatabase: () -> Unit,
    onDownloadAppUpdate: () -> Unit,
    onInstallAppUpdate: () -> Unit,
    onDownloadFirmwareUpdate: () -> Unit,
    onOpenFirmwareFlasher: () -> Unit,
    onOpenFirmwareFlasherForDiagnostic: (String?) -> Unit,
    onOpenModule: (String) -> Unit,
    onOpenCategory: (String) -> Unit,
    onBrowseAllModules: () -> Unit,
    onRefreshDevices: () -> Unit,
    onConnect: (UsbDevice) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val usbConnectBusy = deviceConnectionStates.values.any {
        it is ConnectionState.Connecting || it is ConnectionState.Diagnosing
    } || pendingPermissionRequests.isNotEmpty() || disconnectingFingerprints.isNotEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        appUpdate?.let { update ->
            item {
                val downloadState = appUpdateDownload
                UpdateAvailableCard(
                    title = "App update available",
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
        }


        if (ouiDatabaseStatus is OuiDatabaseStatus.NotDownloaded ||
            ouiDatabaseStatus is OuiDatabaseStatus.Downloading ||
            ouiDatabaseStatus is OuiDatabaseStatus.Error
        ) {
            item {
                OuiDatabaseDashboardWarning(
                    status = ouiDatabaseStatus,
                    progress = ouiDatabaseProgress,
                    onDownload = onDownloadOuiDatabase,
                )
            }
        }

        item {
            DashboardDeviceCard(
                state = connectionState,
                devices = devices,
                deviceConnectionStates = deviceConnectionStates,
                deviceFingerprint = deviceFingerprint,
                pendingPermissionRequests = pendingPermissionRequests,
                disconnectingFingerprints = disconnectingFingerprints,
                usbManager = usbManager,
                usbConnectBusy = usbConnectBusy,
                permissionRevision = permissionRevision,
                onRefreshDevices = onRefreshDevices,
                onConnect = onConnect,
                onDisconnect = onDisconnect,
                firmwareUpdate = firmwareUpdate,
                firmwareAssetDownload = firmwareAssetDownload,
                onDownloadFirmwareUpdate = onDownloadFirmwareUpdate,
                onOpenFirmwareFlasher = onOpenFirmwareFlasher,
                onOpenFirmwareFlasherForDiagnostic = onOpenFirmwareFlasherForDiagnostic,
            )
        }

        if (showTerminalLauncherCard) {
            item {
                HomeTerminalLauncherCard(
                    shortcuts = terminalShortcuts.filter { it.pinHome }.take(3),
                    onOpenShortcuts = onOpenShortcuts,
                    onManageShortcuts = onManageTerminalShortcuts,
                    onRunShortcut = onRunTerminalShortcut,
                )
            }
        }

        item {
            Text(
                text = "Recently used",
                style = MaterialTheme.typography.labelMedium,
                color = NrOnSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        item {
            val recentSpecs = recentModuleIds.mapNotNull { id ->
                modules.firstOrNull { it.id == id }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                recentSpecs.take(3).forEach { spec ->
                    AssistChip(
                        onClick = { if (spec.available) onOpenModule(spec.id) },
                        label = {
                            Text(
                                text = spec.title,
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = spec.icon,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = spec.iconTint ?: NrAccent,
                            )
                        },
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Modules",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                NrQuietButton(
                    text = "Browse all",
                    onClick = onBrowseAllModules,
                    leadingIcon = Icons.Default.Search,
                )
            }
        }

        item {
            val categories = listOf(
                CategorySpec(
                    name = "WiFi",
                    icon = Icons.Default.WifiFind,
                    iconTint = categoryColor("WiFi"),
                    moduleIds = WIFI_ALL_MODULE_IDS,
                    available = true,
                ),
                CategorySpec(
                    name = "BLE",
                    icon = Icons.Default.Bluetooth,
                    iconTint = categoryColor("BLE"),
                    moduleIds = listOf("ble_scanner", "ble_profile", "tracker_detector"),
                    available = true,
                ),
                CategorySpec(
                    name = "Sensors",
                    icon = Icons.Default.Radar,
                    iconTint = categoryColor("Sensors"),
                    moduleIds = listOf("ld2450"),
                    available = true,
                ),
                CategorySpec(
                    name = "Mesh",
                    icon = Icons.Default.SettingsInputAntenna,
                    iconTint = categoryColor("Mesh"),
                    moduleIds = listOf("mesh"),
                    available = true,
                ),
                CategorySpec(
                    name = "Tools",
                    icon = Icons.Default.Search,
                    iconTint = categoryColor("Tools"),
                    moduleIds = listOf("mac_lookup", "bssid_geolocation"),
                    available = true,
                ),
                CategorySpec(
                    name = "Credentials",
                    icon = Icons.Default.Key,
                    iconTint = categoryColor("Credentials"),
                    moduleIds = listOf("credential_manager", "wpa_cracker", "pcap22000"),
                    available = true,
                ),
                CategorySpec(
                    name = "HID",
                    icon = Icons.Default.Keyboard,
                    iconTint = categoryColor("HID"),
                    moduleIds = listOf("ducky", "ble", "badusb", "pin_bruteforce"),
                    available = true,
                ),
                CategorySpec(
                    name = "Storage",
                    icon = Icons.Default.Folder,
                    iconTint = categoryColor("Storage"),
                    moduleIds = listOf("storage"),
                    available = true,
                ),
                CategorySpec(
                    name = "Firmware",
                    icon = Icons.Default.Memory,
                    iconTint = categoryColor("Firmware"),
                    moduleIds = listOf("firmware", "serial_monitor"),
                    available = true,
                ),
                CategorySpec(
                    name = "IR",
                    icon = Icons.Default.SettingsRemote,
                    iconTint = categoryColor("IR"),
                    moduleIds = IR_CATEGORY_MODULE_IDS,
                    available = true,
                ),
                CategorySpec(
                    name = "RF",
                    icon = Icons.Default.Radio,
                    iconTint = categoryColor("RF"),
                    moduleIds = listOf("rf"),
                    available = false,
                    statusLabel = "Unavailable",
                ),
                CategorySpec(
                    name = "RFID",
                    icon = Icons.Default.Nfc,
                    iconTint = categoryColor("RFID"),
                    moduleIds = listOf("rfid"),
                    available = false,
                    statusLabel = "Unavailable",
                ),
            )
            CategoryGrid(
                categories = categories,
                onOpenCategory = onOpenCategory,
            )
        }
    }
}

@Composable
internal fun CategoryGrid(
    categories: List<CategorySpec>,
    onOpenCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { category ->
                    CategoryCard(
                        category = category,
                        onOpenCategory = onOpenCategory,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun CategoryCard(
    category: CategorySpec,
    onOpenCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = category.moduleIds.size
    val countLabel = when {
        count == 1 -> "1 module"
        count > 1 -> "$count modules"
        else -> "No modules"
    }
    val availabilityLabel = if (!category.available) {
        category.statusLabel
    } else {
        null
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (category.available) 1f else 0.4f)
            .clickable(enabled = category.available) {
                onOpenCategory(category.name)
            },
        colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant.copy(alpha = 0.52f)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, NrOutline.copy(alpha = 0.30f)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = category.iconTint,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = category.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = NrOnSurface,
            )
            Text(
                text = availabilityLabel ?: countLabel,
                fontSize = 11.sp,
                color = NrOnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModuleInfoTag(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = NrOnSurfaceVariant,
        modifier = Modifier
            .background(NrSurfaceVariant, RoundedCornerShape(7.dp))
            .border(1.dp, NrOutline.copy(alpha = 0.30f), RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

@Composable
internal fun CategoryModulesScreen(
    modules: List<ModuleCardSpec>,
    onOpenModule: (String) -> Unit,
    modifier: Modifier = Modifier,
    showWifiFilters: Boolean = false,
    wifiGroupFilter: Int = 0,
    onWifiGroupFilterSelected: (Int) -> Unit = {},
    onOpenWifiConfig: () -> Unit = {},
    showIrInfo: Boolean = false,
    irTxLabel: String = "",
    irRxLabel: String = "",
    irDatabaseState: IrDatabaseUiState = IrDatabaseUiState(),
    irDatabaseMetadata: IrDatabaseMetadata? = null,
    irDatabasePresent: Boolean = false,
    irRemoteCount: Int = 0,
    irRootConfigured: Boolean = true,
    onChooseIrRootDirectory: () -> Unit = {},
    onDownloadIrDatabase: () -> Unit = {},
    onCancelIrDatabaseOperation: () -> Unit = {},
    onRescanIrLibrary: () -> Unit = {},
    onOpenIrConfig: () -> Unit = {},
    showRfidInfo: Boolean = false,
    rfidInterfaceLabel: String = "I2C",
    rfidSdaLabel: String = "SDA --",
    rfidSclLabel: String = "SCL --",
    onOpenRfidConfig: () -> Unit = {},
    showSensorConfigAction: Boolean = false,
    onOpenSensorConfig: () -> Unit = {},
    configAccent: Color? = null,
) {
    val filtered = if (showWifiFilters) {
        val ids = when (wifiGroupFilter) {
            1 -> WIFI_ATTACK_MODULE_IDS
            2 -> WIFI_DEFEND_MODULE_IDS
            else -> WIFI_RECON_MODULE_IDS
        }
        ids.mapNotNull { id -> modules.firstOrNull { it.id == id } }
    } else {
        modules
    }
    val effectiveAccent = configAccent ?: MaterialTheme.colorScheme.primary
    var showIrDatabaseRescanDialog by rememberSaveable { mutableStateOf(false) }
    var irDatabaseRescanPrompted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(
        showIrInfo,
        irDatabaseMetadata?.indexedAtMs,
        irDatabaseMetadata?.indexedRemotes,
        irDatabasePresent,
        irDatabaseState.running,
        irRemoteCount,
    ) {
        val databaseExists = irDatabasePresent || irDatabaseMetadata != null
        if (showIrInfo && !irDatabaseRescanPrompted && !irDatabaseState.running &&
            databaseExists && irRemoteCount == 0
        ) {
            irDatabaseRescanPrompted = true
            showIrDatabaseRescanDialog = true
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showWifiFilters) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ModuleInfoTag("2.4 GHz")
                    ModuleInfoTag("5 GHz")
                    Spacer(Modifier.weight(1f))
                    NrQuietButton(
                        text = "WiFi Config",
                        onClick = onOpenWifiConfig,
                        leadingIcon = Icons.Default.Tune,
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val groups = listOf(
                        "Recon" to categoryColor("WiFi Recon"),
                        "Attack" to categoryColor("WiFi Attacks"),
                        "Defend" to categoryColor("WiFi Defense"),
                    )
                    groups.forEachIndexed { index, (label, accent) ->
                        FilterChip(
                            selected = wifiGroupFilter == index,
                            onClick = { onWifiGroupFilterSelected(index) },
                            label = {
                                Text(
                                    text = label,
                                    fontWeight = if (wifiGroupFilter == index) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Medium
                                    },
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.18f),
                                selectedLabelColor = accent,
                            ),
                        )
                    }
                }
            }
        }
        if (showIrInfo) {
            item {
                IrDatabaseCategoryCard(
                    state = irDatabaseState,
                    metadata = irDatabaseMetadata,
                    databasePresent = irDatabasePresent,
                    remoteCount = irRemoteCount,
                    rootConfigured = irRootConfigured,
                    onChooseRoot = onChooseIrRootDirectory,
                    onDownload = onDownloadIrDatabase,
                    onCancel = onCancelIrDatabaseOperation,
                    onRescan = onRescanIrLibrary,
                )
            }
            item {
                IrModuleConfigRow(
                    txLabel = irTxLabel,
                    rxLabel = irRxLabel,
                    onOpenConfig = onOpenIrConfig,
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = effectiveAccent,
                )
            }
        }
        if (showRfidInfo) {
            item {
                RfidModuleConfigRow(
                    interfaceLabel = rfidInterfaceLabel,
                    sdaLabel = rfidSdaLabel,
                    sclLabel = rfidSclLabel,
                    onOpenConfig = onOpenRfidConfig,
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = effectiveAccent,
                )
            }
        }
        if (showSensorConfigAction) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Sensor configuration",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NrOnSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    NrQuietButton(
                        text = "Sensor Config",
                        onClick = onOpenSensorConfig,
                        leadingIcon = Icons.Default.Tune,
                    )
                }
            }
        }
        item {
            Text(
                text = "${filtered.size} modules",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        items(filtered, key = { it.id }, contentType = { "module" }) { module ->
            ModuleCard(
                module = module,
                onClick = { if (module.available) onOpenModule(module.id) },
            )
        }
    }

    if (showIrDatabaseRescanDialog) {
        AlertDialog(
            onDismissRequest = { showIrDatabaseRescanDialog = false },
            title = { Text("Rescan Flipper-IRDB?") },
            text = {
                Text(
                    "Flipper-IRDB is present on storage, but zero remotes are currently " +
                        "indexed. Rescan the IR library now?",
                )
            },
            confirmButton = {
                NrPrimaryButton(
                    text = "Rescan",
                    onClick = {
                        showIrDatabaseRescanDialog = false
                        onRescanIrLibrary()
                    },
                )
            },
            dismissButton = {
                NrQuietButton(
                    text = "Not now",
                    onClick = { showIrDatabaseRescanDialog = false },
                )
            },
        )
    }
}

@Composable
private fun IrDatabaseCategoryCard(
    state: IrDatabaseUiState,
    metadata: IrDatabaseMetadata?,
    databasePresent: Boolean,
    remoteCount: Int,
    rootConfigured: Boolean,
    onChooseRoot: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRescan: () -> Unit,
) {
    val context = LocalContext.current
    val rootMissing = !rootConfigured
    val databaseExists = databasePresent || metadata != null
    val notDownloaded = !databaseExists
    val emptyIndex = databaseExists && remoteCount == 0
    if (!state.running && !rootMissing && databaseExists && remoteCount > 0) return
    if (!state.running && !rootMissing && notDownloaded && !emptyIndex) {
        // Fall through to the download card only when no database is present.
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.34f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                text = when {
                    rootMissing -> "NRSuite root is not configured"
                    state.running && state.phase == IrDatabasePhase.INDEXING ->
                        "IR library indexing in progress"
                    state.running -> "Flipper-IRDB operation in progress"
                    emptyIndex -> "Flipper-IRDB found, but no remotes are indexed"
                    metadata == null && databasePresent -> "Flipper-IRDB found on storage"
                    else -> "Flipper-IRDB not downloaded"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    rootMissing ->
                        "Choose an NRSuite root directory before downloading Flipper-IRDB."
                    state.running ->
                        state.message.orEmpty().ifBlank { "Preparing Flipper-IRDB..." }
                    emptyIndex ->
                        "The database exists on storage, but the remote index contains zero entries."
                    metadata == null && databasePresent ->
                        "Choose Rescan library to index the existing Flipper database."
                    else ->
                        "Download the Flipper remote library to browse and transmit .ir remotes locally."
                },
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            if (state.running) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when (state.phase) {
                            IrDatabasePhase.INDEXING -> "Scanning IR library..."
                            IrDatabasePhase.EXTRACTING -> "Extracting IR database..."
                            else -> "Preparing IR library..."
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (rootMissing) {
                Spacer(Modifier.height(8.dp))
                NrPrimaryButton(
                    text = "Choose NRSuite root",
                    onClick = {
                        Toast.makeText(
                            context,
                            "Choose the NRSuite root directory first.",
                            Toast.LENGTH_SHORT,
                        ).show()
                        onChooseRoot()
                    },
                    leadingIcon = Icons.Default.Folder,
                )
            } else if (state.running) {
                Spacer(Modifier.height(8.dp))
                val progress = when (state.phase) {
                    IrDatabasePhase.DOWNLOADING ->
                        state.downloadPercent?.let { it / 100f }
                    IrDatabasePhase.EXTRACTING,
                    IrDatabasePhase.INDEXING ->
                        state.extractionPercent?.let { it / 100f }
                    else -> null
                }
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                state.currentItem?.takeIf { it.isNotBlank() }?.let { item ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item,
                        style = MaterialTheme.typography.labelSmall,
                        color = NrOnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    NrQuietButton(text = "Cancel", onClick = onCancel)
                }
            } else {
                Spacer(Modifier.height(8.dp))
                if (emptyIndex) {
                    NrPrimaryButton(
                        text = "Rescan library",
                        onClick = onRescan,
                        leadingIcon = Icons.Default.Refresh,
                    )
                } else {
                    NrPrimaryButton(
                        text = if (state.phase == IrDatabasePhase.FAILED) {
                            "Retry download"
                        } else {
                            "Download Flipper-IRDB"
                        },
                        onClick = onDownload,
                        leadingIcon = Icons.Default.Download,
                    )
                }
            }
        }
    }
}

@Composable
internal fun DashboardDeviceCard(
    state: ConnectionState,
    devices: List<UsbSerialDevice>,
    deviceConnectionStates: Map<String, ConnectionState>,
    deviceFingerprint: (UsbDevice) -> String,
    pendingPermissionRequests: Set<Int>,
    disconnectingFingerprints: Set<String>,
    usbManager: UsbManager,
    usbConnectBusy: Boolean,
    permissionRevision: Int,
    onRefreshDevices: () -> Unit,
    onConnect: (UsbDevice) -> Unit,
    onDisconnect: () -> Unit,
    firmwareUpdate: FirmwareUpdateInfo?,
    firmwareAssetDownload: FirmwareAssetDownloadState,
    onDownloadFirmwareUpdate: () -> Unit,
    onOpenFirmwareFlasher: () -> Unit,
    onOpenFirmwareFlasherForDiagnostic: (String?) -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION")
    permissionRevision
    val accent = when (state) {
        ConnectionState.Disconnected -> StatusAmber
        ConnectionState.Connecting -> StatusAmber
        ConnectionState.Diagnosing -> StatusAmber
        is ConnectionState.Connected -> StatusGreen
        is ConnectionState.Failed -> StatusRed
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "DEVICE LINK",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onRefreshDevices,
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh USB devices",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            when (state) {
                ConnectionState.Disconnected -> {
                    StatusIndicator(
                        label = if (devices.isEmpty()) {
                            "No device attached"
                        } else {
                            "Device attached, not connected"
                        },
                        color = StatusAmber,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (devices.isEmpty()) {
                            "Plug in your NRSuite ESP32 with a USB-OTG cable."
                        } else {
                            "Select a device below and tap Connect."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }

                ConnectionState.Connecting -> {
                    StatusIndicator(
                        label = "Connecting to device...",
                        color = StatusAmber,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Waiting for the USB serial handshake and PING response.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }

                ConnectionState.Diagnosing -> {
                    StatusIndicator(
                        label = "Diagnosing connection...",
                        color = StatusAmber,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Checking USB permission, port state, firmware response, and boot activity.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }

                is ConnectionState.Connected -> {
                    StatusIndicator(
                        label = "Connected to ${state.chip ?: "NRSuite device"}",
                        color = StatusGreen,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "fw: ${state.firmwareVersion ?: "unknown"}  ·  USB serial / CDC",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = NrOnSurfaceVariant,
                    )
                    Text(
                        text = "id: ${state.deviceId ?: "unknown"}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = NrOnSurfaceVariant,
                    )
                    if (devices.size > 1) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "${devices.size} USB devices attached",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    FirmwareUpdateInlineNotice(
                        currentVersion = state.firmwareVersion,
                        update = firmwareUpdate,
                        downloadState = firmwareAssetDownload,
                        onDownload = onDownloadFirmwareUpdate,
                        onOpenFlasher = onOpenFirmwareFlasher,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    NrSecondaryButton(
                        text = "Disconnect",
                        onClick = onDisconnect,
                        compact = true,
                    )
                }

                is ConnectionState.Failed -> {
                    StatusIndicator(
                        label = "Connection diagnosis",
                        color = StatusRed,
                    )
                    Spacer(Modifier.height(6.dp))
                    if (state.diagnostic != null) {
                        ConnectionDiagnosticSummary(
                            diagnostic = state.diagnostic,
                            onOpenFirmwareFlasher = onOpenFirmwareFlasherForDiagnostic,
                        )
                    } else {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                }
            }

            if (state !is ConnectionState.Connected && devices.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "ATTACHED USB DEVICES",
                    style = MaterialTheme.typography.labelSmall,
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                devices.take(3).forEach { device ->
                    HomeDeviceRow(
                        entry = device,
                        hasPermission = usbManager.hasPermission(device.device),
                        state = deviceConnectionStates[deviceFingerprint(device.device)],
                        pendingPermission = device.device.deviceId in pendingPermissionRequests,
                        disconnecting = deviceFingerprint(device.device) in disconnectingFingerprints,
                        connectEnabled = !usbConnectBusy,
                        onConnect = { onConnect(device.device) },
                    )
                }
                if (devices.size > 3) {
                    Text(
                        text = "+${devices.size - 3} more — open Devices to manage all",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeDeviceRow(
    entry: UsbSerialDevice,
    hasPermission: Boolean,
    state: ConnectionState?,
    pendingPermission: Boolean,
    disconnecting: Boolean,
    connectEnabled: Boolean,
    onConnect: () -> Unit,
) {
    val statusText = when {
        disconnecting -> "Disconnecting..."
        state is ConnectionState.Connected -> "Connected"
        state is ConnectionState.Connecting -> "Connecting..."
        state is ConnectionState.Diagnosing -> "Diagnosing..."
        pendingPermission -> "Permission requested"
        state is ConnectionState.Failed -> "Connection failed"
        hasPermission -> "Ready to connect"
        else -> "USB permission required"
    }
    val statusColor = when {
        disconnecting -> StatusAmber
        state is ConnectionState.Connected -> StatusGreen
        state is ConnectionState.Connecting -> StatusAmber
        state is ConnectionState.Diagnosing -> StatusAmber
        pendingPermission -> StatusAmber
        state is ConnectionState.Failed -> StatusRed
        else -> NrOnSurfaceVariant
    }
    val buttonEnabled = connectEnabled && !pendingPermission && !disconnecting &&
        state !is ConnectionState.Connected && state !is ConnectionState.Connecting &&
        state !is ConnectionState.Diagnosing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Usb,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (state is ConnectionState.Connected) {
                    "NRSuite ${state.chip ?: "ESP32"}"
                } else {
                    entry.displayName
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
            )
        }
        Spacer(Modifier.width(12.dp))
        NrCompactPrimaryButton(
            text = when {
                pendingPermission -> "Waiting…"
                state is ConnectionState.Connected -> "Connected"
                state is ConnectionState.Connecting -> "Connecting…"
                else -> "Connect"
            },
            onClick = onConnect,
            enabled = buttonEnabled,
            modifier = Modifier.width(100.dp),
        )
    }
}

@Composable
private fun HomeTerminalLauncherCard(
    shortcuts: List<TerminalShortcut>,
    onOpenShortcuts: () -> Unit,
    onManageShortcuts: () -> Unit,
    onRunShortcut: (TerminalShortcut) -> Unit,
) {
    var pendingShortcut by remember { mutableStateOf<TerminalShortcut?>(null) }
    var pendingParameterShortcut by remember { mutableStateOf<TerminalShortcut?>(null) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Terminal Shortcuts",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                NrQuietButton(
                    text = "Open shortcuts",
                    onClick = onOpenShortcuts,
                    compact = true,
                )
            }
            if (shortcuts.isEmpty()) {
                Text(
                    text = "Run saved Termux commands from one place.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
                NrSecondaryButton(
                    text = "Manage shortcuts",
                    onClick = onManageShortcuts,
                    modifier = Modifier.fillMaxWidth(),
                    compact = true,
                )
            } else {
                shortcuts.forEach { shortcut ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (terminalShortcutParameterNames(shortcut.command).isNotEmpty()) {
                                    pendingParameterShortcut = shortcut
                                } else if (shortcut.confirmation) {
                                    pendingShortcut = shortcut
                                } else {
                                    onRunShortcut(shortcut)
                                }
                            }
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = ">_",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = shortcut.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = shortcut.command,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = NrOnSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Run ${shortcut.name}",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                NrQuietButton(
                    text = "Manage shortcuts",
                    onClick = onManageShortcuts,
                    compact = true,
                )
            }
        }
    }

    pendingShortcut?.let { shortcut ->
        AlertDialog(
            onDismissRequest = { pendingShortcut = null },
            title = { Text("Run shortcut?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(shortcut.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        shortcut.command,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                    if (shortcut.description.isNotBlank()) {
                        Text(
                            shortcut.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                NrPrimaryButton(
                    text = "Run",
                    onClick = {
                        pendingShortcut = null
                        onRunShortcut(shortcut)
                    },
                )
            },
            dismissButton = {
                NrQuietButton(
                    text = "Cancel",
                    onClick = { pendingShortcut = null },
                )
            },
        )
    }

    pendingParameterShortcut?.let { shortcut ->
        TerminalShortcutParameterDialog(
            shortcut = shortcut,
            onDismiss = { pendingParameterShortcut = null },
            onRun = { resolved ->
                pendingParameterShortcut = null
                onRunShortcut(resolved)
            },
        )
    }
}

@Composable
private fun UpdateAvailableCard(
    title: String,
    latest: ReleaseInfo,
    buttonLabel: String = "Download",
    buttonEnabled: Boolean = true,
    onButtonClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.34f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = StatusAmber,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
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
            Spacer(Modifier.width(8.dp))
            NrCompactPrimaryButton(
                text = buttonLabel,
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
            )
        }
    }
}

@Composable
internal fun ModulesScreen(
    modules: List<ModuleCardSpec>,
    onOpenModule: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var moduleSearchQuery by rememberSaveable { mutableStateOf("") }
    val categories = listOf(null to "All") + modules.map { it.category }.distinct().map { it to it }
    val categoryFiltered = when (selectedCategory) {
        null -> modules
        "WiFi Recon" -> WIFI_RECON_MODULE_IDS.mapNotNull { id -> modules.firstOrNull { it.id == id } }
        "WiFi Attacks" -> WIFI_ATTACK_MODULE_IDS.mapNotNull { id -> modules.firstOrNull { it.id == id } }
        "WiFi Defense" -> WIFI_DEFEND_MODULE_IDS.mapNotNull { id -> modules.firstOrNull { it.id == id } }
        else -> modules.filter { it.category == selectedCategory }
    }
    val filtered = categoryFiltered.filter { module ->
        val query = moduleSearchQuery.trim()
        query.isBlank() ||
            module.title.contains(query, ignoreCase = true) ||
            module.description.contains(query, ignoreCase = true) ||
            module.category.contains(query, ignoreCase = true)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = moduleSearchQuery,
                onValueChange = { moduleSearchQuery = it },
                placeholder = { Text("Search modules...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                categories.forEach { (category, label) ->
                    NrFilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = label,
                        selectedColor = if (category == null) NrAccent else categoryColor(category),
                    )
                }
            }
        }

        items(filtered, key = { it.id }, contentType = { "module" }) { module ->
            ModuleCard(
                module = module,
                onClick = { if (module.available) onOpenModule(module.id) },
            )
        }
    }
}

@Composable
private fun OuiDatabaseDashboardWarning(
    status: OuiDatabaseStatus,
    progress: Float?,
    onDownload: () -> Unit,
) {
    val downloading = status is OuiDatabaseStatus.Downloading
    val error = status is OuiDatabaseStatus.Error
    val borderColor = when {
        error -> StatusRed
        downloading -> categoryColor("Tools")
        else -> StatusAmber
    }
    val icon = if (downloading) Icons.Default.Download else Icons.Default.WarningAmber
    val iconTint = when {
        error -> StatusRed
        downloading -> categoryColor("Tools")
        else -> StatusAmber
    }
    val title = when (status) {
        OuiDatabaseStatus.Checking -> ""
        OuiDatabaseStatus.NotDownloaded -> "OUI/vendor database not downloaded"
        OuiDatabaseStatus.Downloading -> "Updating OUI/vendor database…"
        is OuiDatabaseStatus.Error -> "OUI/vendor database download failed"
        is OuiDatabaseStatus.Ready -> ""
    }
    val message = when (status) {
        OuiDatabaseStatus.Checking -> ""
        OuiDatabaseStatus.NotDownloaded ->
            "Vendor names and OUI-based signals will be unavailable until this is downloaded."
        OuiDatabaseStatus.Downloading ->
            "Downloading the IEEE OUI registry. Vendor names and OUI-based signals will be available when it finishes."
        is OuiDatabaseStatus.Error -> status.message
        is OuiDatabaseStatus.Ready -> ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.34f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            if (downloading) {
                Spacer(Modifier.height(8.dp))
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { (progress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            } else {
                Spacer(Modifier.height(8.dp))
                NrPrimaryButton(
                    text = if (error) "Retry download" else "Download database",
                    onClick = onDownload,
                )
            }
        }
    }
}
