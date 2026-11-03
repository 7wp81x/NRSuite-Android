package com.swp81x.nrsuite.ui

import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.swp81x.nrsuite.core.ble.BleScanOwner
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.history.HistoryEntry
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
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
import com.swp81x.nrsuite.deviceFingerprintImpl
import com.swp81x.nrsuite.NrSuiteApplication
import com.swp81x.nrsuite.core.oui.OuiDatabaseStatus
import com.swp81x.nrsuite.core.ir.IrRemoteSource
import com.swp81x.nrsuite.core.ir.IrTransmitBackend
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.update.AppUpdateDownloadState
import com.swp81x.nrsuite.core.update.AppUpdateInfo
import com.swp81x.nrsuite.core.update.FirmwareAssetDownloadState
import com.swp81x.nrsuite.core.update.FirmwareUpdateInfo
import com.swp81x.nrsuite.core.update.ReleaseInfo
import com.swp81x.nrsuite.core.defense.DeauthFeedFilter
import com.swp81x.nrsuite.core.usb.UsbSerialDevice
import com.swp81x.nrsuite.ui.components.ModuleCard
import com.swp81x.nrsuite.ui.components.ModuleCardSpec
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.components.UsbAttachPromptCard
import com.swp81x.nrsuite.ui.theme.CategoryAccentTheme
import com.swp81x.nrsuite.ui.theme.categoryColor
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrOnSurface
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrTheme
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.swp81x.nrsuite.core.log.LogEntry
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.ui.theme.LogBgError

