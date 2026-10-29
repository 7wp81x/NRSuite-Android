package com.swp81x.nrsuite.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.MainViewModel
import com.swp81x.nrsuite.WpsPixieUiState
import com.swp81x.nrsuite.core.wps.WpsPixieMaterial
import com.swp81x.nrsuite.ui.theme.NrOnSurface
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusRed

@Composable
fun WpsPixieRoute(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.wpsPixie.collectAsState()
    val context = LocalContext.current

    val pcapPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.importWpsPixiePcap(uri, null)
        }
    }

    val serialPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.importWpsPixieSerialLog(uri, null)
        }
    }

    WpsPixieScreen(
        state = state,
        onChoosePcap = {
            pcapPicker.launch(
                arrayOf(
                    "application/vnd.tcpdump.pcap",
                    "application/octet-stream",
                    "*/*",
                ),
            )
        },
        onChooseSerialLog = {
            serialPicker.launch(arrayOf("text/plain", "text/*", "*/*"))
        },
        onClear = viewModel::clearWpsPixieMaterial,
        onAuthKeyChange = viewModel::setWpsPixieAuthKey,
        onStart = viewModel::startWpsPixieCrack,
        onStop = viewModel::stopWpsPixieCrack,
        modifier = modifier,
    )
}

@Composable
fun WpsPixieScreen(
    state: WpsPixieUiState,
    onChoosePcap: () -> Unit,
    onChooseSerialLog: () -> Unit,
    onClear: () -> Unit,
    onAuthKeyChange: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val captured = state.captured
    val canStart = !state.running &&
        !state.validating &&
        captured != null &&
        state.authKeyHex.length == 64

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
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Offline Pixiewps Cracker",
                            fontWeight = FontWeight.SemiBold,
                            color = NrOnSurface,
                        )
                        Text(
                            text = "Recover a WPS PIN from registrar M1/M2/M3 material.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onChoosePcap, enabled = !state.running && !state.validating) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Choose PCAP")
                    }
                    OutlinedButton(onClick = onChooseSerialLog, enabled = !state.running && !state.validating) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import serial log")
                    }
                }

                if (state.sourceName != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "${state.sourceKind.orEmpty()}: ${state.sourceName}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = NrOnSurface,
                            )
                            Text(
                                text = state.status,
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    state.error != null -> StatusRed
                                    state.result != null -> StatusGreen
                                    state.validating -> StatusAmber
                                    else -> NrOnSurfaceVariant
                                },
                            )
                        }
                        IconButton(
                            onClick = onClear,
                            enabled = !state.running && !state.validating,
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Clear imported material",
                                tint = NrOnSurfaceVariant,
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.status,
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Classic .pcap is supported directly. Convert .pcapng first " +
                            "with: editcap -F pcap input.pcapng output.pcap",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = NrOnSurfaceVariant,
                    )
                }

                if (state.validating) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        if (captured != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurface),
                border = BorderStroke(0.5.dp, NrOutline),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = "Captured WPS material",
                        fontWeight = FontWeight.SemiBold,
                        color = NrOnSurface,
                    )
                    if (!captured.ssid.isNullOrBlank() || !captured.bssid.isNullOrBlank()) {
                        Text(
                            text = listOfNotNull(
                                captured.ssid,
                                captured.bssid,
                                captured.channel?.let { "channel $it" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = StatusAmber,
                        )
                    }
                    MaterialRow("PKE", "${captured.pke.size} bytes · ${captured.pke.shortHex()}")
                    MaterialRow("PKR", "${captured.pkr.size} bytes · ${captured.pkr.shortHex()}")
                    MaterialRow("E-Nonce", captured.eNonce.hex())
                    MaterialRow("R-Nonce", captured.rNonce.hex())
                    MaterialRow("E-Hash1", captured.eHash1.shortHex())
                    MaterialRow("E-Hash2", captured.eHash2.shortHex())
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
                border = BorderStroke(0.5.dp, NrOutline),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    OutlinedTextField(
                        value = state.authKeyHex,
                        onValueChange = onAuthKeyChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.running,
                        label = { Text("AuthKey (64 hex characters)") },
                        supportingText = {
                            Text("A PCAP cannot derive AuthKey. Import the firmware serial log or paste it manually.")
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onStart, enabled = canStart) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Crack PIN")
                        }
                        if (state.running) {
                            OutlinedButton(onClick = onStop) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Stop")
                            }
                        }
                    }
                    if (state.running) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = state.progressText,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = NrOnSurfaceVariant,
                        )
                    }
                }
            }
        }

        state.error?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurface),
                border = BorderStroke(0.5.dp, StatusRed),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusRed,
                )
            }
        }

        state.result?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurface),
                border = BorderStroke(0.5.dp, StatusGreen),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Recovered WPS PIN",
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.SemiBold,
                            color = NrOnSurface,
                        )
                        IconButton(
                            onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(result.pin)) },
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy PIN", tint = StatusGreen)
                        }
                    }
                    Text(
                        text = result.pin,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = StatusGreen,
                    )
                    MaterialRow("Mode", result.mode.label)
                    MaterialRow("PSK1", result.psk1.shortHex())
                    MaterialRow("PSK2", result.psk2.shortHex())
                    MaterialRow("E-S1", result.eS1.hex())
                    MaterialRow("E-S2", result.eS2.hex())
                    result.seedN1?.let { MaterialRow("Seed N1", "0x${it.toString(16).padStart(8, '0')}") }
                }
            }
        }
    }
}

@Composable
private fun MaterialRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            modifier = Modifier.width(84.dp),
            style = MaterialTheme.typography.labelMedium,
            color = NrOnSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = NrOnSurface,
        )
    }
}

private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

private fun ByteArray.shortHex(): String {
    if (size <= 16) return hex()
    return "${copyOfRange(0, 8).hex()}…${copyOfRange(size - 8, size).hex()}"
}
