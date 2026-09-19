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

private const val ACTION_USB_PERMISSION = "com.swp81x.nrsuite.USB_PERMISSION"

private enum class AppTab(val label: String) {
    HOME("Home"),
    MODULES("Modules"),
    LOGS("Logs"),
}

private data class CategorySpec(
    val name: String,
    val icon: ImageVector,
    val iconTint: Color,
    val moduleIds: List<String>,
    val available: Boolean,
    val statusLabel: String? = null,
)

private val modules = listOf(
    ModuleCardSpec(
        id = "wifi",
        title = "WiFi Scan",
        description = "Active 2.4 GHz scan with SSID, BSSID, channel, RSSI, and security.",
        icon = Icons.Default.Wifi,
        category = "Wireless",
        available = true,
    ),
    ModuleCardSpec(
        id = "sniff",
        title = "Packet Sniffer",
        description = "Capture 802.11 frames and export PCAP.",
        icon = Icons.Default.FilterCenterFocus,
        category = "Wireless",
        available = true,
    ),
    ModuleCardSpec(
        id = "beacon",
        title = "Beacon",
        description = "Broadcast custom or hidden SSIDs.",
        icon = Icons.Default.SettingsInputAntenna,
        category = "Wireless",
        available = true,
    ),
    ModuleCardSpec(
        id = "deauth",
        title = "Deauth",
        description = "Send a targeted deauth burst to a BSSID.",
        icon = Icons.Default.WifiOff,
        category = "Wireless",
        available = true,
    ),
    ModuleCardSpec(
        id = "evil_twin",
        title = "Evil Twin",
        description = "Portal + deauth + EAPOL capture workflow.",
        icon = Icons.Default.ContentCopy,
        category = "Wireless",
        available = true,
    ),
    ModuleCardSpec(
        id = "credential_manager",
        title = "Credential Manager",
        description = "Review Evil Twin captures, offline crack results, and saved credentials.",
        icon = Icons.Default.Lock,
        category = "Credentials",
        available = true,
    ),
    ModuleCardSpec(
        id = "wpa_cracker",
        title = "WPA/WPA2 Cracker",
        description = "Offline handshake verification against a wordlist.",
        icon = Icons.Default.Key,
        category = "Credentials",
        available = true,
    ),
    ModuleCardSpec(
        id = "portal",
        title = "Captive Portal",
        description = "Start an AP and serve a custom HTML page.",
        icon = Icons.Default.Language,
        category = "Wireless",
        available = true,
    ),
    ModuleCardSpec(
        id = "ducky",
        title = "Ducky Editor",
        description = "Create, import, and export DuckyScript payloads.",
        icon = Icons.Default.Code,
        category = "HID",
        available = true,
    ),
    ModuleCardSpec(
        id = "ble",
        title = "BLE HID",
        description = "BadBLE payloads and realtime keyboard input.",
        icon = Icons.Default.Bluetooth,
        category = "HID",
        available = true,
    ),
    ModuleCardSpec(
        id = "storage",
        title = "Mass Storage",
        description = "Browse and manage files on the device.",
        icon = Icons.Default.Folder,
        category = "Storage",
        available = true,
    ),
    ModuleCardSpec(
        id = "badusb",
        title = "BadUSB",
        description = "Native USB HID payloads.",
        icon = Icons.Default.Keyboard,
        category = "HID",
        available = true,
    ),
    ModuleCardSpec(
        id = "firmware",
        title = "Firmware Flasher",
        description = "Flash a complete merged firmware image over the USB ROM bootloader.",
        icon = Icons.Default.Memory,
        category = "Firmware",
        available = true,
    ),
    ModuleCardSpec(
        id = "serial_debugger",
        title = "Serial Debugger",
        description = "NRSuite serial monitor and debugger (planned).",
        icon = Icons.Default.DeveloperBoard,
        category = "Firmware",
        available = false,
        statusLabel = "Planned",
    ),
    ModuleCardSpec(
        id = "ir",
        title = "IR",
        description = "Infrared transmit/receive module (planned).",
        icon = Icons.Default.SettingsRemote,
        category = "IR",
        available = false,
        statusLabel = "Planned",
    ),
    ModuleCardSpec(
        id = "rf",
        title = "RF",
        description = "Sub-GHz radio frequency module (planned).",
        icon = Icons.Default.Radio,
        category = "RF",
        available = false,
        statusLabel = "Planned",
    ),
    ModuleCardSpec(
        id = "rfid",
        title = "RFID",
        description = "RFID/NFC read and emulation module (planned).",
        icon = Icons.Default.Nfc,
        category = "RFID",
        available = false,
        statusLabel = "Planned",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NRSuiteApp() {
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val viewModel = remember(appContext) {
        (appContext as NrSuiteApplication).controller
    }
    var showStartup by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(900)
        showStartup = false
    }
    Box(modifier = Modifier.fillMaxSize()) {
        NRSuiteContent(viewModel = viewModel)
        if (showStartup) {
            StartupScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NRSuiteContent(viewModel: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiScope = rememberCoroutineScope()
    val usbManager = remember {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    val devices by viewModel.devices.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val history by viewModel.history.collectAsState()
    val activeDeviceName by viewModel.activeDeviceName.collectAsState()
    val firmwareFlashName by viewModel.firmwareFlashName.collectAsState()
    val firmwareFlashSize by viewModel.firmwareFlashSize.collectAsState()
    val firmwareFlashing by viewModel.firmwareFlashing.collectAsState()
    val firmwareFlashProgress by viewModel.firmwareFlashProgress.collectAsState()
    val firmwareFlashStatus by viewModel.firmwareFlashStatus.collectAsState()
    val firmwareTargetDevice by viewModel.firmwareTargetDevice.collectAsState()
    val recentModuleIds by viewModel.recentModuleIds.collectAsState()
    val scanning by viewModel.scanning.collectAsState()
    val networks by viewModel.networks.collectAsState()
    val sniffing by viewModel.sniffing.collectAsState()
    val sniffPacketCount by viewModel.sniffPacketCount.collectAsState()
    val sniffHandshake by viewModel.sniffHandshake.collectAsState()
    val capturePath by viewModel.capturePath.collectAsState()
    val exportDirectoryName by viewModel.exportDirectoryName.collectAsState()
    val requiresRootDirectory by viewModel.requiresRootDirectory.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    val beaconRunning by viewModel.beaconRunning.collectAsState()
    val beaconSent by viewModel.beaconSent.collectAsState()
    val beaconSsidCount by viewModel.beaconSsidCount.collectAsState()
    val beaconChannel by viewModel.beaconChannel.collectAsState()
    val beaconLists by viewModel.beaconListMap.collectAsState()
    val deauthRunning by viewModel.deauthRunning.collectAsState()
    val deauthSent by viewModel.deauthSent.collectAsState()
    val deauthTarget by viewModel.deauthTarget.collectAsState()
    val deauthChannel by viewModel.deauthChannel.collectAsState()
    val portalRunning by viewModel.portalRunning.collectAsState()
    val portalMode by viewModel.portalMode.collectAsState()
    val portalHtmlSize by viewModel.portalHtmlSize.collectAsState()
    val portalHtmlComplete by viewModel.portalHtmlComplete.collectAsState()
    val portalHtmlUploading by viewModel.portalHtmlUploading.collectAsState()
    val portalHtmlUploadProgress by viewModel.portalHtmlUploadProgress.collectAsState()
    val evilTwinHtmlUploading by viewModel.evilTwinHtmlUploading.collectAsState()
    val evilTwinHtmlUploadProgress by viewModel.evilTwinHtmlUploadProgress.collectAsState()
    val evilTwinHtmlComplete by viewModel.evilTwinHtmlComplete.collectAsState()
    val portalSsid by viewModel.portalSsid.collectAsState()
    val portalChannel by viewModel.portalChannel.collectAsState()
    val portalViews by viewModel.portalViews.collectAsState()
    val portalClients by viewModel.portalClients.collectAsState()
    val portalCapturedData by viewModel.portalCapturedData.collectAsState()
    val portalCredentials by viewModel.portalCredentials.collectAsState()
    val portalHtmlName by viewModel.portalHtmlName.collectAsState()
    val portalEventLog by viewModel.portalEventLog.collectAsState()
    val evilTwinEventLog by viewModel.evilTwinEventLog.collectAsState()
    val evilTwinHtmlName by viewModel.evilTwinHtmlName.collectAsState()
    val portalHandshake by viewModel.portalHandshake.collectAsState()
    val evilTwinPasswords by viewModel.evilTwinPasswords.collectAsState()
    val evilTwinResults by viewModel.evilTwinResults.collectAsState()
    val credentialSessions by viewModel.credentialSessions.collectAsState()
    val crackerSelectedSession by viewModel.crackerSelectedSession.collectAsState()
    val crackerWordlistName by viewModel.crackerWordlistName.collectAsState()
    val crackerCustomPcapName by viewModel.crackerCustomPcapName.collectAsState()
    val crackerCustomPcapValid by viewModel.crackerCustomPcapValid.collectAsState()
    val crackerCustomPcapValidating by viewModel.crackerCustomPcapValidating.collectAsState()
    val crackerCustomPcapMessage by viewModel.crackerCustomPcapMessage.collectAsState()
    val crackerCustomSsidOptions by viewModel.crackerCustomSsidOptions.collectAsState()
    val crackerCustomSsidSelected by viewModel.crackerCustomSsidSelected.collectAsState()
    val crackerCustomSsidManual by viewModel.crackerCustomSsidManual.collectAsState()
    val crackerCustomSsid by viewModel.crackerCustomSsid.collectAsState()
    val crackerRunning by viewModel.crackerRunning.collectAsState()
    val crackerTested by viewModel.crackerTested.collectAsState()
    val crackerSpeed by viewModel.crackerSpeed.collectAsState()
    val crackerResult by viewModel.crackerResult.collectAsState()
    val crackerStatus by viewModel.crackerStatus.collectAsState()
    val storageFiles by viewModel.storageFiles.collectAsState()
    val storageTotal by viewModel.storageTotal.collectAsState()
    val storageUsed by viewModel.storageUsed.collectAsState()
    val storageFree by viewModel.storageFree.collectAsState()
    val storageLoading by viewModel.storageLoading.collectAsState()
    val badUsbPayloadName by viewModel.badUsbPayloadName.collectAsState()
    val badUsbUploading by viewModel.badUsbUploading.collectAsState()
    val badUsbProgress by viewModel.badUsbProgress.collectAsState()
    val duckyScripts by viewModel.duckyScriptMap.collectAsState()
    val bleAdvertising by viewModel.bleAdvertising.collectAsState()
    val bleConnected by viewModel.bleConnected.collectAsState()
    val blePeer by viewModel.blePeer.collectAsState()
    val blePayloadName by viewModel.blePayloadName.collectAsState()
    val bleModifiers by viewModel.bleModifiers.collectAsState()
    val bleModifierHold by viewModel.bleModifierHold.collectAsState()
    val bleScriptRunning by viewModel.bleScriptRunning.collectAsState()

    val connected = connectionState as? ConnectionState.Connected
    val connectedChip = connected?.chip
    val isDeviceConnected = connected != null
    val deviceFeatures = connected?.features.orEmpty()
    val liveModules = remember(
        connectionState,
        scanning,
        sniffing,
        beaconRunning,
        deauthRunning,
        portalRunning,
        portalMode,
        bleAdvertising,
    ) {
        modules.map { module ->
        val runsWithoutDevice = module.id == "ducky" || module.id == "firmware" || module.id == "credential_manager" || module.id == "wpa_cracker"
        val featureKey = when (module.id) {
            "wifi" -> "wifi"
            "sniff" -> "sniff"
            "beacon" -> "beacon"
            "deauth" -> "deauth"
            "portal" -> "portal"
            "evil_twin" -> "portal"
            "storage" -> "storage"
            "ble" -> "ble_hid"
            "badusb" -> "badusb"
            else -> null
        }
        val chipSupported = when (module.id) {
            "ble" -> connectedChip in setOf("ESP32-C3", "ESP32-S3", "ESP32")
            "badusb" -> connectedChip in setOf("ESP32-S2", "ESP32-S3")
            else -> true
        }
        val featureSupported = if (deviceFeatures.isEmpty() || featureKey == null) {
            chipSupported
        } else {
            featureKey in deviceFeatures
        }
        val supported = featureSupported
        val available = module.available && (runsWithoutDevice || (isDeviceConnected && supported))
        val supportLabel = when {
            !module.available -> module.statusLabel
            !isDeviceConnected && !runsWithoutDevice -> null
            isDeviceConnected && !supported && module.id == "ble" -> "No BLE radio on this chip"
            isDeviceConnected && !supported && module.id == "badusb" -> "Requires S2/S3 or matching firmware"
            isDeviceConnected && !supported && module.id == "evil_twin" -> "Firmware portal support required"
            else -> module.statusLabel
        }
        module.copy(
            available = available,
            statusLabel = supportLabel,
            isRunning = when (module.id) {
                "wifi" -> scanning
                "sniff" -> sniffing
                "beacon" -> beaconRunning
                "deauth" -> deauthRunning
                "portal" -> portalRunning && portalMode == "portal"
                "evil_twin" -> portalRunning && portalMode == "evil_twin"
                "ble" -> bleAdvertising
                else -> false
            },
        )
    }
    }

    var selectedTab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var permissionRevision by remember { mutableIntStateOf(0) }
    var activeModuleId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var showRootDirectoryDialog by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = firmwareFlashing) {
        // Swallow back while a firmware flash is in progress.
    }
    BackHandler(enabled = (activeModuleId != null || selectedCategory != null) && !firmwareFlashing) {
        if (activeModuleId != null) {
            activeModuleId = null
        } else {
            selectedCategory = null
        }
    }

    val permissionReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != ACTION_USB_PERMISSION) return
                @Suppress("DEPRECATION")
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (device != null) {
                    val reallyGranted = granted || usbManager.hasPermission(device)
                    viewModel.onPermissionResult(device, reallyGranted)
                    permissionRevision++
                }
            }
        }
    }

    DisposableEffect(context, permissionReceiver) {
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        ContextCompat.registerReceiver(
            context,
            permissionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            runCatching { context.unregisterReceiver(permissionReceiver) }
        }
    }

    val anyModuleRunning = sniffing || beaconRunning || deauthRunning || portalRunning || bleAdvertising || crackerRunning
    val view = LocalView.current
    DisposableEffect(anyModuleRunning) {
        val window = (view.context as? Activity)?.window
        if (anyModuleRunning) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setExportDirectory(uri)
        }
    }

    LaunchedEffect(requiresRootDirectory) {
        if (requiresRootDirectory) {
            showRootDirectoryDialog = true
            viewModel.onRootDirectoryPromptShown()
        }
    }

    LaunchedEffect(exportDirectoryName) {
        if (exportDirectoryName == "Not configured") {
            showRootDirectoryDialog = true
        }
    }

    val htmlPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.setPortalHtmlFile(uri, null)
        }
    }

    val evilTwinHtmlPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.setEvilTwinHtmlFile(uri, null)
        }
    }

    val crackerWordlistPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.setCrackerWordlist(uri, null)
        }
    }

    val crackerPcapPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.setCrackerCustomPcap(uri, null)
        }
    }

    val badUsbPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.setBadUsbPayload(uri, null)
        }
    }

    val blePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.setBlePayload(uri, null)
        }
    }

    val firmwarePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.setFirmwareFlashFile(uri, uri.lastPathSegment?.substringAfterLast('/'))
        }
    }

    fun requestPermission(device: UsbDevice) {
        // FLAG_MUTABLE is required so the system can attach
        // EXTRA_DEVICE and EXTRA_PERMISSION_GRANTED to the broadcast.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val pendingIntent = PendingIntent.getBroadcast(context, device.deviceId, intent, flags)
        usbManager.requestPermission(device, pendingIntent)

        // Fallback for devices/OEMs that do not deliver the permission
        // broadcast reliably: poll hasPermission() and force a recompose.
        uiScope.launch {
            repeat(40) {
                kotlinx.coroutines.delay(250)
                if (usbManager.hasPermission(device)) {
                    permissionRevision++
                    return@launch
                }
            }
        }
    }

    val activeModule = liveModules.firstOrNull { it.id == activeModuleId }
    val showBack = activeModuleId != null || selectedCategory != null
    val activeTitle = when {
        activeModuleId == "firmware" -> "Flasher"
        activeModuleId == "wpa_cracker" -> "WPA Cracker"
        activeModule != null -> activeModule.title
        activeModuleId == "settings" -> "Settings"
        selectedCategory != null -> selectedCategory!!
        else -> "NRSuite"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = activeTitle,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = {
                            if (activeModuleId != null) {
                                activeModuleId = null
                            } else {
                                selectedCategory = null
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
                actions = {
                    ConnectionStatusIndicator(connectionState)
                    IconButton(onClick = { activeModuleId = "settings" }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = NrOnSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (activeModuleId == null) {
                NavigationBar(
                    containerColor = NrSurface,
                    tonalElevation = 0.dp,
                ) {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            enabled = !firmwareFlashing,
                            onClick = {
                                selectedTab = tab
                                activeModuleId = null
                                selectedCategory = null
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NrAccent,
                                selectedTextColor = NrAccent,
                                indicatorColor = NrAccent.copy(alpha = 0.16f),
                                unselectedIconColor = NrOnSurfaceVariant,
                                unselectedTextColor = NrOnSurfaceVariant,
                            ),
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        AppTab.HOME -> Icons.Default.Home
                                        AppTab.MODULES -> Icons.Default.GridView
                                        AppTab.LOGS -> Icons.Default.Terminal
                                    },
                                    contentDescription = tab.label,
                                )
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)

        if (showRootDirectoryDialog) {
            AlertDialog(
                onDismissRequest = { showRootDirectoryDialog = false },
                title = { Text("NRSuite root directory") },
                text = {
                    Text(
                        text = "NRSuite needs a root folder for PCAP captures, logs, " +
                            "DuckyScripts, and portal exports. Choose a folder now, " +
                            "or tap Later and choose it when starting a capture.",
                        color = NrOnSurfaceVariant,
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        showRootDirectoryDialog = false
                        folderPicker.launch(null)
                    }) {
                        Text("Choose folder")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showRootDirectoryDialog = false }) {
                        Text("Later")
                    }
                },
            )
        }

        actionError?.let { message ->
            AlertDialog(
                onDismissRequest = viewModel::consumeActionError,
                title = { Text("Action blocked") },
                text = {
                    Text(
                        text = message,
                        color = NrOnSurfaceVariant,
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::consumeActionError) {
                        Text("OK")
                    }
                },
            )
        }

        when {
            activeModuleId == "wifi" -> {
                WifiScanScreen(
                    scanning = scanning,
                    networks = networks,
                    onScan = viewModel::scanWifi,
                    modifier = contentModifier,
                )
            }

            activeModuleId == "sniff" -> {
                SniffScreen(
                    connected = connectionState is ConnectionState.Connected,
                    sniffing = sniffing,
                    packetCount = sniffPacketCount,
                    capturePath = capturePath,
                    handshake = sniffHandshake,
                    scanning = scanning,
                    networks = networks,
                    exportDirectoryName = exportDirectoryName,
                    onChooseExportDirectory = { folderPicker.launch(null) },
                    onScanWifi = viewModel::scanWifi,
                    onStart = viewModel::startSniff,
                    onStop = viewModel::stopSniff,
                    modifier = contentModifier,
                )
            }

            activeModuleId == "beacon" -> {
                BeaconScreen(
                    connected = connectionState is ConnectionState.Connected,
                    running = beaconRunning,
                    sentFrames = beaconSent,
                    ssidCount = beaconSsidCount,
                    activeChannel = beaconChannel,
                    savedLists = beaconLists,
                    onSaveListAction = viewModel::saveBeaconList,
                    onDeleteList = viewModel::deleteBeaconList,
                    onStart = viewModel::startBeacon,
                    onStop = viewModel::stopBeacon,
                    modifier = contentModifier,
                )
            }

            activeModuleId == "deauth" -> {
                DeauthScreen(
                    connected = connectionState is ConnectionState.Connected,
                    running = deauthRunning,
                    sentFrames = deauthSent,
                    targetBssid = deauthTarget,
                    activeChannel = deauthChannel,
                    scanning = scanning,
                    networks = networks,
                    onScanWifi = viewModel::scanWifi,
                    onStart = viewModel::startDeauth,
                    modifier = contentModifier,
                )
            }

            activeModuleId == "evil_twin" -> {
                EvilTwinScreen(
                    connected = connectionState is ConnectionState.Connected,
                    running = portalRunning && portalMode == "evil_twin",
                    scanning = scanning,
                    networks = networks,
                    handshake = portalHandshake,
                    results = evilTwinResults,
                    eventLog = evilTwinEventLog,
                    selectedHtmlName = evilTwinHtmlName,
                    htmlUploading = evilTwinHtmlUploading,
                    htmlProgress = evilTwinHtmlUploadProgress,
                    htmlComplete = evilTwinHtmlComplete,
                    onScanWifi = viewModel::scanWifi,
                    onChooseHtml = { evilTwinHtmlPicker.launch(arrayOf("text/html", "text/plain", "*/*")) },
                    onClearHtml = viewModel::clearEvilTwinHtmlFile,
                    onStart = viewModel::startEvilTwin,
                    onStop = viewModel::stopPortal,
                    onClearPasswords = viewModel::clearEvilTwinPasswords,
                    onClearEventLog = viewModel::clearEvilTwinEventLog,
                    modifier = contentModifier,
                )
            }

            activeModuleId == "credential_manager" -> {
                CredentialManagerScreen(
                    sessions = credentialSessions,
                    onDeleteSession = viewModel::deleteCredentialSession,
                    onClearAll = viewModel::clearCredentialSessions,
                    modifier = contentModifier,
                )
            }

            activeModuleId == "wpa_cracker" -> {
                WpaCrackerScreen(
                    sessions = credentialSessions,
                    selectedSession = crackerSelectedSession,
                    customPcapName = crackerCustomPcapName,
                    customPcapValid = crackerCustomPcapValid,
                    customPcapValidating = crackerCustomPcapValidating,
                    customPcapMessage = crackerCustomPcapMessage,
                    customSsidOptions = crackerCustomSsidOptions,
                    customSsidSelected = crackerCustomSsidSelected,
                    customSsidManualEnabled = crackerCustomSsidManual,
                    customSsid = crackerCustomSsid,
                    wordlistName = crackerWordlistName,
                    running = crackerRunning,
                    tested = crackerTested,
                    speed = crackerSpeed,
                    result = crackerResult,
                    status = crackerStatus,
                    onSelectSession = viewModel::selectCrackerSession,
                    onChooseCustomPcap = {
                        crackerPcapPicker.launch(
                            arrayOf(
                                "application/vnd.tcpdump.pcap",
                                "application/octet-stream",
                                "*/*",
                            ),
                        )
                    },
                    onClearCustomPcap = viewModel::clearCrackerCustomPcap,
                    onSelectCustomSsid = viewModel::selectCrackerCustomSsid,
                    onUseManualCustomSsid = viewModel::useManualCrackerSsid,
                    onCustomSsidChange = viewModel::setCrackerCustomSsid,
                    onChooseWordlist = { crackerWordlistPicker.launch(arrayOf("text/plain", "*/*")) },
                    onClearWordlist = viewModel::clearCrackerWordlist,
                    onStart = viewModel::startCracker,
                    onStop = viewModel::stopCracker,
                    modifier = contentModifier,
                )
            }

            activeModuleId == "portal" -> {
                PortalScreen(
                    connected = connectionState is ConnectionState.Connected,
                    running = portalRunning && portalMode == "portal",
                    htmlSize = portalHtmlSize,
                    htmlComplete = portalHtmlComplete,
                    activeSsid = portalSsid,
                    activeChannel = portalChannel,
                    portalViews = portalViews,
                    portalClients = portalClients,
                    capturedData = portalCapturedData,
                    credentials = portalCredentials,
                    selectedHtmlName = portalHtmlName,
                    htmlUploading = portalHtmlUploading,
                    htmlProgress = portalHtmlUploadProgress,
                    eventLog = portalEventLog,
                    onChooseHtml = { htmlPicker.launch(arrayOf("text/html", "text/plain", "*/*")) },
                    onClearHtml = viewModel::clearPortalHtmlFile,
                    onClearEventLog = viewModel::clearPortalEventLog,
                    onStart = viewModel::startPortal,
                    onStop = viewModel::stopPortal,
                    modifier = contentModifier,
                )
            }

            activeModuleId == "storage" -> {
                StorageScreen(
                    connected = connectionState is ConnectionState.Connected,
                    loading = storageLoading,
                    files = storageFiles,
                    totalBytes = storageTotal,
                    usedBytes = storageUsed,
                    freeBytes = storageFree,
                    onRefresh = viewModel::refreshStorage,
                    onDelete = viewModel::deleteStorageFile,
                    onStartMassStorage = viewModel::startMassStorage,
                    modifier = contentModifier,
                )
            }

            activeModuleId == "badusb" -> {
                BadUsbScreen(
                    connected = connectionState is ConnectionState.Connected,
                    uploading = badUsbUploading,
                    progress = badUsbProgress,
                    selectedPayloadName = badUsbPayloadName,
                    savedScripts = duckyScripts,
                    onUseSavedScript = viewModel::useBadUsbSavedScript,
                    onChoosePayload = { badUsbPicker.launch(arrayOf("text/plain", "*/*")) },
                    onClearPayload = viewModel::clearBadUsbPayload,
                    onArm = viewModel::armBadUsb,
                    modifier = contentModifier,
                )
            }

            activeModuleId == "ducky" -> {
                DuckyEditorScreen(
                    savedScripts = duckyScripts,
                    onSaveScript = viewModel::saveDuckyScript,
                    onDeleteScript = viewModel::deleteDuckyScript,
                    modifier = contentModifier,
                )
            }

            activeModuleId == "ble" -> {
                BleScreen(
                    connected = connectionState is ConnectionState.Connected,
                    advertising = bleAdvertising,
                    bleConnected = bleConnected,
                    peer = blePeer,
                    selectedPayloadName = blePayloadName,
                    savedScripts = duckyScripts,
                    modifiers = bleModifiers,
                    modifierHold = bleModifierHold,
                    bleScriptRunning = bleScriptRunning,
                    onUseSavedScript = viewModel::useBleSavedScript,
                    onChoosePayload = { blePicker.launch(arrayOf("text/plain", "*/*")) },
                    onClearPayload = viewModel::clearBlePayload,
                    onStartAdvertising = viewModel::startBle,
                    onStop = viewModel::stopBle,
                    onRunPayload = viewModel::runBlePayload,
                    onSendText = viewModel::sendBleKeyboardText,
                    onRealtimeInput = viewModel::sendBleRealtimeInput,
                    onSpecialKey = viewModel::sendBleSpecialKey,
                    onModifierChange = viewModel::setBleModifier,
                    onModifierHoldChange = viewModel::setBleModifierHold,
                    onMouseMove = viewModel::sendBleMouseMove,
                    onMouseScroll = viewModel::sendBleMouseScroll,
                    onMouseButton = viewModel::sendBleMouseButton,
                    modifier = contentModifier,
                )
            }

            activeModuleId == "firmware" -> SettingsScreen(
                connectionState = connectionState,
                exportDirectoryName = exportDirectoryName,
                firmwareFileName = firmwareFlashName,
                firmwareFlashSize = firmwareFlashSize,
                firmwareFlashing = firmwareFlashing,
                firmwareFlashProgress = firmwareFlashProgress,
                firmwareFlashStatus = firmwareFlashStatus,
                devices = devices,
                usbManager = usbManager,
                permissionRevision = permissionRevision,
                selectedFlashTarget = firmwareTargetDevice,
                onChooseExportDirectory = { folderPicker.launch(null) },
                onChooseFirmware = { firmwarePicker.launch(arrayOf("application/octet-stream", "*/*")) },
                onClearFirmware = viewModel::clearFirmwareFlashFile,
                onRefreshDevices = viewModel::refreshDevices,
                onRequestPermission = { device -> requestPermission(device) },
                onSelectFlashTarget = viewModel::selectFirmwareTarget,
                onStartFirmwareFlash = viewModel::startFirmwareFlash,
                flasherOnly = true,
                modifier = contentModifier,
            )

            activeModuleId == "settings" -> SettingsScreen(
                connectionState = connectionState,
                exportDirectoryName = exportDirectoryName,
                firmwareFileName = firmwareFlashName,
                firmwareFlashSize = firmwareFlashSize,
                firmwareFlashing = firmwareFlashing,
                firmwareFlashProgress = firmwareFlashProgress,
                firmwareFlashStatus = firmwareFlashStatus,
                devices = devices,
                usbManager = usbManager,
                permissionRevision = permissionRevision,
                selectedFlashTarget = firmwareTargetDevice,
                onChooseExportDirectory = { folderPicker.launch(null) },
                onChooseFirmware = { firmwarePicker.launch(arrayOf("application/octet-stream", "*/*")) },
                onClearFirmware = viewModel::clearFirmwareFlashFile,
                onRefreshDevices = viewModel::refreshDevices,
                onRequestPermission = { device -> requestPermission(device) },
                onSelectFlashTarget = viewModel::selectFirmwareTarget,
                onStartFirmwareFlash = viewModel::startFirmwareFlash,
                modifier = contentModifier,
            )

            selectedCategory != null && selectedTab == AppTab.HOME -> {
                CategoryModulesScreen(
                    modules = liveModules.filter { it.category == selectedCategory },
                    onOpenModule = { moduleId ->
                        viewModel.onModuleOpened(moduleId)
                        activeModuleId = moduleId
                    },
                    modifier = contentModifier,
                )
            }

            selectedTab == AppTab.HOME -> HomeScreen(
                connectionState = connectionState,
                activeDeviceName = activeDeviceName,
                permissionRevision = permissionRevision,
                devices = devices,
                usbManager = usbManager,
                modules = liveModules,
                onOpenModule = {
                    viewModel.onModuleOpened(it)
                    activeModuleId = it
                },
                onOpenCategory = { selectedCategory = it },
                recentModuleIds = recentModuleIds,
                onRefreshDevices = viewModel::refreshDevices,
                onConnect = { device ->
                    if (usbManager.hasPermission(device)) {
                        viewModel.connect(device)
                    } else {
                        requestPermission(device)
                    }
                },
                onDisconnect = viewModel::disconnect,
                onManageDevice = { activeModuleId = "settings" },
                modifier = contentModifier,
            )

            selectedTab == AppTab.MODULES -> ModulesScreen(
                modules = liveModules,
                onOpenModule = { activeModuleId = it },
                modifier = contentModifier,
            )

            selectedTab == AppTab.LOGS -> LogsScreen(
                logs = logs,
                history = history,
                onClearLogs = viewModel::clearLogs,
                onClearHistory = viewModel::clearHistory,
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun ConnectionStatusIndicator(state: ConnectionState) {
    val (label, color) = when (state) {
        ConnectionState.Disconnected -> "Offline" to StatusNeutral
        ConnectionState.Connecting -> "Connecting" to StatusAmber
        is ConnectionState.Connected -> (state.chip ?: "Online") to StatusGreen
        is ConnectionState.Failed -> "Error" to StatusRed
    }
    StatusIndicator(
        label = label,
        color = color,
        modifier = Modifier.padding(end = 12.dp),
    )
}

@Composable
private fun HomeScreen(
    connectionState: ConnectionState,
    activeDeviceName: String?,
    permissionRevision: Int,
    devices: List<UsbSerialDevice>,
    usbManager: UsbManager,
    modules: List<ModuleCardSpec>,
    recentModuleIds: List<String>,
    onOpenModule: (String) -> Unit,
    onOpenCategory: (String) -> Unit,
    onRefreshDevices: () -> Unit,
    onConnect: (UsbDevice) -> Unit,
    onDisconnect: () -> Unit,
    onManageDevice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            DashboardDeviceCard(
                state = connectionState,
                onDisconnect = onDisconnect,
                onManageDevice = onManageDevice,
            )
        }

        if (connectionState !is ConnectionState.Connected) {
            activeDeviceName?.let { name ->
                item {
                    Text(
                        text = "Last used: $name",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = NrOnSurfaceVariant,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Attached USB devices",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRefreshDevices) {
                        Text("Refresh")
                    }
                }
            }

            if (devices.isEmpty()) {
                item {
                    Text(
                        text = "No supported USB serial devices found. Plug in your NRSuite ESP32 over OTG.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
            } else {
                items(devices, key = { it.device.deviceId }) { device ->
                    DeviceRow(
                        entry = device,
                        hasPermission = usbManager.hasPermission(device.device),
                        permissionRevision = permissionRevision,
                        onConnect = { onConnect(device.device) },
                    )
                }
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
                                tint = NrAccent,
                            )
                        },
                    )
                }
            }
        }

        item {
            Text(
                text = "Modules",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        item {
            val firmwareConnected = connectionState is ConnectionState.Connected
            val categories = listOf(
                CategorySpec(
                    name = "Wireless",
                    icon = Icons.Default.Wifi,
                    iconTint = NrAccent,
                    moduleIds = listOf("wifi", "sniff", "beacon", "deauth", "evil_twin", "portal"),
                    available = firmwareConnected,
                ),
                CategorySpec(
                    name = "Credentials",
                    icon = Icons.Default.Key,
                    iconTint = StatusAmber,
                    moduleIds = listOf("credential_manager", "wpa_cracker"),
                    available = true,
                ),
                CategorySpec(
                    name = "HID",
                    icon = Icons.Default.Keyboard,
                    iconTint = Color(0xFFA78BFA),
                    moduleIds = listOf("ducky", "ble", "badusb"),
                    available = true,
                ),
                CategorySpec(
                    name = "Storage",
                    icon = Icons.Default.Folder,
                    iconTint = StatusAmber,
                    moduleIds = listOf("storage"),
                    available = firmwareConnected,
                ),
                CategorySpec(
                    name = "BLE",
                    icon = Icons.Default.Bluetooth,
                    iconTint = Color(0xFF3D9BFF),
                    moduleIds = emptyList(),
                    available = false,
                    statusLabel = "Planned",
                ),
                CategorySpec(
                    name = "Firmware",
                    icon = Icons.Default.Memory,
                    iconTint = Color(0xFF34D399),
                    moduleIds = listOf("firmware", "serial_debugger"),
                    available = true,
                ),
                CategorySpec(
                    name = "IR",
                    icon = Icons.Default.SettingsRemote,
                    iconTint = Color(0xFFAAB2BD),
                    moduleIds = listOf("ir"),
                    available = false,
                    statusLabel = "Planned",
                ),
                CategorySpec(
                    name = "RF",
                    icon = Icons.Default.Radio,
                    iconTint = Color(0xFFAAB2BD),
                    moduleIds = listOf("rf"),
                    available = false,
                    statusLabel = "Planned",
                ),
                CategorySpec(
                    name = "RFID",
                    icon = Icons.Default.Nfc,
                    iconTint = Color(0xFFAAB2BD),
                    moduleIds = listOf("rfid"),
                    available = false,
                    statusLabel = "Planned",
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
private fun CategoryGrid(
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
private fun CategoryCard(
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
        category.statusLabel ?: "Unavailable"
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
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, NrOutline),
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
                text = if (availabilityLabel != null) "$countLabel · $availabilityLabel" else countLabel,
                fontSize = 11.sp,
                color = NrOnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoryModulesScreen(
    modules: List<ModuleCardSpec>,
    onOpenModule: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = "${modules.size} modules",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        items(modules, key = { it.id }, contentType = { "module" }) { module ->
            ModuleCard(
                module = module,
                onClick = { if (module.available) onOpenModule(module.id) },
            )
        }
    }
}

@Composable
private fun DashboardDeviceCard(
    state: ConnectionState,
    onDisconnect: () -> Unit,
    onManageDevice: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
    ) {
        Column(Modifier.padding(16.dp)) {
            when (state) {
                ConnectionState.Disconnected -> {
                    StatusIndicator(
                        label = "No device connected",
                        color = StatusAmber,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Plug in your NRSuite ESP32 with a USB OTG cable, then grant USB permission.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }

                ConnectionState.Connecting -> {
                    StatusIndicator(
                        label = "Connecting to device...",
                        color = StatusAmber,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Waiting for the USB serial handshake and PING response.",
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
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onManageDevice) {
                            Text("Manage device")
                        }
                        OutlinedButton(onClick = onDisconnect) {
                            Text("Disconnect")
                        }
                    }
                }

                is ConnectionState.Failed -> {
                    StatusIndicator(
                        label = "Connection problem",
                        color = StatusRed,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModulesScreen(
    modules: List<ModuleCardSpec>,
    onOpenModule: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var moduleSearchQuery by rememberSaveable { mutableStateOf("") }
    val categories = listOf(null to "All") + modules.map { it.category }.distinct().map { it to it }
    val categoryFiltered = if (selectedCategory == null) modules else modules.filter { it.category == selectedCategory }
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
private fun LogsScreen(
    logs: List<LogEntry>,
    history: List<HistoryEntry>,
    onClearLogs: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    val filtered = logs.filter { selectedLevel == null || it.level == selectedLevel }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Runtime") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("History (${history.size})") })
        }

        if (selectedTab == 0) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NrSurfaceVariant.copy(alpha = 0.4f)),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Session log",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            val text = logs.joinToString("\n") { "${it.timestamp} [${it.tag}] ${it.message}" }
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(text))
                        }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy log") }
                        IconButton(onClick = {
                            val text = logs.joinToString("\n") { "${it.timestamp} [${it.tag}] ${it.message}" }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(intent, "Export log"))
                        }) { Icon(Icons.Default.Share, contentDescription = "Export log") }
                        IconButton(onClick = onClearLogs, enabled = logs.isNotEmpty()) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear log")
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val levels = listOf(null to "All", LogLevel.ERROR to "Errors", LogLevel.USB to "USB", LogLevel.SUCCESS to "Success")
                        levels.forEach { (level, label) ->
                            NrFilterChip(
                                selected = selectedLevel == level,
                                onClick = { selectedLevel = level },
                                label = label,
                            )
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    item { Text("No logs yet. Connect a device or run a module.", color = NrOnSurfaceVariant) }
                } else {
                    items(filtered.takeLast(300).reversed(), contentType = { "log" }) { entry ->
                        val textColor = when (entry.level) {
                            LogLevel.ERROR -> LogColorError
                            LogLevel.SUCCESS -> LogColorSuccess
                            LogLevel.USB -> LogColorUsb
                            LogLevel.INFO -> LogColorInfo
                        }
                        val bg = if (entry.level == LogLevel.ERROR) LogBgError else Color.Transparent
                        SelectionContainer {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(bg, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    text = entry.timestamp,
                                    fontSize = 11.sp,
                                    color = NrOnSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(56.dp),
                                )
                                Text(
                                    text = "[${entry.tag}] ${entry.message}",
                                    fontSize = 12.sp,
                                    color = textColor,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f),
                                    softWrap = true,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Session history",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            val text = history.joinToString("\n") { "${it.timestamp} [${it.module}] ${it.summary}" }
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(text))
                        }, enabled = history.isNotEmpty()) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy history") }
                        IconButton(onClick = {
                            val text = history.joinToString("\n") { "${it.timestamp} [${it.module}] ${it.summary}" }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(intent, "Export history"))
                        }, enabled = history.isNotEmpty()) { Icon(Icons.Default.Share, contentDescription = "Export history") }
                        IconButton(onClick = onClearHistory, enabled = history.isNotEmpty()) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear history")
                        }
                    }
                }
                if (history.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = NrOnSurfaceVariant,
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("No sessions yet", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Run a module to record your first session.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NrOnSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(history, contentType = { "history" }) { entry ->
                        val color = when (entry.level) {
                            HistoryLevel.SUCCESS -> LogColorSuccess
                            HistoryLevel.ERROR -> LogColorError
                            HistoryLevel.INFO -> LogColorInfo
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = NrSurface),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.5.dp, NrOutline),
                        ) {
                            SelectionContainer {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text(
                                        text = entry.timestamp,
                                        fontSize = 11.sp,
                                        color = NrOnSurfaceVariant,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.width(56.dp),
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = "[${entry.module.uppercase()}]",
                                            fontSize = 12.sp,
                                            color = color,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = entry.summary,
                                            fontSize = 12.sp,
                                            color = NrOnSurfaceVariant,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = entry.level.name,
                                        fontSize = 10.sp,
                                        color = color,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceScreen(
    connectionState: ConnectionState,
    devices: List<UsbSerialDevice>,
    usbManager: UsbManager,
    onRefresh: () -> Unit,
    onConnect: (UsbDevice) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            DeviceConnectionCard(
                state = connectionState,
                onDisconnect = onDisconnect,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Attached USB devices",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRefresh) {
                    Text("Refresh")
                }
            }
        }

        if (devices.isEmpty()) {
            item {
                Text(
                    text = "No supported USB serial devices found. Plug in an ESP32 via OTG and tap Refresh.",
                    color = NrOnSurfaceVariant,
                )
            }
        } else {
            items(devices, key = { it.device.deviceId }) { device ->
                DeviceRow(
                    entry = device,
                    hasPermission = usbManager.hasPermission(device.device),
                    permissionRevision = 0,
                    onConnect = { onConnect(device.device) },
                )
            }
        }

        if (connectionState is ConnectionState.Connected) {
            item {
                HardwareInfoCard(state = connectionState)
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        text = "Firmware",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Firmware update/flashing is not implemented in this app yet. The current firmware version is reported by the device STATUS command.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceConnectionCard(
    state: ConnectionState,
    onDisconnect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "Connection",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            val (label, color) = when (state) {
                ConnectionState.Disconnected -> "Disconnected" to StatusNeutral
                ConnectionState.Connecting -> "Connecting..." to StatusAmber
                is ConnectionState.Connected -> "Connected" to StatusGreen
                is ConnectionState.Failed -> "Error: ${state.message}" to StatusRed
            }
            StatusIndicator(label = label, color = color)
            if (state is ConnectionState.Connected) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Chip: ${state.chip ?: "unknown"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
                Text(
                    text = "Firmware: ${state.firmwareVersion ?: "unknown"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
            if (state !is ConnectionState.Disconnected) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onDisconnect) {
                    Text("Disconnect")
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    entry: UsbSerialDevice,
    hasPermission: Boolean,
    permissionRevision: Int,
    onConnect: () -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION")
    permissionRevision
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.displayName,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (hasPermission) "Permission granted" else "Permission required",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onConnect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NrAccent,
                    contentColor = com.swp81x.nrsuite.ui.theme.NrBackground,
                ),
            ) {
                Text("Connect")
            }
        }
    }
}

@Composable
private fun HardwareInfoCard(state: ConnectionState.Connected) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "Hardware info",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Chip: ${state.chip ?: "unknown"}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
            Text(
                text = "Transport: USB serial / CDC",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}

@Composable
private fun SettingsScreen(
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
                    text = "This writes a complete merged .bin image and replaces the whole firmware. It is not an OTA/incremental update.",
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
                                Text(if (selected) "Selected" else "Select")
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
                                "This will overwrite the entire firmware on " +
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
