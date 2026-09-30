package com.swp81x.nrsuite.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.usb.UsbSerialDevice
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
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
    logText: String,
    inputText: String,
    lineEnding: String,
    errorMessage: String?,
    permissionRevision: Int,
    hasPermission: (UsbSerialDevice) -> Boolean,
    onSelectDevice: (UsbSerialDevice) -> Unit,
    onRequestPermission: (UsbSerialDevice) -> Unit,
    onConnect: (UsbSerialDevice, Int) -> Unit,
    onDisconnect: () -> Unit,
    onBaudChange: (Int) -> Unit,
    onInputChange: (String) -> Unit,
    onLineEndingChange: (String) -> Unit,
    onSend: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_EXPRESSION")
    permissionRevision
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val logScrollState = rememberScrollState()

    LaunchedEffect(logText) {
        if (logText.isNotEmpty()) {
            logScrollState.animateScrollTo(logScrollState.maxValue)
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
            hasPermission = hasPermission,
            onSelectDevice = onSelectDevice,
            onRequestPermission = onRequestPermission,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
            onBaudChange = onBaudChange,
        )

        errorMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = StatusRed,
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = NrSurface),
            border = BorderStroke(0.5.dp, NrOutline),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
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
                    IconButton(
                        onClick = {
                            clipboard.copyWithToast(context, logText, "Serial log copied")
                        },
                        enabled = logText.isNotBlank(),
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy serial log")
                    }
                    IconButton(
                        onClick = onExport,
                        enabled = logText.isNotBlank(),
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Export serial log")
                    }
                }

                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(logScrollState),
                ) {
                    if (logText.isBlank()) {
                        Text(
                            text = "No serial data yet. Connect a device and open the port.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    } else {
                        SelectionContainer {
                            Text(
                                text = logText,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        SerialMonitorInputCard(
            connected = connected,
            inputText = inputText,
            lineEnding = lineEnding,
            onInputChange = onInputChange,
            onLineEndingChange = onLineEndingChange,
            onSend = onSend,
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
    hasPermission: (UsbSerialDevice) -> Boolean,
    onSelectDevice: (UsbSerialDevice) -> Unit,
    onRequestPermission: (UsbSerialDevice) -> Unit,
    onConnect: (UsbSerialDevice, Int) -> Unit,
    onDisconnect: () -> Unit,
    onBaudChange: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, if (connected) StatusGreen else NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Usb,
                    contentDescription = null,
                    tint = if (connected) StatusGreen else StatusNeutral,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (connected) "Serial Monitor connected" else "Serial Monitor",
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
                StatusIndicator(
                    label = when {
                        connected -> "Open"
                        connecting -> "Opening"
                        else -> "Closed"
                    },
                    color = when {
                        connected -> StatusGreen
                        connecting -> StatusAmber
                        else -> StatusNeutral
                    },
                )
            }

            Spacer(Modifier.height(10.dp))
            Text("Baud rate", style = MaterialTheme.typography.labelMedium, color = NrOnSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BAUD_RATES.forEach { rate ->
                    NrFilterChip(
                        selected = baudRate == rate,
                        onClick = { onBaudChange(rate) },
                        label = rate.toString(),
                        selectedColor = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("USB serial devices", style = MaterialTheme.typography.labelMedium, color = NrOnSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            if (devices.isEmpty()) {
                Text(
                    text = "No supported USB serial device found. Plug in an ESP32 or USB-UART adapter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusAmber,
                )
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    devices.forEach { device ->
                        NrFilterChip(
                            selected = selectedDevice?.device?.deviceId == device.device.deviceId,
                            onClick = { onSelectDevice(device) },
                            label = device.displayName,
                            selectedColor = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (connected) {
                    Button(onClick = onDisconnect) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Disconnect")
                    }
                } else if (connecting) {
                    OutlinedButton(onClick = {}, enabled = false) {
                        Text("Connecting...")
                    }
                } else if (selectedDevice == null) {
                    OutlinedButton(onClick = {}, enabled = false) {
                        Text("Select device")
                    }
                } else if (!hasPermission(selectedDevice)) {
                    Button(onClick = { onRequestPermission(selectedDevice) }) {
                        Text("Allow USB")
                    }
                } else {
                    Button(onClick = { onConnect(selectedDevice, baudRate) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Connect")
                    }
                }
            }
        }
    }
}

@Composable
private fun SerialMonitorInputCard(
    connected: Boolean,
    inputText: String,
    lineEnding: String,
    onInputChange: (String) -> Unit,
    onLineEndingChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Send data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = connected,
                label = { Text("Text to send") },
                singleLine = false,
                maxLines = 3,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Line ending:",
                    style = MaterialTheme.typography.labelMedium,
                    color = NrOnSurfaceVariant,
                )
                LINE_ENDINGS.forEach { ending ->
                    NrFilterChip(
                        selected = lineEnding == ending,
                        onClick = { onLineEndingChange(ending) },
                        label = ending,
                        selectedColor = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onSend,
                enabled = connected,
            ) {
                Text("Send")
            }
        }
    }
}
