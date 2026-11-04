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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.swp81x.nrsuite.MainViewModel
import com.swp81x.nrsuite.NrSuiteApplication
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.update.FirmwareUpdateInfo
import com.swp81x.nrsuite.core.update.FirmwareAssetDownloadState
import com.swp81x.nrsuite.core.update.compatibleFirmwareAssets
import com.swp81x.nrsuite.core.update.VersionComparator
import com.swp81x.nrsuite.core.update.ReleaseInfo
import com.swp81x.nrsuite.ui.components.ConnectionDiagnosticSummary
import com.swp81x.nrsuite.ui.components.FirmwareUpdateInlineNotice
import com.swp81x.nrsuite.core.usb.UsbSerialDevice
import com.swp81x.nrsuite.ui.components.ModuleCard
import com.swp81x.nrsuite.ui.components.ModuleCardSpec
import com.swp81x.nrsuite.ui.components.NrCompactPrimaryButton
import com.swp81x.nrsuite.ui.components.NrCompactSecondaryButton
import com.swp81x.nrsuite.ui.components.NrCompactStatusButton
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.components.NrPrimaryButton
import com.swp81x.nrsuite.ui.components.NrQuietButton
import com.swp81x.nrsuite.ui.components.NrSecondaryButton
import com.swp81x.nrsuite.ui.components.NrStatusAction
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.components.NrAnimatedVisibility
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
import java.util.Locale

