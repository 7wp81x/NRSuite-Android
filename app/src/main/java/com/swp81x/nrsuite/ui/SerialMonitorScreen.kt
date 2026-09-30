package com.swp81x.nrsuite.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.SerialLogLine
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.usb.UsbSerialDevice
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.LogColorError
import com.swp81x.nrsuite.ui.theme.LogColorInfo
import com.swp81x.nrsuite.ui.theme.LogColorSuccess
import com.swp81x.nrsuite.ui.theme.LogColorUsb
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed
import com.swp81x.nrsuite.ui.util.copyWithToast

private val BAUD_RATES = listOf(9600, 19200, 38400, 57600, 115200, 230400, 460800, 921600)
private val LINE_ENDINGS = listOf("None", "LF", "CR", "CRLF")

@Composable
fun SerialMonitorScreen(
    devices: List<UsbSerialDevice>,
    selectedDevice: UsbSerialDevice?,
    connected: Boolean,
    connecting: Boolean,
    baudRate: Int,
    logLines: List<SerialLogLine>,
    hexMode: Boolean,
    inputText: String,
    lineEnding: String,
    blockingMessage: String?,
    errorMessage: String?,
    permissionRevision: Int,
    hasPermission: (UsbSerialDevice) -> Boolean,
    onRefreshDevices: () -> Unit,
    onSelectDevice: (UsbSerialDevice) -> Unit,
    onRequestPermission: (UsbSerialDevice) -> Unit,
    onConnect: (UsbSerialDevice, Int) -> Unit,
    onDisconnect: () -> Unit,
    onBaudChange: (Int) -> Unit,
    onHexModeChange: (Boolean) -> Unit,
    onInputChange: (String) -> Unit,
    onLineEndingChange: (String) -> Unit,
    onSend: () -> Unit,
    onClearLog: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_EXPRESSION")
    permissionRevision

    var configExpanded by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(connected) {
        if (connected) configExpanded = false
    }

    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val logListState = rememberLazyListState()
    val logTextForCopy = remember(logLines) {
        logLines.joinToString("\n") { "[${it.timestamp}] ${it.text}" }
    }

    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty()) {
            logListState.animateScrollToItem(logLines.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SerialMonitorConnectionCard(
            devices = devices,
            selectedDevice = selectedDevice,
            connected = connected,
            connecting = connecting,
            baudRate = baudRate,
            hexMode = hexMode,
            expanded = configExpanded,
            hasPermission = hasPermission,
            onToggle = { configExpanded = !configExpanded },
            onRefreshDevices = onRefreshDevices,
            onSelectDevice = onSelectDevice,
            onRequestPermission = onRequestPermission,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
            onBaudChange = onBaudChange,
            onHexModeChange = onHexModeChange,
        )

        blockingMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = StatusAmber,
            )
        }
        errorMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = StatusRed,
            )
        }

        SerialMonitorLogCard(
            modifier = Modifier.weight(1f),
            logLines = logLines,
            hexMode = hexMode,
            logTextForCopy = logTextForCopy,
            logListState = logListState,
            inputText = inputText,
            lineEnding = lineEnding,
            connected = connected,
            onInputChange = onInputChange,
            onLineEndingChange = onLineEndingChange,
            onSend = onSend,
            onHexModeChange = onHexModeChange,
            onCopy = { clipboard.copyWithToast(context, logTextForCopy, "Serial log copied") },
            onClearLog = onClearLog,
            onExport = onExport,
        )
    }
}

