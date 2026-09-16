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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import com.swp81x.nrsuite.ui.theme.LogColorError
import com.swp81x.nrsuite.ui.theme.LogColorInfo
import com.swp81x.nrsuite.ui.theme.LogColorSuccess
import com.swp81x.nrsuite.ui.theme.LogColorUsb
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.FilterChip
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swp81x.nrsuite.MainViewModel
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.usb.UsbSerialDevice
import com.swp81x.nrsuite.ui.components.ModuleCard
import com.swp81x.nrsuite.ui.components.ModuleCardSpec
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed
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
        icon = Icons.AutoMirrored.Filled.Article,
        category = "Wireless",
        available = true,
    ),
    ModuleCardSpec(
        id = "beacon",
        title = "Beacon",
        description = "Broadcast custom or hidden SSIDs.",
        icon = Icons.Default.Campaign,
        category = "Wireless",
        available = true,
    ),
    ModuleCardSpec(
        id = "deauth",
        title = "Deauth",
        description = "Send a targeted deauth burst to a BSSID.",
        icon = Icons.Default.Warning,
        category = "Wireless",
        available = true,
    ),
    ModuleCardSpec(
        id = "portal",
        title = "Captive Portal",
        description = "Start an AP and serve a custom HTML page.",
        icon = Icons.Default.Lock,
        category = "Wireless",
        available = true,
    ),
    ModuleCardSpec(
        id = "ducky",
        title = "Ducky Editor",
        description = "Create, import, and export DuckyScript payloads.",
        icon = Icons.Default.Edit,
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
        icon = Icons.Default.Storage,
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
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NRSuiteApp(viewModel: MainViewModel = viewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val usbManager = remember {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    val devices by viewModel.devices.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val scanning by viewModel.scanning.collectAsState()
    val networks by viewModel.networks.collectAsState()
    val sniffing by viewModel.sniffing.collectAsState()
    val sniffPacketCount by viewModel.sniffPacketCount.collectAsState()
    val sniffHandshake by viewModel.sniffHandshake.collectAsState()
    val capturePath by viewModel.capturePath.collectAsState()
    val exportDirectoryName by viewModel.exportDirectoryName.collectAsState()
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
    val portalHtmlSize by viewModel.portalHtmlSize.collectAsState()
    val portalHtmlComplete by viewModel.portalHtmlComplete.collectAsState()
    val portalSsid by viewModel.portalSsid.collectAsState()
    val portalChannel by viewModel.portalChannel.collectAsState()
    val portalViews by viewModel.portalViews.collectAsState()
    val portalClients by viewModel.portalClients.collectAsState()
    val portalCapturedData by viewModel.portalCapturedData.collectAsState()
    val portalHtmlName by viewModel.portalHtmlName.collectAsState()
    val portalEventLog by viewModel.portalEventLog.collectAsState()
    val storageFiles by viewModel.storageFiles.collectAsState()
    val storageTotal by viewModel.storageTotal.collectAsState()
    val storageUsed by viewModel.storageUsed.collectAsState()
    val storageFree by viewModel.storageFree.collectAsState()
    val storageLoading by viewModel.storageLoading.collectAsState()
    val badUsbPayloadName by viewModel.badUsbPayloadName.collectAsState()
    val badUsbUploading by viewModel.badUsbUploading.collectAsState()
    val badUsbProgress by viewModel.badUsbProgress.collectAsState()
    val bleAdvertising by viewModel.bleAdvertising.collectAsState()
    val bleConnected by viewModel.bleConnected.collectAsState()
    val blePeer by viewModel.blePeer.collectAsState()
    val blePayloadName by viewModel.blePayloadName.collectAsState()

    val connectedChip = (connectionState as? ConnectionState.Connected)?.chip
    val liveModules = modules.map { module ->
        val supported = when (module.id) {
            "ble" -> connectedChip == null || connectedChip in setOf("ESP32-C3", "ESP32-S3", "ESP32")
            "badusb" -> connectedChip == null || connectedChip in setOf("ESP32-S2", "ESP32-S3")
            else -> true
        }
        val supportLabel = when {
            supported -> module.statusLabel
            module.id == "ble" -> "No BLE radio on this chip"
            module.id == "badusb" -> "Requires ESP32-S2/S3"
            else -> "Not supported"
        }
        module.copy(
            available = module.available && supported,
            statusLabel = supportLabel,
            isRunning = when (module.id) {
                "wifi" -> scanning
                "sniff" -> sniffing
                "beacon" -> beaconRunning
                "deauth" -> deauthRunning
                "portal" -> portalRunning
                "ble" -> bleAdvertising
                else -> false
            },
        )
    }

    var selectedTab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var activeModuleId by rememberSaveable { mutableStateOf<String?>(null) }
    var firmwareFileName by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = activeModuleId != null) {
        activeModuleId = null
    }

    val permissionReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != ACTION_USB_PERMISSION) return
                @Suppress("DEPRECATION")
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (device != null) {
                    viewModel.onPermissionResult(device, granted)
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

    val attachReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                    viewModel.onUsbDeviceAttached()
                }
            }
        }
    }

    val detachReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                    @Suppress("DEPRECATION")
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (device != null) viewModel.onUsbDeviceDetached(device)
                }
            }
        }
    }

    DisposableEffect(context, attachReceiver, detachReceiver) {
        ContextCompat.registerReceiver(
            context,
            attachReceiver,
            IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(
            context,
            detachReceiver,
            IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            runCatching { context.unregisterReceiver(attachReceiver) }
            runCatching { context.unregisterReceiver(detachReceiver) }
        }
    }

    val anyModuleRunning = sniffing || beaconRunning || deauthRunning || portalRunning || bleAdvertising
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
        firmwareFileName = uri?.lastPathSegment?.substringAfterLast('/') ?: null
    }

    fun requestPermission(device: UsbDevice) {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val pendingIntent = PendingIntent.getBroadcast(context, device.deviceId, intent, flags)
        usbManager.requestPermission(device, pendingIntent)
    }

    val activeModule = liveModules.firstOrNull { it.id == activeModuleId }
    val activeTitle = activeModule?.title ?: if (activeModuleId == "settings") "Settings" else "NRSuite"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = activeTitle,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Wireless research toolkit",
                            style = MaterialTheme.typography.labelSmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    if (activeModuleId != null) {
                        IconButton(onClick = { activeModuleId = null }) {
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
                NavigationBar {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = {
                                selectedTab = tab
                                activeModuleId = null
                            },
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        AppTab.HOME -> Icons.Default.Home
                                        AppTab.MODULES -> Icons.Default.Widgets
                                        AppTab.LOGS -> Icons.AutoMirrored.Filled.Article
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

            activeModuleId == "portal" -> {
                PortalScreen(
                    connected = connectionState is ConnectionState.Connected,
                    running = portalRunning,
                    htmlSize = portalHtmlSize,
                    htmlComplete = portalHtmlComplete,
                    activeSsid = portalSsid,
                    activeChannel = portalChannel,
                    portalViews = portalViews,
                    portalClients = portalClients,
                    capturedData = portalCapturedData,
                    selectedHtmlName = portalHtmlName,
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
                    onChoosePayload = { badUsbPicker.launch(arrayOf("text/plain", "*/*")) },
                    onClearPayload = viewModel::clearBadUsbPayload,
                    onArm = viewModel::armBadUsb,
                    modifier = contentModifier,
                )
            }

            activeModuleId == "ducky" -> {
                DuckyEditorScreen(modifier = contentModifier)
            }

            activeModuleId == "ble" -> {
                BleScreen(
                    connected = connectionState is ConnectionState.Connected,
                    advertising = bleAdvertising,
                    bleConnected = bleConnected,
                    peer = blePeer,
                    selectedPayloadName = blePayloadName,
                    onChoosePayload = { blePicker.launch(arrayOf("text/plain", "*/*")) },
                    onClearPayload = viewModel::clearBlePayload,
                    onStartAdvertising = viewModel::startBle,
                    onStop = viewModel::stopBle,
                    onRunPayload = viewModel::runBlePayload,
                    onSendText = viewModel::sendBleKeyboardText,
                    modifier = contentModifier,
                )
            }

            activeModuleId == "settings" -> SettingsScreen(
                connectionState = connectionState,
                exportDirectoryName = exportDirectoryName,
                firmwareFileName = firmwareFileName,
                onChooseExportDirectory = { folderPicker.launch(null) },
                onChooseFirmware = { firmwarePicker.launch(arrayOf("application/octet-stream", "*/*")) },
                modifier = contentModifier,
            )

            selectedTab == AppTab.HOME -> HomeScreen(
                connectionState = connectionState,
                devices = devices,
                usbManager = usbManager,
                modules = liveModules,
                onOpenModule = { activeModuleId = it },
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
    devices: List<UsbSerialDevice>,
    usbManager: UsbManager,
    modules: List<ModuleCardSpec>,
    onOpenModule: (String) -> Unit,
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
                        onConnect = { onConnect(device.device) },
                    )
                }
            }
        }

        item {
            Text(
                text = if (connectionState is ConnectionState.Connected) "Quick actions" else "Modules",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        items(modules, key = { it.id }) { module ->
            val dimmed = connectionState !is ConnectionState.Connected
            Box(modifier = Modifier.alpha(if (dimmed) 0.4f else 1f)) {
                ModuleCard(
                    module = module,
                    onClick = { if (!dimmed) onOpenModule(module.id) },
                )
            }
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
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    val categories = listOf(null to "All") + modules.map { it.category }.distinct().map { it to it }
    val filtered = if (selectedCategory == null) modules else modules.filter { it.category == selectedCategory }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = "Module catalog",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Grouped by category. Unavailable modules show their status.",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.forEach { (category, label) ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(label) },
                    )
                }
            }
        }

        items(filtered, key = { it.id }) { module ->
            ModuleCard(
                module = module,
                onClick = { onOpenModule(module.id) },
            )
        }
    }
}

