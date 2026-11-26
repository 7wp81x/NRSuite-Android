package com.swp81x.nrsuite.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swp81x.nrsuite.core.serial.SerialLineDirection
import com.swp81x.nrsuite.core.serial.SerialLogLine
import com.swp81x.nrsuite.core.serial.formatSerialLogLine
import com.swp81x.nrsuite.core.serial.formatSerialLogText
import com.swp81x.nrsuite.core.usb.UsbSerialDevice
import com.swp81x.nrsuite.ui.components.NrAnimatedVisibility
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOnSurface
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed
import kotlin.math.roundToInt

private const val MAX_SERIAL_CLIPBOARD_CHARS = 200_000
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
    partialLine: SerialLogLine?,
    hexMode: Boolean,
    showTimestamps: Boolean,
    wrapLines: Boolean,
    followOutput: Boolean,
    textSizeSp: Float,
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
    onShowTimestampsChange: (Boolean) -> Unit,
    onWrapLinesChange: (Boolean) -> Unit,
    onFollowOutputChange: (Boolean) -> Unit,
    onTextSizeChange: (Float) -> Unit,
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
    LaunchedEffect(connected, connecting) {
        configExpanded = !connected && !connecting
    }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var inputFocused by remember { mutableStateOf(false) }
    val logVerticalScroll = rememberScrollState()
    val logHorizontalScroll = rememberScrollState()
    val dismissFocus = { focusManager.clearFocus() }

    BackHandler(enabled = inputFocused) {
        inputFocused = false
        focusManager.clearFocus()
    }
    val logText = remember(logLines, partialLine, showTimestamps, hexMode) {
        formatSerialLogText(
            lines = logLines,
            partialLine = partialLine,
            showTimestamps = showTimestamps,
            hexMode = hexMode,
        )
    }
    val timestampColor = NrOnSurfaceVariant.copy(alpha = 0.55f)
    val transmitColor = NrAccent
    val annotatedLog = remember(
        logLines,
        partialLine,
        showTimestamps,
        hexMode,
        timestampColor,
        transmitColor,
    ) {
        buildAnnotatedSerialLog(
            lines = logLines,
            partialLine = partialLine,
            showTimestamps = showTimestamps,
            hexMode = hexMode,
            timestampColor = timestampColor,
            transmitColor = transmitColor,
        )
    }
    val hasLogContent = logLines.isNotEmpty() || partialLine?.text?.isNotEmpty() == true

    LaunchedEffect(
        logLines.size,
        partialLine?.text,
        followOutput,
        showTimestamps,
        hexMode,
        wrapLines,
    ) {
        if (followOutput && hasLogContent) {
            withFrameNanos { }
            logVerticalScroll.scrollTo(logVerticalScroll.maxValue)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SerialMonitorConnectionCard(
            devices = devices,
            selectedDevice = selectedDevice,
            connected = connected,
            connecting = connecting,
            baudRate = baudRate,
            expanded = configExpanded,
            hasPermission = hasPermission,
            onToggle = { configExpanded = !configExpanded },
            onRefreshDevices = onRefreshDevices,
            onSelectDevice = onSelectDevice,
            onRequestPermission = onRequestPermission,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
            onBaudChange = onBaudChange,
            onInteraction = dismissFocus,
        )

        blockingMessage?.let {
            SerialMonitorMessage(text = it, color = StatusAmber)
        }
        errorMessage?.let {
            SerialMonitorMessage(text = it, color = StatusRed)
        }

        SerialMonitorLogCard(
            modifier = Modifier.weight(1f),
            logLines = logLines,
            annotatedLog = annotatedLog,
            hasLogContent = hasLogContent,
            logVerticalScroll = logVerticalScroll,
            logHorizontalScroll = logHorizontalScroll,
            inputText = inputText,
            lineEnding = lineEnding,
            connected = connected,
            hexMode = hexMode,
            showTimestamps = showTimestamps,
            wrapLines = wrapLines,
            followOutput = followOutput,
            textSizeSp = textSizeSp,
            onTextSizeChange = onTextSizeChange,
            onInputChange = onInputChange,
            onLineEndingChange = onLineEndingChange,
            onSend = onSend,
            onHexModeChange = onHexModeChange,
            onShowTimestampsChange = onShowTimestampsChange,
            onWrapLinesChange = onWrapLinesChange,
            onFollowOutputChange = onFollowOutputChange,
            onDismissFocus = dismissFocus,
            onInputFocusChanged = { inputFocused = it },
            onCopy = {
                dismissFocus()
                val payload = if (logText.length > MAX_SERIAL_CLIPBOARD_CHARS) {
                    logText.takeLast(MAX_SERIAL_CLIPBOARD_CHARS)
                } else {
                    logText
                }
                val copied = runCatching {
                    val clipboardManager =
                        context.getSystemService(ClipboardManager::class.java)
                    clipboardManager.setPrimaryClip(
                        ClipData.newPlainText("NRSuite serial log", payload)
                    )
                }.isSuccess
                Toast.makeText(
                    context,
                    if (copied) {
                        "Serial log copied" +
                            if (payload.length < logText.length) {
                                " (latest ${payload.length} characters)"
                            } else ""
                    } else {
                        "Could not copy serial log"
                    },
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onClearLog = {
                dismissFocus()
                onClearLog()
            },
            onExport = {
                dismissFocus()
                onExport()
            },
        )
    }
}

@Composable
private fun SerialMonitorMessage(
    text: String,
    color: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            style = MaterialTheme.typography.bodySmall,
            color = color,
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
    expanded: Boolean,
    hasPermission: (UsbSerialDevice) -> Boolean,
    onToggle: () -> Unit,
    onRefreshDevices: () -> Unit,
    onSelectDevice: (UsbSerialDevice) -> Unit,
    onRequestPermission: (UsbSerialDevice) -> Unit,
    onConnect: (UsbSerialDevice, Int) -> Unit,
    onDisconnect: () -> Unit,
    onBaudChange: (Int) -> Unit,
    onInteraction: () -> Unit,
) {
    var deviceMenuExpanded by remember { mutableStateOf(false) }
    var baudMenuExpanded by remember { mutableStateOf(false) }
    val currentSelected = selectedDevice?.takeIf { selected ->
        devices.any { it.device.deviceId == selected.device.deviceId }
    }
    val statusLabel: String
    val statusColor: Color
    when {
        connected -> {
            statusLabel = "Open"
            statusColor = StatusGreen
        }
        connecting -> {
            statusLabel = "Opening"
            statusColor = StatusAmber
        }
        currentSelected == null -> {
            statusLabel = "No device"
            statusColor = StatusNeutral
        }
        !hasPermission(currentSelected) -> {
            statusLabel = "Permission needed"
            statusColor = StatusAmber
        }
        else -> {
            statusLabel = "Ready"
            statusColor = StatusNeutral
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, if (connected) NrAccent else NrOutline),
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
                    tint = if (connected) NrAccent else NrOnSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Serial Monitor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        StatusIndicator(label = statusLabel, color = statusColor)
                    }
                    Text(
                        text = when {
                            connecting -> "Opening ${currentSelected?.displayName ?: "serial device"}..."
                            connected -> "${currentSelected?.displayName ?: "Serial device"} @ $baudRate baud"
                            currentSelected == null -> "Select a USB serial device to begin"
                            !hasPermission(currentSelected) -> "USB access is required for ${currentSelected.displayName}"
                            else -> "${currentSelected.displayName} @ $baudRate baud"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (connected) {
                    OutlinedButton(
                        onClick = {
                            onInteraction()
                            onDisconnect()
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp,
                            vertical = 6.dp,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("Close")
                    }
                }
                if (!connected) {
                    IconButton(onClick = { onInteraction(); onToggle() }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) {
                                "Collapse connection settings"
                            } else {
                                "Expand connection settings"
                            },
                        )
                    }
                }
            }

            NrAnimatedVisibility(visible = expanded && !connected) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = NrOutline)
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DropdownField(
                            modifier = Modifier.weight(1f),
                            label = "USB serial device",
                            value = currentSelected?.displayName ?: "No device selected",
                            enabled = devices.isNotEmpty(),
                            expanded = deviceMenuExpanded,
                            onToggle = { onInteraction(); deviceMenuExpanded = true },
                            onDismiss = { deviceMenuExpanded = false },
                        ) {
                            devices.forEach { device ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = device.displayName,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    onClick = {
                                        onInteraction()
                                        onSelectDevice(device)
                                        deviceMenuExpanded = false
                                    },
                                )
                            }
                        }
                        IconButton(onClick = { onInteraction(); onRefreshDevices() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh USB serial devices",
                                tint = NrOnSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DropdownField(
                            modifier = Modifier.weight(1f),
                            label = "Baud rate",
                            value = baudRate.toString(),
                            enabled = true,
                            expanded = baudMenuExpanded,
                            onToggle = { onInteraction(); baudMenuExpanded = true },
                            onDismiss = { baudMenuExpanded = false },
                        ) {
                            BAUD_RATES.forEach { rate ->
                                DropdownMenuItem(
                                    text = { Text(rate.toString()) },
                                    onClick = {
                                        onInteraction()
                                        onBaudChange(rate)
                                        baudMenuExpanded = false
                                    },
                                )
                            }
                        }

                        Button(
                            onClick = {
                                onInteraction()
                                when {
                                    connecting -> Unit
                                    currentSelected == null -> Unit
                                    !hasPermission(currentSelected) -> onRequestPermission(currentSelected)
                                    else -> onConnect(currentSelected, baudRate)
                                }
                            },
                            enabled = !connecting && currentSelected != null,
                        ) {
                            Text(
                                text = when {
                                    connecting -> "Opening..."
                                    currentSelected != null && !hasPermission(currentSelected) -> "Grant access"
                                    else -> "Connect"
                                },
                            )
                        }
                    }

                    if (devices.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Connect a USB-UART or ESP32 device, then refresh.",
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
private fun SerialMonitorLogCard(
    modifier: Modifier = Modifier,
    logLines: List<SerialLogLine>,
    annotatedLog: AnnotatedString,
    hasLogContent: Boolean,
    logVerticalScroll: ScrollState,
    logHorizontalScroll: ScrollState,
    inputText: String,
    lineEnding: String,
    connected: Boolean,
    hexMode: Boolean,
    showTimestamps: Boolean,
    wrapLines: Boolean,
    followOutput: Boolean,
    textSizeSp: Float,
    onTextSizeChange: (Float) -> Unit,
    onInputChange: (String) -> Unit,
    onLineEndingChange: (String) -> Unit,
    onSend: () -> Unit,
    onHexModeChange: (Boolean) -> Unit,
    onShowTimestampsChange: (Boolean) -> Unit,
    onWrapLinesChange: (Boolean) -> Unit,
    onFollowOutputChange: (Boolean) -> Unit,
    onDismissFocus: () -> Unit,
    onInputFocusChanged: (Boolean) -> Unit,
    onCopy: () -> Unit,
    onClearLog: () -> Unit,
    onExport: () -> Unit,
) {
    var lineEndingExpanded by remember { mutableStateOf(false) }
    val latestTextSize = rememberUpdatedState(textSizeSp)
    val latestTextSizeChange = rememberUpdatedState(onTextSizeChange)
    val terminalBackground = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    } else {
        Color(0xFF070A0D)
    }
    val terminalTextColor = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
        NrOnSurface
    } else {
        Color(0xFFE6EDF3)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Serial output",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            logLines.isEmpty() -> "Waiting for data"
                            else -> "${logLines.size} line${if (logLines.size == 1) "" else "s"}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { onDismissFocus(); onCopy() },
                    enabled = hasLogContent,
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy serial log")
                }
                IconButton(
                    onClick = { onDismissFocus(); onClearLog() },
                    enabled = hasLogContent,
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear serial log")
                }
                IconButton(
                    onClick = { onDismissFocus(); onExport() },
                    enabled = hasLogContent,
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Export serial log")
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NrFilterChip(
                        selected = !hexMode,
                        onClick = { onDismissFocus(); onHexModeChange(false) },
                        label = "Text",
                    )
                    NrFilterChip(
                        selected = hexMode,
                        onClick = { onDismissFocus(); onHexModeChange(true) },
                        label = "Hex",
                    )
                    NrFilterChip(
                        selected = showTimestamps,
                        onClick = { onDismissFocus(); onShowTimestampsChange(!showTimestamps) },
                        label = "Time",
                    )
                    NrFilterChip(
                        selected = wrapLines,
                        onClick = { onDismissFocus(); onWrapLinesChange(!wrapLines) },
                        label = "Wrap",
                    )
                    NrFilterChip(
                        selected = followOutput,
                        onClick = { onDismissFocus(); onFollowOutputChange(!followOutput) },
                        label = "Follow",
                    )
                }
                TextButton(
                    onClick = {
                        onDismissFocus()
                        onTextSizeChange((textSizeSp - 1f).coerceAtLeast(10f))
                    },
                    contentPadding = PaddingValues(horizontal = 2.dp),
                ) {
                    Text("A−")
                }
                Text(
                    text = "${textSizeSp.roundToInt()}sp",
                    style = MaterialTheme.typography.labelSmall,
                    color = NrOnSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        onDismissFocus()
                        onTextSizeChange((textSizeSp + 1f).coerceAtMost(24f))
                    },
                    contentPadding = PaddingValues(horizontal = 2.dp),
                ) {
                    Text("A+")
                }
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(terminalBackground, RoundedCornerShape(8.dp)),
            ) {
                if (!hasLogContent) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Usb,
                            contentDescription = null,
                            tint = NrOnSurfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.size(30.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (connected) {
                                "Connected. Waiting for serial data..."
                            } else {
                                "Connect a USB serial device to view raw output."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { onDismissFocus() })
                            }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    var zooming = false
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val pressed = event.changes.count { it.pressed }
                                        if (pressed >= 2) {
                                            val zoomChange = event.calculateZoom()
                                            if (zoomChange != 1f) {
                                                val nextSize = (latestTextSize.value * zoomChange)
                                                    .coerceIn(10f, 24f)
                                                latestTextSizeChange.value(nextSize)
                                                event.changes.forEach { it.consume() }
                                                zooming = true
                                            }
                                        } else if (zooming) {
                                            break
                                        }
                                        if (pressed == 0) break
                                    }
                                }
                            }
                            .then(
                                if (wrapLines) {
                                    Modifier.verticalScroll(logVerticalScroll)
                                } else {
                                    Modifier
                                        .horizontalScroll(logHorizontalScroll)
                                        .verticalScroll(logVerticalScroll)
                                }
                            )
                            .padding(10.dp),
                    ) {
                        SelectionContainer {
                            Text(
                                text = annotatedLog,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = textSizeSp.sp,
                                    lineHeight = (textSizeSp * 1.5f).sp,
                                ),
                                color = terminalTextColor,
                                softWrap = wrapLines,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CompactSerialInput(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    enabled = connected,
                    onFocusChanged = onInputFocusChanged,
                    onSend = onSend,
                )
                CompactLineEndingSelector(
                    value = lineEnding,
                    enabled = connected,
                    expanded = lineEndingExpanded,
                    onToggle = {
                        onDismissFocus()
                        lineEndingExpanded = true
                    },
                    onDismiss = { lineEndingExpanded = false },
                    onSelect = {
                        onLineEndingChange(it)
                        lineEndingExpanded = false
                    },
                )
                IconButton(
                    onClick = {
                        onDismissFocus()
                        onSend()
                    },
                    enabled = connected,
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send serial data")
                }
            }
        }
    }
}