@Composable
private fun SerialMonitorConnectionCard(
    devices: List<UsbSerialDevice>,
    selectedDevice: UsbSerialDevice?,
    connected: Boolean,
    connecting: Boolean,
    baudRate: Int,
    hexMode: Boolean,
    expanded: Boolean,
    hasPermission: (UsbSerialDevice) -> Boolean,
    onToggle: () -> Unit,
    onRefreshDevices: () -> Unit,
    onSelectDevice: (UsbSerialDevice) -> Unit,
    onRequestPermission: (UsbSerialDevice) -> Unit,
    onConnect: (UsbSerialDevice, Int) -> Unit,
    onDisconnect: () -> Unit,
    onBaudChange: (Int) -> Unit,
    onHexModeChange: (Boolean) -> Unit,
) {
    var deviceMenuExpanded by remember { mutableStateOf(false) }
    var baudMenuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, if (connected) StatusGreen else NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Usb,
                    contentDescription = null,
                    tint = if (connected) StatusGreen else StatusNeutral,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Serial Monitor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            connecting -> "Opening serial port..."
                            connected -> "${selectedDevice?.displayName ?: "Serial device"} @ $baudRate baud"
                            selectedDevice == null -> "Select a USB serial device"
                            !hasPermission(selectedDevice) -> "USB permission required"
                            else -> "Ready to open ${selectedDevice.displayName}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (connected) StatusGreen else NrOnSurfaceVariant,
                    )
                }
                if (connected && !expanded) {
                    StatusIndicator(label = "Open", color = StatusGreen)
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onDisconnect) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Disconnect")
                    }
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                    )
                }
            }

            if (!expanded) return@Column

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DropdownField(
                    modifier = Modifier.weight(1f),
                    label = "Device",
                    value = selectedDevice?.displayName ?: "Select a device",
                    enabled = !connected && devices.isNotEmpty(),
                    expanded = deviceMenuExpanded,
                    onToggle = { deviceMenuExpanded = true },
                    onDismiss = { deviceMenuExpanded = false },
                ) {
                    devices.forEach { device ->
                        DropdownMenuItem(
                            text = { Text(device.displayName) },
                            onClick = {
                                onSelectDevice(device)
                                deviceMenuExpanded = false
                            },
                        )
                    }
                }
                IconButton(onClick = onRefreshDevices) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh USB serial devices",
                        tint = NrOnSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Button(
                    onClick = {
                        when {
                            connected -> onDisconnect()
                            connecting -> Unit
                            selectedDevice == null -> Unit
                            !hasPermission(selectedDevice) -> onRequestPermission(selectedDevice)
                            else -> onConnect(selectedDevice, baudRate)
                        }
                    },
                    enabled = connected || (!connecting && selectedDevice != null),
                ) {
                    Text(
                        text = when {
                            connected -> "Disconnect"
                            connecting -> "Connecting..."
                            selectedDevice == null -> "Connect"
                            !hasPermission(selectedDevice) -> "Allow USB"
                            else -> "Connect"
                        },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DropdownField(
                    modifier = Modifier.weight(1f),
                    label = "Baud rate",
                    value = baudRate.toString(),
                    enabled = !connected,
                    expanded = baudMenuExpanded,
                    onToggle = { baudMenuExpanded = true },
                    onDismiss = { baudMenuExpanded = false },
                ) {
                    BAUD_RATES.forEach { rate ->
                        DropdownMenuItem(
                            text = { Text(rate.toString()) },
                            onClick = {
                                onBaudChange(rate)
                                baudMenuExpanded = false
                            },
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = !hexMode,
                        onClick = { onHexModeChange(false) },
                    )
                    Text("Text", style = MaterialTheme.typography.bodySmall)
                    RadioButton(
                        selected = hexMode,
                        onClick = { onHexModeChange(true) },
                    )
                    Text("Hex", style = MaterialTheme.typography.bodySmall)
                }
            }

            // TODO: Add DTR/RTS outline NrFilterChip toggles here once the
            // ViewModel exposes modem-control state and actions.
        }
    }
}

@Composable
private fun SerialMonitorLogCard(
    modifier: Modifier = Modifier,
    logLines: List<SerialLogLine>,
    hexMode: Boolean,
    logTextForCopy: String,
    logListState: androidx.compose.foundation.lazy.LazyListState,
    inputText: String,
    lineEnding: String,
    connected: Boolean,
    onInputChange: (String) -> Unit,
    onLineEndingChange: (String) -> Unit,
    onSend: () -> Unit,
    onHexModeChange: (Boolean) -> Unit,
    onCopy: () -> Unit,
    onClearLog: () -> Unit,
    onExport: () -> Unit,
) {
    var lineEndingExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Serial log",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onCopy, enabled = logLines.isNotEmpty()) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy serial log")
                }
                IconButton(onClick = onClearLog, enabled = logLines.isNotEmpty()) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear serial log")
                }
                IconButton(onClick = onExport, enabled = logLines.isNotEmpty()) {
                    Icon(Icons.Default.Download, contentDescription = "Export serial log")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
                    .padding(8.dp),
            ) {
                if (logLines.isEmpty()) {
                    Text(
                        text = "No serial data yet. Connect a device and open the port.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = logListState,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(logLines) { line ->
                            SelectionContainer {
                                Column {
                                    Text(
                                        text = "[${line.timestamp}]",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = NrOnSurfaceVariant,
                                    )
                                    Text(
                                        text = if (hexMode) line.text.toByteArray().joinToString(" ") {
                                            "%02X".format(it.toInt() and 0xFF)
                                        } else {
                                            line.text
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = logLevelColor(line.level),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    enabled = connected,
                    placeholder = { Text("Send text") },
                    singleLine = true,
                )
                DropdownField(
                    modifier = Modifier.width(110.dp),
                    label = "Line ending",
                    value = lineEnding,
                    enabled = connected,
                    expanded = lineEndingExpanded,
                    onToggle = { lineEndingExpanded = true },
                    onDismiss = { lineEndingExpanded = false },
                ) {
                    LINE_ENDINGS.forEach { ending ->
                        DropdownMenuItem(
                            text = { Text(ending) },
                            onClick = {
                                onLineEndingChange(ending)
                                lineEndingExpanded = false
                            },
                        )
                    }
                }
                IconButton(
                    onClick = onSend,
                    enabled = connected,
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send serial text")
                }
            }
        }
    }
}

@Composable
private fun DropdownField(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    enabled: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    menuContent: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onToggle() },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(0.5.dp, NrOutline),
            shape = RoundedCornerShape(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = NrOnSurfaceVariant,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Open $label list",
                    tint = if (enabled) MaterialTheme.colorScheme.onSurface else NrOnSurfaceVariant,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            content = menuContent,
        )
    }
}

private fun logLevelColor(level: LogLevel): Color = when (level) {
    LogLevel.ERROR -> LogColorError
    LogLevel.SUCCESS -> LogColorSuccess
    LogLevel.USB -> LogColorUsb
    LogLevel.INFO -> LogColorInfo
}
