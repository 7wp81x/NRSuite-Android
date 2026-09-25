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
import com.swp81x.nrsuite.core.defense.DeauthFeedFilter
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NRSuiteContent(viewModel: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiScope = rememberCoroutineScope()
    val usbManager = remember {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    val devices by viewModel.devices.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val history by viewModel.history.collectAsState()
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
    val deauthDetectorRunning by viewModel.deauthDetectorRunning.collectAsState()
    val deauthDetectorChannel by viewModel.deauthDetectorChannel.collectAsState()
    val deauthDetectorChannelMode by viewModel.deauthDetectorChannelMode.collectAsState()
    val deauthDetectorHopIntervalMs by viewModel.deauthDetectorHopIntervalMs.collectAsState()
    val deauthDetectorCurrentHopChannel by viewModel.deauthDetectorCurrentHopChannel.collectAsState()
    val deauthDetectorActiveAlert by viewModel.deauthDetectorActiveAlert.collectAsState()
    val deauthDetectorFeed by viewModel.deauthDetectorFeed.collectAsState()
    val deauthDetectorFeedFilter by viewModel.deauthDetectorFeedFilter.collectAsState()
    val deauthDetectorFramesPerSecond by viewModel.deauthDetectorFramesPerSecond.collectAsState()
    val deauthDetectorTotalFrames by viewModel.deauthDetectorTotalFrames.collectAsState()
    val deauthDetectorUniqueSourceCount by viewModel.deauthDetectorUniqueSourceCount.collectAsState()
    val deauthDetectorTargets by viewModel.deauthDetectorTargets.collectAsState()
    val deauthDetectorSelectedTarget by viewModel.deauthDetectorSelectedTarget.collectAsState()
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
        deauthDetectorRunning,
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
            "deauth_detector" -> "deauth_detect"
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
            isDeviceConnected && !supported && module.id == "deauth_detector" -> "Requires deauth_detect firmware"
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
                "deauth_detector" -> deauthDetectorRunning
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

    val anyModuleRunning = sniffing || beaconRunning || deauthRunning || deauthDetectorRunning || portalRunning || bleAdvertising || crackerRunning
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

            activeModuleId == "deauth_detector" -> {
                val visibleFeed = remember(deauthDetectorFeed, deauthDetectorFeedFilter) {
                    when (deauthDetectorFeedFilter) {
                        DeauthFeedFilter.ALL -> deauthDetectorFeed
                        DeauthFeedFilter.BROADCAST -> deauthDetectorFeed.filter { it.targetMac == null }
                        DeauthFeedFilter.TARGETED -> deauthDetectorFeed.filter { it.targetMac != null }
                    }
                }
                DeauthDetectorScreen(
                    connected = connectionState is ConnectionState.Connected,
                    running = deauthDetectorRunning,
                    channelMode = deauthDetectorChannelMode,
                    onChannelModeChange = viewModel::setDeauthDetectorChannelMode,
                    hopIntervalMs = deauthDetectorHopIntervalMs,
                    onHopIntervalChange = viewModel::setDeauthDetectorHopIntervalMs,
                    currentHopChannel = deauthDetectorCurrentHopChannel,
                    framesPerSecond = deauthDetectorFramesPerSecond,
                    totalFrames = deauthDetectorTotalFrames,
                    uniqueSourceCount = deauthDetectorUniqueSourceCount,
                    thresholdFramesPerSecond = 10,
                    activeAlert = deauthDetectorActiveAlert,
                    feed = visibleFeed,
                    feedFilter = deauthDetectorFeedFilter,
                    scanResults = deauthDetectorTargets,
                    selectedTarget = deauthDetectorSelectedTarget,
                    channel = deauthDetectorChannel,
                    onChannelChange = viewModel::setDeauthDetectorChannel,
                    onSelectTarget = viewModel::selectDeauthDetectorTarget,
                    onScanClick = viewModel::scanWifi,
                    isScanning = scanning,
                    onFeedFilterChange = viewModel::setDeauthDetectorFeedFilter,
                    onClearFeed = viewModel::clearDeauthDetectorFeed,
                    onStart = viewModel::startDeauthDetector,
                    onStop = viewModel::stopDeauthDetector,
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
