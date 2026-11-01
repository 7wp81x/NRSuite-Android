package com.swp81x.nrsuite.ui

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.Pcap22000UiState
import com.swp81x.nrsuite.core.wpa.Pcap22000Candidate
import com.swp81x.nrsuite.ui.components.SavedItemDropdown
import com.swp81x.nrsuite.ui.theme.NrOnSurface
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusRed

@Composable
internal fun Pcap22000Screen(
    state: Pcap22000UiState,
    onAddPcaps: () -> Unit,
    onRemoveInput: (Int) -> Unit,
    onClearInputs: () -> Unit,
    onSetSsidOverride: (String, String) -> Unit,
    onRemoveSsidOverride: (String) -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var overrideBssid by rememberSaveable { mutableStateOf("") }
    var overrideSsid by rememberSaveable { mutableStateOf("") }
    val uniqueBssids = remember(state.candidates) {
        state.candidates.mapNotNull { it.bssidHex }.distinct().sorted()
    }
    val missingSsidBssids = remember(state.candidates, state.ssidOverrides) {
        state.candidates
            .filter { it.effectiveSsid.isNullOrBlank() }
            .mapNotNull { it.bssidHex }
            .distinct()
            .sorted()
    }
    LaunchedEffect(missingSsidBssids) {
        if (overrideBssid !in missingSsidBssids) {
            overrideBssid = missingSsidBssids.firstOrNull().orEmpty()
        }
    }

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
                Text(
                    text = "PCAP to .hs22000",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Merge classic PCAP captures into one Hashcat .22000 file. " +
                        "Duplicate exchanges are removed automatically and the best handshake is kept.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onAddPcaps, enabled = !state.analyzing) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add PCAP files")
                    }
                    if (state.inputs.isNotEmpty()) {
                        TextButton(onClick = onClearInputs, enabled = !state.analyzing) {
                            Text("Clear all")
                        }
                    }
                }
                state.inputs.forEachIndexed { index, input ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${index + 1}. ${input.name}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = NrOnSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(
                            onClick = { onRemoveInput(index) },
                            enabled = !state.analyzing,
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove PCAP")
                        }
                    }
                }
                if (state.analyzing) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = state.summary ?: "Analyzing...",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
            }
        }

        if (missingSsidBssids.isNotEmpty() || state.ssidOverrides.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurface),
                border = BorderStroke(0.5.dp, NrOutline),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "SSID overrides",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (missingSsidBssids.isNotEmpty()) {
                        SavedItemDropdown(
                            label = "Missing SSID BSSID",
                            selectedName = overrideBssid.takeIf { it.isNotBlank() },
                            names = missingSsidBssids,
                            selectedDisplayName = overrideBssid.takeIf { it.isNotBlank() },
                            itemSupportingText = { "Missing detected ESSID" },
                            onSelect = { overrideBssid = it },
                        )
                        OutlinedTextField(
                            value = overrideSsid,
                            onValueChange = { overrideSsid = it.take(32) },
                            label = { Text("ESSID") },
                            placeholder = { Text("Network name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        )
                        Button(
                            onClick = {
                                onSetSsidOverride(overrideBssid, overrideSsid)
                                overrideSsid = ""
                            },
                            enabled = overrideBssid.isNotBlank() && overrideSsid.isNotBlank(),
                        ) {
                            Text("Save override")
                        }
                    } else {
                        Text(
                            text = "All captured handshakes have an SSID.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                    state.ssidOverrides.entries.sortedBy { it.key }.forEach { (bssid, ssid) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "$ssid · $bssid",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { onRemoveSsidOverride(bssid) }) {
                                Text("Remove")
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
                    text = "Export preview",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.summary ?: "Add and analyze PCAP files to preview the export.",
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        state.error != null -> StatusRed
                        state.exportPreview?.lines?.isNotEmpty() == true -> StatusGreen
                        else -> NrOnSurfaceVariant
                    },
                )
                state.error?.let { error ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusRed,
                    )
                }
                if (state.lastExportCount != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Exported ${state.lastExportCount} handshake(s) successfully.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusGreen,
                    )
                    state.lastExportName?.let { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = NrOnSurfaceVariant,
                        )
                    }
                }
                if (state.exportPreview?.exportedCandidates?.isNotEmpty() == true) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (state.lastExportCount != null) {
                            "Exported handshakes"
                        } else {
                            "Ready to export"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = NrOnSurfaceVariant,
                    )
                    state.exportPreview.exportedCandidates.forEach { candidate ->
                        CandidateRow(candidate)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onExport,
                    enabled = !state.analyzing &&
                        state.exportPreview?.lines?.isNotEmpty() == true,
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export .22000")
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun CandidateRow(candidate: Pcap22000Candidate) {
    val ssid = candidate.effectiveSsid ?: "(missing SSID)"
    Text(
        text = ssid,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = NrOnSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        text = "${candidate.bssidHex ?: "?"} → ${candidate.stationHex ?: "?"}",
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        color = NrOnSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
