package com.swp81x.nrsuite.ui

import android.content.Intent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.rfid.RfidTagObservation
import com.swp81x.nrsuite.core.rfid.RfidUiState
import com.swp81x.nrsuite.ui.components.ModuleStatusCard
import com.swp81x.nrsuite.ui.components.ModuleStatusState
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed
import com.swp81x.nrsuite.ui.util.copyWithToast
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val RFID_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

@Composable
fun RfidScreen(
    connected: Boolean,
    state: RfidUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRefresh: () -> Unit,
    onClearHistory: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(connected) {
        if (connected) onRefresh()
    }
    DisposableEffect(Unit) {
        onDispose { onLeave() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 12.dp,
                top = 12.dp,
                end = 12.dp,
                bottom = 92.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                val statusState = when {
                    !connected || !state.connected -> ModuleStatusState.DISCONNECTED
                    state.errorMessage != null -> ModuleStatusState.ALERT
                    state.running -> ModuleStatusState.RUNNING
                    else -> ModuleStatusState.READY
                }
                ModuleStatusCard(
                    icon = Icons.Default.Nfc,
                    title = when {
                        !connected -> "ESP32 disconnected"
                        !state.configured -> "PN532 not configured"
                        state.scanning -> "Scanning for NFC tags"
                        state.connected -> "PN532 ready"
                        else -> "PN532 unavailable"
                    },
                    subtitle = when {
                        !connected -> "Connect an NRSuite ESP32 to use the PN532 reader."
                        !state.configured -> "Open RFID Config from the RFID category header and set SDA/SCL."
                        state.scanning -> "Present an authorized ISO14443A tag to the reader."
                        state.connected && state.firmwareVersion != null ->
                            "PN532 firmware ${state.firmwareVersion} · I2C"
                        else -> state.errorMessage ?: state.statusMessage ?: "Reader not available."
                    },
                    state = statusState,
                    extraContent = {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = buildString {
                                append("I2C")
                                state.sdaGpio?.let { append(" · SDA GPIO $it") }
                                state.sclGpio?.let { append(" · SCL GPIO $it") }
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = NrOnSurfaceVariant,
                        )
                        if (state.errorMessage != null && state.errorMessage != state.statusMessage) {
                            Text(
                                text = state.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusRed,
                            )
                        }
                    },
                )
            }

            state.lastTag?.let { tag ->
                item {
                    LastTagCard(
                        tag = tag,
                        onCopy = {
                            clipboard.copyWithToast(context, tag.uid, "UID copied")
                        },
                        onShare = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "NFC UID: ${tag.uid}\nATQA: ${tag.atqa}\n" +
                                        "SAK: ${tag.sak}\nType: ${tag.type}",
                                )
                            }
                            context.startActivity(
                                Intent.createChooser(intent, "Share NFC tag"),
                            )
                        },
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Scan history",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onClearHistory,
                        enabled = state.tags.isNotEmpty(),
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text("Clear")
                    }
                }
            }

            if (state.tags.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = NrSurface),
                        border = BorderStroke(0.5.dp, NrOutline),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Default.Nfc,
                                contentDescription = null,
                                tint = NrOnSurfaceVariant,
                                modifier = Modifier.size(32.dp),
                            )
                            Text(
                                text = "No tags yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Start scanning and present the authorized test tag.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NrOnSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(state.tags, key = { "${it.uid}-${it.observedAtMs}" }) { tag ->
                    HistoryRow(tag)
                }
            }
        }

        FloatingActionButton(
            onClick = {
                if (connected) {
                    if (state.running) onStop() else onStart()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = when {
                !connected -> StatusNeutral.copy(alpha = 0.45f)
                state.running -> StatusRed
                else -> NrAccent
            },
        ) {
            Icon(
                imageVector = if (state.running) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (state.running) "Stop RFID scan" else "Start RFID scan",
            )
        }
    }
}

@Composable
private fun LastTagCard(
    tag: RfidTagObservation,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Last tag",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.Nfc, contentDescription = "Copy UID")
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Share tag")
                }
            }
            SelectionContainer {
                Text(
                    text = tag.uid,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = tag.type,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "ATQA ${tag.atqa.ifBlank { "--" }}  ·  SAK ${tag.sak.ifBlank { "--" }}" +
                    "  ·  ${RFID_TIME_FORMAT.format(Instant.ofEpochMilli(tag.observedAtMs))}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = NrOnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryRow(tag: RfidTagObservation) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = tag.uid,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${tag.type} · ATQA ${tag.atqa} · SAK ${tag.sak}",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
            Text(
                text = RFID_TIME_FORMAT.format(Instant.ofEpochMilli(tag.observedAtMs)),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = NrOnSurfaceVariant,
            )
        }
    }
}
