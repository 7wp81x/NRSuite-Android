package com.swp81x.nrsuite.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.geo.BssidGeolocationErrorKind
import com.swp81x.nrsuite.core.geo.BssidGeolocationHistoryEntry
import com.swp81x.nrsuite.core.geo.BssidGeolocationResult
import com.swp81x.nrsuite.core.geo.BssidGeolocationState
import com.swp81x.nrsuite.core.geo.normalizeBssid
import com.swp81x.nrsuite.core.oui.MacLookupResult
import com.swp81x.nrsuite.ui.components.BssidMapView
import com.swp81x.nrsuite.ui.components.RoundedTabRow
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HISTORY_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@Composable
fun MacLookupScreen(
    result: MacLookupResult?,
    geolocationState: BssidGeolocationState,
    history: List<BssidGeolocationHistoryEntry>,
    onLookup: (String) -> Unit,
    onSelectHistory: (String) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var input by rememberSaveable { mutableStateOf("") }
    var confirmClearHistory by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val normalizedInput = normalizeBssid(input)
    val tabs = listOf("Overview", "Lookup History (${history.size})")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RoundedTabRow(
            selectedIndex = selectedTab,
            tabs = tabs,
            onSelect = { selectedTab = it },
        )

        if (selectedTab == 0) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LookupInputCard(
                    input = input,
                    normalizedInput = normalizedInput,
                    loading = geolocationState is BssidGeolocationState.Loading,
                    hasResult = result != null ||
                        geolocationState !is BssidGeolocationState.Idle,
                    onInputChange = { input = it.uppercase() },
                    onLookup = {
                        normalizedInput?.let(onLookup)
                    },
                    onClear = onClear,
                )

                if (result != null) {
                    MacLookupResultCard(result)
                }

                when (geolocationState) {
                    BssidGeolocationState.Idle -> Unit
                    is BssidGeolocationState.Loading -> LocationLoadingCard()
                    is BssidGeolocationState.Failure -> {
                        LocationErrorCard(
                            errorKind = geolocationState.error.kind,
                            message = geolocationState.error.message,
                            onRetry = normalizedInput?.let { target ->
                                { onLookup(target) }
                            },
                        )
                    }
                    is BssidGeolocationState.Success -> {
                        val location = geolocationState.result
                        LocationResultCard(
                            result = location,
                            fromCache = geolocationState.fromCache,
                            warning = geolocationState.warning,
                            onOpenMaps = {
                                openGoogleMaps(
                                    context = context,
                                    latitude = location.latitude,
                                    longitude = location.longitude,
                                )
                            },
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = NrSurface),
                            border = BorderStroke(0.5.dp, NrOutline),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(
                                    text = "Map preview",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                )
                                BssidMapView(
                                    bssid = location.bssid,
                                    latitude = location.latitude,
                                    longitude = location.longitude,
                                    accuracyMeters = location.accuracyMeters,
                                    source = location.source,
                                )
                                Text(
                                    text = "Tiles: © OpenStreetMap contributors",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NrOnSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
            }
        } else {
            if (confirmClearHistory) {
                AlertDialog(
                    onDismissRequest = { confirmClearHistory = false },
                    title = { Text("Clear lookup history?") },
                    text = { Text("All saved BSSID geolocation lookups will be removed.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                confirmClearHistory = false
                                onClearHistory()
                            },
                        ) { Text("Clear") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmClearHistory = false }) { Text("Cancel") }
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${history.size} saved lookup(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (history.isNotEmpty()) {
                    TextButton(onClick = { confirmClearHistory = true }) {
                        Text("Clear all")
                    }
                }
            }

            if (history.isEmpty()) {
                EmptyHistoryCard()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(history, key = { it.bssid }) { entry ->
                        HistoryRow(
                            entry = entry,
                            onOpen = {
                                input = entry.bssid
                                onSelectHistory(entry.bssid)
                                selectedTab = 0
                            },
                            onDelete = { onDeleteHistory(entry.bssid) },
                        )
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }
}

@Composable
private fun LookupInputCard(
    input: String,
    normalizedInput: String?,
    loading: Boolean,
    hasResult: Boolean,
    onInputChange: (String) -> Unit,
    onLookup: () -> Unit,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "BSSID Geolocation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Look up the access-point vendor locally and query its approximate " +
                    "physical location. This is intended for BSSIDs / AP radios only.",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("BSSID / AP MAC address") },
                placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                isError = input.isNotBlank() && normalizedInput == null,
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onLookup,
                    enabled = normalizedInput != null && !loading,
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .width(18.dp)
                                .height(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Look up")
                    }
                }
                if (hasResult) {
                    OutlinedButton(onClick = onClear) {
                        Text("Clear")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Location data is approximate and may be stale. The BSSID may be " +
                    "sent to a third-party location service. Use only for authorized testing.",
                style = MaterialTheme.typography.bodySmall,
                color = StatusAmber,
            )
            if (input.isNotBlank() && normalizedInput == null) {
                Text(
                    text = "Use 12 hex characters, optionally separated by ':' or '-'.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusRed,
                )
            }
        }
    }
}

@Composable
private fun MacLookupResultCard(result: MacLookupResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Vendor lookup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            InfoLine("BSSID", result.normalizedMac)
            InfoLine("OUI prefix", result.ouiPrefix ?: "—")
            InfoLine(
                label = "Vendor",
                value = result.vendor ?: if (result.databaseReady) {
                    "Unknown / not in database"
                } else {
                    "Database not downloaded"
                },
                valueColor = if (result.vendor != null) MaterialTheme.colorScheme.primary else StatusAmber,
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = buildString {
                    append(if (result.locallyAdministered) "Locally administered / randomized" else "Globally administered")
                    if (result.multicast) append(" · multicast")
                    if (result.broadcast) append(" · broadcast")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (result.locallyAdministered) StatusAmber else StatusNeutral,
            )
        }
    }
}

