package com.swp81x.nrsuite.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.credentials.CredentialSession
import com.swp81x.nrsuite.core.handshake.CapturedHandshakeEntry
import com.swp81x.nrsuite.core.handshake.HandshakeCrackStatus
import com.swp81x.nrsuite.core.wpa.CrackerSessionSnapshot
import com.swp81x.nrsuite.core.wpa.CrackerSessionStatus
import com.swp81x.nrsuite.core.wpa.CrackerSourceType
import com.swp81x.nrsuite.core.wpa.CrackerTargetSnapshot
import com.swp81x.nrsuite.core.wpa.CrackerTargetStatus
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.components.NrStatusAction
import com.swp81x.nrsuite.ui.theme.NrOnSurface
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusRed
import com.swp81x.nrsuite.ui.util.copyWithToast
import java.io.File

@Composable
fun WpaCrackerScreen(
    sessions: List<CredentialSession>,
    capturedHandshakes: List<CapturedHandshakeEntry>,
    selectedSession: CredentialSession?,
    selectedCapturedPath: String?,
    sourceType: CrackerSourceType,
    customPcapName: String?,
    imported22000Name: String?,
    targets: List<CrackerTargetSnapshot>,
    targetsLoading: Boolean,
    targetMessage: String?,
    manualMode: Boolean,
    manualSsid: String,
    wordlistName: String?,
    running: Boolean,
    paused: Boolean,
    tested: Long,
    speed: Double,
    result: String?,
    status: String,
    currentTargetIndex: Int,
    selectedTargetCount: Int,
    currentTargetSsid: String,
    progressFraction: Float,
    progressKnown: Boolean,
    savedSessions: List<CrackerSessionSnapshot>,
    onSourceSelected: (CrackerSourceType) -> Unit,
    onSelectSession: (CredentialSession?) -> Unit,
    onSelectCapturedHandshake: (CapturedHandshakeEntry) -> Unit,
    onChooseCustomPcap: () -> Unit,
    onChoose22000: () -> Unit,
    onClearSource: () -> Unit,
    onManualModeChange: (Boolean) -> Unit,
    onManualSsidChange: (String) -> Unit,
    onToggleTarget: (String, Boolean) -> Unit,
    onSelectAllTargets: (Boolean) -> Unit,
    onChooseWordlist: () -> Unit,
    onClearWordlist: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onResumeSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val candidates = sessions.filter { path ->
        !path.pcapPath.isNullOrBlank() && File(path.pcapPath!!).exists()
    }
    val savedHandshakes = capturedHandshakes.filter { entry ->
        File(entry.pcapPath).exists()
    }
    val selectedTargets = targets.filter { it.selected }
    val allSelected = targets.isNotEmpty() && selectedTargets.size == targets.size
    val canStart = !running && selectedTargets.isNotEmpty() && !wordlistName.isNullOrBlank()
    var confirmDeleteSession by remember { mutableStateOf<CrackerSessionSnapshot?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
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
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "WPA Cracker",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = NrOnSurface,
                        )
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        NrFilterChip(
                            selected = sourceType == CrackerSourceType.SAVED_CAPTURE,
                            onClick = { onSourceSelected(CrackerSourceType.SAVED_CAPTURE) },
                            label = "Saved capture",
                            enabled = !running,
                        )
                        NrFilterChip(
                            selected = sourceType == CrackerSourceType.CUSTOM_PCAP,
                            onClick = { onSourceSelected(CrackerSourceType.CUSTOM_PCAP) },
                            label = "Custom PCAP",
                            enabled = !running,
                        )
                        NrFilterChip(
                            selected = sourceType == CrackerSourceType.IMPORTED_22000,
                            onClick = { onSourceSelected(CrackerSourceType.IMPORTED_22000) },
                            label = ".22000",
                            enabled = !running,
                        )
                    }

                    when (sourceType) {
                        CrackerSourceType.SAVED_CAPTURE -> {
                            if (candidates.isEmpty() && savedHandshakes.isEmpty()) {
                                Text(
                                    text = "No saved captures or handshakes found.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NrOnSurfaceVariant,
                                )
                            } else {
                                if (candidates.isNotEmpty()) {
                                    Text(
                                        text = "Credential sessions",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = NrOnSurfaceVariant,
                                    )
                                    candidates.forEach { session ->
                                        SourceSessionRow(
                                            session = session,
                                            selected = session.id == selectedSession?.id,
                                            enabled = !running,
                                            onClick = { onSelectSession(session) },
                                        )
                                    }
                                }
                                if (savedHandshakes.isNotEmpty()) {
                                    Text(
                                        text = "Captured handshakes",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = NrOnSurfaceVariant,
                                    )
                                    savedHandshakes.forEach { entry ->
                                        CapturedHandshakeRow(
                                            entry = entry,
                                            selected = entry.pcapPath == selectedCapturedPath,
                                            enabled = !running,
                                            onClick = { onSelectCapturedHandshake(entry) },
                                        )
                                    }
                                }
                            }
                        }

                        CrackerSourceType.CUSTOM_PCAP -> {
                            CaptureSourceControls(
                                name = customPcapName,
                                loading = targetsLoading,
                                chooseLabel = if (customPcapName == null) "Choose PCAP" else "Change PCAP",
                                enabled = !running,
                                onChoose = onChooseCustomPcap,
                                onClear = onClearSource,
                            )
                            if (customPcapName != null) {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    NrFilterChip(
                                        selected = !manualMode,
                                        onClick = { onManualModeChange(false) },
                                        label = "Detected targets",
                                        enabled = !running,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    NrFilterChip(
                                        selected = manualMode,
                                        onClick = { onManualModeChange(true) },
                                        label = "Manual SSID",
                                        enabled = !running,
                                    )
                                }
                                if (manualMode) {
                                    OutlinedTextField(
                                        value = manualSsid,
                                        onValueChange = onManualSsidChange,
                                        label = { Text("Target SSID") },
                                        singleLine = true,
                                        enabled = !running,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                }
                            }
                        }

                        CrackerSourceType.IMPORTED_22000 -> {
                            CaptureSourceControls(
                                name = imported22000Name,
                                loading = targetsLoading,
                                chooseLabel = if (imported22000Name == null) {
                                    "Choose .22000"
                                } else {
                                    "Change .22000"
                                },
                                enabled = !running,
                                onChoose = onChoose22000,
                                onClear = onClearSource,
                            )
                        }
                    }
                }
            }

            if (targetsLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            targetMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurface),
                border = BorderStroke(0.5.dp, NrOutline),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Targets",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        if (targets.size > 1) {
                            TextButton(
                                onClick = { onSelectAllTargets(!allSelected) },
                                enabled = !running,
                            ) {
                                Text(if (allSelected) "Clear all" else "Select all")
                            }
                        }
                    }
                    if (targets.isEmpty()) {
                        Text(
                            text = "No targets loaded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    } else {
                        targets.forEach { target ->
                            TargetRow(
                                target = target,
                                enabled = !running,
                                showCheckbox = !manualMode,
                                onToggle = { selected ->
                                    onToggleTarget(target.id, selected)
                                },
                            )
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
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Wordlist",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = wordlistName ?: "No wordlist selected",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = if (wordlistName != null) NrOnSurface else NrOnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onChooseWordlist, enabled = !running) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (wordlistName == null) "Choose wordlist" else "Change")
                        }
                        if (wordlistName != null) {
                            TextButton(onClick = onClearWordlist, enabled = !running) {
                                Text("Remove")
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
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Progress",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            result != null -> StatusGreen
                            running && paused -> StatusAmber
                            else -> NrOnSurfaceVariant
                        },
                    )
                    if (running && selectedTargetCount > 0) {
                        Text(
                            text = "${currentTargetSsid.ifBlank { "Preparing" }} · " +
                                "target ${(currentTargetIndex + 1).coerceAtMost(selectedTargetCount)}" +
                                "/$selectedTargetCount",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = NrOnSurfaceVariant,
                        )
                    }
                    if (running) {
                        when {
                            progressKnown -> {
                                LinearProgressIndicator(
                                    progress = { progressFraction.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            !paused -> {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            else -> {
                                Text(
                                    text = "Paused",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = StatusAmber,
                                )
                            }
                        }
                    }
                    if (running || tested > 0L) {
                        Text(
                            text = "$tested tested · ${"%.1f".format(speed)}/s",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = NrOnSurfaceVariant,
                        )
                    }
                    if (running) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = if (paused) onResume else onPause) {
                                Text(if (paused) "Resume" else "Pause")
                            }
                        }
                    }
                    result?.let { password ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = password,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = StatusGreen,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = {
                                clipboard.copyWithToast(context, password, "Password copied")
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy password",
                                    tint = NrOnSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            if (savedSessions.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = NrSurface),
                    border = BorderStroke(0.5.dp, NrOutline),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Saved sessions",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        savedSessions.forEach { session ->
                            SavedSessionRow(
                                session = session,
                                enabled = !running,
                                onResume = { onResumeSession(session.id) },
                                onDelete = { confirmDeleteSession = session },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }

        ModuleActionFab(
            onClick = if (running) onStop else onStart,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            enabled = running || canStart,
            statusAction = if (running) NrStatusAction.STOP else NrStatusAction.START,
        ) {
            Icon(
                imageVector = if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (running) "Stop cracker" else "Start cracker",
            )
        }
    }

    confirmDeleteSession?.let { session ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDeleteSession = null },
            title = { Text("Delete saved session?") },
            text = { Text("Remove the saved cracker checkpoint for ${session.sourceLabel}?") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSession(session.id)
                    confirmDeleteSession = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSession = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SourceSessionRow(
    session: CredentialSession,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) NrSurfaceVariant else NrSurface,
        ),
        border = BorderStroke(
            width = if (selected) 1.dp else 0.5.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else NrOutline,
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                text = session.ssid.ifBlank { "(hidden)" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = NrOnSurface,
            )
            Text(
                text = session.pcapPath?.substringAfterLast('/') ?: "no pcap",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CapturedHandshakeRow(
    entry: CapturedHandshakeEntry,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) NrSurfaceVariant else NrSurface,
        ),
        border = BorderStroke(
            width = if (selected) 1.dp else 0.5.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else NrOutline,
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.ssid.ifBlank { "(hidden)" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = NrOnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (entry.status == HandshakeCrackStatus.CRACKED) {
                    Text(
                        text = "Cracked",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusGreen,
                    )
                }
            }
            Text(
                text = "${entry.bssid} · ch ${entry.channel} · ${File(entry.pcapPath).name}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CaptureSourceControls(
    name: String?,
    loading: Boolean,
    chooseLabel: String,
    enabled: Boolean,
    onChoose: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onChoose, enabled = enabled && !loading) {
            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(chooseLabel)
        }
        if (name != null) {
            TextButton(onClick = onClear, enabled = enabled && !loading) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clear")
            }
        }
    }
    name?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = NrOnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TargetRow(
    target: CrackerTargetSnapshot,
    enabled: Boolean,
    showCheckbox: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showCheckbox) {
            Checkbox(
                checked = target.selected,
                onCheckedChange = onToggle,
                enabled = enabled,
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = target.ssid.ifBlank { "(hidden)" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = NrOnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = targetStatusLabel(target.status),
                    style = MaterialTheme.typography.labelSmall,
                    color = when (target.status) {
                        CrackerTargetStatus.CRACKED -> StatusGreen
                        CrackerTargetStatus.ERROR -> StatusRed
                        CrackerTargetStatus.RUNNING -> StatusAmber
                        else -> NrOnSurfaceVariant
                    },
                )
            }
            Text(
                text = "${target.bssid ?: "?"} → ${target.station ?: "?"}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            target.result?.let { password ->
                Text(
                    text = password,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = StatusGreen,
                )
            }
        }
    }
}

@Composable
private fun SavedSessionRow(
    session: CrackerSessionSnapshot,
    enabled: Boolean,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = session.sourceLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = NrOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${session.wordlistName} · ${sessionSessionLabel(session.status)} · " +
                    "${session.targets.size} target(s)",
                style = MaterialTheme.typography.labelSmall,
                color = NrOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(
            onClick = onResume,
            enabled = enabled && session.status != CrackerSessionStatus.COMPLETED,
        ) {
            Text("Resume")
        }
        IconButton(onClick = onDelete, enabled = enabled) {
            Icon(Icons.Default.Delete, contentDescription = "Delete saved session")
        }
    }
}

private fun targetStatusLabel(status: CrackerTargetStatus): String = when (status) {
    CrackerTargetStatus.PENDING -> "Pending"
    CrackerTargetStatus.RUNNING -> "Cracking"
    CrackerTargetStatus.CRACKED -> "Found"
    CrackerTargetStatus.EXHAUSTED -> "Not found"
    CrackerTargetStatus.STOPPED -> "Stopped"
    CrackerTargetStatus.ERROR -> "Error"
}

private fun sessionSessionLabel(status: CrackerSessionStatus): String = when (status) {
    CrackerSessionStatus.PAUSED -> "Paused"
    CrackerSessionStatus.STOPPED -> "Stopped"
    CrackerSessionStatus.COMPLETED -> "Completed"
    CrackerSessionStatus.INTERRUPTED -> "Interrupted"
}
