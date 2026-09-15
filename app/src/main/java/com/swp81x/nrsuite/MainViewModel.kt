package com.swp81x.nrsuite

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.session.NrSession
import com.swp81x.nrsuite.core.usb.UsbSerialDevice
import com.swp81x.nrsuite.core.usb.UsbSerialDeviceCatalog
import com.swp81x.nrsuite.core.usb.UsbSerialTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    private var session: NrSession? = null
    private var sessionObservers: List<Job> = emptyList()

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

    fun disconnect() {
        val current = session
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
