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
internal fun ConnectionStatusIndicator(state: ConnectionState) {
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
internal fun HomeScreen(
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
internal fun CategoryModulesScreen(
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
internal fun DashboardDeviceCard(
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
internal fun ModulesScreen(
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
