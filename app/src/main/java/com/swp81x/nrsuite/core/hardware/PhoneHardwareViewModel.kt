package com.swp81x.nrsuite.core.hardware

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Phone-owned Android APIs. No bridge commands or privileged shell execution. */
class PhoneHardwareViewModel(app: Application) : AndroidViewModel(app) {
    private val context = app.applicationContext
    private val wifi = context.getSystemService(WifiManager::class.java)
    private val bluetooth = context.getSystemService(BluetoothManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(PhoneHardwareState())
    val state = _state.asStateFlow()
    private var wifiReceiver: BroadcastReceiver? = null
    private var bleCallback: ScanCallback? = null
    private var activeScanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var scanStartedUs = 0L
    private val wifiTimeout = Runnable {
        stopWifi()
        _state.value = _state.value.copy(wifiMessage = "No fresh scan received. Android may throttle requests. Try again later.")
    }
    private val bleTimeout = Runnable { stopBle() }

    fun requiredPermissions(ble: Boolean): Array<String> = when {
        ble && Build.VERSION.SDK_INT >= 31 -> arrayOf(
            Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION,
        )
        else -> arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun permitted(ble: Boolean) = requiredPermissions(ble).all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun locationEnabled(): Boolean {
        val manager = context.getSystemService(LocationManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= 28) manager.isLocationEnabled
        else manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @Suppress("DEPRECATION", "MissingPermission")
    fun scanWifi() {
        stopWifi()
        _state.value = _state.value.copy(wifiResults = emptyList())
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) return wifiError("This phone has no Wi-Fi hardware.")
        if (!permitted(false)) return wifiError("Allow precise location permission to scan Wi-Fi.")
        if (!locationEnabled()) return wifiError("Turn on Android Location services to scan Wi-Fi.")
        if (wifi?.isWifiEnabled != true) return wifiError("Turn on Wi-Fi in Android settings.")
        scanStartedUs = SystemClock.elapsedRealtimeNanos() / 1000
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION || wifiReceiver !== this) return
                try {
                    val fresh = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                    val results = wifi.scanResults.orEmpty().map {
                        PhoneWifiResult(it.SSID.ifBlank { "Hidden SSID" }, it.BSSID.orEmpty(), it.level,
                            it.frequency, it.timestamp >= scanStartedUs)
                    }.sortedByDescending { it.rssi }
                    stopWifi()
                    _state.value = _state.value.copy(wifiResults = results,
                        wifiMessage = if (fresh) "Scan received. Rows marked cached predate this request."
                        else "Android returned cached results. Scan may be throttled.")
                } catch (_: SecurityException) {
                    stopWifi()
                    wifiError("Wi-Fi permission changed. Grant precise location and try again.")
                }
            }
        }
        try {
            wifiReceiver = receiver
            ContextCompat.registerReceiver(context, receiver,
                IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), ContextCompat.RECEIVER_EXPORTED)
            _state.value = _state.value.copy(wifiScanning = true, wifiMessage = "Waiting for Android Wi-Fi scan...")
            handler.postDelayed(wifiTimeout, 20_000)
            if (!wifi.startScan()) {
                stopWifi()
                wifiError("Android declined this scan. Requests are rate limited; try again later.")
            }
        } catch (e: RuntimeException) {
            stopWifi()
            wifiError("Wi-Fi scan unavailable: ${e.javaClass.simpleName}")
        }
    }

    @Suppress("MissingPermission")
    fun scanBle() {
        stopBle()
        _state.value = _state.value.copy(bleResults = emptyList())
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) return bleError("This phone has no BLE hardware.")
        if (!permitted(true)) return bleError("Allow Nearby devices and precise location permissions to scan BLE.")
        if (Build.VERSION.SDK_INT < 31 && !locationEnabled()) return bleError("Turn on Android Location services to scan BLE.")
        try {
            val adapter = bluetooth?.adapter
            if (adapter?.isEnabled != true) return bleError("Turn on Bluetooth in Android settings.")
            val scanner = adapter.bluetoothLeScanner ?: return bleError("BLE scanner unavailable on this phone.")
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    handler.post {
                        if (bleCallback !== this) return@post
                        try {
                            val address = result.device.address
                            val row = PhoneBleResult(result.scanRecord?.deviceName ?: "Unnamed BLE device", address, result.rssi)
                            val rows = (_state.value.bleResults.filterNot { it.address == address } + row)
                                .sortedByDescending { it.rssi }.take(256)
                            _state.value = _state.value.copy(bleResults = rows)
                        } catch (_: SecurityException) {
                            stopBle()
                            bleError("Bluetooth permission changed. Grant permission and try again.")
                        }
                    }
                }
                override fun onScanFailed(errorCode: Int) {
                    handler.post {
                        if (bleCallback !== this) return@post
                        stopBle()
                        bleError("Android BLE scan failed, code $errorCode. Try again later.")
                    }
                }
            }
            activeScanner = scanner
            bleCallback = callback
            _state.value = _state.value.copy(bleScanning = true, bleMessage = "Scanning BLE advertisements for 15 seconds...")
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
            handler.postDelayed(bleTimeout, 15_000)
        } catch (e: RuntimeException) {
            stopBle()
            bleError("BLE scan unavailable: ${e.javaClass.simpleName}")
        }
    }

    private fun wifiError(message: String) { _state.value = _state.value.copy(wifiMessage = message) }
    private fun bleError(message: String) { _state.value = _state.value.copy(bleMessage = message) }

    private fun stopWifi() {
        handler.removeCallbacks(wifiTimeout)
        wifiReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        wifiReceiver = null
        _state.value = _state.value.copy(wifiScanning = false)
    }

    @Suppress("MissingPermission")
    private fun stopBle() {
        handler.removeCallbacks(bleTimeout)
        val callback = bleCallback
        bleCallback = null
        if (callback != null) runCatching { activeScanner?.stopScan(callback) }
        activeScanner = null
        _state.value = _state.value.copy(bleScanning = false,
            bleMessage = if (_state.value.bleScanning) "BLE scan stopped. Results kept for this screen." else _state.value.bleMessage)
    }

    fun stop() {
        val wasScanning = _state.value.wifiScanning
        stopWifi()
        if (wasScanning) wifiError("Wi-Fi scan stopped.")
        stopBle()
    }

    override fun onCleared() { stop() }
}

data class PhoneWifiResult(val ssid: String, val bssid: String, val rssi: Int, val frequency: Int, val fresh: Boolean)
data class PhoneBleResult(val name: String, val address: String, val rssi: Int)
data class PhoneHardwareState(
    val wifiScanning: Boolean = false,
    val bleScanning: Boolean = false,
    val wifiMessage: String = "Scan nearby access points with this phone.",
    val bleMessage: String = "Scan nearby BLE advertisements with this phone.",
    val wifiResults: List<PhoneWifiResult> = emptyList(),
    val bleResults: List<PhoneBleResult> = emptyList(),
)
