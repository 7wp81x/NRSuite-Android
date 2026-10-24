package com.swp81x.nrsuite.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.hid.PinAttemptRecord
import com.swp81x.nrsuite.core.hid.PinBruteforceMode
import com.swp81x.nrsuite.core.hid.PinBruteforceSource
import com.swp81x.nrsuite.core.hid.PinBruteforceUiState
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusRed

@Composable
fun PinBruteforceScreen(
    connected: Boolean,
    state: PinBruteforceUiState,
    attempts: List<PinAttemptRecord>,
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
    onSessionKeyChange: (String) -> Unit,
    onWordlistSelected: (android.net.Uri, String?) -> Unit,
    onClearWordlist: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRefresh: () -> Unit,
    onClearSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var configExpanded by remember { mutableStateOf(true) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var confirmStart by remember { mutableStateOf(false) }
    val controlsEnabled = connected && !state.running && !state.starting
    val sessionActive =
        state.persistedInNvs && state.state in setOf("armed", "running", "paused")
    val configControlsEnabled = controlsEnabled && !sessionActive
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
            StatusCard(state, connected = connected, onDisarm = onStop)
            Spacer(Modifier.height(10.dp))
            WarningCard()
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
                onSessionKeyChange = onSessionKeyChange,
                onChooseWordlist = { wordlistPicker.launch(arrayOf("text/plain")) },
                onClearWordlist = onClearWordlist,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = connected && !state.running,
                ) {
                    Text("Refresh status")
                }
                OutlinedButton(
                    onClick = onClearSession,
                    enabled = connected && !state.running,
                ) {
                    Text("Clear session")
                }
            }
            Spacer(Modifier.height(10.dp))
            if (attempts.isNotEmpty()) {
                AttemptsCard(attempts)
            }
            Spacer(Modifier.height(80.dp))
        }

        if (connected && state.source != null && !state.running) {
            ModuleActionFab(
                onClick = {
                    if (state.running) {
                        onStop()
                    } else {
                        confirmStart = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                enabled = connected && !state.starting,
                containerColor = if (state.running) StatusRed else MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                if (state.starting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        imageVector = if (state.running) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (state.running) "Stop PIN bruteforce" else "Arm PIN bruteforce",
                    )
                }
            }
        }
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("Arm PIN bruteforce?") },
            text = {
                Text(
                    "Source: ${state.source?.wireName ?: "range"}\n" +
                        "Max attempts: ${state.maxAttempts}\n" +
                        (if (state.estimatedTotal > 0) {
                            "Estimated total: ${state.estimatedTotal}\n"
                        } else {
                            ""
                        }) +
                        "\nThis will type PINs through HID into the target device. " +
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
                ) {
                    Text("Start")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmStart = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun StatusCard(
    state: PinBruteforceUiState,
    connected: Boolean,
    onDisarm: () -> Unit,
) {
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
                        text = "PIN Bruteforce",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (connected) "Device connected" else "Device not connected",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (connected) StatusGreen else StatusRed,
                    )
                    Text(
                        text = "State: ${state.state} · ${state.mode.wireName}",
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
            if (state.estimatedTotal > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Estimated total: ${state.estimatedTotal} attempt(s)" +
                        if (state.stopReason.isNotBlank()) " · stop: ${state.stopReason}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
            when {
                state.lastError.isNotBlank() -> {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = state.lastError,
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusRed,
                    )
                }
                state.state == "armed" -> {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "ARMED",
                        style = MaterialTheme.typography.labelLarge,
                        color = StatusGreen,
                    )
                    Text(
                        text = "Unplug and re-plug the device to execute this one-shot HID session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusAmber,
                    )
                    TextButton(onClick = onDisarm) {
                        Text("Disarm")
                    }
                }
                state.state == "starting" -> {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Arming...",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusAmber,
                    )
                }
            }
        }
    }
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
                text = "Supervised authorized testing only. Android, Windows, and iOS " +
                    "devices rate-limit attempts, and some devices erase data after too " +
                    "many failed PINs. HID cannot confirm success.",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConfigCard(
    connected: Boolean,
    state: PinBruteforceUiState,
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
                            PinBruteforceSource.RANGE -> "Range mode"
                            PinBruteforceSource.WORDLIST -> "Wordlist mode"
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
                if (state.persistedInNvs && state.state in setOf("armed", "running", "paused")) {
                    Text(
                        text = "Session in progress: range/source fields are locked. " +
                            "Last PIN: ${state.lastPin.ifBlank { "-" }} · " +
                            "Attempts: ${state.attemptsTotal}. Clear the session to edit configuration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusAmber,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Text("Source", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NrFilterChip(
                        selected = state.source == PinBruteforceSource.RANGE,
                        onClick = { if (controlsEnabled) onSourceChange(PinBruteforceSource.RANGE.wireName) },
                        enabled = controlsEnabled,
                        label = "Range",
                    )
                    NrFilterChip(
                        selected = state.source == PinBruteforceSource.WORDLIST,
                        onClick = { if (controlsEnabled) onSourceChange(PinBruteforceSource.WORDLIST.wireName) },
                        enabled = controlsEnabled,
                        label = "Wordlist",
                    )
                }

                if (state.source == null) {
                    Text(
                        text = "Select Range or Wordlist to configure and arm a session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                } else if (state.source == PinBruteforceSource.RANGE) {
                    Spacer(Modifier.height(8.dp))
                    NumericField("PIN length (4-12)", state.pinLength.toString(), controlsEnabled) {
                        onPinLengthChange(it.toIntOrNull() ?: state.pinLength)
                    }
                    TextField("Start", state.start, controlsEnabled, onStartChange)
                    TextField("End", state.end, controlsEnabled, onEndChange)
                    Text(
                        text = "Range mode is digits 0-9 only. Use Wordlist for custom characters.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onChooseWordlist,
                        enabled = controlsEnabled,
                    ) {
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
                NumericField("Max attempts (safety cap)", state.maxAttempts.toString(), controlsEnabled) {
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
                    NumericField("Start delay after re-plug (ms)", state.startDelayMs.toString(), controlsEnabled) {
                        onStartDelayChange(it.toIntOrNull() ?: state.startDelayMs)
                    }
                    TextField("Session key", state.sessionKey, controlsEnabled, onSessionKeyChange)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Continue on re-arm", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        NrFilterChip(
                            selected = state.autoResume,
                            onClick = { if (controlsEnabled) onAutoResumeChange(!state.autoResume) },
                            enabled = controlsEnabled,
                            label = if (state.autoResume) "On" else "Off",
                        )
                    }
                }

                if (!connected) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Connect an ESP32-S2/S3 before starting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusRed,
                    )
                }
            }
        }
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
            if (newValue.length <= 6 && newValue.all { it.isDigit() }) {
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
private fun AttemptsCard(attempts: List<PinAttemptRecord>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "Attempt history",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            if (attempts.isEmpty()) {
                Text(
                    text = "No attempts yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                ) {
                    items(attempts.asReversed(), key = { it.attemptsTotal }) { attempt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "#${attempt.attemptsTotal}  ${attempt.pin}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
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
}
