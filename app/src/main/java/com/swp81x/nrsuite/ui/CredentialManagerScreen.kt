package com.swp81x.nrsuite.ui

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.swp81x.nrsuite.core.credentials.CredentialSession
import com.swp81x.nrsuite.core.credentials.CredentialSource
import com.swp81x.nrsuite.core.credentials.CredentialStatus
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOnSurface
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import java.io.File

@Composable
fun CredentialManagerScreen(
    sessions: List<CredentialSession>,
    onDeleteSession: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var deleteTarget by remember { mutableStateOf<CredentialSession?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sourceFilter by remember { mutableStateOf<CredentialSource?>(null) }
    var detailSession by remember { mutableStateOf<CredentialSession?>(null) }

    val displayedSessions = sessions.filter { session ->
        val matchesSource = sourceFilter == null || session.source == sourceFilter
        val needle = query.trim()
        val matchesQuery = needle.isBlank() ||
            session.ssid.contains(needle, ignoreCase = true) ||
            session.bssid.contains(needle, ignoreCase = true) ||
            session.credentials.any { credential ->
                credential.value.contains(needle, ignoreCase = true) ||
                    credential.details.values.any { it.contains(needle, ignoreCase = true) }
            }
        matchesSource && matchesQuery
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusIndicator(
                label = if (sessions.isEmpty()) "No saved sessions" else "${displayedSessions.size} of ${sessions.size} session(s)",
                color = StatusNeutral,
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search SSID, BSSID, password...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                NrFilterChip(
                    selected = sourceFilter == null,
                    onClick = { sourceFilter = null },
                    label = "All",
                )
                NrFilterChip(
                    selected = sourceFilter == CredentialSource.EVIL_TWIN,
                    onClick = { sourceFilter = CredentialSource.EVIL_TWIN },
                    label = "Evil Twin",
                )
                NrFilterChip(
                    selected = sourceFilter == CredentialSource.WPA_CRACKER,
                    onClick = { sourceFilter = CredentialSource.WPA_CRACKER },
                    label = "WPA Cracker",
                )
                NrFilterChip(
                    selected = sourceFilter == CredentialSource.PORTAL,
                    onClick = { sourceFilter = CredentialSource.PORTAL },
                    label = "Portal",
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurface),
                border = BorderStroke(0.5.dp, StatusAmber.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = StatusAmber,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Authorized testing only. Passwords are stored app-privately and only correct attempts are saved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
            }

            if (sessions.isEmpty()) {
                EmptyCredsState()
            } else if (displayedSessions.isEmpty()) {
                Text(
                    text = "No sessions match the current search/filter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                displayedSessions.forEach { session ->
                    CredentialSessionCard(
                        session = session,
                        onCopyPassword = { value -> clipboard.setText(AnnotatedString(value)) },
                        onSharePcap = { path -> sharePcap(context, path) },
                        onDelete = { deleteTarget = session },
                        onViewSubmissions = { detailSession = it },
                    )
                }
            }

            Spacer(Modifier.height(80.dp))
        }

        FloatingActionButton(
            onClick = { if (sessions.isNotEmpty()) confirmClearAll = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .alpha(if (sessions.isEmpty()) 0.4f else 1f),
            containerColor = NrSurfaceVariant,
            contentColor = NrOnSurfaceVariant,
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Clear all saved sessions")
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete session") },
            text = {
                Text(
                    "Delete the saved Evil Twin session for " +
                        "${target.ssid.ifBlank { "target" }}? Its PCAP file will be deleted too.",
                    color = NrOnSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSession(target.id)
                    deleteTarget = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("Clear all sessions") },
            text = {
                Text(
                    "Delete all saved credential sessions and their PCAP files?",
                    color = NrOnSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onClearAll()
                    confirmClearAll = false
                }) {
                    Text("Clear all")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    detailSession?.let { session ->
        AlertDialog(
            onDismissRequest = { detailSession = null },
            title = { Text("Portal submissions") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = session.ssid.ifBlank { "(hidden)" },
                        fontWeight = FontWeight.SemiBold,
                        color = NrOnSurface,
                    )
                    session.credentials.forEachIndexed { index, credential ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "#${index + 1}  ${credential.capturedAt}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                        color = NrOnSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = {
                                        val text = credential.details.entries.joinToString("\n") {
                                            "${it.key}: ${it.value}"
                                        }
                                        clipboard.setText(AnnotatedString(text))
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy submission",
                                            tint = NrOnSurfaceVariant,
                                        )
                                    }
                                }
                                credential.details.forEach { (key, value) ->
                                    Text(
                                        text = "$key: $value",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = NrOnSurface,
                                    )
                                }
                                if (credential.details.isEmpty()) {
                                    Text(
                                        text = credential.value,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = NrOnSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detailSession = null }) {
                    Text("Close")
                }
            },
        )
    }
}

@Composable
private fun EmptyCredsState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Key,
            contentDescription = null,
            tint = NrOnSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = "No saved credential sessions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = NrOnSurface,
        )
        Text(
            text = "Run Evil Twin or the offline cracker to save a recovered password.",
            style = MaterialTheme.typography.bodySmall,
            color = NrOnSurfaceVariant,
        )
    }
}

