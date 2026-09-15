package com.swp81x.nrsuite

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swp81x.nrsuite.core.pcap.PcapWriter
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.session.NrSession
import com.swp81x.nrsuite.core.usb.UsbSerialDevice
import com.swp81x.nrsuite.core.usb.UsbSerialDeviceCatalog
import com.swp81x.nrsuite.core.usb.UsbSerialTransport
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val usbManager =
        application.getSystemService(Context.USB_SERVICE) as UsbManager

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

    private var session: NrSession? = null
    private var sessionObservers: List<Job> = emptyList()
    private var pcapWriter: PcapWriter? = null
    private var pcapJob: Job? = null

    init {
        refreshDevices()
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

    fun startSniff(fixedMode: Boolean, channel: Int, intervalMs: Int) {
        val activeSession = session
        if (activeSession == null) {
            appendLog("Connect to a device before sniffing.")
            return
        }
        if (_sniffing.value) return

        val capturesDir = File(getApplication<Application>().filesDir, "captures").apply { mkdirs() }
        val captureFile = File(capturesDir, "capture_${System.currentTimeMillis()}.pcap")
        val writer = runCatching { PcapWriter(captureFile) }.getOrElse { error ->
            appendLog("Could not create capture file: ${error.message}")
            return
        }

        pcapWriter = writer
        _capturePath.value = captureFile.absolutePath
        _sniffPacketCount.value = 0
        _sniffing.value = true

        appendLog("Capture file: ${captureFile.absolutePath}")

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

    private fun appendLog(message: String) {
        _logs.update { (it + message).takeLast(200) }
    }
}
