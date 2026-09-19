package com.swp81x.nrsuite.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.swp81x.nrsuite.ui.components.SavedScriptPicker
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOnSurface
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed

private val specialKeys = listOf(
    "ENTER", "TAB", "ESC", "BACKSPACE", "DELETE",
    "UP", "DOWN", "LEFT", "RIGHT", "HOME", "END",
    "PAGEUP", "PAGEDOWN",
)

@Composable
fun BleScreen(
    connected: Boolean,
    advertising: Boolean,
    bleConnected: Boolean,
    peer: String,
    selectedPayloadName: String?,
    savedScripts: Map<String, String>,
    modifiers: Set<String>,
    modifierHold: Boolean,
    bleScriptRunning: Boolean,
    onUseSavedScript: (String) -> Unit,
    onChoosePayload: () -> Unit,
    onClearPayload: () -> Unit,
    onStartAdvertising: (String) -> Unit,
    onStop: () -> Unit,
    onRunPayload: () -> Unit,
    onSendText: (String) -> Unit,
    onRealtimeInput: (inserted: String, backspaces: Int) -> Unit,
    onSpecialKey: (String) -> Unit,
    onModifierChange: (String, Boolean) -> Unit,
    onModifierHoldChange: (Boolean) -> Unit,
    onMouseMove: (Int, Int) -> Unit,
    onMouseScroll: (Int) -> Unit,
    onMouseButton: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var advertiseName by rememberSaveable { mutableStateOf("NRSuite Keyboard") }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var realtimeText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(bleConnected) {
        if (!bleConnected && selectedTab == 1) {
            selectedTab = 0
        }
        if (!bleConnected) {
            realtimeText = ""
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Payload") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    enabled = connected && bleConnected,
                    text = { Text("Realtime HID") },
                )
            }

            Spacer(Modifier.height(10.dp))

            if (selectedTab == 0) {
                PayloadTab(
                    connected = connected,
                    advertising = advertising,
                    bleConnected = bleConnected,
                    bleScriptRunning = bleScriptRunning,
                    peer = peer,
                    advertiseName = advertiseName,
                    onAdvertiseNameChange = { advertiseName = it },
                    selectedPayloadName = selectedPayloadName,
                    savedScripts = savedScripts,
                    onUseSavedScript = onUseSavedScript,
                    onChoosePayload = onChoosePayload,
                    onClearPayload = onClearPayload,
                    onRunPayload = onRunPayload,
                    onSendText = onSendText,
                )
            } else {
                RealtimeHidTab(
                    connected = connected,
                    bleConnected = bleConnected,
                    modifiers = modifiers,
                    modifierHold = modifierHold,
                    realtimeText = realtimeText,
                    onRealtimeTextChange = { newText ->
                        val oldText = realtimeText
                        val prefix = commonPrefixLength(oldText, newText)
                        val backspaces = oldText.length - prefix
                        val inserted = newText.substring(prefix)
                        realtimeText = newText
                        onRealtimeInput(inserted, backspaces)
                    },
                    onClearText = { realtimeText = "" },
                    onSpecialKey = onSpecialKey,
                    onModifierChange = onModifierChange,
                    onModifierHoldChange = onModifierHoldChange,
                    onMouseMove = onMouseMove,
                    onMouseScroll = onMouseScroll,
                    onMouseButton = onMouseButton,
                )
            }
        }

        if (connected && advertising) {
            FloatingActionButton(
                onClick = onStop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = StatusRed,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Stop, contentDescription = "Stop BLE")
            }
        } else if (connected) {
            FloatingActionButton(
                onClick = { onStartAdvertising(advertiseName) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = NrAccent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Start BLE")
            }
        }
    }
}