@Composable
private fun CredentialSessionCard(
    session: CredentialSession,
    onCopyPassword: (String) -> Unit,
    onSharePcap: (String) -> Unit,
    onDelete: () -> Unit,
    onViewSubmissions: (CredentialSession) -> Unit,
) {
    val pcapFile = session.pcapPath?.let { File(it) }
    val pcapAvailable = pcapFile?.exists() == true

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = NrAccent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = session.ssid.ifBlank { "(hidden)" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NrOnSurface,
                    )
                    Text(
                        text = session.bssid.ifBlank { "unknown BSSID" },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = NrOnSurfaceVariant,
                    )
                    Text(
                        text = sourceLabel(session.source),
                        style = MaterialTheme.typography.labelSmall,
                        color = NrAccent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete session",
                        tint = NrOnSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = buildString {
                    append("Started: ")
                    append(session.startedAt.replace('T', ' '))
                    session.endedAt?.let {
                        append("  ·  Ended: ")
                        append(it.replace('T', ' '))
                    }
                },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (pcapAvailable) "PCAP saved" else "No PCAP",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (pcapAvailable) StatusGreen else StatusNeutral,
                    fontWeight = FontWeight.SemiBold,
                )
                if (pcapAvailable) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = pcapFile?.name.orEmpty(),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = NrOnSurfaceVariant,
                    )
                }
            }

            if (session.source == CredentialSource.PORTAL) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "${session.credentials.size} captured submission(s)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = NrOnSurface,
                )
                session.credentials.lastOrNull()?.let { latest ->
                    Text(
                        text = "Latest: ${latest.capturedAt}  ·  ${latest.details.size} field(s)",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = NrOnSurfaceVariant,
                    )
                }
                if (session.credentials.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { onViewSubmissions(session) }) {
                        Text("View submissions")
                    }
                }
            } else if (session.credentials.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "No correct password captured.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(10.dp))
                session.credentials
                    .filter { it.status == CredentialStatus.CORRECT }
                    .forEach { credential ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "[${credential.capturedAt}] ${credential.value}  :  ✓",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                color = StatusGreen,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onCopyPassword(credential.value) }) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy password",
                                    tint = NrOnSurfaceVariant,
                                )
                            }
                        }
                    }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { session.pcapPath?.let(onSharePcap) },
                    enabled = pcapAvailable,
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share PCAP")
                }
            }
        }
    }
}

private fun sourceLabel(source: CredentialSource): String = when (source) {
    CredentialSource.EVIL_TWIN -> "Evil Twin"
    CredentialSource.WPA_CRACKER -> "WPA Cracker"
    CredentialSource.PORTAL -> "Portal"
    CredentialSource.IMPORTED -> "Imported"
}

private fun sharePcap(context: android.content.Context, path: String) {
    runCatching {
        val file = File(path)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.tcpdump.pcap"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Evil Twin PCAP"))
    }
}