@Composable
private fun LocationLoadingCard() {
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
            CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("Querying BSSID location database...", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LocationErrorCard(
    errorKind: BssidGeolocationErrorKind,
    message: String,
    onRetry: (() -> Unit)?,
) {
    val title = when (errorKind) {
        BssidGeolocationErrorKind.INVALID_INPUT -> "Invalid BSSID"
        BssidGeolocationErrorKind.NOT_FOUND -> "BSSID not found"
        BssidGeolocationErrorKind.NO_NETWORK -> "Network unavailable"
        BssidGeolocationErrorKind.TIMEOUT -> "Lookup timed out"
        BssidGeolocationErrorKind.RATE_LIMITED -> "Rate limited"
        BssidGeolocationErrorKind.SERVICE_ERROR -> "Location service error"
        BssidGeolocationErrorKind.PARSE_ERROR -> "Invalid location response"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, StatusRed),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = StatusRed)
            if (onRetry != null) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

@Composable
private fun LocationResultCard(
    result: BssidGeolocationResult,
    fromCache: Boolean,
    warning: String?,
    onOpenMaps: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Approximate location",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (fromCache) {
                    Text("Cached", style = MaterialTheme.typography.labelSmall, color = StatusAmber)
                }
            }
            if (warning != null) {
                Spacer(Modifier.height(6.dp))
                Text(warning, style = MaterialTheme.typography.bodySmall, color = StatusAmber)
            }
            Spacer(Modifier.height(8.dp))
            InfoLine("BSSID", result.bssid)
            InfoLine("Latitude", "%.6f".format(result.latitude))
            InfoLine("Longitude", "%.6f".format(result.longitude))
            InfoLine(
                "Accuracy",
                result.accuracyMeters?.let { "± %.1f m".format(it) } ?: "Unknown",
            )
            InfoLine("Source", result.source)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onOpenMaps, modifier = Modifier.fillMaxWidth()) {
                Text("Open in Google Maps")
            }
        }
    }
}

@Composable
private fun HistoryRow(
    entry: BssidGeolocationHistoryEntry,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.vendor ?: "(unknown vendor)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = entry.bssid,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "%.5f, %.5f · %s · %s".format(
                        entry.latitude,
                        entry.longitude,
                        entry.accuracyMeters?.let { "±${it.toInt()}m" } ?: "accuracy unknown",
                        formatHistoryTime(entry.lastLookedUpAtMs),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
                if (entry.lookupCount > 1) {
                    Text(
                        text = "Looked up ${entry.lookupCount} times",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusNeutral,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete lookup")
            }
        }
    }
}

@Composable
private fun EmptyHistoryCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("No lookup history yet", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Successful BSSID geolocation lookups will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
        }
    }
}

private fun formatHistoryTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return "unknown time"
    return runCatching {
        Instant.ofEpochMilli(timestampMs)
            .atZone(ZoneId.systemDefault())
            .format(HISTORY_TIME_FORMAT)
    }.getOrDefault("unknown time")
}

private fun openGoogleMaps(context: Context, latitude: Double, longitude: Double) {
    val uri = Uri.parse(
        "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
    )
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }.onFailure {
        Toast.makeText(context, "No app available to open the map link.", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun InfoLine(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = NrOnSurfaceVariant,
            modifier = Modifier.width(90.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = valueColor,
        )
    }
}
