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
internal fun DeviceScreen(
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
internal fun DeviceConnectionCard(
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
internal fun DeviceRow(
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
internal fun HardwareInfoCard(state: ConnectionState.Connected) {
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