@Composable
private fun CompactSerialInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onSend: () -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .height(42.dp)
            .background(NrSurfaceVariant, RoundedCornerShape(10.dp))
            .border(0.5.dp, NrOutline, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp)
            .onFocusChanged { onFocusChanged(it.isFocused) },
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = if (enabled) MaterialTheme.colorScheme.onSurface else NrOnSurfaceVariant,
        ),
        cursorBrush = SolidColor(NrAccent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(
            onSend = {
                if (enabled) onSend()
            },
        ),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "Send data",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NrOnSurfaceVariant.copy(alpha = 0.65f),
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun CompactLineEndingSelector(
    value: String,
    enabled: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    Box {
        Row(
            modifier = Modifier
                .height(42.dp)
                .background(NrSurfaceVariant, RoundedCornerShape(10.dp))
                .border(0.5.dp, NrOutline, RoundedCornerShape(10.dp))
                .clickable(enabled = enabled, onClick = onToggle)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) NrOnSurface else NrOnSurfaceVariant,
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Select line ending",
                tint = if (enabled) NrOnSurfaceVariant else NrOnSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
        ) {
            LINE_ENDINGS.forEach { ending ->
                DropdownMenuItem(
                    text = { Text(ending) },
                    onClick = { onSelect(ending) },
                )
            }
        }
    }
}

