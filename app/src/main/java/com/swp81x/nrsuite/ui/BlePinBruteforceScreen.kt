package com.swp81x.nrsuite.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.hid.BlePinAttemptRecord
import com.swp81x.nrsuite.core.hid.BlePinBruteforceSource
import com.swp81x.nrsuite.core.hid.BlePinBruteforceUiState
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed

@Composable
fun BlePinBruteforceScreen(
    connected: Boolean,
    state: BlePinBruteforceUiState,
    attempts: List<BlePinAttemptRecord>,
    onSourceChange: (String) -> Unit,
    onPinLengthChange: (Int) -> Unit,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onMaxAttemptsChange: (Int) -> Unit,
    onPersistEveryChange: (Int) -> Unit,
    onAttemptsBeforeTimeoutChange: (Int) -> Unit,
    onCooldownChange: (Int) -> Unit,
    onInterAttemptChange: (Int) -> Unit,
    onTypingDelayChange: (Int) -> Unit,
    onStartDelayChange: (Int) -> Unit,
    onAutoResumeChange: (Boolean) -> Unit,
    onAutoStartOnConnectChange: (Boolean) -> Unit,
    onPauseOnDisconnectChange: (Boolean) -> Unit,
    onAutoReconnectChange: (Boolean) -> Unit,
    onSessionKeyChange: (String) -> Unit,
    onWordlistSelected: (android.net.Uri, String?) -> Unit,
    onClearWordlist: () -> Unit,
    onStartAdvertising: (String) -> Unit,
    onStopAdvertising: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClearSession: () -> Unit,
    onForgetBond: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var deviceName by rememberSaveable { mutableStateOf(state.deviceName.ifBlank { "NRSuite PIN Keyboard" }) }
    var configExpanded by remember { mutableStateOf(true) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var confirmStart by remember { mutableStateOf(false) }
    val connectedAndIdle = connected && !state.running && !state.starting
    val sessionActive = state.configured && state.state in setOf("ready", "running", "paused")
    val configControlsEnabled = connectedAndIdle && !sessionActive
    val wordlistPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            onWordlistSelected(uri, uri.lastPathSegment ?: "pin_wordlist.txt")
        }
    }

    LaunchedEffect(connected) {
        if (connected) onRefresh()
    }
    LaunchedEffect(state.deviceName) {
        if (state.deviceName.isNotBlank()) deviceName = state.deviceName
    }
    LaunchedEffect(state.running) {
        if (state.running) configExpanded = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            StatusCard(state = state, connected = connected)
            Spacer(Modifier.height(10.dp))
            WarningCard()
            Spacer(Modifier.height(10.dp))
            BleConnectionCard(
                connected = connected,
                state = state,
                deviceName = deviceName,
                onDeviceNameChange = { deviceName = it },
                onStartAdvertising = { onStartAdvertising(deviceName) },
                onStopAdvertising = onStopAdvertising,
                onForgetBond = onForgetBond,
                onDisconnect = onDisconnect,
                onRefresh = onRefresh,
            )
            Spacer(Modifier.height(10.dp))
            ConfigCard(
                connected = connected,
                state = state,
                controlsEnabled = configControlsEnabled,
                expanded = configExpanded,
                advancedExpanded = advancedExpanded,
                onToggle = { configExpanded = !configExpanded },
                onToggleAdvanced = { advancedExpanded = !advancedExpanded },
                onSourceChange = onSourceChange,
                onPinLengthChange = onPinLengthChange,
                onStartChange = onStartChange,
                onEndChange = onEndChange,
                onTimeoutChange = onTimeoutChange,
                onMaxAttemptsChange = onMaxAttemptsChange,
                onPersistEveryChange = onPersistEveryChange,
                onAttemptsBeforeTimeoutChange = onAttemptsBeforeTimeoutChange,
                onCooldownChange = onCooldownChange,
                onInterAttemptChange = onInterAttemptChange,
                onTypingDelayChange = onTypingDelayChange,
                onStartDelayChange = onStartDelayChange,
                onAutoResumeChange = onAutoResumeChange,
                onAutoStartOnConnectChange = onAutoStartOnConnectChange,
                onPauseOnDisconnectChange = onPauseOnDisconnectChange,
                onAutoReconnectChange = onAutoReconnectChange,
                onSessionKeyChange = onSessionKeyChange,
                onChooseWordlist = { wordlistPicker.launch(arrayOf("text/plain")) },
                onClearWordlist = onClearWordlist,
            )
            Spacer(Modifier.height(10.dp))
            ControlsCard(
                connected = connected,
                state = state,
                onStart = {
                    if (state.paused) onResume() else confirmStart = true
                },
                onPause = onPause,
                onResume = onResume,
                onStop = onStop,
                onNext = onNext,
                onPrevious = onPrevious,
                onClearSession = onClearSession,
            )
            Spacer(Modifier.height(10.dp))
            if (attempts.isNotEmpty()) {
                AttemptsCard(attempts)
            }
            Spacer(Modifier.height(80.dp))
        }

        if (connected && state.bleConnected && !state.running) {
            ModuleActionFab(
                onClick = {
                    if (state.paused) onResume() else confirmStart = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                enabled = state.source != null && !state.starting,
                containerColor = if (state.starting) StatusAmber else MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                if (state.starting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start BLE PIN bruteforce")
                }
            }
        } else if (connected && state.running) {
            ModuleActionFab(
                onClick = onStop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = StatusRed,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Stop, contentDescription = "Stop BLE PIN bruteforce")
            }
        }
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("Start BLE PIN bruteforce?") },
            text = {
                Text(
                    "Source: ${state.source?.wireName ?: "range"}\n" +
                        "Max attempts: ${state.maxAttempts}\n" +
                        (if (state.estimatedTotal > 0) "Estimated total: ${state.estimatedTotal}\n" else "") +
                        "\nThis types PINs over BLE HID into the paired host. " +
                        "HID cannot detect success. Failed attempts may lock or wipe " +
                        "the target. Use only on devices you own or are authorized to test.",
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
private fun StatusCard(state: BlePinBruteforceUiState, connected: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "BLE HID PIN Bruteforce",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (connected) "USB bridge connected" else "USB bridge disconnected",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (connected) StatusGreen else StatusRed,
                    )
                    Text(
                        text = "Session: ${state.state} · BLE: ${state.bleState}",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatusValue("Attempts", state.attemptsTotal.toString())
                StatusValue("Max", state.maxAttempts.toString())
                StatusValue("Progress", "${state.progressPercent}%")
                StatusValue("Last PIN", state.lastPin.ifBlank { "-" })
            }
            if (state.nextPin.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Next PIN: ${state.nextPin}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (state.cooldownActive) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Cooldown: ${(state.cooldownRemainingMs / 1000).coerceAtLeast(1)} s remaining",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusAmber,
                )
            }
            if (state.estimatedTotal > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Estimated total: ${state.estimatedTotal} attempt(s)" +
                        if (state.stopReason.isNotBlank()) " · stop: ${state.stopReason}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
            if (state.running && state.estimatedTotal > state.attemptsTotal) {
                val remaining = state.estimatedTotal - state.attemptsTotal
                val perAttemptMs = state.timeoutMs.toLong() + state.interAttemptMs.toLong() +
                    (state.pinLength.toLong() * state.typingDelayMs.toLong()) + 100L
                val etaSeconds = (remaining * perAttemptMs) / 1000L
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "ETA ~ ${formatEta(etaSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
            if (state.lastError.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = state.lastError,
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusRed,
                )
            }
        }
    }
}

