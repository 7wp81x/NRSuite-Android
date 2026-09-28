package com.swp81x.nrsuite.ui

import android.hardware.usb.UsbDevice
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.usb.UsbSerialDevice
import com.swp81x.nrsuite.ui.components.SavedScriptPicker
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed

@Composable
fun BadUsbScreen(
    devices: List<UsbSerialDevice>,
    selectedTarget: UsbSerialDevice?,
    selectedTargetState: ConnectionState?,
    targetFingerprint: (UsbDevice) -> String,
    deviceConnectionStates: Map<String, ConnectionState>,
    pendingPermissionRequests: Set<Int>,
    uploading: Boolean,
    progress: Int,
    armedFingerprints: Set<String>,
    selectedPayloadName: String?,
    savedScripts: Map<String, String>,
    onSelectTarget: (UsbDevice) -> Unit,
    onChooseTarget: (UsbDevice) -> Unit,
    onUseSavedScript: (String) -> Unit,
    onChoosePayload: () -> Unit,
    onClearPayload: () -> Unit,
    onArm: (mscMode: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var configExpanded by remember { mutableStateOf(true) }
    var mscMode by remember { mutableStateOf(false) }
    var confirmArm by remember { mutableStateOf(false) }

    val selectedFingerprint = selectedTarget?.let { targetFingerprint(it.device) }
    val targetArmed = selectedFingerprint != null && selectedFingerprint in armedFingerprints
    val targetReady = isBadUsbSupported(selectedTargetState)
    val selectedPendingPermission = selectedTarget?.let {
        it.device.deviceId in pendingPermissionRequests
    } == true

    LaunchedEffect(devices, selectedTarget) {
        if (selectedTarget == null && devices.size == 1) {
            onSelectTarget(devices.first().device)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            BadUsbDeviceSelectorCard(
                devices = devices,
                selectedTarget = selectedTarget,
                selectedTargetState = selectedTargetState,
                targetFingerprint = targetFingerprint,
                deviceConnectionStates = deviceConnectionStates,
                pendingPermissionRequests = pendingPermissionRequests,
                armedFingerprints = armedFingerprints,
                uploading = uploading,
                onSelectTarget = onSelectTarget,
                onChooseTarget = onChooseTarget,
            )

            Spacer(Modifier.height(10.dp))

            ConfigZone(
                targetReady = targetReady,
                selectedTarget = selectedTarget,
                selectedTargetState = selectedTargetState,
                uploading = uploading,
                expanded = configExpanded,
                selectedPayloadName = selectedPayloadName,
                savedScripts = savedScripts,
                onUseSavedScript = onUseSavedScript,
                mscMode = mscMode,
                onToggle = { configExpanded = !configExpanded },
                onChoosePayload = onChoosePayload,
                onClearPayload = onClearPayload,
                onMscChange = { mscMode = it },
            )

            Spacer(Modifier.height(10.dp))

            ResultZone(
                selectedTarget = selectedTarget,
                selectedTargetState = selectedTargetState,
                uploading = uploading,
                progress = progress,
                targetArmed = targetArmed,
                selectedPayloadName = selectedPayloadName,
            )
        }

        if (targetReady && selectedPayloadName != null) {
            FloatingActionButton(
                onClick = { if (!uploading) confirmArm = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = if (uploading) StatusAmber else MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = if (uploading) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (uploading) "Uploading" else "Arm payload",
                )
            }
        }
    }

    if (confirmArm) {
        AlertDialog(
            onDismissRequest = { confirmArm = false },
            title = { Text("Arm BadUSB payload?") },
            text = {
                Text(
                    "The payload will be uploaded to " +
                        "${selectedTarget?.displayName ?: "the selected device"} and armed. " +
                        "It executes once on the next power-on/re-plug" +
                        (if (mscMode) " and also exposes mass storage." else ".") +
                        " Only use payloads on devices you own or are authorized to test."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmArm = false
                        onArm(mscMode)
                    },
                ) {
                    Text("Arm")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmArm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun BadUsbDeviceSelectorCard(
    devices: List<UsbSerialDevice>,
    selectedTarget: UsbSerialDevice?,
    selectedTargetState: ConnectionState?,
    targetFingerprint: (UsbDevice) -> String,
    deviceConnectionStates: Map<String, ConnectionState>,
    pendingPermissionRequests: Set<Int>,
    armedFingerprints: Set<String>,
    uploading: Boolean,
    onSelectTarget: (UsbDevice) -> Unit,
    onChooseTarget: (UsbDevice) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedFingerprint = selectedTarget?.let { targetFingerprint(it.device) }
    val selectedArmed = selectedFingerprint != null && selectedFingerprint in armedFingerprints
    val selectedPendingPermission = selectedTarget?.let {
        it.device.deviceId in pendingPermissionRequests
    } == true
    val selectedConnected = selectedTargetState is ConnectionState.Connected

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Usb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "BadUSB target",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Choose the device that receives the payload.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            if (devices.isEmpty()) {
                Text(
                    text = "No USB devices detected. Connect an ESP32-S2/S3 before arming.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusAmber,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = devices.size > 1 && !uploading) {
                                    expanded = true
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                            border = BorderStroke(0.5.dp, NrOutline),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = selectedTarget?.displayName ?: "Select a device",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = badUsbStatusLabel(
                                            state = selectedTargetState,
                                            pendingPermission = selectedPendingPermission,
                                            armed = selectedArmed,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NrOnSurfaceVariant,
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = if (devices.size > 1) {
                                        "Open device list"
                                    } else {
                                        "Only one device available"
                                    },
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            devices.forEach { device ->
                                val fingerprint = targetFingerprint(device.device)
                                val state = deviceConnectionStates[fingerprint]
                                val pending = device.device.deviceId in pendingPermissionRequests
                                val armed = fingerprint in armedFingerprints
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(device.displayName)
                                            Text(
                                                text = badUsbStatusLabel(state, pending, armed),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (armed) StatusGreen else NrOnSurfaceVariant,
                                            )
                                        }
                                    },
                                    onClick = {
                                        onSelectTarget(device.device)
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Button(
                        onClick = { selectedTarget?.let { onChooseTarget(it.device) } },
                        enabled = selectedTarget != null &&
                            !selectedConnected &&
                            !selectedPendingPermission &&
                            !uploading,
                    ) {
                        Text(
                            text = when {
                                selectedPendingPermission -> "Requesting..."
                                selectedConnected -> "Connected"
                                selectedTarget == null -> "Choose"
                                else -> "Choose"
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigZone(
    targetReady: Boolean,
    selectedTarget: UsbSerialDevice?,
    selectedTargetState: ConnectionState?,
    uploading: Boolean,
    expanded: Boolean,
    selectedPayloadName: String?,
    savedScripts: Map<String, String>,
    onUseSavedScript: (String) -> Unit,
    mscMode: Boolean,
    onToggle: () -> Unit,
    onChoosePayload: () -> Unit,
    onClearPayload: () -> Unit,
    onMscChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "BadUSB configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Native USB HID DuckyScript",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                    )
                }
            }

            if (expanded) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    SavedScriptPicker(
                        names = savedScripts.keys.toList(),
                        selectedName = selectedPayloadName,
                        onSelect = onUseSavedScript,
                        enabled = !uploading,
                        label = "DuckyScript payload",
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onChoosePayload, enabled = !uploading) {
                            Text("Choose payload")
                        }
                        OutlinedButton(
                            onClick = onClearPayload,
                            enabled = !uploading && selectedPayloadName != null,
                        ) {
                            Text("Clear")
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Also expose mass storage",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = mscMode,
                            onCheckedChange = onMscChange,
                            enabled = !uploading,
                        )
                    }

                    if (!targetReady) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = badUsbRequirementMessage(selectedTarget, selectedTargetState),
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAmber,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultZone(
    selectedTarget: UsbSerialDevice?,
    selectedTargetState: ConnectionState?,
    uploading: Boolean,
    progress: Int,
    targetArmed: Boolean,
    selectedPayloadName: String?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = "Payload status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            val selectedConnected = selectedTargetState is ConnectionState.Connected
            val selectedSupported = isBadUsbSupported(selectedTargetState)
            StatusIndicator(
                label = when {
                    selectedTarget == null -> "No BadUSB target selected"
                    !selectedConnected -> "Target not connected"
                    !selectedSupported -> "Device not supported"
                    uploading -> "Uploading payload"
                    targetArmed -> "ARMED"
                    selectedPayloadName != null -> "Ready to arm"
                    else -> "No payload selected"
                },
                color = when {
                    selectedTarget == null -> StatusNeutral
                    !selectedConnected -> StatusAmber
                    !selectedSupported -> StatusRed
                    uploading -> StatusAmber
                    targetArmed -> StatusGreen
                    selectedPayloadName != null -> StatusGreen
                    else -> StatusNeutral
                },
            )

            if (selectedTarget != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Target: ${selectedTarget.displayName}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
                Text(
                    text = badUsbStatusLabel(selectedTargetState, false, targetArmed),
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }

            if (uploading || progress > 0) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Upload progress",
                    style = MaterialTheme.typography.labelLarge,
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$progress%",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
            }

            if (!isBadUsbSupported(selectedTargetState)) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = badUsbRequirementMessage(selectedTarget, selectedTargetState),
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else if (selectedPayloadName == null) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Choose a .txt or .conf DuckyScript file, then arm it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else if (targetArmed) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Payload is armed. Unplug and re-plug the device to execute it once.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusGreen,
                )
            }
        }
    }
}

private fun isBadUsbSupported(state: ConnectionState?): Boolean =
    state is ConnectionState.Connected &&
        state.chip in setOf("ESP32-S2", "ESP32-S3") &&
        (state.features.isEmpty() || "badusb" in state.features)

private fun badUsbStatusLabel(
    state: ConnectionState?,
    pendingPermission: Boolean,
    armed: Boolean = false,
): String = when {
    pendingPermission -> "Requesting permission..."
    armed -> if (state is ConnectionState.Connected && !state.deviceId.isNullOrBlank()) {
        "ARMED · ${state.deviceId}"
    } else {
        "ARMED"
    }
    state is ConnectionState.Connecting -> "Connecting..."
    state is ConnectionState.Failed -> "Connection failed"
    state is ConnectionState.Connected -> if (isBadUsbSupported(state)) {
        buildString {
            append("Connected")
            state.deviceId?.let { append(" · $it") }
        }
    } else {
        "Device not supported"
    }
    else -> "Not connected"
}

private fun badUsbRequirementMessage(
    selectedTarget: UsbSerialDevice?,
    state: ConnectionState?,
): String = when {
    selectedTarget == null -> "Choose a BadUSB target device."
    state !is ConnectionState.Connected -> "Connect the selected device before arming a payload."
    !isBadUsbSupported(state) ->
        "Device not supported. BadUSB requires an ESP32-S2 or ESP32-S3 with badusb firmware."
    else -> "The selected device is ready."
}
