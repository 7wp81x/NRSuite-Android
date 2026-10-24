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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.ir.IrLearnedButton
import com.swp81x.nrsuite.core.ir.IrReceivedSignal
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun IrReceiverScreen(
    esp32: ConnectionState.Connected?,
    pendingCapture: IrReceivedSignal?,
    learnedButtons: List<IrLearnedButton>,
    nextButtonNumber: Int,
    suggestedRemoteFileName: String,
    savingRemote: Boolean,
    txAvailable: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onStartReceiver: () -> Unit,
    onStopReceiver: () -> Unit,
    onSavePending: (name: String) -> Unit,
    onDiscardPending: () -> Unit,
    onReplayPending: () -> Unit,
    onDeleteLearned: (id: String) -> Unit,
    onRenameLearned: (id: String, name: String) -> Unit,
    onReplayLearned: (id: String) -> Unit,
    onClearLearned: () -> Unit,
    onSaveRemote: (remoteName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var remoteFileName by rememberSaveable { mutableStateOf(suggestedRemoteFileName) }
    var renameTarget by remember { mutableStateOf<IrLearnedButton?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    var menuButtonId by remember { mutableStateOf<String?>(null) }
    var pendingName by rememberSaveable { mutableStateOf("") }
    var pendingNameCaptureId by rememberSaveable { mutableStateOf("") }

    val defaultPendingName = "btn_%02d".format(nextButtonNumber)
    LaunchedEffect(pendingCapture?.id) {
        if (pendingCapture != null && pendingCapture.id != pendingNameCaptureId) {
            pendingName = defaultPendingName
            pendingNameCaptureId = pendingCapture.id
        }
    }
    LaunchedEffect(Unit) { onRefresh() }
    LaunchedEffect(suggestedRemoteFileName) {
        if (remoteFileName.startsWith("captured_remote_") &&
            remoteFileName != suggestedRemoteFileName
        ) {
            remoteFileName = suggestedRemoteFileName
        }
    }

    val listening = esp32?.irRxActive == true
    val hasIrRx = esp32 != null && "ir_rx" in esp32.features
    val canToggleReceiver = hasIrRx && esp32.irRxGpio != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NrSurface),
            border = BorderStroke(0.5.dp, NrOutline),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "IR Receiver",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Authorized testing only.",
                            style = MaterialTheme.typography.labelSmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = onRefresh) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.width(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Refresh")
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (esp32 == null) {
                    Text(
                        text = "No ESP32 connected. Connect a device with IR RX support.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusAmber,
                    )
                } else {
                    Text(
                        text = "Connected: ${esp32.chip ?: "ESP32"} · " +
                            "${esp32.firmwareVersion ?: "unknown firmware"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusGreen,
                    )
                    Text(
                        text = when {
                            !hasIrRx -> "Firmware does not advertise IR RX support."
                            esp32.irRxGpio == null -> "Configure an IR RX GPIO in IR Configuration."
                            listening -> "● Receiving on GPIO ${esp32.irRxGpio}."
                            pendingCapture != null || learnedButtons.isNotEmpty() ->
                                "○ Receiver stopped · captured buttons retained."
                            else -> "○ Receiver stopped."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            !hasIrRx -> StatusRed
                            esp32.irRxGpio == null -> StatusAmber
                            listening -> StatusGreen
                            else -> StatusAmber
                        },
                    )
                    if (canToggleReceiver) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = if (listening) onStopReceiver else onStartReceiver,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (listening) StatusRed else StatusGreen,
                            ),
                        ) {
                            if (listening) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Stop Receiver")
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Start Receiver")
                            }
                        }
                    }
                }
            }
        }

        if (errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
                border = BorderStroke(0.5.dp, StatusRed),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusRed,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        if (pendingCapture != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (pendingCapture.overflow) {
                        NrSurface
                    } else {
                        StatusGreen.copy(alpha = 0.10f)
                    },
                ),
                border = BorderStroke(
                    1.dp,
                    if (pendingCapture.overflow) StatusAmber else StatusGreen,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        text = "Pending capture",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (pendingCapture.overflow) StatusAmber else StatusGreen,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${pendingCapture.protocol} · ${pendingCapture.timingsUs.size} timings · " +
                            "${pendingCapture.frequencyHz} Hz" +
                            (pendingCapture.gpio?.let { " · GPIO $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = NrOnSurfaceVariant,
                    )
                    Text(
                        text = pendingCapture.timingsUs.take(24).joinToString(" ") +
                            if (pendingCapture.timingsUs.size > 24) " …" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = NrOnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (pendingCapture.overflow) {
                        Text(
                            text = "Capture buffer overflowed; this signal may be incomplete.",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusAmber,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pendingName,
                        onValueChange = { pendingName = it },
                        label = { Text("Button name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onSavePending(pendingName) },
                            enabled = pendingName.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Save Button")
                        }
                        OutlinedButton(
                            onClick = onReplayPending,
                            enabled = txAvailable,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Replay")
                        }
                    }
                    TextButton(onClick = onDiscardPending) { Text("Discard capture") }
                }
            }
        } else if (listening) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
                border = BorderStroke(0.5.dp, NrOutline),
                shape = RoundedCornerShape(10.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Waiting for an IR signal…", fontWeight = FontWeight.SemiBold)
                    Text(
                        "A new distinct signal replaces any previous unsaved capture.",
                        style = MaterialTheme.typography.labelSmall,
                        color = NrOnSurfaceVariant,
                    )
                }
            }
        }

        if (learnedButtons.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Captured buttons · ${learnedButtons.size}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClearLearned) { Text("Clear") }
            }
            learnedButtons.forEach { button ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = NrSurface),
                    border = BorderStroke(0.5.dp, NrOutline),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = button.name,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${button.timingsUs.size} timings · " +
                                    "${button.frequencyHz} Hz",
                                style = MaterialTheme.typography.labelSmall,
                                color = NrOnSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { onReplayLearned(button.id) },
                            enabled = txAvailable,
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Replay ${button.name}")
                        }
                        Box {
                            IconButton(onClick = { menuButtonId = button.id }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Button actions")
                            }
                            DropdownMenu(
                                expanded = menuButtonId == button.id,
                                onDismissRequest = { menuButtonId = null },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = {
                                        renameTarget = button
                                        renameText = button.name
                                        menuButtonId = null
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        onDeleteLearned(button.id)
                                        menuButtonId = null
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NrSurface),
            border = BorderStroke(0.5.dp, NrOutline),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = "Save captured remote",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = remoteFileName,
                    onValueChange = { remoteFileName = it },
                    label = { Text("Remote / .ir file name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onSaveRemote(remoteFileName) },
                    enabled = learnedButtons.isNotEmpty() && remoteFileName.isNotBlank() && !savingRemote,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (savingRemote) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Saving...")
                    } else {
                        Text("Save remote as .ir")
                    }
                }
                Text(
                    text = "Saves ${learnedButtons.size} captured button(s) under NRSuite/IR/Library.",
                    style = MaterialTheme.typography.labelSmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
    }

    renameTarget?.let { button ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename button") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Button name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameLearned(button.id, renameText)
                        renameTarget = null
                    },
                    enabled = renameText.isNotBlank(),
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            },
        )
    }
}

private fun formatCaptureTimestamp(value: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(value))