@Composable
private fun BleConnectionCard(
    connected: Boolean,
    state: BlePinBruteforceUiState,
    deviceName: String,
    onDeviceNameChange: (String) -> Unit,
    onStartAdvertising: () -> Unit,
    onStopAdvertising: () -> Unit,
    onForgetBond: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "BLE transport",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            state.bleConnected -> "Connected"
                            state.bleAdvertising -> "Advertising"
                            else -> "Stopped"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            state.bleConnected -> StatusGreen
                            state.bleAdvertising -> StatusAmber
                            else -> StatusNeutral
                        },
                    )
                }
                OutlinedButton(onClick = onRefresh, enabled = connected) { Text("Refresh") }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Peer: ${state.peer.ifBlank { "-" }} · Bonded: ${if (state.bonded) "yes" else "no"}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
            )
            if (!state.bleAdvertising && !state.bleConnected) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = onDeviceNameChange,
                    label = { Text("Advertised name") },
                    singleLine = true,
                    enabled = connected && !state.running,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onStartAdvertising, enabled = connected && !state.running) {
                        Text("Start advertising")
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onStopAdvertising, enabled = connected) {
                        Text("Stop advertising")
                    }
                    OutlinedButton(onClick = onDisconnect, enabled = connected && state.bleConnected) {
                        Text("Disconnect")
                    }
                    OutlinedButton(onClick = onForgetBond, enabled = connected) {
                        Text("Forget bond")
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, StatusAmber),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(14.dp)) {
            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = StatusAmber)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Supervised authorized testing only. BLE HID cannot confirm success. " +
                    "Some hosts require the target to be unlocked for pairing, rate-limit input, " +
                    "or erase data after too many failed PINs. Paired hosts may reconnect automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConfigCard(
    connected: Boolean,
    state: BlePinBruteforceUiState,
    controlsEnabled: Boolean,
    expanded: Boolean,
    advancedExpanded: Boolean,
    onToggle: () -> Unit,
    onToggleAdvanced: () -> Unit,
    onSourceChange: (String) -> Unit,
    onPinLengthChange: (Int) -> Unit,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onMaxAttemptsChange: (Int) -> Unit,
    onPersistEveryChange: (Int) -> Unit,
    onAttemptsBeforeTimeoutChange: (Int) -> Unit,
    onCooldownChange: (Int) -> Unit,
    onInterAttemptChange: (Int) -> Unit,
    onTypingDelayChange: (Int) -> Unit,
    onStartDelayChange: (Int) -> Unit,
    onAutoResumeChange: (Boolean) -> Unit,
    onAutoStartOnConnectChange: (Boolean) -> Unit,
    onPauseOnDisconnectChange: (Boolean) -> Unit,
    onAutoReconnectChange: (Boolean) -> Unit,
    onSessionKeyChange: (String) -> Unit,
    onChooseWordlist: () -> Unit,
    onClearWordlist: () -> Unit,
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
                        text = "Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when (state.source) {
                            BlePinBruteforceSource.RANGE -> "Range mode"
                            BlePinBruteforceSource.WORDLIST -> "Wordlist mode"
                            null -> "Select a source"
                        },
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
                Spacer(Modifier.height(8.dp))
                if (state.configured && state.state in setOf("ready", "running", "paused")) {
                    Text(
                        text = "Session active: configuration is locked. " +
                            "Last PIN: ${state.lastPin.ifBlank { "-" }} · Attempts: ${state.attemptsTotal}. " +
                            "Clear the session to edit configuration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusAmber,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Text("Source", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NrFilterChip(
                        selected = state.source == BlePinBruteforceSource.RANGE,
                        onClick = { if (controlsEnabled) onSourceChange(BlePinBruteforceSource.RANGE.wireName) },
                        enabled = controlsEnabled,
                        label = "Range",
                    )
                    NrFilterChip(
                        selected = state.source == BlePinBruteforceSource.WORDLIST,
                        onClick = { if (controlsEnabled) onSourceChange(BlePinBruteforceSource.WORDLIST.wireName) },
                        enabled = controlsEnabled,
                        label = "Wordlist",
                    )
                }

                if (state.source == BlePinBruteforceSource.RANGE) {
                    Spacer(Modifier.height(8.dp))
                    NumericField("PIN length (4-12)", state.pinLength.toString(), controlsEnabled) {
                        onPinLengthChange(it.toIntOrNull() ?: state.pinLength)
                    }
                    TextField("Start", state.start, controlsEnabled, onStartChange)
                    TextField("End", state.end, controlsEnabled, onEndChange)
                } else if (state.source == BlePinBruteforceSource.WORDLIST) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onChooseWordlist, enabled = controlsEnabled) {
                        Text(if (state.wordlistUploading) "Uploading..." else "Choose wordlist (.txt)")
                    }
                    if (state.wordlistName != null) {
                        Text(
                            text = "${state.wordlistName} · ${state.wordlistLines} line(s) · " +
                                "${state.wordlistBytes} bytes" +
                                if (state.wordlistUploadedToDevice) " · uploaded" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.wordlistUploadedToDevice) StatusGreen else NrOnSurfaceVariant,
                        )
                        if (state.wordlistUploading) {
                            LinearProgressIndicator(
                                progress = { state.wordlistUploadProgress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (state.wordlistError != null) {
                            Text(
                                text = state.wordlistError,
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusRed,
                            )
                        }
                        TextButton(onClick = onClearWordlist, enabled = controlsEnabled) {
                            Text("Clear wordlist")
                        }
                    }
                    Text(
                        text = "Limit: 1000 lines / 16 KB. Lines longer than 64 characters are rejected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }

                NumericField("Timeout per attempt (ms)", state.timeoutMs.toString(), controlsEnabled) {
                    onTimeoutChange(it.toIntOrNull() ?: state.timeoutMs)
                }
                NumericField("Max attempts (hard cap)", state.maxAttempts.toString(), controlsEnabled) {
                    onMaxAttemptsChange(it.toIntOrNull() ?: state.maxAttempts)
                }
                NumericField(
                    "Attempts before cooldown (0 = off)",
                    state.attemptsBeforeTimeout.toString(),
                    controlsEnabled,
                ) {
                    onAttemptsBeforeTimeoutChange(it.toIntOrNull() ?: state.attemptsBeforeTimeout)
                }
                NumericField("Cooldown duration (ms)", state.cooldownMs.toString(), controlsEnabled) {
                    onCooldownChange(it.toIntOrNull() ?: state.cooldownMs)
                }
                TextButton(onClick = onToggleAdvanced, enabled = controlsEnabled) {
                    Text(if (advancedExpanded) "Hide advanced" else "Advanced options")
                }
                if (advancedExpanded) {
                    NumericField("Persist every N attempts", state.persistEvery.toString(), controlsEnabled) {
                        onPersistEveryChange(it.toIntOrNull() ?: state.persistEvery)
                    }
                    NumericField("Inter-attempt delay (ms)", state.interAttemptMs.toString(), controlsEnabled) {
                        onInterAttemptChange(it.toIntOrNull() ?: state.interAttemptMs)
                    }
                    NumericField("Typing delay per character (ms)", state.typingDelayMs.toString(), controlsEnabled) {
                        onTypingDelayChange(it.toIntOrNull() ?: state.typingDelayMs)
                    }
                    NumericField("Start delay after connect (ms)", state.startDelayMs.toString(), controlsEnabled) {
                        onStartDelayChange(it.toIntOrNull() ?: state.startDelayMs)
                    }
                    TextField("Session key", state.sessionKey, controlsEnabled, onSessionKeyChange)
                    ToggleRow("Continue on re-arm", state.autoResume, controlsEnabled, onAutoResumeChange)
                    ToggleRow("Auto-start after reconnect", state.autoStartOnConnect, controlsEnabled, onAutoStartOnConnectChange)
                    ToggleRow("Pause on BLE disconnect", state.pauseOnDisconnect, controlsEnabled, onPauseOnDisconnectChange)
                    ToggleRow("Restart advertising on disconnect", state.autoReconnect, controlsEnabled, onAutoReconnectChange)
                }

                if (!connected) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Connect an ESP32-C3/S3/classic ESP32 before starting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusRed,
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlsCard(
    connected: Boolean,
    state: BlePinBruteforceUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClearSession: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "Live controls",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Start, pause, resume, stop, and step the sequence while the USB CDC bridge stays connected.",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onStart,
                    enabled = connected && state.bleConnected && !state.running && !state.starting,
                ) { Text(if (state.paused) "Resume" else "Start") }
                OutlinedButton(onClick = onPause, enabled = connected && state.running) { Text("Pause") }
                OutlinedButton(onClick = onResume, enabled = connected && state.paused && state.bleConnected) { Text("Resume") }
                OutlinedButton(onClick = onStop, enabled = connected && (state.running || state.paused || state.state == "ready")) { Text("Stop") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPrevious, enabled = connected && state.configured && state.attemptsTotal > 0) { Text("Previous") }
                OutlinedButton(onClick = onNext, enabled = connected && state.configured && !state.finished) { Text("Next / Skip") }
                OutlinedButton(onClick = onClearSession, enabled = connected && !state.running) { Text("Clear session") }
            }
        }
    }
}

