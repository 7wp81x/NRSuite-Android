package com.swp81x.nrsuite.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.ble.BleCharacteristicProfile
import com.swp81x.nrsuite.core.ble.BleDeviceObservation
import com.swp81x.nrsuite.core.ble.BleServiceProfile
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed
import com.swp81x.nrsuite.ui.util.copyWithToast
import com.swp81x.nrsuite.ui.util.rssiToProximity

@Composable
fun BleProfileScreen(
    connected: Boolean,
    running: Boolean,
    target: BleDeviceObservation?,
    devices: List<BleDeviceObservation>,
    services: List<BleServiceProfile>,
    status: String,
    onSelectTarget: (BleDeviceObservation) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmStart by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            BleProfileStatusCard(
                connected = connected,
                running = running,
                target = target,
                status = status,
            )

            Spacer(Modifier.height(10.dp))

            BleProfileTargetCard(
                devices = devices,
                selectedTarget = target,
                running = running,
                onSelectTarget = onSelectTarget,
            )

            Spacer(Modifier.height(10.dp))

            BleProfileResultsCard(
                services = services,
                running = running,
                onClear = onClear,
            )

            Spacer(Modifier.height(80.dp))
        }

        if (connected) {
            FloatingActionButton(
                onClick = {
                    if (running) onStop() else if (target != null) confirmStart = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = if (running) StatusRed else MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (running) "Stop BLE profile" else "Start BLE profile",
                )
            }
        }
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("Start BLE profile?") },
            text = {
                Text(
                    "This connects to ${target?.name ?: target?.address ?: "the selected device"} " +
                        "and performs a read-only service and characteristic enumeration. " +
                        "Use only where authorized."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmStart = false
                        onStart()
                    },
                ) { Text("Start") }
            },
            dismissButton = {
                TextButton(onClick = { confirmStart = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BleProfileStatusCard(
    connected: Boolean,
    running: Boolean,
    target: BleDeviceObservation?,
    status: String,
) {
    val color = when {
        !connected -> StatusNeutral
        running -> StatusAmber
        target != null && status.startsWith("Profile complete") -> StatusGreen
        else -> StatusAmber
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, if (running) MaterialTheme.colorScheme.primary else NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Usb,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = when {
                            !connected -> "Disconnected"
                            running -> "Profiling"
                            servicesStatus(status) -> "Ready"
                            else -> "Ready"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                    target?.let {
                        Text(
                            text = "${it.name ?: "Unnamed device"} · ${it.address}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = NrOnSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun servicesStatus(status: String): Boolean =
    status.startsWith("Profile complete") || status.startsWith("Target selected")

@Composable
private fun BleProfileTargetCard(
    devices: List<BleDeviceObservation>,
    selectedTarget: BleDeviceObservation?,
    running: Boolean,
    onSelectTarget: (BleDeviceObservation) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "Profile target",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Choose a device discovered by BLE Scanner.",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            if (devices.isEmpty()) {
                Text(
                    text = "No discovered BLE devices. Run BLE Scanner first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusAmber,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(devices, key = { it.address }) { device ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
                            border = BorderStroke(
                                0.5.dp,
                                if (selectedTarget?.address == device.address) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    NrOutline
                                },
                            ),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = device.name ?: "Unnamed device",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = "${device.address} · ${device.rssi} dBm · ${rssiToProximity(device.rssi).label}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = NrOnSurfaceVariant,
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onSelectTarget(device) },
                                    enabled = !running,
                                ) {
                                    Text(if (selectedTarget?.address == device.address) "Selected" else "Select")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BleProfileResultsCard(
    services: List<BleServiceProfile>,
    running: Boolean,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Services & characteristics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (services.isEmpty()) {
                            "No profile data yet"
                        } else {
                            "${services.size} service(s), ${services.sumOf { it.characteristics.size }} characteristic(s)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                if (services.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onClear,
                        enabled = !running,
                    ) {
                        Text("Clear")
                    }
                }
            }

            if (services.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(services, key = { it.uuid }) { service ->
                        BleServiceRow(service)
                    }
                }
            }
        }
    }
}

@Composable
private fun BleServiceRow(service: BleServiceProfile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                text = "Service ${service.uuid}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (service.characteristics.isEmpty()) {
                Text(
                    text = "No characteristics discovered",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(6.dp))
                service.characteristics.forEach { characteristic ->
                    BleCharacteristicRow(characteristic)
                }
            }
        }
    }
}

@Composable
private fun BleCharacteristicRow(characteristic: BleCharacteristicProfile) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val props = buildList {
        if (characteristic.read) add("Read")
        if (characteristic.write) add("Write")
        if (characteristic.writeNoResponse) add("Write NR")
        if (characteristic.notify) add("Notify")
        if (characteristic.indicate) add("Indicate")
        if (characteristic.broadcast) add("Broadcast")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = characteristic.uuid,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
            Text(
                text = if (props.isEmpty()) "No properties" else props.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = NrOnSurfaceVariant,
            )
        }
        IconButton(
            onClick = {
                clipboard.copyWithToast(
                    context,
                    "Characteristic: ${characteristic.uuid}\n" +
                        "Properties: ${props.joinToString(", ").ifBlank { "none" }}",
                    "Characteristic copied",
                )
            },
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy characteristic",
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