@Composable
private fun PayloadTab(
    connected: Boolean,
    advertising: Boolean,
    bleConnected: Boolean,
    bleScriptRunning: Boolean,
    peer: String,
    advertiseName: String,
    onAdvertiseNameChange: (String) -> Unit,
    selectedPayloadName: String?,
    savedScripts: Map<String, String>,
    onUseSavedScript: (String) -> Unit,
    onChoosePayload: () -> Unit,
    onClearPayload: () -> Unit,
    onRunPayload: () -> Unit,
    onSendText: (String) -> Unit,
) {
    var sendText by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ConfigCard(
            connected = connected,
            bleConnected = bleConnected,
            bleScriptRunning = bleScriptRunning,
            advertising = advertising,
            advertiseName = advertiseName,
            onAdvertiseNameChange = onAdvertiseNameChange,
            selectedPayloadName = selectedPayloadName,
            savedScripts = savedScripts,
            onUseSavedScript = onUseSavedScript,
            onChoosePayload = onChoosePayload,
            onClearPayload = onClearPayload,
            onRunPayload = onRunPayload,
        )

        StatusCard(connected = connected, advertising = advertising, bleConnected = bleConnected, peer = peer)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(0.5.dp, NrOutline),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Send line", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = sendText,
                    onValueChange = { sendText = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = connected && bleConnected,
                    label = { Text("Text or DuckyScript line") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (sendText.isNotBlank()) {
                            onSendText(sendText)
                            sendText = ""
                        }
                    },
                    enabled = connected && bleConnected && sendText.isNotBlank() && !bleScriptRunning,
                ) {
                    Text(if (bleScriptRunning) "Busy..." else "Send")
                }
            }
        }
    }
}

@Composable
private fun RealtimeHidTab(
    connected: Boolean,
    bleConnected: Boolean,
    modifiers: Set<String>,
    modifierHold: Boolean,
    realtimeText: String,
    onRealtimeTextChange: (String) -> Unit,
    onClearText: () -> Unit,
    onSpecialKey: (String) -> Unit,
    onModifierChange: (String, Boolean) -> Unit,
    onModifierHoldChange: (Boolean) -> Unit,
    onMouseMove: (Int, Int) -> Unit,
    onMouseScroll: (Int) -> Unit,
    onMouseButton: (String, Boolean) -> Unit,
) {
    val enabled = connected && bleConnected

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(0.5.dp, NrOutline),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = "Realtime keyboard",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (enabled) "Types as you type. Enter sends Return." else "Connect a BLE host to enable realtime input.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) StatusGreen else StatusAmber,
                )

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Modifiers",
                    style = MaterialTheme.typography.labelMedium,
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("CTRL", "SHIFT", "ALT", "GUI").forEach { key ->
                        val active = key in modifiers
                        OutlinedButton(
                            onClick = { onModifierChange(key, !active) },
                            enabled = enabled,
                        ) {
                            Text(if (active) "$key ON" else key)
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Hold modifiers", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (modifierHold) {
                                "Modifiers stay active until tapped off."
                            } else {
                                "Modifiers release after the next key/text input."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = modifierHold,
                        onCheckedChange = onModifierHoldChange,
                        enabled = enabled,
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Special keys",
                    style = MaterialTheme.typography.labelMedium,
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    specialKeys.forEach { key ->
                        OutlinedButton(
                            onClick = { onSpecialKey(key) },
                            enabled = enabled,
                        ) {
                            Text(key)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = realtimeText,
                    onValueChange = onRealtimeTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    minLines = 2,
                    maxLines = 4,
                    label = { Text("Type here") },
                    placeholder = { Text("Realtime keyboard input") },
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onClearText, enabled = enabled && realtimeText.isNotEmpty()) {
                        Text("Clear box")
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(0.5.dp, NrOutline),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = "Mouse",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Drag the touchpad to move. Buttons click. Use scroll for wheel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )

                Spacer(Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.45f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NrSurfaceVariant)
                        .pointerInput(enabled) {
                            if (!enabled) return@pointerInput
                            var accumulatedX = 0f
                            var accumulatedY = 0f
                            detectDragGestures(
                                onDragStart = {
                                    accumulatedX = 0f
                                    accumulatedY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    accumulatedX += dragAmount.x
                                    accumulatedY += dragAmount.y
                                    val dx = accumulatedX.roundToInt()
                                    val dy = accumulatedY.roundToInt()
                                    if (dx != 0 || dy != 0) {
                                        accumulatedX -= dx
                                        accumulatedY -= dy
                                        onMouseMove(dx, dy)
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (enabled) "Touchpad" else "Connect a host first",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            onMouseButton("LEFT", true)
                            onMouseButton("LEFT", false)
                        },
                        enabled = enabled,
                    ) { Text("Left") }
                    OutlinedButton(
                        onClick = {
                            onMouseButton("MIDDLE", true)
                            onMouseButton("MIDDLE", false)
                        },
                        enabled = enabled,
                    ) { Text("Middle") }
                    OutlinedButton(
                        onClick = {
                            onMouseButton("RIGHT", true)
                            onMouseButton("RIGHT", false)
                        },
                        enabled = enabled,
                    ) { Text("Right") }
                }

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onMouseScroll(1) },
                        enabled = enabled,
                    ) { Text("Scroll up") }
                    OutlinedButton(
                        onClick = { onMouseScroll(-1) },
                        enabled = enabled,
                    ) { Text("Scroll down") }
                }
            }
        }
    }
}

