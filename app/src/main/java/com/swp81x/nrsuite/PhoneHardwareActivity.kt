package com.swp81x.nrsuite

import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.swp81x.nrsuite.core.hardware.PhoneHardwareViewModel
import com.swp81x.nrsuite.ui.theme.NRSuiteTheme

/** Independent entry point: usable without an ESP32 connection on any Android edition. */
class PhoneHardwareActivity : ComponentActivity() {
    private lateinit var hardware: PhoneHardwareViewModel
    private var nfc by mutableStateOf("NFC reader is stopped.")
    private var readingNfc by mutableStateOf(false)
    private val adapter get() = NfcAdapter.getDefaultAdapter(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hardware = ViewModelProvider(this)[PhoneHardwareViewModel::class.java]
        setContent { NRSuiteTheme { HardwareScreen() } }
    }

    private fun stopNfc() {
        runCatching { adapter?.disableReaderMode(this) }
        if (readingNfc) nfc = "NFC reader stopped. Start it again to read a tag."
        readingNfc = false
    }

    private fun readNfc() {
        val reader = adapter
        if (reader == null) { nfc = "This phone has no NFC adapter."; return }
        if (!reader.isEnabled) { nfc = "Turn on NFC in Android settings."; return }
        try {
            reader.enableReaderMode(this, { tag ->
                val id = tag.id.joinToString("") { "%02X".format(it.toInt() and 0xff) }
                runOnUiThread {
                    if (!readingNfc) return@runOnUiThread
                    nfc = "Tag ID: $id\nTechnologies: ${tag.techList.joinToString { it.substringAfterLast('.') }}\nTag identifiers may be randomized."
                }
            }, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NFC_BARCODE or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null)
            readingNfc = true
            nfc = "Hold a tag near the phone. Reads identifier and technology names only."
        } catch (e: RuntimeException) { nfc = "NFC unavailable: ${e.javaClass.simpleName}" }
    }

    override fun onStop() {
        hardware.stop()
        stopNfc()
        super.onStop()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun HardwareScreen() {
        val state by hardware.state.collectAsState()
        var pending by rememberSaveable { mutableStateOf("") }
        var permissionMessage by remember { mutableStateOf("") }
        val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val ble = pending == "ble"
            val allowed = hardware.requiredPermissions(ble).all { permission ->
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            }
            if (allowed) {
                if (ble) hardware.scanBle() else hardware.scanWifi()
                permissionMessage = ""
            } else permissionMessage = "Permission denied. Allow permissions in Android app settings to scan."
            pending = ""
        }
        fun requestScan(ble: Boolean) {
            pending = if (ble) "ble" else "wifi"
            permissions.launch(hardware.requiredPermissions(ble))
        }
        Scaffold(topBar = {
            TopAppBar(title = { Text("Phone hardware") }, navigationIcon = {
                TextButton(onClick = { finish() }) { Text("Back") }
            })
        }) { padding ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                item {
                    Text("Built-in phone hardware", style = MaterialTheme.typography.titleLarge)
                    Text("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE}")
                    Text("Uses Android APIs without an ESP32. Works on non-rooted, rooted, and NetHunter Android phones with the same permission rules.")
                    Button(onClick = { startActivity(Intent(this@PhoneHardwareActivity, RootControlActivity::class.java)) }) {
                        Text("Root / NetHunter controls")
                    }
                    Text("Discovery uses Android APIs. Root radio and NetHunter controls are available in the separate control panel.",
                        style = MaterialTheme.typography.bodySmall)
                    if (permissionMessage.isNotEmpty()) Text(permissionMessage, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:$packageName")))
                    }) { Text("App permissions") }
                }
                item {
                    Text("Wi-Fi", style = MaterialTheme.typography.titleMedium)
                    Text(state.wifiMessage)
                    Button(onClick = { requestScan(false) }, enabled = !state.wifiScanning && pending.isEmpty()) { Text("Scan phone Wi-Fi") }
                    TextButton(onClick = { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }) { Text("Wi-Fi settings") }
                    TextButton(onClick = { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text("Location settings") }
                }
                items(state.wifiResults) { row ->
                    Text("${row.ssid}  ${row.rssi} dBm\n${row.bssid}  ${row.frequency} MHz" + if (row.fresh) "" else "  [cached]")
                }
                item {
                    HorizontalDivider()
                    Text("Bluetooth Low Energy", style = MaterialTheme.typography.titleMedium)
                    Text(state.bleMessage)
                    Button(onClick = { requestScan(true) }, enabled = !state.bleScanning && pending.isEmpty()) { Text("Scan phone BLE") }
                    TextButton(onClick = { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text("Bluetooth settings") }
                    Text("BLE addresses may change. Advertisements do not establish a device owner's identity.", style = MaterialTheme.typography.bodySmall)
                }
                items(state.bleResults, key = { it.address }) { row -> Text("${row.name}  ${row.rssi} dBm\n${row.address}") }
                item {
                    HorizontalDivider()
                    Text("NFC", style = MaterialTheme.typography.titleMedium)
                    Text(nfc)
                    Button(onClick = { if (readingNfc) { stopNfc(); nfc = "NFC reader stopped." } else readNfc() }) {
                        Text(if (readingNfc) "Stop NFC reader" else "Read NFC tag")
                    }
                    OutlinedButton(onClick = { hardware.stop(); stopNfc(); nfc = "NFC reader stopped." }) { Text("Stop phone readers") }
                    Text("Scans stop when this screen leaves the foreground. Results remain in memory only.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