private const val ACTION_USB_PERMISSION = "com.swp81x.nrsuite.USB_PERMISSION"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NRSuiteContent(viewModel: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiScope = rememberCoroutineScope()
    val homeListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val usbManager = remember {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    val devices by viewModel.devices.collectAsState()
    val usbAttachPrompts by viewModel.usbAttachPrompts.collectAsState()
    val firmwareOnboardingDismissed by viewModel.firmwareOnboardingDismissed.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val primaryFingerprint by viewModel.primaryFingerprint.collectAsState()
    val wifiDeviceRevision by viewModel.wifiDeviceRevision.collectAsState()
    val deviceConnectionStates by viewModel.deviceConnectionStates.collectAsState()
    val pendingPermissionRequests by viewModel.pendingPermissionRequests.collectAsState()
    val unacknowledgedDevices by viewModel.unacknowledgedDevices.collectAsState()
    val disconnectingFingerprints by viewModel.disconnectingFingerprints.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val history by viewModel.history.collectAsState()
    val firmwareFlashName by viewModel.firmwareFlashName.collectAsState()
    val firmwareFlashSize by viewModel.firmwareFlashSize.collectAsState()
    val firmwareFlashing by viewModel.firmwareFlashing.collectAsState()
    val firmwareFlashProgress by viewModel.firmwareFlashProgress.collectAsState()
    val firmwareFlashStatus by viewModel.firmwareFlashStatus.collectAsState()
    val firmwareTargetDevice by viewModel.firmwareTargetDevice.collectAsState()
    val recentModuleIds by viewModel.recentModuleIds.collectAsState()
    val scanning by viewModel.scanning.collectAsState()
    val networks by viewModel.networks.collectAsState()
    val wifiBand by viewModel.wifiBand.collectAsState()
    val sniffing by viewModel.sniffing.collectAsState()
    val sniffPacketCount by viewModel.sniffPacketCount.collectAsState()
    val sniffCaptureBytes by viewModel.sniffCaptureBytes.collectAsState()
    val sniffHandshake by viewModel.sniffHandshake.collectAsState()
    val capturePath by viewModel.capturePath.collectAsState()
    val exportDirectoryName by viewModel.exportDirectoryName.collectAsState()
    val wifiCaptureDirectoryName by viewModel.wifiCaptureDirectoryName.collectAsState()
    val ouiRules by viewModel.ouiRules.collectAsState()
    val ouiDatabaseStatus by viewModel.ouiDatabaseStatus.collectAsState()
    val ouiDatabaseProgress by viewModel.ouiDatabaseDownloadProgress.collectAsState()
    val macLookupResult by viewModel.macLookupResult.collectAsState()
    val bssidGeolocationState by viewModel.bssidGeolocationState.collectAsState()
    val bssidGeolocationHistory by viewModel.bssidGeolocationHistory.collectAsState()
    val bssidNearbyState by viewModel.bssidNearbyState.collectAsState()
    val irRemotes by viewModel.irRemotes.collectAsState()
    val irSelectedRemote by viewModel.irSelectedRemote.collectAsState()
    val irLoadingRemote by viewModel.irLoadingRemote.collectAsState()
    val irImporting by viewModel.irImporting.collectAsState()
    val irScanProgress by viewModel.irScanProgress.collectAsState()
    val irSearchState by viewModel.irSearchState.collectAsState()
    val irCommonButtonsState by viewModel.irCommonButtonsState.collectAsState()
    val irPendingCapture by viewModel.irPendingCapture.collectAsState()
    val irLearnedButtons by viewModel.irLearnedButtons.collectAsState()
    val irNextButtonNumber by viewModel.irNextButtonNumber.collectAsState()
    val irJammerState by viewModel.irJammerState.collectAsState()
    val irSavingReceivedRemote by viewModel.irSavingReceivedRemote.collectAsState()
    val irSavingEsp32Pins by viewModel.irSavingEsp32Pins.collectAsState()
    val irFinderCategory by viewModel.irFinderCategory.collectAsState()
    val irFinderBrand by viewModel.irFinderBrand.collectAsState()
    val suggestedReceivedRemoteName = remember(irRemotes) {
        val existing = irRemotes.asSequence()
            .filter { it.source == IrRemoteSource.RECEIVED }
            .map { it.fileName.substringBeforeLast('.').lowercase() }
            .toSet()
        var number = 1
        while ("captured_remote_%02d".format(number) in existing) number++
        "captured_remote_%02d".format(number)
    }
    val irDatabaseState by viewModel.irDatabaseState.collectAsState()
    val irDatabaseMetadata by viewModel.irDatabaseMetadata.collectAsState()
    val irDatabasePresent by viewModel.irDatabasePresent.collectAsState()
    val irTransmitting by viewModel.irTransmitting.collectAsState()
    val irCapabilities by viewModel.irCapabilities.collectAsState()
    val irTransmitBackend by viewModel.irTransmitBackend.collectAsState()
    val irLoopMode by viewModel.irLoopMode.collectAsState()
    val irActiveLoopSignalId by viewModel.irActiveLoopSignalId.collectAsState()
    val irLoopIntervalMs by viewModel.irLoopIntervalMs.collectAsState()
    val irStatusMessage by viewModel.irStatusMessage.collectAsState()
    val irErrorMessage by viewModel.irErrorMessage.collectAsState()
    val requiresOuiDatabase by viewModel.requiresOuiDatabase.collectAsState()
    val requiresRootDirectory by viewModel.requiresRootDirectory.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val beaconRunning by viewModel.beaconRunning.collectAsState()
    val beaconSent by viewModel.beaconSent.collectAsState()
    val beaconSsidCount by viewModel.beaconSsidCount.collectAsState()
    val beaconChannel by viewModel.beaconChannel.collectAsState()
    val beaconLists by viewModel.beaconListMap.collectAsState()
    val probeMonitorRunning by viewModel.probeMonitorRunning.collectAsState()
    val probeMonitorFixed by viewModel.probeMonitorFixed.collectAsState()
    val probeMonitorChannel by viewModel.probeMonitorChannel.collectAsState()
    val probeMonitorEntries by viewModel.probeMonitorEntries.collectAsState()
    val probeMonitorFrameCount by viewModel.probeMonitorFrameCount.collectAsState()
    val channelAnalyzerNetworks by viewModel.channelAnalyzerNetworks.collectAsState()
    val wpsLockDetectorState by viewModel.wpsLockDetector.collectAsState()
    val wpsAttemptDetectorState by viewModel.wpsAttemptDetector.collectAsState()
    val htmlTemplates by viewModel.htmlTemplates.collectAsState()
    val htmlTemplateMessage by viewModel.htmlTemplateMessage.collectAsState()
    val deauthRunning by viewModel.deauthRunning.collectAsState()
    val deauthSent by viewModel.deauthSent.collectAsState()
    val deauthFailed by viewModel.deauthFailed.collectAsState()
    val deauthElapsedMs by viewModel.deauthElapsedMs.collectAsState()
    val deauthPps by viewModel.deauthPps.collectAsState()
    val deauthTarget by viewModel.deauthTarget.collectAsState()
    val deauthChannel by viewModel.deauthChannel.collectAsState()
    val deauthDetectorRunning by viewModel.deauthDetectorRunning.collectAsState()
    val deauthDetectorChannel by viewModel.deauthDetectorChannel.collectAsState()
    val deauthDetectorChannelMode by viewModel.deauthDetectorChannelMode.collectAsState()
    val deauthDetectorHopIntervalMs by viewModel.deauthDetectorHopIntervalMs.collectAsState()
    val deauthDetectorCurrentHopChannel by viewModel.deauthDetectorCurrentHopChannel.collectAsState()
    val deauthDetectorActiveAlert by viewModel.deauthDetectorActiveAlert.collectAsState()
    val deauthDetectorFeed by viewModel.deauthDetectorFeed.collectAsState()
    val deauthDetectorFeedFilter by viewModel.deauthDetectorFeedFilter.collectAsState()
    val deauthDetectorFramesPerSecond by viewModel.deauthDetectorFramesPerSecond.collectAsState()
    val deauthDetectorTotalFrames by viewModel.deauthDetectorTotalFrames.collectAsState()
    val deauthDetectorUniqueSourceCount by viewModel.deauthDetectorUniqueSourceCount.collectAsState()
    val deauthDetectorTargets by viewModel.deauthDetectorTargets.collectAsState()
    val deauthDetectorSelectedTarget by viewModel.deauthDetectorSelectedTarget.collectAsState()
    val rogueApRunning by viewModel.rogueApRunning.collectAsState()
    val rogueApScanning by viewModel.rogueApScanning.collectAsState()
    val rogueApAlerts by viewModel.rogueApAlerts.collectAsState()
    val rogueApNearby by viewModel.rogueApNearby.collectAsState()
    val rogueApLastScanAt by viewModel.rogueApLastScanAt.collectAsState()
    val clientPresenceRunning by viewModel.clientPresenceRunning.collectAsState()
    val clientPresenceMode by viewModel.clientPresenceMode.collectAsState()
    val clientPresenceFixed by viewModel.clientPresenceFixed.collectAsState()
    val clientPresenceChannel by viewModel.clientPresenceChannel.collectAsState()
    val clientPresenceTargetBssid by viewModel.clientPresenceTargetBssid.collectAsState()
    val clientPresenceClients by viewModel.clientPresenceClients.collectAsState()
    val clientPresenceFrameCount by viewModel.clientPresenceFrameCount.collectAsState()
    val clientPresenceLastTriggerAt by viewModel.clientPresenceLastTriggerAt.collectAsState()
    val hiddenApRunning by viewModel.hiddenApRunning.collectAsState()
    val hiddenApStarting by viewModel.hiddenApStarting.collectAsState()
    val hiddenApFixed by viewModel.hiddenApFixed.collectAsState()
    val hiddenApChannel by viewModel.hiddenApChannel.collectAsState()
    val hiddenApCurrentHopChannel by viewModel.hiddenApCurrentHopChannel.collectAsState()
    val hiddenApObservations by viewModel.hiddenApObservations.collectAsState()
    val hiddenApCandidates by viewModel.hiddenApCandidates.collectAsState()
    val hiddenApDeauthEnabled by viewModel.hiddenApDeauthEnabled.collectAsState()
    val portalRunning by viewModel.portalRunning.collectAsState()
    val portalMode by viewModel.portalMode.collectAsState()
    val portalHtmlSize by viewModel.portalHtmlSize.collectAsState()
    val portalHtmlComplete by viewModel.portalHtmlComplete.collectAsState()
    val portalHtmlUploading by viewModel.portalHtmlUploading.collectAsState()
    val portalHtmlUploadProgress by viewModel.portalHtmlUploadProgress.collectAsState()
    val evilTwinHtmlUploading by viewModel.evilTwinHtmlUploading.collectAsState()
    val evilTwinHtmlUploadProgress by viewModel.evilTwinHtmlUploadProgress.collectAsState()
    val evilTwinHtmlComplete by viewModel.evilTwinHtmlComplete.collectAsState()
    val portalCredentials by viewModel.portalCredentials.collectAsState()
    val portalHtmlName by viewModel.portalHtmlName.collectAsState()
    val portalEventLog by viewModel.portalEventLog.collectAsState()
    val evilTwinEventLog by viewModel.evilTwinEventLog.collectAsState()
    val evilTwinHtmlName by viewModel.evilTwinHtmlName.collectAsState()
    val portalHandshake by viewModel.portalHandshake.collectAsState()
    val evilTwinPasswords by viewModel.evilTwinPasswords.collectAsState()
    val evilTwinResults by viewModel.evilTwinResults.collectAsState()
    val credentialSessions by viewModel.credentialSessions.collectAsState()
    val crackerRunning by viewModel.crackerRunning.collectAsState()
    val storageFiles by viewModel.storageFiles.collectAsState()
    val storageTotal by viewModel.storageTotal.collectAsState()
    val storageUsed by viewModel.storageUsed.collectAsState()
    val storageFree by viewModel.storageFree.collectAsState()
    val storageLoading by viewModel.storageLoading.collectAsState()
    val badUsbPayloadName by viewModel.badUsbPayloadName.collectAsState()
    val badUsbUploading by viewModel.badUsbUploading.collectAsState()
    val pinBruteforce by viewModel.pinBruteforce.collectAsState()
    val pinAttempts by viewModel.pinAttempts.collectAsState()
    val blePinBruteforce by viewModel.blePinBruteforce.collectAsState()
    val blePinAttempts by viewModel.blePinAttempts.collectAsState()
    val ld2450 by viewModel.ld2450.collectAsState()
    val rfid by viewModel.rfid.collectAsState()
    val autoUpdateCheck by viewModel.autoUpdateCheck.collectAsState()
    val appUpdate by viewModel.appUpdate.collectAsState()
    val appUpdateDownload by viewModel.appUpdateDownload.collectAsState()
    val firmwareUpdate by viewModel.firmwareUpdate.collectAsState()
    val firmwareReleases by viewModel.firmwareReleases.collectAsState()
    val firmwareAssetDownload by viewModel.firmwareAssetDownload.collectAsState()
    val firmwareManualRecovery by viewModel.firmwareManualRecovery.collectAsState()
    val updateCheckInProgress by viewModel.updateCheckInProgress.collectAsState()
    val updateError by viewModel.updateError.collectAsState()
    val badUsbProgress by viewModel.badUsbProgress.collectAsState()
    val badUsbArmedFingerprints by viewModel.badUsbArmedFingerprints.collectAsState()
    val badUsbTargetDevice by viewModel.badUsbTargetDevice.collectAsState()
    val duckyScripts by viewModel.duckyScriptMap.collectAsState()
    val bleAdvertising by viewModel.bleAdvertising.collectAsState()
    val bleConnected by viewModel.bleConnected.collectAsState()
    val blePeer by viewModel.blePeer.collectAsState()
    val blePayloadName by viewModel.blePayloadName.collectAsState()
    val bleModifiers by viewModel.bleModifiers.collectAsState()
    val bleModifierHold by viewModel.bleModifierHold.collectAsState()
    val bleScriptRunning by viewModel.bleScriptRunning.collectAsState()
    val bleScanStarting by viewModel.bleScanStarting.collectAsState()
    val bleScanRunning by viewModel.bleScanRunning.collectAsState()
    val bleScanOwner by viewModel.bleScanOwner.collectAsState()
    val bleScanStopping by viewModel.bleScanStopping.collectAsState()
    val bleScanActive by viewModel.bleScanActive.collectAsState()
    val bleScanDevices by viewModel.bleScanDevices.collectAsState()
    val trackerObservations by viewModel.trackerObservations.collectAsState()
    val bleProfileTarget by viewModel.bleProfileTarget.collectAsState()
    val bleProfileRunning by viewModel.bleProfileRunning.collectAsState()
    val bleProfileStatus by viewModel.bleProfileStatus.collectAsState()
    val bleProfileServices by viewModel.bleProfileServices.collectAsState()
    val meshInitialized by viewModel.meshInitialized.collectAsState()
    val meshRole by viewModel.meshRole.collectAsState()
    val meshSessionId by viewModel.meshSessionId.collectAsState()
    val meshNodeId by viewModel.meshNodeId.collectAsState()
    val meshPeerCount by viewModel.meshPeerCount.collectAsState()
    val meshActive by viewModel.meshActive.collectAsState()
    val meshProvisioning by viewModel.meshProvisioning.collectAsState()
    val meshActionInProgress by viewModel.meshActionInProgress.collectAsState()
    val meshPassphrase by viewModel.meshPassphrase.collectAsState()
    val meshKeyId by viewModel.meshKeyId.collectAsState()
    val meshCheckingPassphrase by viewModel.meshCheckingPassphrase.collectAsState()
    val meshPassphraseMatch by viewModel.meshPassphraseMatch.collectAsState()
    val meshSetupMessage by viewModel.meshSetupMessage.collectAsState()
    val meshHasStoredCredentials by viewModel.meshHasStoredCredentials.collectAsState()
    val meshStoredKeyId by viewModel.meshStoredKeyId.collectAsState()
    val meshHasGlobalCredentials by viewModel.meshHasGlobalCredentials.collectAsState()
    val meshGlobalKeyId by viewModel.meshGlobalKeyId.collectAsState()
    val meshChannel by viewModel.meshChannel.collectAsState()
    val meshNodes by viewModel.meshNodes.collectAsState()
    val meshLastError by viewModel.meshLastError.collectAsState()
    val serialMonitorSelected by viewModel.serialMonitorSelected.collectAsState()
    val serialMonitorConnected by viewModel.serialMonitorConnected.collectAsState()
    val serialMonitorConnecting by viewModel.serialMonitorConnecting.collectAsState()
    val serialMonitorBaud by viewModel.serialMonitorBaud.collectAsState()
    val serialMonitorLogLines by viewModel.serialMonitorLogLines.collectAsState()
    val serialMonitorHexMode by viewModel.serialMonitorHexMode.collectAsState()
    val serialMonitorInput by viewModel.serialMonitorInput.collectAsState()
    val serialMonitorLineEnding by viewModel.serialMonitorLineEnding.collectAsState()
    val serialMonitorBlockingMessage by viewModel.serialMonitorBlockingMessage.collectAsState()
    val serialMonitorError by viewModel.serialMonitorError.collectAsState()
    val autoHandshakeState by viewModel.autoHandshake.uiState.collectAsState()
    val autoHandshakeSessions by viewModel.autoHandshake.sessions.collectAsState()
    val autoHandshakeFeed by viewModel.autoHandshake.feed.collectAsState()
    val pwnModeState by viewModel.autoHandshake.pwnMode.collectAsState()
    val capturedHandshakes by viewModel.autoHandshake.captures.collectAsState()

    LaunchedEffect(toastMessage) {
        toastMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.consumeToastMessage()
        }
    }

    val connected = connectionState as? ConnectionState.Connected
    val connectedChip = connected?.chip
    val isDeviceConnected = connected != null
    val deviceFeatures = connected?.features.orEmpty()
    val badUsbCapableConnected = deviceConnectionStates.values.any { state ->
        state is ConnectionState.Connected &&
            state.chip in setOf("ESP32-S2", "ESP32-S3") &&
            (state.features.isEmpty() || "badusb" in state.features)
    }
    val showWifiBandSelector = connectedChip == "ESP32-C5"
    val wifi5Supported = showWifiBandSelector && "wifi_5ghz" in deviceFeatures
    val reported5gChannels = connected?.allowed5gChannels.orEmpty()
    val wifi5Available = wifi5Supported && reported5gChannels.isNotEmpty()
    val wifi5UnavailableMessage = when {
        connectedChip == null -> "Connect an ESP32-C5 to use 5 GHz."
        connectedChip != "ESP32-C5" -> "5 GHz requires ESP32-C5."
        "wifi_5ghz" !in deviceFeatures -> "5 GHz is not enabled in this firmware build."
        reported5gChannels.isEmpty() ->
            "No 5 GHz channels are allowed by the current Wi-Fi regulatory country. " +
                "Update the country in WiFi Config."
        else -> ""
    }
    val allowed2gChannels = connected?.allowed2gChannels.orEmpty().ifEmpty {
        com.swp81x.nrsuite.core.wifi.WIFI_2G_CHANNELS
    }
    val allowed5gChannels = if (wifi5Available) {
        reported5gChannels
    } else {
        emptyList()
    }
    val allowedWifiChannels = (allowed2gChannels + allowed5gChannels).distinct()

    // Device handoff safety: preserve valid per-module channel selections, but
    // never leave a 5 GHz selection active after replacing a C5 with a 2.4 GHz
    // only board (or after the regulatory channel mask changes).
    val connectedWifiDeviceIdentity = connected?.deviceId?.takeIf { it.isNotBlank() }
        ?: connectedChip
    LaunchedEffect(
        primaryFingerprint,
        connectedWifiDeviceIdentity,
        allowedWifiChannels,
    ) {
        if (connected != null && allowedWifiChannels.isNotEmpty()) {
            viewModel.normalizeWifiStateForConnectedDevice(
                deviceIdentity = connectedWifiDeviceIdentity,
                allowedChannels = allowedWifiChannels,
            )
        }
    }

    val bleScannerScanRunning = bleScanRunning && bleScanOwner == BleScanOwner.SCANNER
    val bleScannerScanStarting = bleScanStarting && bleScanOwner == BleScanOwner.SCANNER
    val bleScannerScanStopping = bleScanStopping && bleScanOwner == BleScanOwner.SCANNER
    val trackerScanRunning = bleScanRunning && bleScanOwner == BleScanOwner.TRACKER_DETECTOR
    val profileScanRunning = bleScanRunning && bleScanOwner == BleScanOwner.PROFILE
    val profileScanStarting = bleScanStarting && bleScanOwner == BleScanOwner.PROFILE
    val profileScanStopping = bleScanStopping && bleScanOwner == BleScanOwner.PROFILE
    LaunchedEffect(showWifiBandSelector, wifiBand) {
        if (!showWifiBandSelector && wifiBand != com.swp81x.nrsuite.core.wifi.WifiBand.AUTO) {
            viewModel.setWifiBand(com.swp81x.nrsuite.core.wifi.WifiBand.AUTO)
        }
    }
    val pinBruteforceCapableConnected = deviceConnectionStates.values.any { state ->
        state is ConnectionState.Connected &&
            state.chip in setOf("ESP32-S2", "ESP32-S3") &&
            (state.features.isEmpty() || "pin_bruteforce" in state.features)
    }
    val blePinBruteforceCapableConnected = deviceConnectionStates.values.any { state ->
        state is ConnectionState.Connected &&
            state.chip in setOf("ESP32-C3", "ESP32-S3", "ESP32") &&
            (state.features.isEmpty() || "ble_pin_bruteforce" in state.features)
    }
    val themePalette = NrTheme.colors
    val liveModules = remember(
        themePalette,
        connectionState,
        deviceConnectionStates,
        scanning,
        sniffing,
        beaconRunning,
        deauthRunning,
        deauthDetectorRunning,
        rogueApRunning,
        clientPresenceRunning,
        hiddenApRunning,
        autoHandshakeState.running,
        portalRunning,
        portalMode,
        bleAdvertising,
        bleScanRunning,
        ouiDatabaseStatus,
        meshActive,
        blePinBruteforce.running,
        rfid.running,
        wpsLockDetectorState.scanning,
        wpsAttemptDetectorState.starting,
        wpsAttemptDetectorState.running,
        wpsAttemptDetectorState.sessions.size,
    ) {
        modules.map { module ->
        val runsWithoutDevice = module.id == "ducky" ||
            module.id == "credential_manager" ||
            module.id == "wpa_cracker" ||
            module.id == "pcap22000" ||
            module.id == "mac_lookup" ||
            module.id == "bssid_geolocation" ||
            module.id == "serial_monitor" ||
            module.id == "firmware" ||
            module.id == "ir" ||
            module.id == "ir_search" ||
            module.id == "ir_config" ||
            module.id == "ir_jammer"
        val requiresDevice = !runsWithoutDevice
        val featureKey = when (module.id) {
            "wifi" -> "wifi"
            "sniff" -> "sniff"
            "beacon" -> "beacon"
            "deauth" -> "deauth"
            "deauth_detector" -> "deauth_detect"
            "nrugochi" -> "nrugochi"
            "client_presence" -> "client_detect"
            "hidden_ap" -> "hidden_ap"
            "wps_lock_detector" -> "wps_lock_detect"
            "wps_attempt_detector" -> "wps_attempt_detect"
            "ble_scanner" -> "ble_scan"
            "tracker_detector" -> "ble_scan"
            "ble_profile" -> "ble_profile"
            "mesh" -> "mesh_provision"
            "portal" -> "portal"
            "evil_twin" -> "portal"
            "storage" -> "storage"
            "ble" -> "ble_hid"
            "badusb" -> "badusb"
            "pin_bruteforce" -> "pin_bruteforce"
            "ble_pin_bruteforce" -> "ble_pin_bruteforce"
            "ld2450" -> "ld2450"
            "rfid" -> "rfid"
            "ir_receiver" -> "ir_rx"
            else -> null
        }
        val chipSupported = when (module.id) {
            "ble" -> connectedChip in setOf("ESP32-C3", "ESP32-S3", "ESP32")
            "ble_scanner" -> connectedChip in setOf("ESP32-C3", "ESP32-S3", "ESP32")
            "tracker_detector" -> connectedChip in setOf("ESP32-C3", "ESP32-S3", "ESP32")
            "ble_profile" -> connectedChip in setOf("ESP32-C3", "ESP32-S3", "ESP32")
            "badusb" -> connectedChip in setOf("ESP32-S2", "ESP32-S3")
            "pin_bruteforce" -> connectedChip in setOf("ESP32-S2", "ESP32-S3")
            "ble_pin_bruteforce" -> connectedChip in setOf("ESP32-C3", "ESP32-S3", "ESP32")
            else -> true
        }
        val featureSupported = when {
            module.id == "badusb" -> badUsbCapableConnected
            module.id == "pin_bruteforce" -> pinBruteforceCapableConnected
            module.id == "ble_pin_bruteforce" -> blePinBruteforceCapableConnected
            deviceFeatures.isEmpty() || featureKey == null -> chipSupported
            else -> featureKey in deviceFeatures
        }
        val supported = featureSupported
        val availabilityLabel = when {
            !module.available -> module.statusLabel ?: "Unavailable"
            !isDeviceConnected && requiresDevice -> "Device needed"
            isDeviceConnected && !supported -> when {
                module.id in setOf("ble", "ble_scanner", "tracker_detector", "ble_profile", "badusb", "pin_bruteforce", "ble_pin_bruteforce") -> "Not supported"
                else -> "Firmware required"
            }
            else -> null
        }
        val availabilityColor = when (availabilityLabel) {
            null -> null
            "Device needed" -> themePalette.statusNeutral
            "Unavailable" -> themePalette.statusNeutral.copy(alpha = 0.5f)
            "Not supported" -> themePalette.statusRed
            else -> themePalette.statusAmber
        }
        module.copy(
            available = module.available,
            iconTint = categoryColor(module.category, themePalette.isLight),
            statusLabel = availabilityLabel,
            statusColor = availabilityColor,
            isRunning = when (module.id) {
                "wifi" -> scanning
                "sniff" -> sniffing
                "beacon" -> beaconRunning
                "deauth" -> deauthRunning
                "deauth_detector" -> deauthDetectorRunning
                "rogue_ap" -> rogueApRunning
                "client_presence" -> clientPresenceRunning
                "hidden_ap" -> hiddenApRunning
                "portal" -> portalRunning && portalMode == "portal"
                "evil_twin" -> portalRunning && portalMode == "evil_twin"
                "ble" -> bleAdvertising
                "ble_pin_bruteforce" -> blePinBruteforce.running
                "ble_scanner" -> bleScannerScanRunning || bleScannerScanStarting
                "tracker_detector" -> trackerScanRunning
                "ble_profile" -> bleProfileRunning || profileScanRunning || profileScanStarting
                "wps_lock_detector" -> wpsLockDetectorState.scanning
                "wps_attempt_detector" -> wpsAttemptDetectorState.running ||
                    wpsAttemptDetectorState.starting ||
                    wpsAttemptDetectorState.scanningTargets
                "mesh" -> meshActive
                "ld2450" -> ld2450.running
                "rfid" -> rfid.running
                else -> false
            },
        )
    }
    }

    var selectedTab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var permissionRevision by remember { mutableIntStateOf(0) }
    var activeModuleId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var showRootDirectoryDialog by rememberSaveable { mutableStateOf(false) }
    var showOuiDatabaseDialog by rememberSaveable { mutableStateOf(false) }
    var showFirmwareOnboardingDialog by rememberSaveable { mutableStateOf(false) }
    var firmwareOnboardingDontShowAgain by rememberSaveable { mutableStateOf(false) }
    var rootDirectoryPickerActive by rememberSaveable { mutableStateOf(false) }
    var previousActiveModuleId by remember { mutableStateOf<String?>(null) }
    var irRemoteReturnTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var wifiGroupFilter by rememberSaveable { mutableIntStateOf(0) }

    // BSSID lookup state is intentionally temporary: leaving the module clears
    // the selected result and any nearby-AP discovery state. Saved history stays.
    LaunchedEffect(activeModuleId) {
        if (previousActiveModuleId == "mac_lookup" && activeModuleId != "mac_lookup") {
            viewModel.clearMacLookup()
        }
        if (previousActiveModuleId == "bssid_geolocation" &&
            activeModuleId != "bssid_geolocation"
        ) {
            viewModel.clearBssidGeolocation()
        }
        if (previousActiveModuleId == "ir" && activeModuleId != "ir") {
            viewModel.stopIrTransmission()
            viewModel.closeIrRemote()
        }
        if (previousActiveModuleId == "ir_search" &&
            activeModuleId != "ir_search" &&
            !(activeModuleId == "ir" && irRemoteReturnTarget == "ir_search")
        ) {
            viewModel.stopIrSearch()
        }
        if (previousActiveModuleId == "ir_receiver" && activeModuleId != "ir_receiver") {
            viewModel.stopIrEsp32Rx()
        }
        if (previousActiveModuleId == "ir_jammer" && activeModuleId != "ir_jammer") {
            viewModel.stopIrJammer()
        }
        if (previousActiveModuleId == "probe_monitor" &&
            activeModuleId != "probe_monitor"
        ) {
            viewModel.resetProbeMonitor()
        }
        if (previousActiveModuleId == "channel_analyzer" &&
            activeModuleId != "channel_analyzer"
        ) {
            viewModel.resetChannelAnalyzer()
        }
        if (previousActiveModuleId == "wps_pixie_live" &&
            activeModuleId != "wps_pixie_live"
        ) {
            viewModel.resetWpsPixieLiveScreen()
        }
        if (previousActiveModuleId != activeModuleId &&
            (previousActiveModuleId?.startsWith("ir") == true ||
                activeModuleId?.startsWith("ir") == true)
        ) {
            viewModel.clearIrMessages()
        }
        if (previousActiveModuleId in setOf("firmware", "settings") &&
            activeModuleId !in setOf("firmware", "settings")
        ) {
            viewModel.onFirmwareFlasherScreenClosed()
        }
        previousActiveModuleId = activeModuleId
    }

    val irRemoteDetailVisible = activeModuleId == "ir" &&
        (irSelectedRemote != null || irLoadingRemote)
    val navigateBack: () -> Unit = {
        when {
            irRemoteDetailVisible -> {
                viewModel.closeIrRemote()
                val target = irRemoteReturnTarget
                irRemoteReturnTarget = null
                if (target != null) activeModuleId = target
            }
            activeModuleId != null -> {
                irRemoteReturnTarget = null
                activeModuleId = null
            }
            selectedCategory != null -> selectedCategory = null
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1_300)
        if (!firmwareOnboardingDismissed) showFirmwareOnboardingDialog = true
    }

    BackHandler(enabled = activeModuleId != null || selectedCategory != null) {
        // Firmware flashing is allowed to continue in the background.
        navigateBack()
    }

    val permissionReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != ACTION_USB_PERMISSION) return
                @Suppress("DEPRECATION")
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (device != null) {
                    viewModel.clearPermissionRequest(device.deviceId)
                    val reallyGranted = granted || usbManager.hasPermission(device)
                    viewModel.onPermissionResult(device, reallyGranted)
                    permissionRevision++
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

    val anyModuleRunning = sniffing || beaconRunning || deauthRunning || deauthDetectorRunning ||
        rogueApRunning || clientPresenceRunning || hiddenApRunning || hiddenApStarting ||
        portalRunning || bleAdvertising || bleScanRunning || bleProfileRunning || meshActive ||
        crackerRunning || wpsAttemptDetectorState.running || wpsAttemptDetectorState.starting ||
        wpsAttemptDetectorState.scanningTargets
    val view = LocalView.current
    DisposableEffect(anyModuleRunning) {
        val window = (view.context as? Activity)?.window
        if (anyModuleRunning) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        rootDirectoryPickerActive = false
        if (uri != null) {
            viewModel.setExportDirectory(uri)
        }
    }

    val wifiCaptureFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setWifiCaptureDirectory(uri)
        }
    }

    LaunchedEffect(requiresRootDirectory) {
        if (requiresRootDirectory) {
            showRootDirectoryDialog = true
            viewModel.onRootDirectoryPromptShown()
        }
    }

    // On a fresh install the NRSuite root directory prompt takes priority.
    // Queue the OUI database prompt until that dialog is no longer visible.
    LaunchedEffect(requiresOuiDatabase, showRootDirectoryDialog, rootDirectoryPickerActive) {
        if (requiresOuiDatabase &&
            !showRootDirectoryDialog &&
            !rootDirectoryPickerActive
        ) {
            showOuiDatabaseDialog = true
            viewModel.onOuiDatabasePromptShown()
        }
    }

    LaunchedEffect(exportDirectoryName) {
        if (exportDirectoryName == "Not configured") {
            showRootDirectoryDialog = true
        }
    }

    val htmlPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.setPortalHtmlFile(uri, null)
        }
    }

    val evilTwinHtmlPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.setEvilTwinHtmlFile(uri, null)
        }
    }

    val htmlTemplatePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importHtmlTemplate(uri, null)
        }
    }


    val badUsbPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.setBadUsbPayload(uri, null)
        }
    }

    val blePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.setBlePayload(uri, null)
        }
    }

    val firmwarePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.setFirmwareFlashFile(uri, uri.lastPathSegment?.substringAfterLast('/'))
        }
    }

    fun requestPermission(device: UsbDevice) {
        // FLAG_MUTABLE is required so the system can attach
        // EXTRA_DEVICE and EXTRA_PERMISSION_GRANTED to the broadcast.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val pendingIntent = PendingIntent.getBroadcast(context, device.deviceId, intent, flags)
        viewModel.markPermissionRequested(device)
        usbManager.requestPermission(device, pendingIntent)

        // Fallback for devices/OEMs that do not deliver the permission
        // broadcast reliably: poll hasPermission() and force a recompose.
        uiScope.launch {
            repeat(40) {
                kotlinx.coroutines.delay(250)
                if (usbManager.hasPermission(device)) {
                    permissionRevision++
                    return@launch
                }
            }
        }
    }

    fun requestSerialMonitorPermission(device: UsbDevice) {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val pendingIntent = PendingIntent.getBroadcast(context, device.deviceId, intent, flags)
        viewModel.markSerialMonitorPermissionRequested(device)
        usbManager.requestPermission(device, pendingIntent)

        uiScope.launch {
            repeat(40) {
                kotlinx.coroutines.delay(250)
                if (usbManager.hasPermission(device)) {
                    permissionRevision++
                    return@launch
                }
            }
        }
    }

    fun requestFirmwarePermission(device: UsbDevice) {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val pendingIntent = PendingIntent.getBroadcast(context, device.deviceId, intent, flags)
        viewModel.markFirmwarePermissionRequested(device)
        usbManager.requestPermission(device, pendingIntent)

        uiScope.launch {
            repeat(40) {
                kotlinx.coroutines.delay(250)
                if (usbManager.hasPermission(device)) {
                    permissionRevision++
                    return@launch
                }
            }
        }
    }

    fun connectOrRequestPermission(device: UsbDevice) {
        if (device.deviceId in pendingPermissionRequests) return
        if (usbManager.hasPermission(device)) {
            viewModel.connect(device)
        } else {
            requestPermission(device)
        }
    }

    fun isSerialMonitorActiveDevice(deviceId: Int): Boolean =
        serialMonitorConnected && serialMonitorSelected?.device?.deviceId == deviceId

    val activeModule = liveModules.firstOrNull { it.id == activeModuleId }
    val irTxChipLabel = when (irTransmitBackend) {
        IrTransmitBackend.PHONE -> if (irCapabilities.hasEmitter) "TX: Phone" else "TX: Unavailable"
        IrTransmitBackend.ESP32 -> if (connected != null &&
            "ir_tx" in deviceFeatures && connected.irTxGpio != null
        ) {
            "TX: GPIO ${connected.irTxGpio}"
        } else {
            "TX: Unavailable"
        }
        IrTransmitBackend.AUTO -> when {
            connected != null && "ir_tx" in deviceFeatures && connected.irTxGpio != null ->
                "TX: GPIO ${connected.irTxGpio}"
            irCapabilities.hasEmitter -> "TX: Phone"
            else -> "TX: Unavailable"
        }
    }
    val irRxChipLabel = when {
        connected == null -> "RX: —"
        "ir_rx" !in deviceFeatures -> "RX: —"
        connected.irRxGpio != null -> "RX: GPIO ${connected.irRxGpio}"
        else -> "RX: Off"
    }
    val irReplayBackendLabel = when (irTransmitBackend) {
        IrTransmitBackend.PHONE -> if (irCapabilities.hasEmitter) "Phone IR" else "Phone IR unavailable"
        IrTransmitBackend.ESP32 -> when {
            connected == null -> "ESP32 unavailable"
            "ir_tx" !in deviceFeatures -> "ESP32 firmware lacks IR TX"
            connected.irTxGpio == null -> "ESP32 TX GPIO not configured"
            else -> "ESP32 IR · GPIO ${connected.irTxGpio}"
        }
        IrTransmitBackend.AUTO -> when {
            connected != null && "ir_tx" in deviceFeatures && connected.irTxGpio != null ->
                "Auto → ESP32 IR · GPIO ${connected.irTxGpio}"
            irCapabilities.hasEmitter -> "Auto → Phone IR"
            else -> "No IR TX available"
        }
    }
    val irReplayBackendAvailable = when (irTransmitBackend) {
        IrTransmitBackend.PHONE -> irCapabilities.hasEmitter
        IrTransmitBackend.ESP32 -> connected != null &&
            "ir_tx" in deviceFeatures && connected.irTxGpio != null
        IrTransmitBackend.AUTO -> irCapabilities.hasEmitter ||
            (connected != null && "ir_tx" in deviceFeatures && connected.irTxGpio != null)
    }
    val moduleUnavailableMessage = if (activeModuleId == "ir") {
        val hasEsp32 = connected != null
        val hasEsp32IrTx = "ir_tx" in deviceFeatures
        val esp32IrTxReady = hasEsp32 && hasEsp32IrTx && connected?.irTxGpio != null
        when (irTransmitBackend) {
            IrTransmitBackend.PHONE -> if (!irCapabilities.hasEmitter) {
                "This phone does not expose an IR emitter. Switch the IR backend to ESP32 IR or use a supported phone."
            } else {
                null
            }
            IrTransmitBackend.ESP32 -> when {
                !hasEsp32 -> "ESP32 IR is selected, but no ESP32 is connected."
                !hasEsp32IrTx -> "The connected firmware does not advertise IR TX support yet."
                connected?.irTxGpio == null ->
                    "Configure the ESP32 IR TX GPIO in IR Configuration."
                !esp32IrTxReady -> "ESP32 IR TX is not ready."
                else -> null
            }
            IrTransmitBackend.AUTO -> when {
                esp32IrTxReady -> null
                irCapabilities.hasEmitter -> null
                !hasEsp32 -> "No phone IR emitter detected. Connect an ESP32 with IR TX support or use a phone with an IR emitter."
                !hasEsp32IrTx -> "The connected firmware does not advertise IR TX support yet."
                connected?.irTxGpio == null ->
                    "No phone IR emitter detected. Configure the ESP32 IR TX GPIO in IR Configuration."
                else -> null
            }
        }
    } else activeModule?.let { module ->
        when (module.statusLabel) {
            "Device needed" ->
                "Connect your NRSuite ESP32 over USB-OTG to use ${module.title}."
            "Not supported" ->
                "This board doesn't support ${module.title}."
            "Firmware required" ->
                "Update the firmware to use ${module.title} on this board."
            "OUI DB required" ->
                "Download the OUI/vendor database to use ${module.title}."
            "Unavailable" ->
                "${module.title} is not available in this build."
            else -> null
        }
    }
    val showBack = activeModuleId != null || selectedCategory != null
    val activeTitle = when {
        irRemoteDetailVisible -> "IR Remote Details"
        activeModuleId == "wifi_config" -> "WiFi config"
        activeModuleId == "firmware" -> "Flasher"
        activeModuleId == "wpa_cracker" -> "WPA Cracker"
        activeModuleId == "pcap22000" -> "PCAP to .hs22000"
        activeModuleId == "rfid_config" -> "RFID config"
        activeModuleId == "sensor_config" -> "Sensor config"
        activeModuleId == "devices" -> "Devices"
        activeModuleId == "module_catalog" -> "All Modules"
        activeModule != null -> activeModule.title
        activeModuleId == "settings" -> "Settings"
        selectedCategory != null -> selectedCategory!!
        selectedTab == AppTab.DEVICES -> "Devices"
        selectedTab == AppTab.LOGS -> "Logs"
        else -> "NRSuite"
    }

    val connectedDeviceOptions = devices.filter { device ->
        deviceConnectionStates[viewModel.deviceFingerprintImpl(device.device)] is ConnectionState.Connected
    }
    val canSwitchActiveDevice = connectedDeviceOptions.size > 1
    val switchToNextActiveDevice: () -> Unit = {
        if (canSwitchActiveDevice) {
            val currentIndex = connectedDeviceOptions.indexOfFirst {
                viewModel.deviceFingerprintImpl(it.device) == primaryFingerprint
            }
            val next = connectedDeviceOptions[
                if (currentIndex >= 0) {
                    (currentIndex + 1) % connectedDeviceOptions.size
                } else {
                    0
                }
            ]
            if (viewModel.switchActiveDevice(next.device)) {
                val nextFingerprint = viewModel.deviceFingerprintImpl(next.device)
                val chip = (deviceConnectionStates[nextFingerprint] as? ConnectionState.Connected)?.chip
                Toast.makeText(
                    context,
                    "Active device: ${chip?.let { "NRSuite $it" } ?: next.displayName}",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    val suppressUsbAttachPrompt =
        (activeModuleId != null && activeModuleId in setOf(
            "devices",
            "firmware",
            "settings",
            "serial_monitor",
            "badusb",
        )) ||
        (activeModuleId == null && selectedCategory == null &&
            selectedTab in setOf(AppTab.HOME, AppTab.DEVICES))
    LaunchedEffect(suppressUsbAttachPrompt) {
        if (suppressUsbAttachPrompt) viewModel.dismissUsbAttachPrompt()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            NRSuiteTopBar(
                title = activeTitle,
                showBack = showBack,
                backDescription = when {
                    irRemoteDetailVisible && irRemoteReturnTarget == "ir_search" ->
                        "Back to IR Finder"
                    irRemoteDetailVisible -> "Back to IR Remotes"
                    else -> "Back"
                },
                onBack = navigateBack,
                connectionState = connectionState,
                hasDevices = devices.isNotEmpty(),
                hasPermission = devices.firstOrNull()?.let {
                    usbManager.hasPermission(it.device)
                } == true,
                hasPendingPermissions = pendingPermissionRequests.any { deviceId ->
                    !isSerialMonitorActiveDevice(deviceId)
                },
                showConnectionStatus = showBack,
                onConnectionClick = if (canSwitchActiveDevice) {
                    switchToNextActiveDevice
                } else {
                    null
                },
                showCatalogAction = activeModuleId == null && selectedCategory == null,
                onOpenCatalog = { activeModuleId = "module_catalog" },
                onOpenSettings = { activeModuleId = "settings" },
            )
        },
        bottomBar = {
            if (activeModuleId == null && selectedCategory == null) {
                NRSuiteBottomBar(
                    selectedTab = selectedTab,
                    onSelectTab = { tab ->
                        selectedTab = tab
                        activeModuleId = null
                        selectedCategory = null
                    },
                )
            }
        },
        snackbarHost = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (!suppressUsbAttachPrompt) {
                    usbAttachPrompts.takeLast(3).forEach { prompt ->
                        UsbAttachPromptCard(
                            prompt = prompt,
                            onConnect = {
                                viewModel.dismissUsbAttachPrompt(prompt.deviceId)
                                connectOrRequestPermission(prompt.device.device)
                            },
                            onOpenDevices = {
                                viewModel.dismissUsbAttachPrompt(prompt.deviceId)
                                activeModuleId = null
                                selectedCategory = null
                                selectedTab = AppTab.DEVICES
                            },
                            onDismiss = {
                                viewModel.dismissUsbAttachPrompt(prompt.deviceId)
                            },
                        )
                    }
                    if (usbAttachPrompts.size > 3) {
                        Text(
                            text = "+${usbAttachPrompts.size - 3} more USB device(s) detected",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        )
                    }
                }
                SnackbarHost(hostState = snackbarHostState)
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)

        if (showRootDirectoryDialog) {
            AlertDialog(
                onDismissRequest = { showRootDirectoryDialog = false },
                title = { Text("NRSuite root directory") },
                text = {
                    Text(
                        text = "NRSuite needs a root folder for PCAP captures, logs, " +
                            "DuckyScripts, and portal exports. Choose a folder now, " +
                            "or tap Later and choose it when starting a capture.",
                        color = NrOnSurfaceVariant,
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        showRootDirectoryDialog = false
                        rootDirectoryPickerActive = true
                        folderPicker.launch(null)
                    }) {
                        Text("Choose folder")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showRootDirectoryDialog = false }) {
                        Text("Later")
                    }
                },
            )
        }

        if (showOuiDatabaseDialog && !showRootDirectoryDialog && !rootDirectoryPickerActive) {
            AlertDialog(
                onDismissRequest = { showOuiDatabaseDialog = false },
                title = { Text("MAC vendor database") },
                text = {
                    Text(
                        text = "Download the IEEE OUI registry now for offline MAC/vendor lookup? " +
                            "You can also do this later in Settings.",
                        color = NrOnSurfaceVariant,
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        showOuiDatabaseDialog = false
                        viewModel.downloadOuiDatabase()
                    }) {
                        Text("Download")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showOuiDatabaseDialog = false }) {
                        Text("Later")
                    }
                },
            )
        }

        if (showFirmwareOnboardingDialog &&
            !showRootDirectoryDialog &&
            !rootDirectoryPickerActive &&
            !showOuiDatabaseDialog
        ) {
            AlertDialog(
                onDismissRequest = { showFirmwareOnboardingDialog = false },
                title = { Text("Flash NRSuite firmware?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "To use the ESP32 modules, the board needs NRSuite firmware. " +
                                "You can flash it now or find the flasher later in Settings → Firmware.",
                            color = NrOnSurfaceVariant,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = firmwareOnboardingDontShowAgain,
                                onCheckedChange = { firmwareOnboardingDontShowAgain = it },
                            )
                            Text(
                                text = "Don't show again",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showFirmwareOnboardingDialog = false
                            viewModel.dismissFirmwareOnboarding()
                            viewModel.onModuleOpened("firmware")
                            activeModuleId = "firmware"
                        },
                    ) {
                        Text("Open Flasher")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            showFirmwareOnboardingDialog = false
                            if (firmwareOnboardingDontShowAgain) {
                                viewModel.dismissFirmwareOnboarding()
                            }
                        },
                    ) {
                        Text("Not now")
                    }
                },
            )
        }

        actionError?.let { message ->
            AlertDialog(
                onDismissRequest = viewModel::consumeActionError,
                title = { Text("Action blocked") },
                text = {
                    Text(
                        text = message,
                        color = NrOnSurfaceVariant,
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::consumeActionError) {
                        Text("OK")
                    }
                },
            )
        }

        val screenAccentCategory = when (activeModuleId) {
            "wifi_config" -> "WiFi"
            "rfid_config" -> "RFID"
            "sensor_config" -> "Sensors"
            else -> activeModule?.category
        }
        CategoryAccentTheme(screenAccentCategory) {
            ModuleScreenScaffold(
                modifier = contentModifier,
                unavailableMessage = moduleUnavailableMessage,
            ) {
            key(wifiDeviceRevision) {
            when {
            activeModuleId == "wifi_config" -> {
                WifiConfigScreen(
                    connected = connected,
                    band = wifiBand,
                    showBandSelector = showWifiBandSelector,
                    fiveGhzAvailable = wifi5Available,
                    fiveGhzUnavailableMessage = wifi5UnavailableMessage,
                    onBandSelected = viewModel::setWifiBand,
                    onCountrySelected = viewModel::setWifiCountry,
                    captureOutputLabel = wifiCaptureDirectoryName
                        ?: "Global · $exportDirectoryName",
                    hasCustomCaptureOutput = wifiCaptureDirectoryName != null,
                    onChooseCaptureOutput = { wifiCaptureFolderPicker.launch(null) },
                    onResetCaptureOutput = { viewModel.setWifiCaptureDirectory(null) },
                    ouiRules = ouiRules,
                    onAddOuiRule = viewModel::addOuiRule,
                    onDeleteOuiRule = viewModel::deleteOuiRule,
                    modifier = Modifier,
                )
            }

            activeModuleId == "wifi" -> {
                WifiScanScreen(
                    connected = isDeviceConnected,
                    scanning = scanning,
                    networks = networks,
                    band = wifiBand,
                    showBandSelector = showWifiBandSelector,
                    fiveGhzAvailable = wifi5Available,
                    fiveGhzUnavailableMessage = wifi5UnavailableMessage,
                    onBandSelected = viewModel::setWifiBand,
                    onScan = viewModel::scanWifi,
                    modifier = Modifier,
                )
            }

            activeModuleId == "probe_monitor" -> {
                ProbeMonitorRoute(
                    connected = isDeviceConnected,
                    running = probeMonitorRunning,
                    fixed = probeMonitorFixed,
                    channel = probeMonitorChannel,
                    entries = probeMonitorEntries,
                    frameCount = probeMonitorFrameCount,
                    allowedChannels = allowedWifiChannels,
                    onStart = viewModel::startProbeMonitor,
                    onStop = viewModel::stopProbeMonitor,
                    modifier = Modifier,
                )
            }

            activeModuleId == "channel_analyzer" -> {
                ChannelAnalyzerRoute(
                    connected = isDeviceConnected,
                    scanning = scanning,
                    networks = channelAnalyzerNetworks,
                    onScan = viewModel::scanChannelAnalyzer,
                    modifier = Modifier,
                )
            }

            activeModuleId == "sniff" -> {
                SniffScreen(
                    connected = connectionState is ConnectionState.Connected,
                    sniffing = sniffing,
                    packetCount = sniffPacketCount,
                    captureBytes = sniffCaptureBytes,
                    capturePath = capturePath,
                    handshake = sniffHandshake,
                    scanning = scanning,
                    networks = networks,
                    onScanWifi = viewModel::scanWifiForTargets,
                    onStart = viewModel::startSniff,
                    onStop = viewModel::stopSniff,
                    showBandSelector = showWifiBandSelector,
                    fiveGhzAvailable = wifi5Available,
                    fiveGhzUnavailableMessage = wifi5UnavailableMessage,
                    allowedChannels = allowedWifiChannels,
                    modifier = Modifier,
                )
            }

            activeModuleId == "beacon" -> {
                BeaconScreen(
                    connected = connectionState is ConnectionState.Connected,
                    running = beaconRunning,
                    sentFrames = beaconSent,
                    ssidCount = beaconSsidCount,
                    activeChannel = beaconChannel,
                    savedLists = beaconLists,
                    onSaveListAction = viewModel::saveBeaconList,
                    onRenameListAction = viewModel::renameBeaconList,
                    onDeleteList = viewModel::deleteBeaconList,
                    onStart = viewModel::startBeacon,
                    onStop = viewModel::stopBeacon,
                    allowedChannels = allowedWifiChannels,
                    modifier = Modifier,
                )
            }

            activeModuleId == "deauth" -> {
                DeauthScreen(
                    connected = connectionState is ConnectionState.Connected,
                    running = deauthRunning,
                    sentFrames = deauthSent,
                    failedFrames = deauthFailed,
                    elapsedMs = deauthElapsedMs,
                    pps = deauthPps,
                    targetBssid = deauthTarget,
                    activeChannel = deauthChannel,
                    scanning = scanning,
                    networks = networks,
                    allowedChannels = allowedWifiChannels,
                    onScanWifi = viewModel::scanWifiForTargets,
                    onStart = viewModel::startDeauth,
                    onStop = viewModel::stopDeauth,
                    modifier = Modifier,
                )
            }

            activeModuleId == "nrugochi" -> {
                NrugochiScreen(
                    connected = isDeviceConnected,
                    state = autoHandshakeState,
                    sessions = autoHandshakeSessions,
                    feed = autoHandshakeFeed,
                    pwnMode = pwnModeState,
                    captures = capturedHandshakes,
                    onStart = viewModel::startAutoHandshake,
                    onStop = viewModel::stopAutoHandshake,
                    onResumeSession = viewModel::resumeAutoHandshake,
                    onDeleteSession = viewModel::deleteAutoHandshakeSession,
                    onClearSessions = viewModel::clearAutoHandshakeSessions,
                    onPwnModeEnabledChange = viewModel::setPwnModeEnabled,
                    onAddPwnPassword = viewModel::addPwnPassword,
                    onRemovePwnPassword = viewModel::removePwnPassword,
                    onCrackCapture = { entry ->
                        viewModel.openCaptureInCracker(entry)
                        activeModuleId = "wpa_cracker"
                    },
                    onDeleteCapture = viewModel::deleteCapturedHandshake,
                    onResetStats = viewModel::resetNrugochiStats,
                    modifier = Modifier,
                )
            }

            activeModuleId == "client_presence" -> {
                ClientPresenceScreen(
                    connected = connectionState is ConnectionState.Connected,
                    running = clientPresenceRunning,
                    mode = clientPresenceMode,
                    fixed = clientPresenceFixed,
                    channel = clientPresenceChannel,
                    targetBssid = clientPresenceTargetBssid,
                    scanning = scanning,
                    networks = networks,
                    clients = clientPresenceClients,
                    frameCount = clientPresenceFrameCount,
                    lastTriggerAt = clientPresenceLastTriggerAt,
                    onModeChange = viewModel::setClientPresenceMode,
                    onFixedChange = viewModel::setClientPresenceFixed,
                    onChannelChange = viewModel::setClientPresenceChannel,
                    onScanWifi = viewModel::scanWifiForTargets,
                    onSelectTarget = viewModel::selectClientPresenceTarget,
                    onStart = { band ->
                        viewModel.startClientPresence(
                            mode = clientPresenceMode,
                            fixed = clientPresenceFixed,
                            channel = clientPresenceChannel,
                            targetBssid = clientPresenceTargetBssid,
                            intervalMs = 300,
                            band = band,
                        )
                    },
                    onStop = viewModel::stopClientPresence,
                    onTriggerReconnect = viewModel::triggerClientReconnectBurst,
                    onClear = viewModel::clearClientPresence,
                    showBandSelector = showWifiBandSelector,
                    fiveGhzAvailable = wifi5Available,
                    fiveGhzUnavailableMessage = wifi5UnavailableMessage,
                    allowedChannels = allowedWifiChannels,
                    modifier = Modifier,
                )
            }

            activeModuleId == "wps_lock_detector" -> {
                WpsLockDetectorScreen(
                    connected = connectionState is ConnectionState.Connected,
                    scanning = wpsLockDetectorState.scanning,
                    accessPoints = wpsLockDetectorState.accessPoints,
                    error = wpsLockDetectorState.error,
                    onScan = viewModel::startWpsLockScan,
                    modifier = Modifier,
                )
            }

            activeModuleId == "wps_attempt_detector" -> {
                WpsAttemptDetectorScreen(
                    connected = connectionState is ConnectionState.Connected,
                    state = wpsAttemptDetectorState,
                    onScanWpsTargets = viewModel::scanWpsAttemptTargets,
                    onStart = viewModel::startWpsAttemptDetector,
                    onStop = viewModel::stopWpsAttemptDetector,
                    modifier = Modifier,
                )
            }

            activeModuleId == "hidden_ap" -> {
                HiddenApScreen(
                    connected = connectionState is ConnectionState.Connected,
                    running = hiddenApRunning,
                    starting = hiddenApStarting,
                    fixed = hiddenApFixed,
                    channel = hiddenApChannel,
                    currentHopChannel = hiddenApCurrentHopChannel,
                    observations = hiddenApObservations,
                    candidates = hiddenApCandidates,
                    deauthEnabled = hiddenApDeauthEnabled,
                    onFixedChange = viewModel::setHiddenApFixed,
                    onChannelChange = viewModel::setHiddenApChannel,
                    onDeauthToggle = viewModel::setHiddenApDeauthEnabled,
                    onStart = { band -> viewModel.startHiddenAp(band) },
                    onStop = viewModel::stopHiddenAp,
                    onForceReconnect = viewModel::forceHiddenApReconnect,
                    onClearCandidates = viewModel::clearHiddenApCandidates,
                    onClearObservations = viewModel::clearHiddenApObservations,
                    showBandSelector = showWifiBandSelector,
                    fiveGhzAvailable = wifi5Available,
                    fiveGhzUnavailableMessage = wifi5UnavailableMessage,
                    allowedChannels = allowedWifiChannels,
                    modifier = Modifier,
                )
            }

            activeModuleId == "deauth_detector" -> {
                val visibleFeed = remember(deauthDetectorFeed, deauthDetectorFeedFilter) {
                    when (deauthDetectorFeedFilter) {
                        DeauthFeedFilter.ALL -> deauthDetectorFeed
                        DeauthFeedFilter.BROADCAST -> deauthDetectorFeed.filter { it.targetMac == null }
                        DeauthFeedFilter.TARGETED -> deauthDetectorFeed.filter { it.targetMac != null }
                    }
                }
                DeauthDetectorScreen(
                    connected = connectionState is ConnectionState.Connected,
                    running = deauthDetectorRunning,
                    channelMode = deauthDetectorChannelMode,
                    onChannelModeChange = viewModel::setDeauthDetectorChannelMode,
                    hopIntervalMs = deauthDetectorHopIntervalMs,
                    onHopIntervalChange = viewModel::setDeauthDetectorHopIntervalMs,
                    currentHopChannel = deauthDetectorCurrentHopChannel,
                    framesPerSecond = deauthDetectorFramesPerSecond,
                    totalFrames = deauthDetectorTotalFrames,
                    uniqueSourceCount = deauthDetectorUniqueSourceCount,
                    thresholdFramesPerSecond = 10,
                    activeAlert = deauthDetectorActiveAlert,
                    feed = visibleFeed,
                    feedFilter = deauthDetectorFeedFilter,
                    scanResults = deauthDetectorTargets,
                    selectedTarget = deauthDetectorSelectedTarget,
                    channel = deauthDetectorChannel,
                    onSelectTarget = viewModel::selectDeauthDetectorTarget,
                    onScanClick = viewModel::scanWifiForTargets,
                    isScanning = scanning,
                    onFeedFilterChange = viewModel::setDeauthDetectorFeedFilter,
                    onClearFeed = viewModel::clearDeauthDetectorFeed,
                    onStart = { band -> viewModel.startDeauthDetector(band) },
                    onStop = viewModel::stopDeauthDetector,
                    showBandSelector = showWifiBandSelector,
                    fiveGhzAvailable = wifi5Available,
                    fiveGhzUnavailableMessage = wifi5UnavailableMessage,
                    modifier = Modifier,
                )
            }

            activeModuleId == "ir" -> {
                IrScreen(
                    remotes = irRemotes,
                    selectedRemote = irSelectedRemote,
                    loadingRemote = irLoadingRemote,
                    importing = irImporting,
                    transmitting = irTransmitting,
                    capabilities = irCapabilities,
                    esp32IrReady = connected != null &&
                        "ir_tx" in deviceFeatures && connected.irTxGpio != null,
                    txAvailable = irReplayBackendAvailable,
                    backend = irTransmitBackend,
                    loopMode = irLoopMode,
                    activeLoopSignalId = irActiveLoopSignalId,
                    loopIntervalMs = irLoopIntervalMs,
                    scanProgress = irScanProgress,
                    errorMessage = irErrorMessage,
                    onImport = viewModel::importIrFile,
                    onRefreshLibrary = viewModel::refreshIrLibrary,
                    onOpenRemote = { remoteId ->
                        irRemoteReturnTarget = null
                        viewModel.openIrRemote(remoteId)
                    },
                    openedFromFinder = irRemoteReturnTarget == "ir_search",
                    onToggleFavorite = viewModel::toggleIrFavorite,
                    onDeleteRemote = viewModel::deleteIrRemote,
                    onSendSignal = viewModel::sendIrSignal,
                    onToggleSignal = viewModel::toggleIrSignal,
                    onSetLoopMode = viewModel::setIrLoopMode,
                    onStopTransmission = viewModel::stopIrTransmission,
                    onClearMessages = viewModel::clearIrMessages,
                    modifier = Modifier,
                )
            }

            activeModuleId == "ir_search" -> {
                IrSearchScreen(
                    remotes = irRemotes,
                    state = irSearchState,
                    commonButtonsState = irCommonButtonsState,
                    initialCategory = irFinderCategory,
                    initialBrand = irFinderBrand,
                    onSelectionChanged = viewModel::setIrFinderSelection,
                    onPrepareCommonButtons = viewModel::prepareIrCommonButtons,
                    onRefreshCommonButtons = viewModel::refreshIrCommonButtons,
                    onStart = viewModel::startIrSearch,
                    onPause = viewModel::pauseIrSearch,
                    onResume = viewModel::resumeIrSearch,
                    onPrevious = viewModel::previousIrSearch,
                    onNext = viewModel::nextIrSearch,
                    onManualSend = viewModel::manualSendIrSearch,
                    onToggleLoop = viewModel::toggleIrSearchLoop,
                    onStop = viewModel::stopIrSearch,
                    onFound = viewModel::markIrSearchFound,
                    onOpenMarkedRemote = { candidate ->
                        if (irSearchState.runState == com.swp81x.nrsuite.core.ir.IrSearchRunState.RUNNING) {
                            viewModel.pauseIrSearch()
                        }
                        irRemoteReturnTarget = "ir_search"
                        viewModel.openIrRemote(candidate.remoteId)
                        activeModuleId = "ir"
                    },
                    onRemoveMarkedResult = { candidate ->
                        viewModel.removeIrSearchMarkedResult(
                            candidate.remoteId,
                            candidate.buttonName,
                        )
                    },
                    modifier = Modifier,
                )
            }

            activeModuleId == "ir_receiver" -> {
                IrReceiverScreen(
                    esp32 = connected,
                    pendingCapture = irPendingCapture,
                    learnedButtons = irLearnedButtons,
                    nextButtonNumber = irNextButtonNumber,
                    suggestedRemoteFileName = suggestedReceivedRemoteName,
                    savingRemote = irSavingReceivedRemote,
                    txAvailable = irReplayBackendAvailable,
                    errorMessage = irErrorMessage,
                    onRefresh = viewModel::refreshIrEsp32Status,
                    onStartReceiver = viewModel::startIrEsp32Rx,
                    onStopReceiver = viewModel::stopIrEsp32Rx,
                    onSavePending = viewModel::savePendingIrButton,
                    onDiscardPending = viewModel::discardPendingIrButton,
                    onReplayPending = viewModel::replayPendingIrButton,
                    onDeleteLearned = viewModel::deleteIrLearnedButton,
                    onRenameLearned = viewModel::renameIrLearnedButton,
                    onReplayLearned = viewModel::replayIrLearnedButton,
                    onClearLearned = viewModel::clearIrLearnedButtons,
                    onSaveRemote = viewModel::saveIrLearnedRemote,
                    modifier = Modifier,
                )
            }

            activeModuleId == "ir_jammer" -> {
                IrJammerScreen(
                    state = irJammerState,
                    txAvailable = irReplayBackendAvailable,
                    onStart = viewModel::startIrJammer,
                    onStop = viewModel::stopIrJammer,
                    modifier = Modifier,
                )
            }

            activeModuleId == "ir_config" -> {
                IrConfigScreen(
                    capabilities = irCapabilities,
                    backend = irTransmitBackend,
                    loopIntervalMs = irLoopIntervalMs,
                    exportDirectoryName = exportDirectoryName,
                    remoteCount = irRemotes.size,
                    importing = irImporting,
                    scanProgress = irScanProgress,
                    irDatabaseState = irDatabaseState,
                    irDatabaseMetadata = irDatabaseMetadata,
                    esp32 = connected,
                    errorMessage = irErrorMessage,
                    statusMessage = irStatusMessage,
                    savingPins = irSavingEsp32Pins,
                    rootConfigured = exportDirectoryName != "Not configured",
                    onChooseRootDirectory = {
                        showRootDirectoryDialog = false
                        rootDirectoryPickerActive = true
                        folderPicker.launch(null)
                    },
                    onSetBackend = viewModel::setIrTransmitBackend,
                    onSetLoopInterval = viewModel::setIrLoopIntervalMs,
                    onRefreshCapabilities = viewModel::refreshIrCapabilities,
                    onRescanLibrary = viewModel::refreshIrLibrary,
                    onDownloadIrDatabase = viewModel::downloadIrDatabase,
                    onRetryIrDatabaseOperation = viewModel::retryIrDatabaseOperation,
                    onCancelIrDatabaseOperation = viewModel::cancelIrDatabaseOperation,
                    onImportIrZip = viewModel::importIrZip,
                    onDeleteIrDatabase = viewModel::deleteIrDatabase,
                    onRefreshEsp32Ir = viewModel::refreshIrEsp32Status,
                    onSetIrEsp32Pins = viewModel::setIrEsp32Pins,
                    modifier = Modifier,
                )
            }

            activeModuleId == "mac_lookup" -> {
                OfflineMacLookupScreen(
                    result = macLookupResult,
                    ouiDatabaseStatus = ouiDatabaseStatus,
                    onLookup = viewModel::lookupMac,
                    onClear = viewModel::clearMacLookup,
                    onDownloadOuiDatabase = viewModel::downloadOuiDatabase,
                    modifier = Modifier,
                )
            }

            activeModuleId == "bssid_geolocation" -> {
                MacLookupScreen(
                    result = macLookupResult,
                    geolocationState = bssidGeolocationState,
                    nearbyState = bssidNearbyState,
                    history = bssidGeolocationHistory,
                    onLookup = viewModel::lookupBssidGeolocation,
                    onSelectHistory = viewModel::selectBssidGeolocationHistory,
                    onDeleteHistory = viewModel::deleteBssidGeolocationHistory,
                    onClearHistory = viewModel::clearBssidGeolocationHistory,
                    onClear = viewModel::clearBssidGeolocation,
                    onNearbyToggle = viewModel::setBssidNearbyApsEnabled,
                    modifier = Modifier,
                )
            }

            activeModuleId == "rogue_ap" -> {
                RogueApScreen(
                    connected = connectionState is ConnectionState.Connected,
                    running = rogueApRunning,
                    scanning = rogueApScanning,
                    nearbyNetworks = rogueApNearby,
                    alerts = rogueApAlerts,
                    lastScanAt = rogueApLastScanAt,
                    ouiDatabaseStatus = ouiDatabaseStatus,
                    onDownloadOuiDatabase = viewModel::downloadOuiDatabase,
                    onStart = viewModel::startRogueApDetector,
                    onStop = viewModel::stopRogueApDetector,
                    onClearAlerts = viewModel::clearRogueApAlerts,
                    modifier = Modifier,
                )
            }

            activeModuleId == "evil_twin" -> {
                EvilTwinModuleRoute(
                    viewModel = viewModel,
                    connected = connectionState is ConnectionState.Connected,
                    scanning = scanning,
                    networks = networks,
                    allowedChannels = allowedWifiChannels,
                    onChooseHtml = { evilTwinHtmlPicker.launch(arrayOf("text/html", "text/plain", "*/*")) },
                    onImportHtmlTemplate = {
                        htmlTemplatePicker.launch(arrayOf("text/html", "text/plain", "*/*"))
                    },
                    modifier = Modifier,
                )
            }

            activeModuleId == "credential_manager" -> {
                CredentialManagerScreen(
                    sessions = credentialSessions,
                    onDeleteSession = viewModel::deleteCredentialSession,
                    onClearAll = viewModel::clearCredentialSessions,
                    modifier = Modifier,
                )
            }

            activeModuleId == "wpa_cracker" -> {
                WpaCrackerRoute(
                    viewModel = viewModel,
                    modifier = Modifier,
                )
            }

            activeModuleId == "pcap22000" -> {
                Pcap22000Route(
                    viewModel = viewModel,
                    modifier = Modifier,
                )
            }

            activeModuleId == "wps_pixie_live" -> {
                WpsPixieLiveRoute(
                    viewModel = viewModel,
                    modifier = Modifier,
                )
            }

            activeModuleId == "portal" -> {
                PortalModuleRoute(
                    viewModel = viewModel,
                    connected = connectionState is ConnectionState.Connected,
                    allowedChannels = allowedWifiChannels,
                    onChooseHtml = { htmlPicker.launch(arrayOf("text/html", "text/plain", "*/*")) },
                    onImportHtmlTemplate = {
                        htmlTemplatePicker.launch(arrayOf("text/html", "text/plain", "*/*"))
                    },
                    modifier = Modifier,
                )
            }

            activeModuleId == "storage" -> {
                StorageScreen(
                    connected = connectionState is ConnectionState.Connected,
                    loading = storageLoading,
                    files = storageFiles,
                    totalBytes = storageTotal,
                    usedBytes = storageUsed,
                    freeBytes = storageFree,
                    onRefresh = viewModel::refreshStorage,
                    onDelete = viewModel::deleteStorageFile,
                    onStartMassStorage = viewModel::startMassStorage,
                    modifier = Modifier,
                )
            }

            activeModuleId == "pin_bruteforce" -> {
                PinBruteforceScreen(
                    connected = isDeviceConnected,
                    state = pinBruteforce,
                    attempts = pinAttempts,
                    onSourceChange = viewModel::setPinBruteforceSource,
                    onPinLengthChange = viewModel::setPinBruteforcePinLength,
                    onStartChange = viewModel::setPinBruteforceStart,
                    onEndChange = viewModel::setPinBruteforceEnd,
                    onTimeoutChange = viewModel::setPinBruteforceTimeout,
                    onMaxAttemptsChange = viewModel::setPinBruteforceMaxAttempts,
                    onPersistEveryChange = viewModel::setPinBruteforcePersistEvery,
                    onAttemptsBeforeTimeoutChange = viewModel::setPinBruteforceAttemptsBeforeTimeout,
                    onCooldownChange = viewModel::setPinBruteforceCooldown,
                    onInterAttemptChange = viewModel::setPinBruteforceInterAttempt,
                    onTypingDelayChange = viewModel::setPinBruteforceTypingDelay,
                    onStartDelayChange = viewModel::setPinBruteforceStartDelay,
                    onAutoResumeChange = viewModel::setPinBruteforceAutoResume,
                    onSessionKeyChange = viewModel::setPinBruteforceSessionKey,
                    onWordlistSelected = viewModel::setPinBruteforceWordlist,
                    onClearWordlist = viewModel::clearPinBruteforceWordlist,
                    onStart = viewModel::startPinBruteforce,
                    onStop = viewModel::stopPinBruteforce,
                    onRefresh = viewModel::refreshPinBruteforce,
                    onClearSession = viewModel::clearPinBruteforce,
                    modifier = Modifier,
                )
            }

            activeModuleId == "ble_pin_bruteforce" -> {
                BlePinBruteforceScreen(
                    connected = isDeviceConnected,
                    state = blePinBruteforce,
                    attempts = blePinAttempts,
                    onSourceChange = viewModel::setBlePinBruteforceSource,
                    onPinLengthChange = viewModel::setBlePinBruteforcePinLength,
                    onStartChange = viewModel::setBlePinBruteforceStart,
                    onEndChange = viewModel::setBlePinBruteforceEnd,
                    onTimeoutChange = viewModel::setBlePinBruteforceTimeout,
                    onMaxAttemptsChange = viewModel::setBlePinBruteforceMaxAttempts,
                    onPersistEveryChange = viewModel::setBlePinBruteforcePersistEvery,
                    onAttemptsBeforeTimeoutChange = viewModel::setBlePinBruteforceAttemptsBeforeTimeout,
                    onCooldownChange = viewModel::setBlePinBruteforceCooldown,
                    onInterAttemptChange = viewModel::setBlePinBruteforceInterAttempt,
                    onTypingDelayChange = viewModel::setBlePinBruteforceTypingDelay,
                    onStartDelayChange = viewModel::setBlePinBruteforceStartDelay,
                    onAutoResumeChange = viewModel::setBlePinBruteforceAutoResume,
                    onAutoStartOnConnectChange = viewModel::setBlePinBruteforceAutoStartOnConnect,
                    onPauseOnDisconnectChange = viewModel::setBlePinBruteforcePauseOnDisconnect,
                    onAutoReconnectChange = viewModel::setBlePinBruteforceAutoReconnect,
                    onSessionKeyChange = viewModel::setBlePinBruteforceSessionKey,
                    onWordlistSelected = viewModel::setBlePinBruteforceWordlist,
                    onClearWordlist = viewModel::clearBlePinBruteforceWordlist,
                    onStartAdvertising = viewModel::startBlePinAdvertising,
                    onStopAdvertising = viewModel::stopBlePinAdvertising,
                    onStart = viewModel::startBlePinBruteforce,
                    onPause = viewModel::pauseBlePinBruteforce,
                    onResume = viewModel::resumeBlePinBruteforce,
                    onStop = viewModel::stopBlePinBruteforce,
                    onNext = viewModel::nextBlePinBruteforce,
                    onPrevious = viewModel::previousBlePinBruteforce,
                    onClearSession = viewModel::clearBlePinBruteforceSession,
                    onForgetBond = viewModel::forgetBlePinBond,
                    onDisconnect = viewModel::disconnectBlePinPeer,
                    onRefresh = viewModel::refreshBlePinBruteforce,
                    modifier = Modifier,
                )
            }

            activeModuleId == "sensor_config" -> {
                SensorConfigScreen(
                    connected = isDeviceConnected,
                    ld2450 = ld2450,
                    onRefresh = viewModel::refreshLd2450,
                    onSetLd2450Pins = viewModel::setLd2450Pins,
                    modifier = Modifier,
                )
            }

            activeModuleId == "ld2450" -> {
                Ld2450Screen(
                    connected = isDeviceConnected,
                    state = ld2450,
                    onStart = viewModel::startLd2450,
                    onStop = viewModel::stopLd2450,
                    onRefresh = viewModel::refreshLd2450,
                    onMaxRangeChange = viewModel::setLd2450MaxRange,
                    onZoneAlertChange = viewModel::setLd2450ZoneAlert,
                    onMeshBroadcastChange = viewModel::setLd2450MeshBroadcast,
                    onHeartbeatEnabledChange = viewModel::setLd2450HeartbeatEnabled,
                    onScanDurationChange = viewModel::setLd2450ScanDuration,
                    onSleepDurationChange = viewModel::setLd2450SleepDuration,
                    onLimitDotCountChange = viewModel::setLd2450LimitDotCount,
                    onMaxDotsChange = viewModel::setLd2450MaxDots,
                    modifier = Modifier,
                )
            }

            activeModuleId == "rfid" -> {
                RfidScreen(
                    connected = isDeviceConnected,
                    state = rfid,
                    onStart = viewModel::startRfidScan,
                    onStop = { viewModel.stopRfidScan() },
                    onRefresh = viewModel::refreshRfidStatus,
                    onClearHistory = viewModel::clearRfidHistory,
                    onLeave = { viewModel.stopRfidScan() },
                    modifier = Modifier,
                )
            }

            activeModuleId == "rfid_config" -> {
                RfidConfigScreen(
                    connected = isDeviceConnected,
                    state = rfid,
                    onRefresh = viewModel::refreshRfidStatus,
                    onSave = viewModel::saveRfidConfig,
                    modifier = Modifier,
                )
            }

            activeModuleId == "badusb" -> {
                val badUsbFingerprint = badUsbTargetDevice?.let {
                    viewModel.deviceFingerprintImpl(it.device)
                }
                BadUsbScreen(
                    devices = devices,
                    selectedTarget = badUsbTargetDevice,
                    selectedTargetState = badUsbFingerprint?.let { deviceConnectionStates[it] },
                    targetFingerprint = { viewModel.deviceFingerprintImpl(it) },
                    deviceConnectionStates = deviceConnectionStates,
                    pendingPermissionRequests = pendingPermissionRequests,
                    uploading = badUsbUploading,
                    progress = badUsbProgress,
                    armedFingerprints = badUsbArmedFingerprints,
                    selectedPayloadName = badUsbPayloadName,
                    savedScripts = duckyScripts,
                    onSelectTarget = { device -> viewModel.selectBadUsbTarget(device) },
                    onChooseTarget = { device ->
                        viewModel.selectBadUsbTarget(device)
                        if (usbManager.hasPermission(device)) {
                            viewModel.connect(device)
                        } else {
                            requestPermission(device)
                        }
                    },
                    onUseSavedScript = viewModel::useBadUsbSavedScript,
                    onChoosePayload = { badUsbPicker.launch(arrayOf("text/plain", "*/*")) },
                    onClearPayload = viewModel::clearBadUsbPayload,
                    onArm = viewModel::armBadUsb,
                    modifier = Modifier,
                )
            }

            activeModuleId == "ducky" -> {
                DuckyEditorScreen(
                    savedScripts = duckyScripts,
                    onSaveScript = viewModel::saveDuckyScript,
                    onDeleteScript = viewModel::deleteDuckyScript,
                    modifier = Modifier,
                )
            }

            activeModuleId == "tracker_detector" -> {
                TrackerDetectorScreen(
                    connected = connectionState is ConnectionState.Connected,
                    scanning = trackerScanRunning,
                    trackers = trackerObservations,
                    onStart = { viewModel.startBleScan(true, BleScanOwner.TRACKER_DETECTOR) },
                    onStop = { viewModel.stopBleScan(BleScanOwner.TRACKER_DETECTOR) },
                    onClear = viewModel::clearTrackers,
                    modifier = Modifier,
                )
            }

            activeModuleId == "ble_profile" -> {
                BleProfileScreen(
                    connected = connectionState is ConnectionState.Connected,
                    running = bleProfileRunning,
                    scanStarting = profileScanStarting,
                    scanRunning = profileScanRunning,
                    scanStopping = profileScanStopping,
                    target = bleProfileTarget,
                    devices = bleScanDevices,
                    services = bleProfileServices,
                    status = bleProfileStatus,
                    onSelectTarget = viewModel::selectBleProfileTarget,
                    onStartScan = { viewModel.startBleScan(bleScanActive, BleScanOwner.PROFILE) },
                    onStopScan = { viewModel.stopBleScan(BleScanOwner.PROFILE) },
                    onStart = viewModel::startBleProfile,
                    onStop = viewModel::stopBleProfile,
                    onClear = viewModel::clearBleProfile,
                    modifier = Modifier,
                )
            }

            activeModuleId == "ble_scanner" -> {
                BleScannerScreen(
                    connected = connectionState is ConnectionState.Connected,
                    running = bleScannerScanRunning,
                    starting = bleScannerScanStarting,
                    stopping = bleScannerScanStopping,
                    active = bleScanActive,
                    devices = bleScanDevices,
                    onActiveChange = viewModel::setBleScanActive,
                    onStart = { viewModel.startBleScan(bleScanActive, BleScanOwner.SCANNER) },
                    onStop = { viewModel.stopBleScan(BleScanOwner.SCANNER) },
                    onClear = viewModel::clearBleScan,
                    modifier = Modifier,
                )
            }

            activeModuleId == "ble" -> {
                BleScreen(
                    connected = connectionState is ConnectionState.Connected,
                    advertising = bleAdvertising,
                    bleConnected = bleConnected,
                    peer = blePeer,
                    selectedPayloadName = blePayloadName,
                    savedScripts = duckyScripts,
                    modifiers = bleModifiers,
                    modifierHold = bleModifierHold,
                    bleScriptRunning = bleScriptRunning,
                    onUseSavedScript = viewModel::useBleSavedScript,
                    onChoosePayload = { blePicker.launch(arrayOf("text/plain", "*/*")) },
                    onClearPayload = viewModel::clearBlePayload,
                    onStartAdvertising = viewModel::startBle,
                    onStop = viewModel::stopBle,
                    onRunPayload = viewModel::runBlePayload,
                    onRealtimeInput = viewModel::sendBleRealtimeInput,
                    onSpecialKey = viewModel::sendBleSpecialKey,
                    onModifierChange = viewModel::setBleModifier,
                    onModifierHoldChange = viewModel::setBleModifierHold,
                    onMouseMove = viewModel::sendBleMouseMove,
                    onMouseScroll = viewModel::sendBleMouseScroll,
                    onMouseButton = viewModel::sendBleMouseButton,
                    modifier = Modifier,
                )
            }

            activeModuleId == "devices" -> {
                DeviceScreen(
                    connectionState = connectionState,
                    activeFingerprint = primaryFingerprint,
                    devices = devices,
                    deviceConnectionStates = deviceConnectionStates,
                    deviceFingerprint = { viewModel.deviceFingerprintImpl(it) },
                    pendingPermissionRequests = pendingPermissionRequests,
                    disconnectingFingerprints = disconnectingFingerprints,
                    usbManager = usbManager,
                    firmwareUpdate = firmwareUpdate,
                    firmwareReleases = firmwareReleases,
                    firmwareAssetDownload = firmwareAssetDownload,
                    onDownloadFirmwareUpdate = viewModel::downloadFirmwareUpdate,
                    onOpenFirmwareFlasherForUpdate = {
                        viewModel.prepareFirmwareFlasherForUpdate()
                        viewModel.onModuleOpened("firmware")
                        activeModuleId = "firmware"
                    },
                    onRefresh = viewModel::refreshDevices,
                    onConnect = { device -> connectOrRequestPermission(device) },
                    onDisconnect = viewModel::disconnect,
                    onDisconnectDevice = viewModel::disconnectDevice,
                    onRefreshStatus = viewModel::refreshActiveDeviceStatus,
                    onSwitchDevice = { device ->
                        if (viewModel.switchActiveDevice(device)) {
                            uiScope.launch {
                                snackbarHostState.showSnackbar(
                                    "Active device switched to ${device.productName ?: "USB device"}",
                                )
                            }
                        }
                    },
                    onOpenFirmwareFlasher = { device, chip ->
                        viewModel.prepareFirmwareFlasherForDiagnostic(chip, device?.deviceId)
                        if (device != null) {
                            if (usbManager.hasPermission(device)) {
                                viewModel.selectFirmwareTarget(device)
                            } else {
                                requestFirmwarePermission(device)
                            }
                        }
                        viewModel.onModuleOpened("firmware")
                        activeModuleId = "firmware"
                    },
                    modifier = Modifier,
                )
            }

            activeModuleId == "module_catalog" -> {
                ModulesScreen(
                    modules = liveModules.filterNot { it.id == "ir_config" },
                    onOpenModule = {
                        viewModel.onModuleOpened(it)
                        activeModuleId = it
                    },
                    modifier = Modifier,
                )
            }

            activeModuleId == "mesh" -> {
                MeshScreen(
                    connected = isDeviceConnected,
                    initialized = meshInitialized,
                    role = meshRole,
                    active = meshActive,
                    sessionId = meshSessionId,
                    nodeId = meshNodeId,
                    channel = meshChannel,
                    passphrase = meshPassphrase,
                    keyId = meshKeyId,
                    passphraseMatch = meshPassphraseMatch,
                    checkingPassphrase = meshCheckingPassphrase,
                    provisioning = meshProvisioning,
                    actionInProgress = meshActionInProgress,
                    setupMessage = meshSetupMessage,
                    lastError = meshLastError,
                    hasStoredCredentials = meshHasStoredCredentials,
                    storedKeyId = meshStoredKeyId,
                    hasGlobalCredentials = meshHasGlobalCredentials,
                    globalKeyId = meshGlobalKeyId,
                    nodes = meshNodes,
                    onRefresh = viewModel::refreshMeshStatus,
                    onPassphraseChange = viewModel::setMeshPassphrase,
                    onKeyIdChange = viewModel::setMeshKeyId,
                    onChannelChange = viewModel::setMeshChannel,
                    onCheckPassphrase = viewModel::verifyMeshPassphrase,
                    onProvision = viewModel::provisionMesh,
                    onProvisionWithSavedCredentials = viewModel::provisionMeshWithSavedCredentials,
                    onAuthenticateAndActivate = viewModel::authenticateAndActivateMesh,
                    onAuthenticateStored = viewModel::authenticateAndActivateStoredMesh,
                    onDeactivate = viewModel::deactivateMesh,
                    onApplyChannel = viewModel::changeMeshChannel,
                    onForgetPassphrase = viewModel::forgetMeshPassphrase,
                    onClearKeys = viewModel::clearMeshKeys,
                    onClearNodes = viewModel::clearMeshNodes,
                    onClearSetupMessage = viewModel::clearMeshSetupMessage,
                    onClearError = viewModel::clearMeshError,
                    modifier = Modifier,
                )
            }

            activeModuleId == "serial_monitor" -> {
                SerialMonitorScreen(
                    devices = devices,
                    selectedDevice = serialMonitorSelected,
                    connected = serialMonitorConnected,
                    connecting = serialMonitorConnecting,
                    baudRate = serialMonitorBaud,
                    logLines = serialMonitorLogLines,
                    hexMode = serialMonitorHexMode,
                    inputText = serialMonitorInput,
                    lineEnding = serialMonitorLineEnding,
                    blockingMessage = serialMonitorBlockingMessage,
                    errorMessage = serialMonitorError,
                    permissionRevision = permissionRevision,
                    hasPermission = { usbManager.hasPermission(it.device) },
                    onRefreshDevices = viewModel::refreshDevices,
                    onSelectDevice = viewModel::selectSerialMonitorDevice,
                    onRequestPermission = { device -> requestSerialMonitorPermission(device.device) },
                    onConnect = { device, baud -> viewModel.startSerialMonitor(device, baud) },
                    onDisconnect = viewModel::stopSerialMonitor,
                    onBaudChange = viewModel::setSerialMonitorBaud,
                    onHexModeChange = viewModel::setSerialMonitorHexMode,
                    onInputChange = viewModel::setSerialMonitorInput,
                    onLineEndingChange = viewModel::setSerialMonitorLineEnding,
                    onSend = viewModel::sendSerialMonitorInput,
                    onClearLog = viewModel::clearSerialMonitorLog,
                    onExport = viewModel::exportSerialMonitorLog,
                    modifier = Modifier,
                )
            }

            activeModuleId == "firmware" || activeModuleId == "settings" -> {
                NRSuiteSettingsRoute(
                    connectionState = connectionState,
                    exportDirectoryName = exportDirectoryName,
                    firmwareFileName = firmwareFlashName,
                    firmwareFlashSize = firmwareFlashSize,
                    firmwareFlashing = firmwareFlashing,
                    firmwareFlashProgress = firmwareFlashProgress,
                    firmwareFlashStatus = firmwareFlashStatus,
                    devices = devices,
                    usbManager = usbManager,
                    permissionRevision = permissionRevision,
                    selectedFlashTarget = firmwareTargetDevice,
                    firmwareReleases = firmwareReleases,
                    firmwareAssetDownload = firmwareAssetDownload,
                    firmwareManualRecovery = firmwareManualRecovery,
                    autoUpdateCheck = autoUpdateCheck,
                    updateCheckInProgress = updateCheckInProgress,
                    appUpdate = appUpdate,
                    appUpdateDownload = appUpdateDownload,
                    firmwareUpdate = firmwareUpdate,
                    updateError = updateError,
                    ouiDatabaseStatus = ouiDatabaseStatus,
                    ouiDatabaseProgress = ouiDatabaseProgress,
                    flasherOnly = activeModuleId == "firmware",
                    onChooseExportDirectory = { folderPicker.launch(null) },
                    onChooseFirmware = {
                        firmwarePicker.launch(
                            arrayOf("application/octet-stream", "application/x-binary"),
                        )
                    },
                    onRequestPermission = { device -> requestFirmwarePermission(device) },
                    onOpenFirmwareFlasher = {
                        viewModel.prepareFirmwareFlasherForUpdate()
                        viewModel.onModuleOpened("firmware")
                        activeModuleId = "firmware"
                    },
                    viewModel = viewModel,
                )
            }

            selectedCategory != null && selectedTab == AppTab.HOME -> {
                val categoryModules = when (selectedCategory) {
                    "WiFi" -> liveModules.filter { it.id in WIFI_ALL_MODULE_IDS }
                    "IR" -> liveModules.filter { it.id in IR_CATEGORY_MODULE_IDS }
                    else -> liveModules.filter { it.category == selectedCategory }
                }
                CategoryModulesScreen(
                    modules = categoryModules,
                    onOpenModule = { moduleId ->
                        viewModel.onModuleOpened(moduleId)
                        activeModuleId = moduleId
                    },
                    showWifiFilters = selectedCategory == "WiFi",
                    wifiGroupFilter = wifiGroupFilter,
                    onWifiGroupFilterSelected = { wifiGroupFilter = it },
                    onOpenWifiConfig = { activeModuleId = "wifi_config" },
                    showIrInfo = selectedCategory == "IR",
                    configAccent = when (selectedCategory) {
                        "WiFi" -> categoryColor("WiFi")
                        "IR" -> categoryColor("IR")
                        "RFID" -> categoryColor("RFID")
                        "Sensors" -> categoryColor("Sensors")
                        else -> null
                    },
                    irTxLabel = irTxChipLabel,
                    irRxLabel = irRxChipLabel,
                    irDatabaseState = irDatabaseState,
                    irDatabaseMetadata = irDatabaseMetadata,
                    irDatabasePresent = irDatabasePresent,
                    irRemoteCount = irRemotes.size,
                    irRootConfigured = exportDirectoryName != "Not configured",
                    onChooseIrRootDirectory = {
                        showRootDirectoryDialog = false
                        rootDirectoryPickerActive = true
                        folderPicker.launch(null)
                    },
                    onDownloadIrDatabase = viewModel::downloadIrDatabase,
                    onCancelIrDatabaseOperation = viewModel::cancelIrDatabaseOperation,
                    onRescanIrLibrary = viewModel::refreshIrLibrary,
                    onOpenIrConfig = { activeModuleId = "ir_config" },
                    showRfidInfo = selectedCategory == "RFID",
                    rfidInterfaceLabel = "I2C",
                    rfidSdaLabel = rfid.sdaGpio?.let { "SDA GPIO $it" } ?: "SDA --",
                    rfidSclLabel = rfid.sclGpio?.let { "SCL GPIO $it" } ?: "SCL --",
                    onOpenRfidConfig = { activeModuleId = "rfid_config" },
                    showSensorConfigAction = selectedCategory == "Sensors",
                    onOpenSensorConfig = { activeModuleId = "sensor_config" },
                    modifier = Modifier,
                )
            }

            selectedTab == AppTab.HOME -> HomeScreen(
                connectionState = connectionState,
                permissionRevision = permissionRevision,
                devices = devices,
                deviceConnectionStates = deviceConnectionStates,
                deviceFingerprint = { viewModel.deviceFingerprintImpl(it) },
                pendingPermissionRequests = pendingPermissionRequests,
                disconnectingFingerprints = disconnectingFingerprints,
                usbManager = usbManager,
                modules = liveModules,
                listState = homeListState,
                ouiDatabaseStatus = ouiDatabaseStatus,
                ouiDatabaseProgress = ouiDatabaseProgress,
                appUpdate = appUpdate,
                appUpdateDownload = appUpdateDownload,
                firmwareUpdate = firmwareUpdate,
                firmwareAssetDownload = firmwareAssetDownload,
                onDownloadOuiDatabase = viewModel::downloadOuiDatabase,
                onDownloadAppUpdate = viewModel::downloadAppUpdate,
                onInstallAppUpdate = viewModel::installAppUpdate,
                onDownloadFirmwareUpdate = viewModel::downloadFirmwareUpdate,
                onOpenFirmwareFlasher = {
                    viewModel.prepareFirmwareFlasherForUpdate()
                    viewModel.onModuleOpened("firmware")
                    activeModuleId = "firmware"
                },
                onOpenFirmwareFlasherForDiagnostic = { chip ->
                    viewModel.prepareFirmwareFlasherForDiagnostic(chip)
                    viewModel.onModuleOpened("firmware")
                    activeModuleId = "firmware"
                },
                onOpenModule = {
                    viewModel.onModuleOpened(it)
                    activeModuleId = it
                },
                onOpenCategory = { selectedCategory = it },
                onBrowseAllModules = { activeModuleId = "module_catalog" },
                recentModuleIds = recentModuleIds,
                onRefreshDevices = viewModel::refreshDevices,
                onConnect = { device ->
                    if (usbManager.hasPermission(device)) {
                        viewModel.connect(device)
                    } else {
                        requestPermission(device)
                    }
                },
                onDisconnect = viewModel::disconnect,
                modifier = Modifier,
            )

            selectedTab == AppTab.DEVICES -> DeviceScreen(
                connectionState = connectionState,
                activeFingerprint = primaryFingerprint,
                devices = devices,
                deviceConnectionStates = deviceConnectionStates,
                deviceFingerprint = { viewModel.deviceFingerprintImpl(it) },
                pendingPermissionRequests = pendingPermissionRequests,
                disconnectingFingerprints = disconnectingFingerprints,
                usbManager = usbManager,
                firmwareUpdate = firmwareUpdate,
                firmwareReleases = firmwareReleases,
                firmwareAssetDownload = firmwareAssetDownload,
                onDownloadFirmwareUpdate = viewModel::downloadFirmwareUpdate,
                onOpenFirmwareFlasherForUpdate = {
                    viewModel.prepareFirmwareFlasherForUpdate()
                    viewModel.onModuleOpened("firmware")
                    activeModuleId = "firmware"
                },
                onRefresh = viewModel::refreshDevices,
                onConnect = { device -> connectOrRequestPermission(device) },
                onDisconnect = viewModel::disconnect,
                onDisconnectDevice = viewModel::disconnectDevice,
                onRefreshStatus = viewModel::refreshActiveDeviceStatus,
                onSwitchDevice = { device ->
                    if (viewModel.switchActiveDevice(device)) {
                        uiScope.launch {
                            snackbarHostState.showSnackbar(
                                "Active device switched to ${device.productName ?: "USB device"}",
                            )
                        }
                    }
                },
                onOpenFirmwareFlasher = { device, chip ->
                    viewModel.prepareFirmwareFlasherForDiagnostic(chip, device?.deviceId)
                    if (device != null) {
                        if (usbManager.hasPermission(device)) {
                            viewModel.selectFirmwareTarget(device)
                        } else {
                            requestFirmwarePermission(device)
                        }
                    }
                    viewModel.onModuleOpened("firmware")
                    activeModuleId = "firmware"
                },
                modifier = Modifier,
            )

            selectedTab == AppTab.LOGS -> LogsScreen(
                logs = logs,
                history = history,
                onClearLogs = viewModel::clearLogs,
                onClearHistory = viewModel::clearHistory,
                modifier = Modifier,
            )
            }
            }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NRSuiteTopBar(
    title: String,
    showBack: Boolean,
    backDescription: String,
    onBack: () -> Unit,
    connectionState: ConnectionState,
    hasDevices: Boolean,
    hasPermission: Boolean,
    hasPendingPermissions: Boolean,
    showConnectionStatus: Boolean,
    onConnectionClick: (() -> Unit)?,
    showCatalogAction: Boolean,
    onOpenCatalog: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                if (showBack) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backDescription,
                        )
                    }
                }
            },
            actions = {
                if (showConnectionStatus) {
                    ConnectionStatusIndicator(
                        state = connectionState,
                        hasDevices = hasDevices,
                        hasPermission = hasPermission,
                        hasPendingPermissions = hasPendingPermissions,
                        onClick = onConnectionClick,
                    )
                }
                if (showCatalogAction) {
                    IconButton(onClick = onOpenCatalog) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "All modules",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.background,
            ),
        )
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun NRSuiteBottomBar(
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.30f),
        ),
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
        ) {
            AppTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelectTab(tab) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    icon = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = when (tab) {
                                    AppTab.HOME -> Icons.Default.Home
                                    AppTab.DEVICES -> Icons.Default.Usb
                                    AppTab.LOGS -> Icons.Default.Terminal
                                },
                                contentDescription = tab.label,
                            )
                            Spacer(Modifier.size(2.dp))
                            Box(
                                modifier = Modifier
                                    .size(3.dp)
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.Transparent
                                        },
                                        CircleShape,
                                    ),
                            )
                        }
                    },
                    label = { Text(tab.label) },
                )
            }
        }
    }
}


