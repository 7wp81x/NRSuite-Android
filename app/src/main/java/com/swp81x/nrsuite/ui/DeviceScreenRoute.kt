package com.swp81x.nrsuite.ui

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.swp81x.nrsuite.MainViewModel
import com.swp81x.nrsuite.deviceFingerprintImpl

/**
 * Keeps the large app shell from owning the Devices screen's many state
 * collectors and callback closures. Behaviour and arguments passed to
 * [DeviceScreen] remain unchanged.
 */
@Composable
internal fun DeviceScreenRoute(
    viewModel: MainViewModel,
    usbManager: UsbManager,
    onConnectRequest: (UsbDevice) -> Unit,
    onOpenFirmwareFlasher: (UsbDevice?, String?) -> Unit,
    onOpenFirmwareFlasherForUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val primaryFingerprint by viewModel.primaryFingerprint.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val deviceConnectionStates by viewModel.deviceConnectionStates.collectAsState()
    val pendingPermissionRequests by viewModel.pendingPermissionRequests.collectAsState()
    val disconnectingFingerprints by viewModel.disconnectingFingerprints.collectAsState()
    val firmwareUpdate by viewModel.firmwareUpdate.collectAsState()
    val firmwareReleases by viewModel.firmwareReleases.collectAsState()
    val firmwareAssetDownload by viewModel.firmwareAssetDownload.collectAsState()
    val firmwareFlashDeviceState by viewModel.firmwareFlashDeviceState.collectAsState()
    val deviceRestarting by viewModel.deviceRestarting.collectAsState()

    DeviceScreen(
        connectionState = connectionState,
        activeFingerprint = primaryFingerprint,
        devices = devices,
        deviceConnectionStates = deviceConnectionStates,
        deviceFingerprint = { viewModel.deviceFingerprintImpl(it) },
        pendingPermissionRequests = pendingPermissionRequests,
        disconnectingFingerprints = disconnectingFingerprints,
        usbManager = usbManager,
        firmwareFlashDeviceState = firmwareFlashDeviceState,
        firmwareUpdate = firmwareUpdate,
        firmwareReleases = firmwareReleases,
        firmwareAssetDownload = firmwareAssetDownload,
        onDownloadFirmwareUpdate = viewModel::downloadFirmwareUpdate,
        onOpenFirmwareFlasherForUpdate = onOpenFirmwareFlasherForUpdate,
        onRefresh = viewModel::refreshDevices,
        onConnect = onConnectRequest,
        onDisconnect = viewModel::disconnect,
        onDisconnectDevice = viewModel::disconnectDevice,
        onRefreshStatus = viewModel::refreshDeviceStatus,
        onSwitchDevice = { device ->
            if (viewModel.switchActiveDevice(device)) {
                Toast.makeText(
                    context,
                    "Active device: ${device.productName ?: "USB device"}",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        },
        onOpenFirmwareFlasher = onOpenFirmwareFlasher,
        onStopModulesForMemory = viewModel::stopModulesForMemory,
        onRestartEsp32 = viewModel::restartEsp32,
        restartInProgress = deviceRestarting,
        modifier = modifier,
    )
}