@Composable
internal fun DeviceScreen(
    connectionState: ConnectionState,
    activeFingerprint: String?,
    devices: List<UsbSerialDevice>,
    deviceConnectionStates: Map<String, ConnectionState>,
    deviceFingerprint: (UsbDevice) -> String,
    pendingPermissionRequests: Set<Int>,
    disconnectingFingerprints: Set<String>,
    usbManager: UsbManager,
    firmwareUpdate: FirmwareUpdateInfo?,
    firmwareReleases: List<ReleaseInfo>,
    firmwareAssetDownload: FirmwareAssetDownloadState,
    onDownloadFirmwareUpdate: () -> Unit,
    onOpenFirmwareFlasherForUpdate: () -> Unit,
    onRefresh: () -> Unit,
    onConnect: (UsbDevice) -> Unit,
    onDisconnect: () -> Unit,
    onDisconnectDevice: (UsbDevice) -> Unit,
    onRefreshStatus: (UsbDevice) -> Unit,
    onSwitchDevice: (UsbDevice) -> Unit,
    onOpenFirmwareFlasher: (UsbDevice?, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val usbConnectBusy = deviceConnectionStates.values.any {
        it is ConnectionState.Connecting || it is ConnectionState.Diagnosing
    } || pendingPermissionRequests.isNotEmpty() || disconnectingFingerprints.isNotEmpty()
    val activeDevice = activeFingerprint?.let { selected ->
        devices.firstOrNull { deviceFingerprint(it.device) == selected }
    }
    val otherDevices = devices.filterNot {
        activeFingerprint != null && deviceFingerprint(it.device) == activeFingerprint
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            DeviceConnectionCard(
                state = connectionState,
                activeDevice = activeDevice,
                disconnecting = disconnectingFingerprints.isNotEmpty(),
                onDisconnect = onDisconnect,
                onRefreshStatus = onRefreshStatus,
                onOpenFirmwareFlasher = onOpenFirmwareFlasher,
                firmwareUpdate = firmwareUpdate,
                firmwareAssetDownload = firmwareAssetDownload,
                onDownloadFirmwareUpdate = onDownloadFirmwareUpdate,
                onOpenFirmwareFlasherForUpdate = onOpenFirmwareFlasherForUpdate,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Other USB devices",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                NrQuietButton(
                    text = "Refresh",
                    onClick = onRefresh,
                )
            }
        }

        if (otherDevices.isEmpty()) {
            item {
                Text(
                    text = if (activeDevice != null) {
                        "No other USB devices attached."
                    } else {
                        "No supported USB serial devices found. Plug in an ESP32 via OTG and tap Refresh."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        } else {
            items(otherDevices, key = { it.device.deviceId }) { device ->
                DeviceRow(
                    entry = device,
                    hasPermission = usbManager.hasPermission(device.device),
                    permissionRevision = 0,
                    state = deviceConnectionStates[deviceFingerprint(device.device)],
                    pendingPermission = device.device.deviceId in pendingPermissionRequests,
                    disconnecting = deviceFingerprint(device.device) in disconnectingFingerprints,
                    connectEnabled = !usbConnectBusy,
                    updateAvailableTag = availableFirmwareUpdateTag(
                        deviceConnectionStates[deviceFingerprint(device.device)],
                        firmwareReleases,
                    ),
                    onConnect = { onConnect(device.device) },
                    onDisconnect = { onDisconnectDevice(device.device) },
                    onSwitch = { onSwitchDevice(device.device) },
                    onRefreshStatus = onRefreshStatus,
                    onOpenFirmwareFlasher = { chip ->
                        onOpenFirmwareFlasher(device.device, chip)
                    },
                )
            }
        }

    }
}

@Composable
internal fun DeviceConnectionCard(
    state: ConnectionState,
    activeDevice: UsbSerialDevice?,
    disconnecting: Boolean,
    onDisconnect: () -> Unit,
    onRefreshStatus: (UsbDevice) -> Unit,
    onOpenFirmwareFlasher: (UsbDevice?, String?) -> Unit,
    firmwareUpdate: FirmwareUpdateInfo?,
    firmwareAssetDownload: FirmwareAssetDownloadState,
    onDownloadFirmwareUpdate: () -> Unit,
    onOpenFirmwareFlasherForUpdate: () -> Unit,
) {
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (state is ConnectionState.Failed) {
                StatusRed.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)
            },
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ACTIVE DEVICE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (state is ConnectionState.Connected) {
                    IconButton(
                        onClick = { activeDevice?.device?.let { onRefreshStatus(it) } },
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh device status",
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            val (label, color) = if (disconnecting) {
                "Disconnecting..." to StatusAmber
            } else {
                when (state) {
                    ConnectionState.Disconnected -> "Disconnected" to StatusNeutral
                    ConnectionState.Connecting -> "Connecting..." to StatusAmber
                    ConnectionState.Diagnosing -> "Diagnosing..." to StatusAmber
                    is ConnectionState.Connected -> "Connected" to StatusGreen
                    is ConnectionState.Failed -> "Connection diagnosis" to StatusRed
                }
            }
            StatusIndicator(label = label, color = color)
            if (state is ConnectionState.Connected) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = activeDevice?.let {
                        if (state.chip != null) "NRSuite ${state.chip}" else it.displayName
                    } ?: "NRSuite ${state.chip ?: "device"}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Firmware: ${state.firmwareVersion ?: "unknown"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
                Text(
                    text = "Device ID: ${state.deviceId ?: "unknown"}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
                NrQuietButton(
                    text = if (detailsExpanded) "Hide details" else "Show details",
                    onClick = { detailsExpanded = !detailsExpanded },
                    leadingIcon = if (detailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                )
                NrAnimatedVisibility(visible = detailsExpanded) {
                    DeviceSpecsContent(state)
                }
                FirmwareUpdateInlineNotice(
                    currentVersion = state.firmwareVersion,
                    update = firmwareUpdate,
                    downloadState = firmwareAssetDownload,
                    onDownload = onDownloadFirmwareUpdate,
                    onOpenFlasher = onOpenFirmwareFlasherForUpdate,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(8.dp))
                NrSecondaryButton(
                    text = "Disconnect",
                    onClick = onDisconnect,
                    compact = true,
                )
            }
            if (state is ConnectionState.Failed && state.diagnostic != null) {
                Spacer(Modifier.height(8.dp))
                ConnectionDiagnosticSummary(
                    diagnostic = state.diagnostic,
                    onOpenFirmwareFlasher = { chip ->
                        onOpenFirmwareFlasher(activeDevice?.device, chip)
                    },
                )
            }
            if (!disconnecting) {
                when (state) {
                    ConnectionState.Connecting -> {
                        Spacer(Modifier.height(8.dp))
                        NrQuietButton(text = "Cancel", onClick = onDisconnect)
                    }
                    ConnectionState.Diagnosing -> Unit
                    is ConnectionState.Failed -> {
                        Spacer(Modifier.height(8.dp))
                        NrQuietButton(text = "Clear", onClick = onDisconnect)
                    }
                    else -> Unit
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
    state: ConnectionState?,
    pendingPermission: Boolean,
    disconnecting: Boolean = false,
    connectEnabled: Boolean = true,
    updateAvailableTag: String? = null,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSwitch: () -> Unit,
    onRefreshStatus: (UsbDevice) -> Unit,
    onOpenFirmwareFlasher: (String?) -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION")
    permissionRevision
    var expanded by rememberSaveable(entry.device.deviceId) { mutableStateOf(false) }
    LaunchedEffect(state is ConnectionState.Failed) {
        if (state is ConnectionState.Failed) expanded = true
    }
    val borderColor = when {
        state is ConnectionState.Connected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        state is ConnectionState.Failed -> StatusRed.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Usb,
                    contentDescription = null,
                    tint = when {
                        state is ConnectionState.Connected -> StatusGreen
                        state is ConnectionState.Failed -> StatusRed
                        else -> NrOnSurfaceVariant
                    },
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
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val statusText = when {
                        disconnecting -> "Disconnecting..."
                        state is ConnectionState.Connected -> "Connected · inactive"
                        state is ConnectionState.Connecting -> "Connecting..."
                        state is ConnectionState.Diagnosing -> "Diagnosing..."
                        pendingPermission -> "Permission requested"
                        state is ConnectionState.Failed -> "Connection failed"
                        hasPermission -> "Permission granted"
                        else -> "Permission required"
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
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                    )
                    if (updateAvailableTag != null) {
                        Text(
                            text = "Update available → $updateAvailableTag",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusAmber,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                when {
                    disconnecting -> {
                        NrCompactStatusButton(
                            text = "Disconnecting…",
                            status = NrStatusAction.PAUSE,
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.width(100.dp),
                        )
                    }

                    state is ConnectionState.Connected -> {
                        NrCompactPrimaryButton(
                            text = "Switch",
                            onClick = onSwitch,
                            modifier = Modifier.width(100.dp),
                        )
                    }

                    state is ConnectionState.Connecting || state is ConnectionState.Diagnosing -> {
                        NrCompactStatusButton(
                            text = if (state is ConnectionState.Diagnosing) {
                                "Diagnosing…"
                            } else {
                                "Connecting…"
                            },
                            status = NrStatusAction.PAUSE,
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.width(100.dp),
                        )
                    }

                    else -> {
                        NrCompactPrimaryButton(
                            text = "Connect",
                            onClick = onConnect,
                            enabled = connectEnabled,
                            modifier = Modifier.width(100.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (state is ConnectionState.Connected) {
                        "Live device details"
                    } else {
                        "USB and firmware details"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                NrQuietButton(
                    text = if (expanded) "Hide" else "Details",
                    onClick = { expanded = !expanded },
                    leadingIcon = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    compact = true,
                )
            }

            NrAnimatedVisibility(visible = expanded) {
                Spacer(Modifier.height(4.dp))
                DeviceDetailsContent(
                    entry = entry,
                    hasPermission = hasPermission,
                    state = state,
                    updateAvailableTag = updateAvailableTag,
                    onRefreshStatus = onRefreshStatus,
                    onOpenFirmwareFlasher = onOpenFirmwareFlasher,
                    onDisconnect = onDisconnect,
                )
            }
        }
    }
}

@Composable
private fun DeviceDetailsContent(
    entry: UsbSerialDevice,
    hasPermission: Boolean,
    state: ConnectionState?,
    updateAvailableTag: String?,
    onRefreshStatus: (UsbDevice) -> Unit,
    onOpenFirmwareFlasher: (String?) -> Unit,
    onDisconnect: () -> Unit,
) {
    DeviceInfoLine("USB port", entry.displayName)
    DeviceInfoLine("USB permission", if (hasPermission) "Granted" else "Required")
    DeviceInfoLine(
        "USB IDs",
        "VID 0x%04X · PID 0x%04X".format(Locale.US, entry.device.vendorId, entry.device.productId),
    )
    DeviceInfoLine("Device path", entry.device.deviceName)
    when (state) {
        is ConnectionState.Connected -> {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Live device specs",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                NrQuietButton(
                    text = "Refresh",
                    onClick = { onRefreshStatus(entry.device) },
                    leadingIcon = Icons.Default.Refresh,
                    compact = true,
                )
            }
            DeviceSpecsContent(state)
            if (updateAvailableTag != null) {
                Spacer(Modifier.height(6.dp))
                NrPrimaryButton(
                    text = "Update this device",
                    onClick = { onOpenFirmwareFlasher(state.chip) },
                    compact = true,
                )
            }
            Spacer(Modifier.height(6.dp))
            NrSecondaryButton(
                text = "Disconnect inactive device",
                onClick = onDisconnect,
                compact = true,
            )
        }
        is ConnectionState.Failed -> {
            if (state.diagnostic != null) {
                Spacer(Modifier.height(8.dp))
                ConnectionDiagnosticSummary(
                    diagnostic = state.diagnostic,
                    onOpenFirmwareFlasher = onOpenFirmwareFlasher,
                )
            } else {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusRed,
                )
            }
        }
        ConnectionState.Connecting -> Text(
            text = "Waiting for the NRSuite handshake.",
            style = MaterialTheme.typography.bodySmall,
            color = NrOnSurfaceVariant,
        )
        ConnectionState.Diagnosing -> Text(
            text = "Collecting connection diagnostics.",
            style = MaterialTheme.typography.bodySmall,
            color = StatusAmber,
        )
        else -> Unit
    }
}

@Composable
private fun DeviceInfoLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = NrOnSurfaceVariant,
            modifier = Modifier.width(108.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DeviceSpecsContent(state: ConnectionState.Connected) {
    SpecRow("Chip", state.chip ?: "Unknown")
    SpecRow("Firmware", state.firmwareVersion ?: "Unknown")
    SpecRow("Device ID", state.deviceId ?: "Unknown")
    SpecRow(
        "Protocol",
        buildString {
            append(state.protocolVersion?.toString() ?: "?")
            if (state.protocolMinor != null) {
                append(" · minor ")
                append(state.protocolMinor)
            }
        },
    )
    SpecRow("Uptime", formatUptime(state.uptimeMs))
    SpecRow("Heap usage", formatHeapUsage(state.heapFree, state.heapTotal, state.heapUsed))
    SpecRow("Min free heap", formatBytes(state.heapMinFree))
    SpecRow("Largest block", formatBytes(state.heapMaxAlloc))
    SpecRow(
        "PSRAM",
        if (state.psramTotal == null) {
            "Not reported"
        } else if (state.psramTotal == 0L) {
            "Not present"
        } else {
            formatPair(state.psramFree, state.psramTotal, "free")
        },
    )
    SpecRow(
        "CPU",
        when {
            state.cpuMhz != null && state.chipCores != null ->
                "${state.cpuMhz} MHz · ${state.chipCores} cores"
            state.cpuMhz != null -> "${state.cpuMhz} MHz"
            state.chipCores != null -> "${state.chipCores} cores"
            else -> "Not reported"
        },
    )
    SpecRow("Flash", formatBytes(state.flashSize))
    if (!state.sdkVersion.isNullOrBlank()) SpecRow("SDK", state.sdkVersion)
    SpecRow("Transport", transportLabel(state.transport))
    SpecRow("Features", "${state.features.size} advertised")
}

private fun transportLabel(transport: String?): String = when (transport) {
    "usb_cdc" -> "Native USB CDC/JTAG"
    "uart0" -> "UART"
    null -> "Not reported"
    else -> transport
}

private fun availableFirmwareUpdateTag(
    state: ConnectionState?,
    releases: List<ReleaseInfo>,
): String? {
    val connected = state as? ConnectionState.Connected ?: return null
    val chip = connected.chip ?: return null
    val current = connected.firmwareVersion ?: return null
    return releases.firstOrNull { release ->
        release.compatibleFirmwareAssets(chip).isNotEmpty() &&
            VersionComparator.isNewer(release.tag, current)
    }?.tag
}

@Composable
internal fun HardwareInfoCard(
    state: ConnectionState.Connected,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Device specs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                NrQuietButton(
                    text = "Refresh",
                    onClick = onRefresh,
                    leadingIcon = Icons.Default.Refresh,
                    compact = true,
                )
            }
            Spacer(Modifier.height(4.dp))

            SpecRow("Chip", state.chip ?: "Unknown")
            SpecRow("Firmware", state.firmwareVersion ?: "Unknown")
            SpecRow("Device ID", state.deviceId ?: "Unknown")
            SpecRow(
                "Protocol",
                buildString {
                    append(state.protocolVersion?.toString() ?: "?")
                    if (state.protocolMinor != null) {
                        append(" · minor ")
                        append(state.protocolMinor)
                    }
                },
            )
            SpecRow("Uptime", formatUptime(state.uptimeMs))
            SpecRow(
                "Heap usage",
                formatHeapUsage(state.heapFree, state.heapTotal, state.heapUsed),
            )
            SpecRow("Min free heap", formatBytes(state.heapMinFree))
            SpecRow("Largest block", formatBytes(state.heapMaxAlloc))
            SpecRow(
                "PSRAM",
                if (state.psramTotal == null) {
                    "Not reported"
                } else if (state.psramTotal == 0L) {
                    "Not present"
                } else {
                    formatPair(state.psramFree, state.psramTotal, "free")
                },
            )
            SpecRow(
                "CPU",
                when {
                    state.cpuMhz != null && state.chipCores != null ->
                        "${state.cpuMhz} MHz · ${state.chipCores} cores"
                    state.cpuMhz != null -> "${state.cpuMhz} MHz"
                    state.chipCores != null -> "${state.chipCores} cores"
                    else -> "Not reported"
                },
            )
            SpecRow("Flash", formatBytes(state.flashSize))
            if (!state.sdkVersion.isNullOrBlank()) {
                SpecRow("SDK", state.sdkVersion)
            }
            SpecRow("Features", "${state.features.size} advertised")
            SpecRow("Transport", transportLabel(state.transport))
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = NrOnSurfaceVariant,
            modifier = Modifier.width(112.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = NrOnSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun formatBytes(bytes: Long?): String {
    if (bytes == null) return "Not reported"
    if (bytes >= 1024L * 1024L) {
        return String.format(Locale.US, "%.2f MiB", bytes / (1024.0 * 1024.0))
    }
    if (bytes >= 1024L) {
        return String.format(Locale.US, "%.1f KiB", bytes / 1024.0)
    }
    return "$bytes B"
}

private fun formatPair(free: Long?, total: Long?, suffix: String): String {
    if (free == null && total == null) return "Not reported"
    if (free == null) return formatBytes(total)
    if (total == null) return formatBytes(free)
    return "${formatBytes(free)} $suffix / ${formatBytes(total)}"
}

private fun formatHeapUsage(free: Long?, total: Long?, used: Long?): String {
    if (total == null || total <= 0L) return "Not reported"
    val actualUsed = (used ?: free?.let { total - it }) ?: return formatBytes(total)
    val percent = ((actualUsed * 100.0) / total).coerceIn(0.0, 100.0)
    return String.format(
        Locale.US,
        "%s used / %s · %s free · %.1f%%",
        formatBytes(actualUsed),
        formatBytes(total),
        free?.let(::formatBytes) ?: "unknown",
        percent,
    )
}

private fun formatUptime(uptimeMs: Long?): String {
    if (uptimeMs == null) return "Not reported"
    val totalSeconds = uptimeMs / 1000
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return when {
        days > 0 -> "${days}d ${hours}h ${minutes}m ${seconds}s"
        hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
