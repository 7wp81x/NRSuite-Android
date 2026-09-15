package com.swp81x.nrsuite

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.compose.foundation.layout.PaddingValues
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.usb.UsbSerialDevice
import com.swp81x.nrsuite.ui.theme.NRSuiteTheme
import org.json.JSONObject

private const val ACTION_USB_PERMISSION = "com.swp81x.nrsuite.USB_PERMISSION"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NRSuiteTheme {
                NRSuiteApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NRSuiteApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val usbManager = remember {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    val devices by viewModel.devices.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val scanning by viewModel.scanning.collectAsState()
    val networks by viewModel.networks.collectAsState()

    val permissionReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != ACTION_USB_PERMISSION) return
                @Suppress("DEPRECATION")
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                val granted =
                    intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (device != null) {
                    viewModel.onPermissionResult(device, granted)
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

    fun requestPermission(device: UsbDevice) {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val pendingIntent = PendingIntent.getBroadcast(context, device.deviceId, intent, flags)
        usbManager.requestPermission(device, pendingIntent)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("NRSuite") })
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ConnectionCard(
                    state = connectionState,
                    scanning = scanning,
                    onScan = viewModel::scanWifi,
                    onDisconnect = viewModel::disconnect,
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "USB devices",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::refreshDevices) {
                        Text("Refresh")
                    }
                }
            }

            if (devices.isEmpty()) {
                item {
                    Text(
                        text = "No supported USB serial devices found. Plug in an ESP32 and tap Refresh.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(devices, key = { it.device.deviceId }) { device ->
                    DeviceRow(
                        entry = device,
                        hasPermission = usbManager.hasPermission(device.device),
                        onConnect = {
                            if (usbManager.hasPermission(device.device)) {
                                viewModel.connect(device.device)
                            } else {
                                requestPermission(device.device)
                            }
                        },
                    )
                }
            }

            if (connectionState is ConnectionState.Connected) {
                item {
                    Text(
                        text = "WiFi scan",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (networks.isEmpty()) {
                    item {
                        Text(
                            text = "No scan results yet. Tap Scan WiFi above.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(networks.take(50), key = { it.optString("bssid", it.toString()) }) { network ->
                        NetworkRow(network)
                    }
                }
            }

            item {
                Text(
                    text = "Session log",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(logs.takeLast(100).reversed()) { line ->
                Text(
                    text = line,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

}

@Composable
private fun ConnectionCard(
    state: ConnectionState,
    scanning: Boolean,
    onScan: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val (statusText, statusColor) = when (state) {
        ConnectionState.Disconnected -> "Disconnected" to Color(0xFF9E9E9E)
        ConnectionState.Connecting -> "Connecting..." to Color(0xFFFFC107)
        is ConnectionState.Connected -> {
            val chipText = state.chip ?: "NRSuite device"
            "Connected: $chipText" to Color(0xFF4CAF50)
        }
        is ConnectionState.Failed -> "Error: ${state.message}" to Color(0xFFF44336)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "Connection",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(
                    Modifier
                        .background(statusColor, RoundedCornerShape(50))
                        .width(10.dp)
                        .height(10.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = statusText,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (state is ConnectionState.Connected) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Firmware: ${state.firmwareVersion ?: "unknown"}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state !is ConnectionState.Disconnected) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onDisconnect) {
                        Text("Disconnect")
                    }
                    if (state is ConnectionState.Connected) {
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onScan, enabled = !scanning) {
                            Text(if (scanning) "Scanning..." else "Scan WiFi")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkRow(network: JSONObject) {
    val ssid = network.optString("ssid").ifBlank { "(hidden)" }
    val bssid = network.optString("bssid")
    val channel = network.optInt("channel")
    val rssi = network.optInt("rssi")
    val security = network.optString("security").ifBlank { "?" }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text(
                text = ssid,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "$bssid  •  ch $channel  •  $rssi dBm  •  $security",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeviceRow(
    entry: UsbSerialDevice,
    hasPermission: Boolean,
    onConnect: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.displayName,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (hasPermission) "Permission granted" else "Permission required",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onConnect) {
                Text(if (hasPermission) "Connect" else "Allow")
            }
        }
    }
}
