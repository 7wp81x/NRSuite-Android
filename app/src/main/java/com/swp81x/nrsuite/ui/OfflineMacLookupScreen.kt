package com.swp81x.nrsuite.ui

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.oui.MacLookupResult
import com.swp81x.nrsuite.core.oui.OuiDatabaseStatus
import com.swp81x.nrsuite.ui.theme.NrOnSurface
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed
import com.swp81x.nrsuite.ui.util.copyWithToast

@Composable
fun OfflineMacLookupScreen(
    result: MacLookupResult?,
    ouiDatabaseStatus: OuiDatabaseStatus,
    onLookup: (String) -> Unit,
    onClear: () -> Unit,
    onDownloadOuiDatabase: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val normalizedInput = normalizeMacQuery(input)
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val databaseReady = ouiDatabaseStatus is OuiDatabaseStatus.Ready

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
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Offline MAC Lookup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = NrOnSurface,
                        )
                        Text(
                            text = "OUI/vendor lookup only. No map, geolocation, or network request.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.uppercase() },
                    label = { Text("MAC address or BSSID") },
                    placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                    singleLine = true,
                    isError = input.isNotBlank() && normalizedInput == null,
                    supportingText = if (input.isNotBlank() && normalizedInput == null) {
                        { Text("Enter 12 hexadecimal digits, optionally separated by : or -.") }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { normalizedInput?.let(onLookup) },
                        enabled = normalizedInput != null,
                    ) {
                        Text("Lookup")
                    }
                    OutlinedButton(
                        onClick = {
                            input = ""
                            onClear()
                        },
                        enabled = input.isNotBlank() || result != null,
                    ) {
                        Text("Clear")
                    }
                }
            }
        }

        if (!databaseReady) {
            OuiDatabaseNotice(
                status = ouiDatabaseStatus,
                onDownload = onDownloadOuiDatabase,
            )
        }

        result?.let { lookup ->
            MacLookupResultCard(
                result = lookup,
                onCopy = {
                    clipboard.copyWithToast(context, lookup.toShareText())
                },
                onShare = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "MAC lookup: ${lookup.normalizedMac}")
                        putExtra(Intent.EXTRA_TEXT, lookup.toShareText())
                    }
                    context.startActivity(
                        Intent.createChooser(intent, "Share MAC lookup"),
                    )
                },
            )
        }

        Text(
            text = "Locally administered MACs are often randomized by phones. " +
                "A missing vendor may mean the prefix is not in the downloaded OUI registry.",
            style = MaterialTheme.typography.labelSmall,
            color = NrOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun OuiDatabaseNotice(
    status: OuiDatabaseStatus,
    onDownload: () -> Unit,
) {
    val (title, message, color) = when (status) {
        OuiDatabaseStatus.Checking -> Triple(
            "Checking OUI database",
            "The offline registry is loading.",
            StatusNeutral,
        )
        OuiDatabaseStatus.NotDownloaded -> Triple(
            "OUI database not downloaded",
            "Download the IEEE OUI registry once for offline MAC/vendor lookups. " +
                "Lookup itself never sends the queried MAC anywhere.",
            StatusAmber,
        )
        OuiDatabaseStatus.Downloading -> Triple(
            "Downloading OUI database",
            "The offline vendor registry is being updated.",
            StatusAmber,
        )
        is OuiDatabaseStatus.Error -> Triple(
            "OUI database error",
            status.message,
            StatusRed,
        )
        is OuiDatabaseStatus.Ready -> Triple(
            "OUI database ready",
            "${status.vendorCount} vendor prefixes · updated ${status.updatedAt}",
            StatusGreen,
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            if (status is OuiDatabaseStatus.NotDownloaded || status is OuiDatabaseStatus.Error) {
                OutlinedButton(onClick = onDownload) {
                    Text("Download OUI database")
                }
            }
        }
    }
}

@Composable
private fun MacLookupResultCard(
    result: MacLookupResult,
    onCopy: () -> Unit,
    onShare: () -> Unit,
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
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = result.normalizedMac,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = NrOnSurface,
                    )
                    Text(
                        text = result.vendor ?: if (result.databaseReady) {
                            "No vendor match in the offline OUI registry"
                        } else {
                            "Offline OUI registry is not loaded"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (result.vendor != null) StatusGreen else StatusNeutral,
                    )
                }
                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy MAC lookup",
                        tint = NrOnSurfaceVariant,
                    )
                }
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share MAC lookup",
                        tint = NrOnSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LookupMetadataPill("OUI", result.ouiPrefix ?: "—")
                LookupMetadataPill(
                    "Locally administered",
                    if (result.locallyAdministered) "Yes" else "No",
                )
                LookupMetadataPill("Multicast", if (result.multicast) "Yes" else "No")
            }

            if (result.broadcast) {
                Text(
                    text = "Broadcast address: this is not assigned to a vendor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusAmber,
                )
            }
            Text(
                text = "Registration source: offline IEEE OUI registry. " +
                    "The prefix identifies the organization assigned the address block, " +
                    "not necessarily the current device manufacturer.",
                style = MaterialTheme.typography.labelSmall,
                color = NrOnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LookupMetadataPill(label: String, value: String) {
    Column(
        modifier = Modifier
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NrOnSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = NrOnSurface,
        )
    }
}

private fun MacLookupResult.toShareText(): String = buildString {
    appendLine("MAC: $normalizedMac")
    appendLine("OUI: ${ouiPrefix ?: "unknown"}")
    appendLine("Vendor: ${vendor ?: "unknown"}")
    appendLine("Locally administered: ${if (locallyAdministered) "yes" else "no"}")
    appendLine("Multicast: ${if (multicast) "yes" else "no"}")
    append("Broadcast: ${if (broadcast) "yes" else "no"}")
}

internal fun normalizeMacQuery(input: String): String? {
    val compact = input.trim()
        .uppercase()
        .replace(":", "")
        .replace("-", "")
        .replace(".", "")
        .replace(" ", "")
    if (compact.length != 12) return null
    if (compact.any { it.digitToIntOrNull(16) == null }) return null
    return compact.chunked(2).joinToString(":")
}