@Composable
private fun AttemptsCard(attempts: List<BlePinAttemptRecord>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "Live attempt feed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
            ) {
                items(attempts.asReversed()) { attempt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "#${attempt.attemptsTotal}  ${attempt.pin}" +
                                if (attempt.skipped) "  [skipped]" else "",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = attempt.timestamp,
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun formatEta(seconds: Long): String {
    if (seconds < 60) return "${seconds}s"
    val minutes = seconds / 60
    val remainder = seconds % 60
    if (minutes < 60) return "${minutes}m ${remainder}s"
    val hours = minutes / 60
    val minuteRemainder = minutes % 60
    return "${hours}h ${minuteRemainder}m"
}

@Composable
private fun StatusValue(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = NrOnSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun TextField(
    label: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NumericField(
    label: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    var text by remember { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(value, focused) {
        if (!focused) text = value
    }

    OutlinedTextField(
        value = text,
        onValueChange = { newValue ->
            if (newValue.length <= 10 && newValue.all { it.isDigit() }) {
                text = newValue
                if (newValue.isNotEmpty()) onValueChange(newValue)
            }
        },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState -> focused = focusState.isFocused },
    )
}

@Composable
private fun ToggleRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        NrFilterChip(
            selected = selected,
            onClick = { if (enabled) onToggle(!selected) },
            enabled = enabled,
            label = if (selected) "On" else "Off",
        )
    }
}