@Composable
private fun LogsScreen(
    logs: List<LogEntry>,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    val filtered = logs.filter { selectedLevel == null || it.level == selectedLevel }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Session log",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    val text = logs.joinToString("\n") { "${it.timestamp} [${it.tag}] ${it.message}" }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(intent, "Export log"))
                }) {
                    Icon(Icons.Default.Share, contentDescription = "Export log")
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val levels = listOf(
                    null to "All",
                    LogLevel.ERROR to "Errors",
                    LogLevel.USB to "USB",
                    LogLevel.SUCCESS to "Success",
                )
                levels.forEach { (level, label) ->
                    FilterChip(
                        selected = selectedLevel == level,
                        onClick = { selectedLevel = level },
                        label = { Text(label) },
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Text(
                    text = "No logs yet. Connect a device or run a module.",
                    color = NrOnSurfaceVariant,
                )
            }
        } else {
            items(filtered.takeLast(300).reversed()) { entry ->
                val textColor = when (entry.level) {
                    LogLevel.ERROR -> LogColorError
                    LogLevel.SUCCESS -> LogColorSuccess
                    LogLevel.USB -> LogColorUsb
                    LogLevel.INFO -> LogColorInfo
                }
                val bg = if (entry.level == LogLevel.ERROR) LogBgError else Color.Transparent
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bg, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
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
                    )
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
    onConnect: () -> Unit,
) {
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
            Button(onClick = onConnect) {
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
    onChooseExportDirectory: () -> Unit,
    onChooseFirmware: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentFirmware = (connectionState as? ConnectionState.Connected)?.firmwareVersion

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Capture export folder", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = exportDirectoryName,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = onChooseExportDirectory) {
                    Text("Choose folder")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Captures are written here when selected. Otherwise they stay in app-private storage.",
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
                Text("Firmware update", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Current version: ${currentFirmware ?: "not connected"}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = firmwareFileName ?: "No firmware .bin selected",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onChooseFirmware) {
                    Text("Choose firmware .bin")
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { },
                    enabled = false,
                ) {
                    Text("Start update (not implemented)")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "UI only for now. The flashing/download/verify/write workflow is intentionally not wired yet.",
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