@Composable
private fun ConfigCard(
    connected: Boolean,
    bleConnected: Boolean,
    bleScriptRunning: Boolean,
    advertising: Boolean,
    advertiseName: String,
    onAdvertiseNameChange: (String) -> Unit,
    selectedPayloadName: String?,
    savedScripts: Map<String, String>,
    onUseSavedScript: (String) -> Unit,
    onChoosePayload: () -> Unit,
    onClearPayload: () -> Unit,
    onRunPayload: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = NrAccent,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "BLE HID",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "BadBLE payloads and realtime HID",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = advertiseName,
                onValueChange = onAdvertiseNameChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !advertising,
                label = { Text("Advertised name") },
                singleLine = true,
            )

            Spacer(Modifier.height(10.dp))
            Text("DuckyScript payload", style = MaterialTheme.typography.labelLarge)
            Text(
                text = selectedPayloadName ?: "No payload selected",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            SavedScriptPicker(
                names = savedScripts.keys.toList(),
                selectedName = selectedPayloadName?.takeIf { it in savedScripts },
                onSelect = onUseSavedScript,
                onDelete = {},
                enabled = !bleScriptRunning,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onChoosePayload, enabled = !bleScriptRunning) {
                    Text("Choose payload")
                }
                OutlinedButton(
                    onClick = onClearPayload,
                    enabled = !bleScriptRunning && selectedPayloadName != null,
                ) {
                    Text("Clear")
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onRunPayload,
                enabled = connected && bleConnected && selectedPayloadName != null && !bleScriptRunning,
            ) {
                Text(if (bleScriptRunning) "Running payload..." else "Run payload")
            }
        }
    }
}

@Composable
private fun StatusCard(
    connected: Boolean,
    advertising: Boolean,
    bleConnected: Boolean,
    peer: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("BLE status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            StatusIndicator(
                label = when {
                    !connected -> "No device connected"
                    bleConnected -> "Host connected"
                    advertising -> "Advertising / waiting for host"
                    else -> "Idle"
                },
                color = when {
                    !connected -> StatusNeutral
                    bleConnected -> StatusGreen
                    advertising -> StatusAmber
                    else -> StatusNeutral
                },
            )
            if (peer.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Peer: $peer",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
            }
        }
    }
}

private fun commonPrefixLength(a: String, b: String): Int {
    val max = minOf(a.length, b.length)
    var index = 0
    while (index < max && a[index] == b[index]) index++
    return index
}
