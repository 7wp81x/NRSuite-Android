package com.swp81x.nrsuite.ui

import android.hardware.usb.UsbManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.swp81x.nrsuite.MainViewModel
import com.swp81x.nrsuite.core.usb.UsbSerialDevice

/**
 * Collects Serial Monitor state inside its own composition scope.
 *
 * The root NRSuiteContent composable is already at the DEX outgoing-argument
 * limit. Keeping feature-local StateFlow subscriptions here avoids adding more
 * captured values to the root composition lambda.
 */
@Composable
internal fun SerialMonitorRoute(
    viewModel: MainViewModel,
    devices: List<UsbSerialDevice>,
    permissionRevision: Int,
    usbManager: UsbManager,
    onRequestPermission: (UsbSerialDevice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedDevice by viewModel.serialMonitorSelected.collectAsState()
    val connected by viewModel.serialMonitorConnected.collectAsState()
    val connecting by viewModel.serialMonitorConnecting.collectAsState()
    val baudRate by viewModel.serialMonitorBaud.collectAsState()
    val logLines by viewModel.serialMonitorLogLines.collectAsState()
    val partialLine by viewModel.serialMonitorPartialLine.collectAsState()
    val hexMode by viewModel.serialMonitorHexMode.collectAsState()
    val showTimestamps by viewModel.serialMonitorShowTimestamps.collectAsState()
    val wrapLines by viewModel.serialMonitorWrapLines.collectAsState()
    val followOutput by viewModel.serialMonitorFollowOutput.collectAsState()
    val textSizeSp by viewModel.serialMonitorTextSizeSp.collectAsState()
    val inputText by viewModel.serialMonitorInput.collectAsState()
    val lineEnding by viewModel.serialMonitorLineEnding.collectAsState()
    val blockingMessage by viewModel.serialMonitorBlockingMessage.collectAsState()
    val errorMessage by viewModel.serialMonitorError.collectAsState()

    SerialMonitorScreen(
        devices = devices,
        selectedDevice = selectedDevice,
        connected = connected,
        connecting = connecting,
        baudRate = baudRate,
        logLines = logLines,
        partialLine = partialLine,
        hexMode = hexMode,
        showTimestamps = showTimestamps,
        wrapLines = wrapLines,
        followOutput = followOutput,
        textSizeSp = textSizeSp,
        inputText = inputText,
        lineEnding = lineEnding,
        blockingMessage = blockingMessage,
        errorMessage = errorMessage,
        permissionRevision = permissionRevision,
        hasPermission = { usbManager.hasPermission(it.device) },
        onRefreshDevices = viewModel::refreshDevices,
        onSelectDevice = viewModel::selectSerialMonitorDevice,
        onRequestPermission = onRequestPermission,
        onConnect = { device, baud -> viewModel.startSerialMonitor(device, baud) },
        onDisconnect = viewModel::stopSerialMonitor,
        onBaudChange = viewModel::setSerialMonitorBaud,
        onHexModeChange = viewModel::setSerialMonitorHexMode,
        onShowTimestampsChange = viewModel::setSerialMonitorShowTimestamps,
        onWrapLinesChange = viewModel::setSerialMonitorWrapLines,
        onFollowOutputChange = viewModel::setSerialMonitorFollowOutput,
        onTextSizeChange = viewModel::setSerialMonitorTextSizeSp,
        onInputChange = viewModel::setSerialMonitorInput,
        onLineEndingChange = viewModel::setSerialMonitorLineEnding,
        onSend = viewModel::sendSerialMonitorInput,
        onClearLog = viewModel::clearSerialMonitorLog,
        onExport = viewModel::exportSerialMonitorLog,
        modifier = modifier,
    )
}
