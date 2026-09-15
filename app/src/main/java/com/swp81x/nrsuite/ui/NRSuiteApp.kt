package com.swp81x.nrsuite.ui

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Widgets
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
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed
import org.json.JSONObject

private const val ACTION_USB_PERMISSION = "com.swp81x.nrsuite.USB_PERMISSION"

private enum class AppTab(val label: String) {
    HOME("Home"),
    MODULES("Modules"),
    LOGS("Logs"),
    DEVICE("Device"),
    SETTINGS("Settings"),
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
        available = false,
        statusLabel = "Planned",
    ),
    ModuleCardSpec(
        id = "beacon",
        title = "Beacon Broadcast",
        description = "Broadcast custom or hidden SSIDs.",
        icon = Icons.Default.Campaign,
        category = "Wireless",
        available = false,
        statusLabel = "Planned",
    ),
    ModuleCardSpec(
        id = "portal",
        title = "Captive Portal",
        description = "Start an AP and serve a custom HTML page.",
        icon = Icons.Default.Lock,
        category = "Wireless",
        available = false,
        statusLabel = "Planned",
    ),
    ModuleCardSpec(
        id = "ble",
        title = "BLE HID",
        description = "Keyboard emulation handled by the ESP32.",
        icon = Icons.Default.Bluetooth,
        category = "HID",
        available = false,
        statusLabel = "Planned",
    ),
    ModuleCardSpec(
        id = "storage",
        title = "Mass Storage",
        description = "Browse and manage files on the device.",
        icon = Icons.Default.Storage,
        category = "Storage",
        available = false,
        statusLabel = "Planned",
    ),
    ModuleCardSpec(
        id = "badusb",
        title = "BadUSB",
        description = "Native USB HID payloads.",
        icon = Icons.Default.Keyboard,
        category = "HID",
        available = false,
        statusLabel = "Planned",
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

    var selectedTab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var activeModuleId by rememberSaveable { mutableStateOf<String?>(null) }

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

    fun requestPermission(device: UsbDevice) {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val pendingIntent = PendingIntent.getBroadcast(context, device.deviceId, intent, flags)
        usbManager.requestPermission(device, pendingIntent)
    }

    val activeModule = modules.firstOrNull { it.id == activeModuleId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = activeModule?.title ?: "NRSuite",
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
                                        AppTab.DEVICE -> Icons.Default.Usb
                                        AppTab.SETTINGS -> Icons.Default.Settings
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

            selectedTab == AppTab.HOME -> HomeScreen(
                connectionState = connectionState,
                modules = modules,
                onOpenModule = { activeModuleId = it },
                onGoToDevice = { selectedTab = AppTab.DEVICE },
                modifier = contentModifier,
            )

            selectedTab == AppTab.MODULES -> ModulesScreen(
                modules = modules,
                onOpenModule = { activeModuleId = it },
                modifier = contentModifier,
            )

            selectedTab == AppTab.LOGS -> LogsScreen(
                logs = logs,
                modifier = contentModifier,
            )

            selectedTab == AppTab.DEVICE -> DeviceScreen(
                connectionState = connectionState,
                devices = devices,
                usbManager = usbManager,
                onRefresh = viewModel::refreshDevices,
                onConnect = { device ->
                    if (usbManager.hasPermission(device)) {
                        viewModel.connect(device)
                    } else {
                        requestPermission(device)
                    }
                },
                onDisconnect = viewModel::disconnect,
                modifier = contentModifier,
            )

            selectedTab == AppTab.SETTINGS -> SettingsScreen(modifier = contentModifier)
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
    modules: List<ModuleCardSpec>,
    onOpenModule: (String) -> Unit,
    onGoToDevice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val availableModules = modules.filter { it.available }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = "Dashboard",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Device status and quick module access.",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        item {
            DashboardDeviceCard(
                state = connectionState,
                onGoToDevice = onGoToDevice,
            )
        }

        if (connectionState is ConnectionState.Connected) {
            item {
                Text(
                    text = "Quick actions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (availableModules.isEmpty()) {
                item {
                    Text(
                        text = "No modules are available for this firmware yet.",
                        color = NrOnSurfaceVariant,
                    )
                }
            } else {
                items(availableModules, key = { it.id }) { module ->
                    ModuleCard(
                        module = module,
                        onClick = { onOpenModule(module.id) },
                    )
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            text = "Available after connection",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Connect your ESP32 over USB OTG to enable WiFi Scan and future modules.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardDeviceCard(
    state: ConnectionState,
    onGoToDevice: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            when (state) {
                ConnectionState.Disconnected -> {
                    StatusIndicator(
                        label = "No companion device connected",
                        color = StatusAmber,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Plug in your NRSuite ESP32 with a USB OTG cable, then grant USB permission.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onGoToDevice) {
                        Text("Connect a device")
                    }
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
                        text = "Firmware: ${state.firmwareVersion ?: "unknown"}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = NrOnSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onGoToDevice) {
                        Text("Manage device")
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
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onGoToDevice) {
                        Text("Open Device tab")
                    }
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

        modules.groupBy { it.category }.forEach { (category, categoryModules) ->
            item(key = "header-$category") {
                Text(
                    text = category.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = NrAccent,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(categoryModules, key = { it.id }) { module ->
                ModuleCard(
                    module = module,
                    onClick = { onOpenModule(module.id) },
                )
            }
        }
    }
}

@Composable
private fun LogsScreen(
    logs: List<String>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text(
                text = "Session log",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        if (logs.isEmpty()) {
            item {
                Text(
                    text = "No logs yet. Connect a device or run a module.",
                    color = NrOnSurfaceVariant,
                )
            }
        } else {
            items(logs.takeLast(300).reversed()) { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
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
                Text(if (hasPermission) "Connect" else "Allow")
            }
        }
    }
}

@Composable
private fun HardwareInfoCard(state: ConnectionState.Connected) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
private fun SettingsScreen(modifier: Modifier = Modifier) {
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
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Appearance", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Dark technical theme is fixed for now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("About", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "NRSuite Android app. The ESP32 handles radio, BLE, and USB peripheral work.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }
    }
}