@Composable
private fun NRSuiteSettingsRoute(
    connectionState: ConnectionState,
    exportDirectoryName: String,
    firmwareFileName: String?,
    firmwareFlashSize: Long,
    firmwareFlashing: Boolean,
    firmwareFlashProgress: Int,
    firmwareFlashStatus: String?,
    devices: List<UsbSerialDevice>,
    usbManager: UsbManager,
    permissionRevision: Int,
    selectedFlashTarget: UsbSerialDevice?,
    firmwareReleases: List<ReleaseInfo>,
    firmwareAssetDownload: FirmwareAssetDownloadState,
    firmwareManualRecovery: Boolean,
    autoUpdateCheck: Boolean,
    updateCheckInProgress: Boolean,
    appUpdate: AppUpdateInfo?,
    appUpdateDownload: AppUpdateDownloadState,
    firmwareUpdate: FirmwareUpdateInfo?,
    updateError: String?,
    ouiDatabaseStatus: OuiDatabaseStatus,
    ouiDatabaseProgress: Float?,
    flasherOnly: Boolean,
    onChooseExportDirectory: () -> Unit,
    onChooseFirmware: () -> Unit,
    onRequestPermission: (UsbDevice) -> Unit,
    onOpenFirmwareFlasher: () -> Unit,
    viewModel: MainViewModel,
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val selectedFlashTargetChip by viewModel.firmwareTargetChip.collectAsState()
    val selectedFlashTargetFirmwareVersion by
        viewModel.firmwareTargetFirmwareVersion.collectAsState()
    SettingsScreen(
        connectionState = connectionState,
        exportDirectoryName = exportDirectoryName,
        firmwareFileName = firmwareFileName,
        firmwareFlashSize = firmwareFlashSize,
        firmwareFlashing = firmwareFlashing,
        firmwareFlashProgress = firmwareFlashProgress,
        firmwareFlashStatus = firmwareFlashStatus,
        devices = devices,
        usbManager = usbManager,
        permissionRevision = permissionRevision,
        selectedFlashTarget = selectedFlashTarget,
        selectedFlashTargetChip = selectedFlashTargetChip,
        selectedFlashTargetFirmwareVersion = selectedFlashTargetFirmwareVersion,
        onChooseExportDirectory = onChooseExportDirectory,
        onChooseFirmware = onChooseFirmware,
        onClearFirmware = viewModel::clearFirmwareFlashFile,
        onRefreshDevices = viewModel::refreshDevices,
        onRequestPermission = onRequestPermission,
        onSelectFlashTarget = viewModel::selectFirmwareTarget,
        onStartFirmwareFlash = viewModel::startFirmwareFlash,
        firmwareReleases = firmwareReleases,
        firmwareAssetDownload = firmwareAssetDownload,
        onDownloadFirmwareAsset = viewModel::downloadFirmwareAsset,
        firmwareManualRecovery = firmwareManualRecovery,
        onArmManualFirmwareRecovery = viewModel::armManualFirmwareRecovery,
        onFlashManualFirmwareNow = viewModel::flashManualFirmwareNow,
        onFlashManualFirmwareWithReset = viewModel::flashManualFirmwareWithReset,
        onDisarmManualFirmwareRecovery = viewModel::disarmManualFirmwareRecovery,
        ouiDatabaseStatus = ouiDatabaseStatus,
        ouiDatabaseProgress = ouiDatabaseProgress,
        onDownloadOuiDatabase = viewModel::downloadOuiDatabase,
        autoUpdateCheck = autoUpdateCheck,
        updateCheckInProgress = updateCheckInProgress,
        appUpdate = appUpdate,
        appUpdateDownload = appUpdateDownload,
        firmwareUpdate = firmwareUpdate,
        updateError = updateError,
        onAutoUpdateCheckChange = viewModel::setAutoUpdateCheck,
        onCheckForUpdates = { viewModel.checkForUpdates(force = true) },
        onAutoCheckForUpdates = { viewModel.checkForUpdates(force = false) },
        onDownloadAppUpdate = viewModel::downloadAppUpdate,
        onInstallAppUpdate = viewModel::installAppUpdate,
        onDownloadFirmwareUpdate = viewModel::downloadFirmwareUpdate,
        onOpenFirmwareFlasher = onOpenFirmwareFlasher,
        onClearUpdateDownloads = viewModel::clearUpdateDownloads,
        themeMode = themeMode,
        onThemeModeChange = viewModel::setThemeMode,
        flasherOnly = flasherOnly,
        modifier = Modifier,
    )
}
