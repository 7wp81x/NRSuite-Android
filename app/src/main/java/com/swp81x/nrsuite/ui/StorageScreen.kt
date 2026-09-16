package com.swp81x.nrsuite.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.storage.StorageFile
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusNeutral

@Composable
fun StorageScreen(
    connected: Boolean,
    loading: Boolean,
    files: List<StorageFile>,
    totalBytes: Long,
    usedBytes: Long,
    freeBytes: Long,
    onRefresh: () -> Unit,
    onDelete: (String) -> Unit,
    onStartMassStorage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var confirmMassStorage by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                StorageSummaryCard(
                    connected = connected,
                    loading = loading,
                    fileCount = files.size,
                    totalBytes = totalBytes,
                    usedBytes = usedBytes,
                    freeBytes = freeBytes,
                    onRefresh = onRefresh,
                    onStartMassStorage = { confirmMassStorage = true },
                )
            }

            if (!connected) {
                item {
                    Text(
                        text = "Connect an ESP32 to browse the device filesystem.",
                        color = NrOnSurfaceVariant,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            } else if (files.isEmpty()) {
                item {
                    EmptyStorageState(loading = loading)
                }
            } else {
                items(files, key = { it.name }) { file ->
                    StorageFileRow(
                        file = file,
                        onDelete = { deleteTarget = file.name },
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onRefresh,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = NrAccent,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh storage")
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete file?") },
            text = { Text("Delete '$target' from the device filesystem?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        onDelete(target)
                    },
                ) {
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

    if (confirmMassStorage) {
        AlertDialog(
            onDismissRequest = { confirmMassStorage = false },
            title = { Text("Enter USB mass storage mode?") },
            text = {
                Text(
                    "The ESP32 will re-enumerate as a USB drive and the bridge connection will disappear. " +
                        "You will need to reset or re-plug the device to return to the app."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmMassStorage = false
                        onStartMassStorage()
                    },
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmMassStorage = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun StorageSummaryCard(
    connected: Boolean,
    loading: Boolean,
    fileCount: Int,
    totalBytes: Long,
    usedBytes: Long,
    freeBytes: Long,
    onRefresh: () -> Unit,
    onStartMassStorage: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = NrAccent,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Device storage",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            !connected -> "Not connected"
                            loading -> "Loading..."
                            else -> "$fileCount file(s)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onRefresh, enabled = connected && !loading) {
                    Text("Refresh")
                }
            }

            Spacer(Modifier.height(12.dp))
            MetricRow("Total", formatBytes(totalBytes))
            MetricRow("Used", formatBytes(usedBytes))
            MetricRow("Free", formatBytes(freeBytes))

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onStartMassStorage,
                enabled = connected,
            ) {
                Icon(Icons.Default.Usb, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start USB mass storage")
            }
        }
    }
}

@Composable
private fun StorageFileRow(
    file: StorageFile,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                tint = NrOnSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = formatBytes(file.size.toLong()),
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete ${file.name}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun EmptyStorageState(loading: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                tint = StatusNeutral,
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (loading) "Loading files..." else "No files on device storage.",
                color = NrOnSurfaceVariant,
            )
            if (!loading) {
                Text(
                    text = "Tap refresh to reload the listing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = NrOnSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return if (unitIndex == 0) {
        "${bytes} B"
    } else {
        String.format("%.1f %s", value, units[unitIndex])
    }
}
