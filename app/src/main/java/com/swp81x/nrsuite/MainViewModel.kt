package com.swp81x.nrsuite

import android.app.Application
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.swp81x.nrsuite.core.credentials.CapturedCredential
import com.swp81x.nrsuite.core.defense.DeauthAlert
import com.swp81x.nrsuite.core.defense.OuiRule
import com.swp81x.nrsuite.core.defense.matchOuiRule
import com.swp81x.nrsuite.core.defense.OuiRuleAction
import com.swp81x.nrsuite.core.defense.NearbyAp
import com.swp81x.nrsuite.core.defense.RogueApAlert
import com.swp81x.nrsuite.core.defense.TrustedNetwork
import com.swp81x.nrsuite.core.defense.DeauthChannelMode
import com.swp81x.nrsuite.core.defense.DeauthFeedEntry
import com.swp81x.nrsuite.core.defense.DeauthFeedFilter
import com.swp81x.nrsuite.core.credentials.CredentialSession
import com.swp81x.nrsuite.core.credentials.CredentialSource
import com.swp81x.nrsuite.core.credentials.CredentialStatus
import com.swp81x.nrsuite.core.credentials.CredentialStore
import com.swp81x.nrsuite.core.eapol.EapolHandshake
import com.swp81x.nrsuite.core.flasher.Esp32Flasher
import com.swp81x.nrsuite.core.flasher.UsbSerialFlasherTransport
import com.swp81x.nrsuite.core.eapol.EapolParser
import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.history.HistoryEntry
import com.swp81x.nrsuite.core.log.LogEntry
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.oui.MacLookupResult
import com.swp81x.nrsuite.core.oui.OuiDatabaseRepository
import com.swp81x.nrsuite.core.oui.OuiDatabaseStatus
import com.swp81x.nrsuite.core.pcap.PcapReader
import com.swp81x.nrsuite.core.pcap.PcapWriter
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.session.NrSession
import com.swp81x.nrsuite.core.wifi.DetectedSsid
import com.swp81x.nrsuite.core.wifi.NetworkTarget
import com.swp81x.nrsuite.core.wifi.PcapSsidParser
import com.swp81x.nrsuite.core.wpa.WpaCracker
import com.swp81x.nrsuite.core.wpa.WpaHandshakeVerifier
import com.swp81x.nrsuite.core.wpa.WpaHandshakeParser
import com.swp81x.nrsuite.core.wpa.WpaHandshake
import com.swp81x.nrsuite.core.wpa.EvilTwinResult
import com.swp81x.nrsuite.core.sniff.SniffRequest
import com.swp81x.nrsuite.core.storage.StorageFile
import com.swp81x.nrsuite.core.usb.UsbSerialDevice
import com.swp81x.nrsuite.core.usb.UsbSerialDeviceCatalog
import com.swp81x.nrsuite.core.usb.UsbSerialTransport
import com.swp81x.nrsuite.service.NrSuiteForegroundService
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class CapturedPassword(
    val value: String,
    val capturedAt: String,
)

/**
 * Application-scoped NRSuite controller.
 *
 * Despite its historical name, this class intentionally does not extend
 * ViewModel. It is owned by [NrSuiteApplication] so the USB bridge, event
 * stream, PCAP collectors, and operation state survive Activity destruction
 * while the foreground service keeps the process alive.
 */
