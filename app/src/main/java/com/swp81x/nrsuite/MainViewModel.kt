package com.swp81x.nrsuite

import android.app.Application
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swp81x.nrsuite.core.pcap.PcapWriter
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.session.NrSession
import com.swp81x.nrsuite.core.usb.UsbSerialDevice
import com.swp81x.nrsuite.core.usb.UsbSerialDeviceCatalog
import com.swp81x.nrsuite.core.usb.UsbSerialTransport
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

    private val _exportDirectoryName = MutableStateFlow("App-private storage")
    val exportDirectoryName: StateFlow<String> = _exportDirectoryName.asStateFlow()

    private val _devices = MutableStateFlow<List<UsbSerialDevice>>(emptyList())
    val devices: StateFlow<List<UsbSerialDevice>> = _devices.asStateFlow()

    private val _connectionState =
        MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

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

    private var beaconStatusJob: Job? = null

    private var session: NrSession? = null
    private var sessionObservers: List<Job> = emptyList()
    private var pcapWriter: PcapWriter? = null
    private var pcapJob: Job? = null

    init {
        loadExportDirectory()
        refreshDevices()
    }

    fun setExportDirectory(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { resolver.takePersistableUriPermission(uri, flags) }
        _exportDirectory.value = uri
        preferences.edit().putString(PREF_EXPORT_DIRECTORY, uri.toString()).apply()
        _exportDirectoryName.value = displayNameForTreeUri(uri)
        appendLog("Capture export folder: ${_exportDirectoryName.value}")
    }

    private fun loadExportDirectory() {
        val stored = preferences.getString(PREF_EXPORT_DIRECTORY, null) ?: return
        val uri = runCatching { Uri.parse(stored) }.getOrNull() ?: return
        _exportDirectory.value = uri
        _exportDirectoryName.value = displayNameForTreeUri(uri)
    }

    fun refreshDevices() {
        val found = UsbSerialDeviceCatalog.list(usbManager)
        _devices.value = found
        appendLog("Found ${found.size} supported USB serial device(s).")
    }

    fun hasPermission(device: UsbDevice): Boolean = usbManager.hasPermission(device)

    fun onPermissionResult(device: UsbDevice, granted: Boolean) {
        appendLog(
            if (granted) {
                "USB permission granted for ${device.deviceName}."
            } else {
                "USB permission denied for ${device.deviceName}."
            }
        )
        if (granted) {
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
                else -> appendLog("WiFi scan complete: $count network(s).")
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
                _beaconSent.value = 0
                _beaconSsidCount.value = response.optInt("ssids", cleanSsids.size)
                _beaconChannel.value = response.optInt("channel", channel)
                appendLog("Beacon broadcast started.")
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

        val activeSession = session
        viewModelScope.launch {
            val response = activeSession?.sendCommand("STOP_BEACON", timeoutMs = 6_000)
            if (response?.optBoolean("ok") == true) {
                _beaconSent.value = response.optInt("sent", _beaconSent.value)
                _beaconSsidCount.value = response.optInt("ssids", _beaconSsidCount.value)
                appendLog("Beacon stopped. Frames sent: ${_beaconSent.value}.")
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

    fun startSniff(fixedMode: Boolean, channel: Int, intervalMs: Int) {
        val activeSession = session
        if (activeSession == null) {
            appendLog("Connect to a device before sniffing.")
            return
        }
        if (_sniffing.value) return

        val captureName = "capture_${System.currentTimeMillis()}.pcap"
        val exportUri = _exportDirectory.value
        val writerResult = runCatching {
            if (exportUri != null) {
                val documentUri = createPcapDocument(exportUri, captureName)
                val outputStream = getApplication<Application>().contentResolver
                    .openOutputStream(documentUri, "wt")
                    ?: throw IOException("Could not open export file")
                val displayName = displayNameForTreeUri(exportUri)
                PcapWriter(outputStream, closeOutput = true) to "$displayName/$captureName"
            } else {
                val capturesDir = File(getApplication<Application>().filesDir, "captures").apply { mkdirs() }
                val captureFile = File(capturesDir, captureName)
                PcapWriter(captureFile) to captureFile.absolutePath
            }
        }
        val (writer, captureDisplayPath) = writerResult.getOrElse { error ->
            appendLog("Could not create capture output: ${error.message}")
            return
        }

        pcapWriter = writer
        _capturePath.value = captureDisplayPath
        _sniffPacketCount.value = 0
        _sniffing.value = true

        appendLog("Capture file: $captureDisplayPath")

        pcapJob = viewModelScope.launch(Dispatchers.IO) {
            activeSession.pcap.collect { frame ->
                try {
                    writer.writePacket(frame)
                    _sniffPacketCount.update { it + 1 }
                } catch (t: Throwable) {
                    appendLog("PCAP write error: ${t.message}")
                }
            }
        }

        viewModelScope.launch {
            val args = JSONObject().apply {
                put("mode", if (fixedMode) "fixed" else "hop")
                put("channel", channel)
                put("interval_ms", intervalMs)
            }
            val response = activeSession.sendCommand("START_SNIFF", args, timeoutMs = 12_000)
            if (response?.optBoolean("ok") == true) {
                appendLog(if (fixedMode) "Sniffing started on channel $channel." else "Channel-hopping sniffing started.")
            } else {
                appendLog("Failed to start sniffing: ${response?.optString("msg") ?: "timeout"}")
                stopSniff()
            }
        }
    }

    fun stopSniff() {
        if (!_sniffing.value) return
        _sniffing.value = false

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
            _capturePath.value?.let { appendLog("Capture saved: $it") }
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
        session = null
        viewModelScope.launch {
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
        _connectionState.value = ConnectionState.Disconnected
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

    private fun appendLog(message: String) {
        _logs.update { (it + message).takeLast(200) }
    }

    companion object {
        private const val PREFERENCES_NAME = "nrsuite"
        private const val PREF_EXPORT_DIRECTORY = "export_directory_uri"
    }
}
