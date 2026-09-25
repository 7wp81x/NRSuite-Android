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
import androidx.compose.material.icons.filled.PersonSearch
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
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.WifiFind
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


internal enum class AppTab(val label: String) {
    HOME("Home"),
    MODULES("Modules"),
    LOGS("Logs"),
}

internal data class CategorySpec(
    val name: String,
    val icon: ImageVector,
    val iconTint: Color,
    val moduleIds: List<String>,
    val available: Boolean,
    val statusLabel: String? = null,
)

internal val modules = listOf(
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
        id = "client_presence",
        title = "Client/Presence Detector",
        description = "Passively detect client management frames, with optional active reconnection triggering.",
        icon = Icons.Default.PersonSearch,
        category = "Detection",
        available = true,
    ),
    ModuleCardSpec(
        id = "deauth_detector",
        title = "Deauth Detector",
        description = "Passively detect deauth and disassoc frames around you.",
        icon = Icons.Default.WifiFind,
        category = "Detection",
        available = true,
    ),
    ModuleCardSpec(
        id = "rogue_ap",
        title = "Rogue AP Detector",
        description = "Compare visible APs against a trusted baseline and OUI rules.",
        icon = Icons.Default.Router,
        category = "Detection",
        available = true,
    ),
    ModuleCardSpec(
        id = "mac_lookup",
        title = "MAC Lookup",
        description = "Offline OUI and vendor lookup for any MAC address.",
        icon = Icons.Default.Search,
        category = "Detection",
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