class MainViewModel(internal val app: Application) {
    internal val scope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, error ->
                appendLog(
                    "Unhandled bridge error: ${error.message ?: error.javaClass.simpleName}",
                    level = LogLevel.ERROR,
                )
            },
    )

    internal val usbManager =
        app.getSystemService(Context.USB_SERVICE) as UsbManager
    internal val preferences =
        app.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    internal val _exportDirectory = MutableStateFlow<Uri?>(null)
    val exportDirectory: StateFlow<Uri?> = _exportDirectory.asStateFlow()

    private val _exportDirectoryName = MutableStateFlow("Not configured")
    val exportDirectoryName: StateFlow<String> = _exportDirectoryName.asStateFlow()

    internal val _requiresRootDirectory = MutableStateFlow(false)
    val requiresRootDirectory: StateFlow<Boolean> = _requiresRootDirectory.asStateFlow()

    internal val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    internal val _devices = MutableStateFlow<List<UsbSerialDevice>>(emptyList())
    val devices: StateFlow<List<UsbSerialDevice>> = _devices.asStateFlow()

    internal val _connectionState =
        MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    internal val _firmwareFlashUri = MutableStateFlow<Uri?>(null)
    val firmwareFlashUri: StateFlow<Uri?> = _firmwareFlashUri.asStateFlow()

    internal val _firmwareFlashName = MutableStateFlow<String?>(null)
    val firmwareFlashName: StateFlow<String?> = _firmwareFlashName.asStateFlow()

    internal val _firmwareFlashSize = MutableStateFlow(0L)
    val firmwareFlashSize: StateFlow<Long> = _firmwareFlashSize.asStateFlow()

    internal val _firmwareFlashing = MutableStateFlow(false)
    val firmwareFlashing: StateFlow<Boolean> = _firmwareFlashing.asStateFlow()

    internal val _firmwareFlashProgress = MutableStateFlow(0)
    val firmwareFlashProgress: StateFlow<Int> = _firmwareFlashProgress.asStateFlow()

    internal val _firmwareFlashStatus = MutableStateFlow<String?>(null)
    val firmwareFlashStatus: StateFlow<String?> = _firmwareFlashStatus.asStateFlow()

    internal val _firmwareTargetDevice = MutableStateFlow<UsbSerialDevice?>(null)
    val firmwareTargetDevice: StateFlow<UsbSerialDevice?> = _firmwareTargetDevice.asStateFlow()

    private val _recentModuleIds = MutableStateFlow(
        preferences.getString(PREF_RECENT_MODULES, "")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    )
    val recentModuleIds: StateFlow<List<String>> = _recentModuleIds.asStateFlow()

    private val _events = MutableStateFlow<List<JSONObject>>(emptyList())
    val events: StateFlow<List<JSONObject>> = _events.asStateFlow()

    internal val _networks = MutableStateFlow<List<JSONObject>>(emptyList())
    val networks: StateFlow<List<JSONObject>> = _networks.asStateFlow()

    internal val _ouiRules = MutableStateFlow<List<OuiRule>>(emptyList())
    val ouiRules: StateFlow<List<OuiRule>> = _ouiRules.asStateFlow()

    internal val ouiDatabaseRepository = OuiDatabaseRepository(File(app.filesDir, "oui/oui.csv"))
    val ouiDatabaseStatus: StateFlow<OuiDatabaseStatus> = ouiDatabaseRepository.status
    val ouiDatabaseDownloadProgress: StateFlow<Float?> = ouiDatabaseRepository.progress

    internal val _macLookupResult = MutableStateFlow<MacLookupResult?>(null)
    val macLookupResult: StateFlow<MacLookupResult?> = _macLookupResult.asStateFlow()

    internal val _requiresOuiDatabase = MutableStateFlow(false)
    val requiresOuiDatabase: StateFlow<Boolean> = _requiresOuiDatabase.asStateFlow()

    internal val _trustedNetworks = MutableStateFlow<List<TrustedNetwork>>(emptyList())
    val trustedNetworks: StateFlow<List<TrustedNetwork>> = _trustedNetworks.asStateFlow()

    internal val _rogueApRunning = MutableStateFlow(false)
    val rogueApRunning: StateFlow<Boolean> = _rogueApRunning.asStateFlow()

    internal val _rogueApScanning = MutableStateFlow(false)
    val rogueApScanning: StateFlow<Boolean> = _rogueApScanning.asStateFlow()

    internal val _rogueApAlerts = MutableStateFlow<List<RogueApAlert>>(emptyList())
    val rogueApAlerts: StateFlow<List<RogueApAlert>> = _rogueApAlerts.asStateFlow()

    internal val _rogueApNearby = MutableStateFlow<List<NearbyAp>>(emptyList())
    val rogueApNearby: StateFlow<List<NearbyAp>> = _rogueApNearby.asStateFlow()

    internal val _rogueApLastScanAt = MutableStateFlow<String?>(null)
    val rogueApLastScanAt: StateFlow<String?> = _rogueApLastScanAt.asStateFlow()

    internal var rogueApScanJob: Job? = null

    internal val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    internal val _sniffing = MutableStateFlow(false)
    val sniffing: StateFlow<Boolean> = _sniffing.asStateFlow()

    internal val _sniffPacketCount = MutableStateFlow(0L)
    val sniffPacketCount: StateFlow<Long> = _sniffPacketCount.asStateFlow()

    internal val _sniffHandshake = MutableStateFlow(EapolHandshake())
    val sniffHandshake: StateFlow<EapolHandshake> = _sniffHandshake.asStateFlow()

    internal val _capturePath = MutableStateFlow<String?>(null)
    val capturePath: StateFlow<String?> = _capturePath.asStateFlow()

    internal val _beaconRunning = MutableStateFlow(false)
    val beaconRunning: StateFlow<Boolean> = _beaconRunning.asStateFlow()

    internal val _beaconSent = MutableStateFlow(0)
    val beaconSent: StateFlow<Int> = _beaconSent.asStateFlow()

    internal val _beaconSsidCount = MutableStateFlow(0)
    val beaconSsidCount: StateFlow<Int> = _beaconSsidCount.asStateFlow()

    internal val _beaconChannel = MutableStateFlow(0)
    val beaconChannel: StateFlow<Int> = _beaconChannel.asStateFlow()

    internal val _beaconListMap = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val beaconListMap: StateFlow<Map<String, List<String>>> = _beaconListMap.asStateFlow()

    internal var beaconStatusJob: Job? = null

    internal val _deauthRunning = MutableStateFlow(false)
    val deauthRunning: StateFlow<Boolean> = _deauthRunning.asStateFlow()

    internal val _deauthSent = MutableStateFlow(0)
    val deauthSent: StateFlow<Int> = _deauthSent.asStateFlow()

    internal val _deauthTarget = MutableStateFlow("")
    val deauthTarget: StateFlow<String> = _deauthTarget.asStateFlow()

    internal val _deauthChannel = MutableStateFlow(0)
    val deauthChannel: StateFlow<Int> = _deauthChannel.asStateFlow()

    internal val _deauthDetectorRunning = MutableStateFlow(false)
    val deauthDetectorRunning: StateFlow<Boolean> = _deauthDetectorRunning.asStateFlow()

    internal val _deauthDetectorChannel = MutableStateFlow(6)
    val deauthDetectorChannel: StateFlow<Int> = _deauthDetectorChannel.asStateFlow()

    internal val _deauthDetectorActiveAlert = MutableStateFlow<DeauthAlert?>(null)
    val deauthDetectorActiveAlert: StateFlow<DeauthAlert?> = _deauthDetectorActiveAlert.asStateFlow()

    internal val _deauthDetectorFeed = MutableStateFlow<List<DeauthFeedEntry>>(emptyList())
    val deauthDetectorFeed: StateFlow<List<DeauthFeedEntry>> = _deauthDetectorFeed.asStateFlow()

    internal val _deauthDetectorFeedFilter = MutableStateFlow(DeauthFeedFilter.ALL)
    val deauthDetectorFeedFilter: StateFlow<DeauthFeedFilter> = _deauthDetectorFeedFilter.asStateFlow()

    internal val _deauthDetectorChannelMode = MutableStateFlow(DeauthChannelMode.TARGETED)
    val deauthDetectorChannelMode: StateFlow<DeauthChannelMode> = _deauthDetectorChannelMode.asStateFlow()

    internal val _deauthDetectorHopIntervalMs = MutableStateFlow(300)
    val deauthDetectorHopIntervalMs: StateFlow<Int> = _deauthDetectorHopIntervalMs.asStateFlow()

    internal val _deauthDetectorCurrentHopChannel = MutableStateFlow<Int?>(null)
    val deauthDetectorCurrentHopChannel: StateFlow<Int?> = _deauthDetectorCurrentHopChannel.asStateFlow()

    internal val _deauthDetectorFramesPerSecond = MutableStateFlow(0)
    val deauthDetectorFramesPerSecond: StateFlow<Int> = _deauthDetectorFramesPerSecond.asStateFlow()

    internal val _deauthDetectorTotalFrames = MutableStateFlow(0)
    val deauthDetectorTotalFrames: StateFlow<Int> = _deauthDetectorTotalFrames.asStateFlow()

    internal val _deauthDetectorUniqueSourceCount = MutableStateFlow(0)
    val deauthDetectorUniqueSourceCount: StateFlow<Int> = _deauthDetectorUniqueSourceCount.asStateFlow()

    internal val _deauthDetectorTargets = MutableStateFlow<List<NetworkTarget>>(emptyList())
    val deauthDetectorTargets: StateFlow<List<NetworkTarget>> = _deauthDetectorTargets.asStateFlow()

    internal val _deauthDetectorSelectedTarget = MutableStateFlow<NetworkTarget?>(null)
    val deauthDetectorSelectedTarget: StateFlow<NetworkTarget?> = _deauthDetectorSelectedTarget.asStateFlow()

    internal val deauthDetectorFrameTimestamps = ArrayDeque<Long>()
    internal val deauthDetectorSources = mutableSetOf<String>()
    internal val deauthDetectorWindowTargets = ArrayDeque<String>()
    internal val deauthDetectorWindowReasons = ArrayDeque<Int>()
    internal val deauthDetectorWindowSources = ArrayDeque<String>()
    internal val deauthDetectorSourceLatestRssi = mutableMapOf<String, Int>()
    internal var deauthDetectorAlertStartedAtMs: Long? = null
    internal var deauthDetectorLastEventAtMs: Long = 0L
    internal var deauthAlertClearJob: Job? = null
    internal var deauthFpsResetJob: Job? = null

    internal val _portalRunning = MutableStateFlow(false)
    val portalRunning: StateFlow<Boolean> = _portalRunning.asStateFlow()

    internal val _portalHtmlSize = MutableStateFlow(0)
    val portalHtmlSize: StateFlow<Int> = _portalHtmlSize.asStateFlow()

    internal val _portalHtmlComplete = MutableStateFlow(false)
    val portalHtmlComplete: StateFlow<Boolean> = _portalHtmlComplete.asStateFlow()

    internal val _portalHtmlUploading = MutableStateFlow(false)
    val portalHtmlUploading: StateFlow<Boolean> = _portalHtmlUploading.asStateFlow()

    internal val _portalHtmlUploadProgress = MutableStateFlow(0)
    val portalHtmlUploadProgress: StateFlow<Int> = _portalHtmlUploadProgress.asStateFlow()

    internal val _evilTwinHtmlUploading = MutableStateFlow(false)
    val evilTwinHtmlUploading: StateFlow<Boolean> = _evilTwinHtmlUploading.asStateFlow()

    internal val _evilTwinHtmlUploadProgress = MutableStateFlow(0)
    val evilTwinHtmlUploadProgress: StateFlow<Int> = _evilTwinHtmlUploadProgress.asStateFlow()

    internal val _evilTwinHtmlComplete = MutableStateFlow(false)
    val evilTwinHtmlComplete: StateFlow<Boolean> = _evilTwinHtmlComplete.asStateFlow()

    internal val _portalSsid = MutableStateFlow("")
    val portalSsid: StateFlow<String> = _portalSsid.asStateFlow()

    internal val _portalChannel = MutableStateFlow(0)
    val portalChannel: StateFlow<Int> = _portalChannel.asStateFlow()

    internal val _portalViews = MutableStateFlow(0)
    val portalViews: StateFlow<Int> = _portalViews.asStateFlow()

    internal val _portalClients = MutableStateFlow(0)
    val portalClients: StateFlow<Int> = _portalClients.asStateFlow()

    internal val _portalCapturedData = MutableStateFlow(0)
    val portalCapturedData: StateFlow<Int> = _portalCapturedData.asStateFlow()

    internal val _portalCredentials = MutableStateFlow<List<CapturedCredential>>(emptyList())
    val portalCredentials: StateFlow<List<CapturedCredential>> = _portalCredentials.asStateFlow()

    internal val _portalHtmlUri = MutableStateFlow<Uri?>(null)
    val portalHtmlUri: StateFlow<Uri?> = _portalHtmlUri.asStateFlow()

    internal val _portalHtmlName = MutableStateFlow<String?>(null)
    val portalHtmlName: StateFlow<String?> = _portalHtmlName.asStateFlow()

    internal val _portalMode = MutableStateFlow<String?>(null)
    val portalMode: StateFlow<String?> = _portalMode.asStateFlow()

    internal val _portalEventLog = MutableStateFlow<List<String>>(emptyList())
    val portalEventLog: StateFlow<List<String>> = _portalEventLog.asStateFlow()

    internal val _evilTwinEventLog = MutableStateFlow<List<String>>(emptyList())
    val evilTwinEventLog: StateFlow<List<String>> = _evilTwinEventLog.asStateFlow()

    internal val _evilTwinHtmlUri = MutableStateFlow<Uri?>(null)
    internal val _evilTwinHtmlName = MutableStateFlow<String?>(null)
    val evilTwinHtmlName: StateFlow<String?> = _evilTwinHtmlName.asStateFlow()

    internal val _portalHandshake = MutableStateFlow(EapolHandshake())
    val portalHandshake: StateFlow<EapolHandshake> = _portalHandshake.asStateFlow()

    internal val _evilTwinPasswords = MutableStateFlow<List<CapturedPassword>>(emptyList())
    val evilTwinPasswords: StateFlow<List<CapturedPassword>> = _evilTwinPasswords.asStateFlow()

    internal val _portalWpaHandshake = MutableStateFlow(WpaHandshake())
    val portalWpaHandshake: StateFlow<WpaHandshake> = _portalWpaHandshake.asStateFlow()

    internal val _evilTwinResults = MutableStateFlow<List<EvilTwinResult>>(emptyList())
    val evilTwinResults: StateFlow<List<EvilTwinResult>> = _evilTwinResults.asStateFlow()

    internal val credentialStore = CredentialStore(File(app.filesDir, "Credentials"))
    internal val _credentialSessions = MutableStateFlow<List<CredentialSession>>(emptyList())
    val credentialSessions: StateFlow<List<CredentialSession>> = _credentialSessions.asStateFlow()
    internal val credentialLock = Any()
    internal var activeCredentialSession: CredentialSession? = null
    internal var evilTwinAutoStopIssued = false
    internal var activePortalTargetBssid: String = ""

    internal val _crackerSelectedSession = MutableStateFlow<CredentialSession?>(null)
    val crackerSelectedSession: StateFlow<CredentialSession?> = _crackerSelectedSession.asStateFlow()

    internal val _crackerWordlistUri = MutableStateFlow<Uri?>(null)
    val crackerWordlistUri: StateFlow<Uri?> = _crackerWordlistUri.asStateFlow()

    internal val _crackerWordlistName = MutableStateFlow<String?>(null)
    val crackerWordlistName: StateFlow<String?> = _crackerWordlistName.asStateFlow()

    internal val _crackerRunning = MutableStateFlow(false)
    val crackerRunning: StateFlow<Boolean> = _crackerRunning.asStateFlow()

    internal val _crackerTested = MutableStateFlow(0L)
    val crackerTested: StateFlow<Long> = _crackerTested.asStateFlow()

    internal val _crackerSpeed = MutableStateFlow(0.0)
    val crackerSpeed: StateFlow<Double> = _crackerSpeed.asStateFlow()

    internal val _crackerResult = MutableStateFlow<String?>(null)
    val crackerResult: StateFlow<String?> = _crackerResult.asStateFlow()

    internal val _crackerStatus = MutableStateFlow("Select a saved handshake and a wordlist.")
    val crackerStatus: StateFlow<String> = _crackerStatus.asStateFlow()

    internal val _crackerCustomPcapUri = MutableStateFlow<Uri?>(null)
    val crackerCustomPcapUri: StateFlow<Uri?> = _crackerCustomPcapUri.asStateFlow()

    internal val _crackerCustomPcapName = MutableStateFlow<String?>(null)
    val crackerCustomPcapName: StateFlow<String?> = _crackerCustomPcapName.asStateFlow()

    internal val _crackerCustomSsid = MutableStateFlow("")
    val crackerCustomSsid: StateFlow<String> = _crackerCustomSsid.asStateFlow()

    internal val _crackerCustomPcapValid = MutableStateFlow(false)
    val crackerCustomPcapValid: StateFlow<Boolean> = _crackerCustomPcapValid.asStateFlow()

    internal val _crackerCustomPcapValidating = MutableStateFlow(false)
    val crackerCustomPcapValidating: StateFlow<Boolean> = _crackerCustomPcapValidating.asStateFlow()

    internal val _crackerCustomPcapMessage = MutableStateFlow("No custom PCAP selected.")
    val crackerCustomPcapMessage: StateFlow<String> = _crackerCustomPcapMessage.asStateFlow()

    internal val _crackerCustomSsidOptions = MutableStateFlow<List<DetectedSsid>>(emptyList())
    val crackerCustomSsidOptions: StateFlow<List<DetectedSsid>> = _crackerCustomSsidOptions.asStateFlow()

    internal val _crackerCustomSsidSelected = MutableStateFlow<String?>(null)
    val crackerCustomSsidSelected: StateFlow<String?> = _crackerCustomSsidSelected.asStateFlow()

    internal val _crackerCustomSsidManual = MutableStateFlow(true)
    val crackerCustomSsidManual: StateFlow<Boolean> = _crackerCustomSsidManual.asStateFlow()

    internal var crackerJob: Job? = null

    internal val _evilTwinCapturePath = MutableStateFlow<String?>(null)
    val evilTwinCapturePath: StateFlow<String?> = _evilTwinCapturePath.asStateFlow()

    internal var portalPcapJob: Job? = null
    internal var portalPcapWriter: PcapWriter? = null
    internal var portalPcapFile: File? = null
    private val exportedCapturePaths = mutableSetOf<String>()

    internal var portalStatusJob: Job? = null

    internal val _storageFiles = MutableStateFlow<List<StorageFile>>(emptyList())
    val storageFiles: StateFlow<List<StorageFile>> = _storageFiles.asStateFlow()

    internal val _storageTotal = MutableStateFlow(0L)
    val storageTotal: StateFlow<Long> = _storageTotal.asStateFlow()

    internal val _storageUsed = MutableStateFlow(0L)
    val storageUsed: StateFlow<Long> = _storageUsed.asStateFlow()

    internal val _storageFree = MutableStateFlow(0L)
    val storageFree: StateFlow<Long> = _storageFree.asStateFlow()

    internal val _storageLoading = MutableStateFlow(false)
    val storageLoading: StateFlow<Boolean> = _storageLoading.asStateFlow()

    internal val _badUsbPayloadUri = MutableStateFlow<Uri?>(null)
    val badUsbPayloadUri: StateFlow<Uri?> = _badUsbPayloadUri.asStateFlow()

    internal val _badUsbPayloadName = MutableStateFlow<String?>(null)
    val badUsbPayloadName: StateFlow<String?> = _badUsbPayloadName.asStateFlow()

    internal val _badUsbSavedScriptText = MutableStateFlow<String?>(null)

    internal val _bleSavedScriptText = MutableStateFlow<String?>(null)

    internal val _duckyScriptMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val duckyScriptMap: StateFlow<Map<String, String>> = _duckyScriptMap.asStateFlow()

    internal val _badUsbUploading = MutableStateFlow(false)
    val badUsbUploading: StateFlow<Boolean> = _badUsbUploading.asStateFlow()

    internal val _badUsbProgress = MutableStateFlow(0)
    val badUsbProgress: StateFlow<Int> = _badUsbProgress.asStateFlow()

    internal val _bleAdvertising = MutableStateFlow(false)
    val bleAdvertising: StateFlow<Boolean> = _bleAdvertising.asStateFlow()

    internal val _bleConnected = MutableStateFlow(false)
    val bleConnected: StateFlow<Boolean> = _bleConnected.asStateFlow()

    internal val _blePeer = MutableStateFlow("")
    val blePeer: StateFlow<String> = _blePeer.asStateFlow()

    internal val _blePayloadUri = MutableStateFlow<Uri?>(null)
    val blePayloadUri: StateFlow<Uri?> = _blePayloadUri.asStateFlow()

    internal val _blePayloadName = MutableStateFlow<String?>(null)
    val blePayloadName: StateFlow<String?> = _blePayloadName.asStateFlow()

    internal val _bleModifiers = MutableStateFlow<Set<String>>(emptySet())
    val bleModifiers: StateFlow<Set<String>> = _bleModifiers.asStateFlow()

    internal val _bleModifierHold = MutableStateFlow(false)
    val bleModifierHold: StateFlow<Boolean> = _bleModifierHold.asStateFlow()

    internal val _bleScriptRunning = MutableStateFlow(false)
    val bleScriptRunning: StateFlow<Boolean> = _bleScriptRunning.asStateFlow()

    internal val bleTypeBuffer = StringBuilder()
    internal var bleTypeJob: Job? = null

    internal var bleStatusJob: Job? = null

    internal var session: NrSession? = null
    internal var activeSerialDevice: UsbSerialDevice? = null
    internal var activeDeviceFingerprint: String? = null
    internal var sessionObservers: List<Job> = emptyList()
    internal var pcapWriter: PcapWriter? = null
    internal var pcapJob: Job? = null

    init {
        loadExportDirectory()
        this.loadBeaconListsImpl()
        this.loadOuiRulesImpl()
        this.loadOuiDatabaseImpl()
        this.loadTrustedNetworksImpl()
        this.loadDuckyScriptsImpl()
        loadHistory()
        this.loadCredentialSessionsImpl()
        this.refreshDevicesImpl()
    }

    fun onModuleOpened(moduleId: String) {
        val current = _recentModuleIds.value.toMutableList()
        current.remove(moduleId)
        current.add(0, moduleId)
        val trimmed = current.take(3)
        _recentModuleIds.value = trimmed
        preferences.edit().putString(PREF_RECENT_MODULES, trimmed.joinToString(",")).apply()
        clearScanResultsForModule(moduleId)
    }

    private fun clearScanResultsForModule(moduleId: String) {
        when (moduleId) {
            "wifi", "sniff", "deauth", "evil_twin" -> {
                _networks.value = emptyList()
                _deauthDetectorTargets.value = emptyList()
            }

            "deauth_detector" -> {
                if (!_deauthDetectorRunning.value) {
                    _deauthDetectorTargets.value = emptyList()
                    _deauthDetectorSelectedTarget.value = null
                }
            }

            "rogue_ap" -> {
                _rogueApNearby.value = emptyList()
            }
        }
    }

    fun saveBeaconList(name: String, ssids: List<String>) = this.saveBeaconListImpl(name, ssids)

    fun deleteBeaconList(name: String) = this.deleteBeaconListImpl(name)

    fun addOuiRule(ouiPrefix: String, label: String, action: OuiRuleAction) =
        this.addOuiRuleImpl(ouiPrefix, label, action)

    fun deleteOuiRule(id: String) = this.deleteOuiRuleImpl(id)

    fun downloadOuiDatabase() = this.downloadOuiDatabaseImpl()

    fun lookupMac(mac: String) = this.lookupMacImpl(mac)

    fun clearMacLookup() {
        _macLookupResult.value = null
    }

    fun onOuiDatabasePromptShown() = this.onOuiDatabasePromptShownImpl()

    fun captureRogueApBaseline() = this.captureRogueApBaselineImpl()

    fun clearRogueApBaseline() = this.clearRogueApBaselineImpl()

    fun startRogueApDetector() = this.startRogueApDetectorImpl()

    fun stopRogueApDetector() = this.stopRogueApDetectorImpl()

    fun clearRogueApAlerts() {
        _rogueApAlerts.value = emptyList()
    }



    fun onRootDirectoryPromptShown() {
        _requiresRootDirectory.value = false
    }

    fun setExportDirectory(uri: Uri) {
        val resolver = app.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { resolver.takePersistableUriPermission(uri, flags) }
        _exportDirectory.value = uri
        preferences.edit().putString(PREF_EXPORT_DIRECTORY, uri.toString()).apply()
        _exportDirectoryName.value = displayNameForTreeUri(uri)
        appendLog("Capture export folder: ${_exportDirectoryName.value}")
        ensureRootStructure(uri)
    }

    private fun loadExportDirectory() {
        val stored = preferences.getString(PREF_EXPORT_DIRECTORY, null) ?: return
        val uri = runCatching { Uri.parse(stored) }.getOrNull() ?: return
        _exportDirectory.value = uri
        _exportDirectoryName.value = displayNameForTreeUri(uri)
        ensureRootStructure(uri)
    }

    fun refreshDevices() = this.refreshDevicesImpl()

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun clearHistory() {
        _history.value = emptyList()
        persistHistory()
    }

    fun addHistory(module: String, summary: String, level: HistoryLevel = HistoryLevel.INFO) {
        val entry = HistoryEntry(
            id = System.currentTimeMillis(),
            timestamp = java.time.LocalTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")),
            module = module,
            summary = summary,
            level = level,
        )
        _history.update { (listOf(entry) + it).take(300) }
        persistHistory()
    }

    private fun loadHistory() {
        val raw = preferences.getString(PREF_HISTORY, null) ?: return
        runCatching {
            val array = org.json.JSONArray(raw)
            val entries = mutableListOf<HistoryEntry>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                entries += HistoryEntry(
                    id = obj.optLong("id"),
                    timestamp = obj.optString("timestamp"),
                    module = obj.optString("module"),
                    summary = obj.optString("summary"),
                    level = runCatching {
                        HistoryLevel.valueOf(obj.optString("level"))
                    }.getOrDefault(HistoryLevel.INFO),
                )
            }
            _history.value = entries
        }
    }

    private fun persistHistory() {
        val array = org.json.JSONArray()
        _history.value.forEach { entry ->
            array.put(org.json.JSONObject().apply {
                put("id", entry.id)
                put("timestamp", entry.timestamp)
                put("module", entry.module)
                put("summary", entry.summary)
                put("level", entry.level.name)
            })
        }
        preferences.edit().putString(PREF_HISTORY, array.toString()).apply()
    }

    fun onUsbDeviceAttached() = this.onUsbDeviceAttachedImpl()


    fun onUsbDeviceDetached(device: UsbDevice) = this.onUsbDeviceDetachedImpl(device)

    fun hasPermission(device: UsbDevice): Boolean = this.hasPermissionImpl(device)

    fun onPermissionResult(device: UsbDevice, granted: Boolean) = this.onPermissionResultImpl(device, granted)

    fun connect(device: UsbDevice) = this.connectImpl(device)

    fun scanWifi() = this.scanWifiImpl()

    fun setBadUsbPayload(uri: Uri, name: String?) = this.setBadUsbPayloadImpl(uri, name)

    fun clearBadUsbPayload() = this.clearBadUsbPayloadImpl()

    fun saveDuckyScript(name: String, script: String) = this.saveDuckyScriptImpl(name, script)

    fun deleteDuckyScript(name: String) = this.deleteDuckyScriptImpl(name)

    fun useBadUsbSavedScript(name: String) = this.useBadUsbSavedScriptImpl(name)

    fun useBleSavedScript(name: String) = this.useBleSavedScriptImpl(name)



    fun armBadUsb(mscMode: Boolean) = this.armBadUsbImpl(mscMode)

    fun refreshStorage() = this.refreshStorageImpl()

    fun deleteStorageFile(name: String) = this.deleteStorageFileImpl(name)

    fun startMassStorage() = this.startMassStorageImpl()

    fun setEvilTwinHtmlFile(uri: Uri, name: String?) = this.setEvilTwinHtmlFileImpl(uri, name)

    fun clearEvilTwinHtmlFile() = this.clearEvilTwinHtmlFileImpl()

    fun setFirmwareFlashFile(uri: Uri, name: String?) = this.setFirmwareFlashFileImpl(uri, name)


    fun clearFirmwareFlashFile() = this.clearFirmwareFlashFileImpl()

    fun selectFirmwareTarget(device: UsbDevice) = this.selectFirmwareTargetImpl(device)

    fun startFirmwareFlash(targetChip: String, skipReset: Boolean) = this.startFirmwareFlashImpl(targetChip, skipReset)

    internal fun stopActiveOperations() {
        finishActiveCredentialSession()
        beaconStatusJob?.cancel()
        beaconStatusJob = null
        portalStatusJob?.cancel()
        portalStatusJob = null
        portalPcapJob?.cancel()
        portalPcapJob = null
        pcapJob?.cancel()
        pcapJob = null
        bleStatusJob?.cancel()
        bleStatusJob = null

        _beaconRunning.value = false
        _portalRunning.value = false
        _sniffing.value = false
        _deauthRunning.value = false
        _deauthDetectorRunning.value = false
        rogueApScanJob?.cancel()
        rogueApScanJob = null
        _rogueApRunning.value = false
        _rogueApScanning.value = false
        deauthAlertClearJob?.cancel()
        deauthAlertClearJob = null
        deauthFpsResetJob?.cancel()
        deauthFpsResetJob = null
        _deauthDetectorActiveAlert.value = null
        _deauthDetectorCurrentHopChannel.value = null
        _deauthDetectorFramesPerSecond.value = 0
        _bleAdvertising.value = false
        _bleConnected.value = false
        _bleScriptRunning.value = false
        this.clearBleRealtimeStateImpl()
        _portalMode.value = null

        val captureToExport = portalPcapFile
        runCatching { portalPcapWriter?.close() }
        portalPcapWriter = null
        portalPcapFile = null
        exportPcapToRootIfConfigured(captureToExport)
        runCatching { pcapWriter?.close() }
        pcapWriter = null
        _evilTwinCapturePath.value = null
        updateForegroundService()
    }

    fun setPortalHtmlFile(uri: Uri, name: String?) = this.setPortalHtmlFileImpl(uri, name)

    fun clearPortalHtmlFile() = this.clearPortalHtmlFileImpl()

    fun startPortal(ssid: String, channel: Int, targetBssid: String) = this.startPortalImpl(ssid, channel, targetBssid)

    fun startEvilTwin(ssid: String, channel: Int, targetBssid: String) = this.startEvilTwinImpl(ssid, channel, targetBssid)







    fun clearEvilTwinPasswords() = this.clearEvilTwinPasswordsImpl()



    internal fun timestampNow(): String =
        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    internal fun timeHmNow(): String =
        java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))



    internal fun upsertCredentialSessionLocked(session: CredentialSession) {
        val updated = (_credentialSessions.value.filterNot { it.id == session.id } + session)
            .sortedByDescending { it.startedAt }
        _credentialSessions.value = updated
        scope.launch(Dispatchers.IO) {
            runCatching { credentialStore.saveSessions(updated) }
        }
    }

    private fun finishActiveCredentialSessionLocked() {
        val session = activeCredentialSession ?: return
        activeCredentialSession = null

        // Portal/rogue-AP sessions can capture a lot of data, so only persist
        // them when at least one submission was actually captured.
        if (session.source == CredentialSource.PORTAL && session.credentials.isEmpty()) {
            return
        }

        val finished = session.copy(
            endedAt = session.endedAt ?: timestampNow(),
            pcapPath = session.pcapPath ?: portalPcapFile?.absolutePath,
        )
        upsertCredentialSessionLocked(finished)
    }

    internal fun finishActiveCredentialSession() {
        synchronized(credentialLock) {
            finishActiveCredentialSessionLocked()
        }
    }

    fun deleteCredentialSession(id: String) = this.deleteCredentialSessionImpl(id)

    fun clearCredentialSessions() = this.clearCredentialSessionsImpl()

    fun selectCrackerSession(session: CredentialSession?) = this.selectCrackerSessionImpl(session)

    fun setCrackerCustomPcap(uri: Uri, name: String?) = this.setCrackerCustomPcapImpl(uri, name)

    fun selectCrackerCustomSsid(ssid: String) = this.selectCrackerCustomSsidImpl(ssid)

    fun useManualCrackerSsid() = this.useManualCrackerSsidImpl()

    fun setCrackerCustomSsid(value: String) = this.setCrackerCustomSsidImpl(value)

    fun clearCrackerCustomPcap() = this.clearCrackerCustomPcapImpl()


    fun setCrackerWordlist(uri: Uri, name: String?) = this.setCrackerWordlistImpl(uri, name)

    fun clearCrackerWordlist() = this.clearCrackerWordlistImpl()

    fun startCracker() = this.startCrackerImpl()

    fun stopCracker() = this.stopCrackerImpl()

    fun clearEvilTwinEventLog() = this.clearEvilTwinEventLogImpl()

    fun clearPortalEventLog() = this.clearPortalEventLogImpl()


    fun stopPortal() = this.stopPortalImpl()


    fun setBlePayload(uri: Uri, name: String?) = this.setBlePayloadImpl(uri, name)

    fun clearBlePayload() = this.clearBlePayloadImpl()

    fun startBle(advertiseName: String) = this.startBleImpl(advertiseName)

    fun stopBle() = this.stopBleImpl()

    fun runBlePayload() = this.runBlePayloadImpl()

    fun sendBleKeyboardText(text: String) = this.sendBleKeyboardTextImpl(text)

    /**
     * Realtime keyboard stream. [backspaces] are emitted first, then [inserted]
     * text. Firmware's Print::write handles '\b' as backspace and '\n' as Enter.
     */
    fun sendBleRealtimeInput(inserted: String, backspaces: Int) = this.sendBleRealtimeInputImpl(inserted, backspaces)

    fun sendBleSpecialKey(key: String) = this.sendBleSpecialKeyImpl(key)

    fun setBleModifierHold(enabled: Boolean) = this.setBleModifierHoldImpl(enabled)

    fun sendBleMouseMove(dx: Int, dy: Int) = this.sendBleMouseMoveImpl(dx, dy)

    fun sendBleMouseScroll(wheel: Int) = this.sendBleMouseScrollImpl(wheel)

    fun sendBleMouseButton(button: String, down: Boolean) = this.sendBleMouseButtonImpl(button, down)

    fun releaseBleMouseButtons() = this.releaseBleMouseButtonsImpl()


    fun setBleModifier(key: String, down: Boolean) = this.setBleModifierImpl(key, down)





    fun startDeauth(bssid: String, channel: Int, client: String, count: Int, duration: Int, intervalMs: Int) = this.startDeauthImpl(bssid, channel, client, count, duration, intervalMs)

    fun startDeauthDetector() = this.startDeauthDetectorImpl()

    fun stopDeauthDetector() = this.stopDeauthDetectorImpl()

    fun clearDeauthDetectorFeed() {
        _deauthDetectorFeed.value = emptyList()
    }

    fun setDeauthDetectorFeedFilter(filter: DeauthFeedFilter) {
        _deauthDetectorFeedFilter.value = filter
    }

    fun setDeauthDetectorChannel(channel: Int) {
        _deauthDetectorChannel.value = channel.coerceIn(1, 14)
    }

    fun setDeauthDetectorChannelMode(mode: DeauthChannelMode) {
        _deauthDetectorChannelMode.value = mode
    }

    fun setDeauthDetectorHopIntervalMs(intervalMs: Int) {
        _deauthDetectorHopIntervalMs.value = intervalMs.coerceIn(100, 2_000)
    }

    fun selectDeauthDetectorTarget(target: NetworkTarget?) {
        _deauthDetectorSelectedTarget.value = target
        target?.let { _deauthDetectorChannel.value = it.channel.coerceIn(1, 14) }
    }

    fun startBeacon(ssids: List<String>, channel: Int, intervalMs: Int, hidden: Boolean, randomBssid: Boolean) = this.startBeaconImpl(ssids, channel, intervalMs, hidden, randomBssid)

    fun stopBeacon() = this.stopBeaconImpl()


    fun startSniff(request: SniffRequest) = this.startSniffImpl(request)

    fun stopSniff() = this.stopSniffImpl()

    internal fun activeRadioLabel(includeBle: Boolean = true): String? {
        if (includeBle && (_bleAdvertising.value || _bleConnected.value)) {
            return "BLE HID"
        }
        return when {
            _portalRunning.value -> {
                if (_portalMode.value == "evil_twin") "Evil Twin" else "Captive Portal"
            }
            _sniffing.value -> "Packet Sniffer"
            _beaconRunning.value -> "Beacon Broadcast"
            _deauthRunning.value -> "Deauthentication"
            _deauthDetectorRunning.value -> "Deauth Detector"
            _rogueApRunning.value -> "Rogue AP Detector"
            _rogueApScanning.value -> "Rogue AP baseline scan"
            _scanning.value -> "WiFi Scan"
            else -> null
        }
    }

    internal fun ensureRadioIdle(requested: String): Boolean {
        val active = activeRadioLabel()
        if (active == null) return true
        val message = "Cannot start $requested while $active is active. Stop $active first."
        appendLog(message, level = LogLevel.ERROR)
        _actionError.value = message
        return false
    }

    fun consumeActionError() {
        _actionError.value = null
    }

    internal fun updateForegroundService() {
        val context = app
        val activeText = when {
            _sniffing.value -> "Packet capture active"
            _beaconRunning.value -> "Beacon broadcast active"
            _portalRunning.value -> "Captive portal active"
            _deauthRunning.value -> "Deauth burst active"
            _deauthDetectorRunning.value -> "Deauth detector active"
            _rogueApRunning.value -> "Rogue AP detector active"
            _bleAdvertising.value || _bleConnected.value -> "BLE HID active"
            else -> null
        }
        val intent = Intent(context, NrSuiteForegroundService::class.java)
        if (activeText != null) {
            intent.action = NrSuiteForegroundService.ACTION_START
            intent.putExtra(NrSuiteForegroundService.EXTRA_TEXT, activeText)
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.stopService(intent)
        }
    }


    fun disconnect() {
        val current = session
        beaconStatusJob?.cancel()
        beaconStatusJob = null
        if (_beaconRunning.value) {
            _beaconRunning.value = false
            scope.launch {
                runCatching { current?.sendCommand("STOP_BEACON", timeoutMs = 4_000) }
            }
        }
        if (_sniffing.value) {
            _sniffing.value = false
            pcapJob?.cancel()
            pcapJob = null
            scope.launch(Dispatchers.IO) {
                runCatching { pcapWriter?.close() }
                pcapWriter = null
            }
        }
        if (_deauthDetectorRunning.value) {
            _deauthDetectorRunning.value = false
            scope.launch {
                runCatching { current?.sendCommand("DEAUTH_DETECT_STOP", timeoutMs = 4_000) }
            }
        }
        if (_rogueApRunning.value) {
            _rogueApRunning.value = false
            rogueApScanJob?.cancel()
            rogueApScanJob = null
        }
        portalStatusJob?.cancel()
        portalStatusJob = null
        session = null
        updateForegroundService()
        scope.launch {
            if (_portalRunning.value) {
                _portalRunning.value = false
                runCatching { current?.sendCommand("STOP_PORTAL", timeoutMs = 4_000) }
            }
            updateForegroundService()
            current?.disconnect()
            disconnectInternal()
        }
    }

    internal fun observe(session: NrSession) {
        sessionObservers = listOf(
            scope.launch {
                session.state.collect { _connectionState.value = it }
            },
            scope.launch {
                session.logs.collect { appendLog(it) }
            },
            scope.launch {
                session.events.collect { event ->
                    _events.update { (it + event).takeLast(100) }
                    when (event.optString("type")) {
                        "scan_ap" -> {
                            val bssid = event.optString("bssid")
                            val vendor = ouiDatabaseRepository.lookup(bssid)?.vendor
                            val ouiRule = matchOuiRule(bssid, _ouiRules.value)
                            val ouiWhitelisted = ouiRule?.action == OuiRuleAction.WHITELIST
                            val ouiBlacklisted = ouiRule?.action == OuiRuleAction.BLACKLIST
                            if (!vendor.isNullOrBlank()) event.put("vendor", vendor)
                            event.put("oui_whitelisted", ouiWhitelisted)
                            event.put("oui_blacklisted", ouiBlacklisted)

                            _networks.update { current ->
                                (current.filterNot { it.optString("bssid") == bssid } + event)
                                    .sortedByDescending { it.optInt("rssi", -999) }
                            }
                            val ssid = event.optString("ssid").ifBlank { "(hidden)" }
                            val target = NetworkTarget(
                                ssid = ssid,
                                bssid = bssid,
                                channel = event.optInt("channel", 0),
                                rssi = event.optInt("rssi", -100),
                                security = event.optString("security", "?"),
                                vendor = vendor,
                                ouiWhitelisted = ouiWhitelisted,
                                ouiBlacklisted = ouiBlacklisted,
                            )
                            _deauthDetectorTargets.update { current ->
                                (current.filterNot { it.bssid.equals(bssid, ignoreCase = true) } + target)
                                    .sortedByDescending { it.rssi }
                            }
                            _deauthDetectorSelectedTarget.update { selected ->
                                selected?.takeIf { it.bssid.equals(bssid, ignoreCase = true) } ?: selected
                            }
                            appendLog(
                                "AP: $ssid  ${event.optString("bssid")}  " +
                                    "ch ${event.optInt("channel")}  ${event.optInt("rssi")} dBm  " +
                                    event.optString("security")
                            )
                        }
                        "portal_viewed" -> {
                            _portalViews.update { it + 1 }
                            val ip = event.optString("client_ip", "unknown")
                            appendLog("Portal viewed from $ip.")
                            this@MainViewModel.portalLogImpl("GET / from $ip")
                        }
                        "captive_data" -> {
                            _portalCapturedData.update { it + 1 }
                            val ip = event.optString("ip", "unknown")
                            val userAgent = event.optString("user_agent", "")
                            val data = event.optJSONObject("data")
                            val fields = data?.keys()?.asSequence()?.joinToString(", ") { key ->
                                "$key=${data.optString(key)}"
                            } ?: ""
                            appendLog("Captive data received from $ip.")
                            this@MainViewModel.portalLogImpl("POST /login from $ip | UA: $userAgent | data: $fields")
                            val details = linkedMapOf<String, String>()
                            data?.keys()?.forEach { key ->
                                details[key] = data.optString(key)
                            }

                            val submittedPassword = data?.optString("password").orEmpty().ifBlank {
                                data?.optString("pass").orEmpty()
                            }
                            if (submittedPassword.isNotBlank() && _portalMode.value == "evil_twin") {
                                appendLog("Captured password candidate (${submittedPassword.length} chars).")
                                val capturedAt = java.time.LocalTime.now()
                                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                                _evilTwinPasswords.update {
                                    (it + CapturedPassword(submittedPassword, capturedAt)).takeLast(50)
                                }
                                this@MainViewModel.verifyEvilTwinPasswordsImpl()
                            } else if (_portalMode.value == "portal" && details.isNotEmpty()) {
                                val capturedAt = java.time.LocalTime.now()
                                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                                val value = submittedPassword.ifBlank {
                                    details.values.firstOrNull().orEmpty()
                                }
                                val credential = CapturedCredential(
                                    value = value,
                                    capturedAt = capturedAt,
                                    status = CredentialStatus.UNVERIFIED,
                                    source = CredentialSource.PORTAL,
                                    details = details,
                                )
                                synchronized(credentialLock) {
                                    activeCredentialSession = activeCredentialSession
                                        ?.takeIf { it.source == CredentialSource.PORTAL }
                                        ?.let { it.copy(credentials = it.credentials + credential) }
                                }
                                _portalCredentials.update { (it + credential).takeLast(100) }
                                appendLog("Portal credential captured (${details.size} field(s)).")
                            }
                        }
                        "client_associated" -> {
                            _portalClients.update { it + 1 }
                            val client = event.optString("client")
                            val rssi = event.optInt("rssi")
                            appendLog("Client associated: $client ($rssi dBm).")
                            this@MainViewModel.portalLogImpl("Client associated: $client ($rssi dBm)")
                        }
                        "deauth_stats" -> {
                            _deauthSent.value = event.optInt("sent_frames", _deauthSent.value)
                            appendLog("Deauth stats: ${_deauthSent.value} frame(s) sent.")
                            this@MainViewModel.portalLogImpl("Deauth stats: ${_deauthSent.value} frame(s) sent")
                        }
                        "deauth_detected" -> this@MainViewModel.recordDeauthDetectorFrame(event)
                        "deauth_detector_hop" -> {
                            if (_deauthDetectorChannelMode.value == DeauthChannelMode.HOPPING) {
                                _deauthDetectorCurrentHopChannel.value = event.optInt(
                                    "channel",
                                    _deauthDetectorCurrentHopChannel.value ?: 1,
                                )
                            }
                        }
                        "heartbeat" -> Unit
                    }
                }
            },
        )
    }

    internal fun disconnectInternal() {
        stopActiveOperations()
        sessionObservers.forEach { it.cancel() }
        sessionObservers = emptyList()
        session?.let { activeSession ->
            scope.launch { activeSession.disconnect() }
        }
        session = null
        activeDeviceFingerprint = null
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun ensureRootStructure(rootUri: Uri) {
        scope.launch(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(app, rootUri)
            if (root == null) {
                appendLog("Could not open the selected NRSuite root directory.")
                return@launch
            }

            val expectedDirectories = listOf("Pcap", "DuckyEditor", "Logs", "Portals")
            val readyDirectories = mutableListOf<String>()
            for (name in expectedDirectories) {
                val existing = root.findFile(name)
                if (existing != null) {
                    readyDirectories += name
                } else if (root.createDirectory(name) != null) {
                    readyDirectories += name
                } else {
                    appendLog("Could not create NRSuite/$name in the selected root.")
                }
            }

            if (readyDirectories.isNotEmpty()) {
                appendLog("NRSuite root ready: ${readyDirectories.joinToString(", ")}.")
            }
        }
    }

    internal fun ensureChildDirectory(rootUri: Uri, name: String): DocumentFile? {
        val root = DocumentFile.fromTreeUri(app, rootUri) ?: return null
        return root.findFile(name) ?: root.createDirectory(name)
    }

    internal fun createPcapDocumentInDirectory(directoryUri: Uri, displayName: String): Uri {
        return DocumentsContract.createDocument(
            app.contentResolver,
            directoryUri,
            "application/vnd.tcpdump.pcap",
            displayName,
        ) ?: throw IOException("Storage provider did not create a document")
    }

    private fun createPcapDocument(treeUri: Uri, displayName: String): Uri {
        val resolver = app.contentResolver
        val parentDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocumentId)
        return DocumentsContract.createDocument(
            resolver,
            parentUri,
            "application/vnd.tcpdump.pcap",
            displayName,
        ) ?: throw IOException("Storage provider did not create a document")
    }

    internal fun displayNameForTreeUri(uri: Uri): String {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        val fromDocumentId = documentId
            ?.substringAfterLast('/')
            ?.substringAfterLast(':')
            ?.trim()
        if (!fromDocumentId.isNullOrBlank()) {
            return fromDocumentId
        }
        return uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "Selected folder"
    }

    internal fun exportPcapToRootIfConfigured(sourceFile: File?) {
        val rootUri = _exportDirectory.value ?: return
        if (sourceFile == null || !sourceFile.exists() || sourceFile.length() == 0L) return

        val key = sourceFile.absolutePath
        synchronized(exportedCapturePaths) {
            if (!exportedCapturePaths.add(key)) return
        }

        scope.launch(Dispatchers.IO) {
            runCatching {
                val pcapDirUri = ensureChildDirectory(rootUri, "Pcap")?.uri ?: rootUri
                val displayName = sourceFile.name
                val documentUri = createPcapDocumentInDirectory(pcapDirUri, displayName)
                app.contentResolver.openOutputStream(documentUri, "wt")?.use { output ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                } ?: throw IOException("Could not open export file")

                appendLog(
                    "Evil Twin PCAP exported to " +
                        "${displayNameForTreeUri(rootUri)}/Pcap/$displayName",
                )
            }.onFailure { error ->
                synchronized(exportedCapturePaths) {
                    exportedCapturePaths.remove(key)
                }
                appendLog("Could not export Evil Twin PCAP: ${error.message}", level = LogLevel.ERROR)
            }
        }
    }


    internal fun appendLog(
        message: String,
        level: LogLevel = LogLevel.INFO,
        tag: String = "app",
    ) {
        val entry = LogEntry(
            timestamp = java.time.LocalTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")),
            level = level,
            tag = tag,
            message = message,
        )
        _logs.update { (it + entry).takeLast(300) }
    }

    companion object {
        private const val PREFERENCES_NAME = "nrsuite"
        private const val PREF_EXPORT_DIRECTORY = "export_directory_uri"
        private const val PREF_HISTORY = "session_history"
        private const val PREF_RECENT_MODULES = "recent_modules"
        internal val MAC_PATTERN = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
    }
}