private fun buildAnnotatedSerialLog(
    lines: List<SerialLogLine>,
    partialLine: SerialLogLine?,
    showTimestamps: Boolean,
    hexMode: Boolean,
    timestampColor: Color,
    transmitColor: Color,
): AnnotatedString = buildAnnotatedString {
    val renderedLines = lines + listOfNotNull(
        partialLine?.takeIf { it.text.isNotEmpty() },
    )
    renderedLines.forEachIndexed { index, line ->
        if (showTimestamps) {
            withStyle(SpanStyle(color = timestampColor)) {
                append('[')
                append(line.timestamp)
                append("] ")
            }
        }
        val body = formatSerialLogLine(
            line = line,
            showTimestamps = false,
            hexMode = hexMode,
        )
        val bodyColor = if (line.direction == SerialLineDirection.TX) {
            transmitColor
        } else {
            Color.Unspecified
        }
        withStyle(
            SpanStyle(
                color = bodyColor,
                fontWeight = if (line.direction == SerialLineDirection.TX) {
                    FontWeight.Medium
                } else {
                    FontWeight.Normal
                },
            ),
        ) {
            append(body)
        }
        if (index < renderedLines.lastIndex) {
            append('\n')
        }
    }
}

@Composable
private fun DropdownField(
    modifier: Modifier,
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
            colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
            border = BorderStroke(0.5.dp, NrOutline),
            shape = RoundedCornerShape(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    if (label.isNotBlank()) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
