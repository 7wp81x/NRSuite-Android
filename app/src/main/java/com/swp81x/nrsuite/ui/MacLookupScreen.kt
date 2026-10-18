package com.swp81x.nrsuite.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.geo.BssidGeolocationResult
import com.swp81x.nrsuite.core.geo.normalizeBssid
import com.swp81x.nrsuite.core.oui.MacLookupResult
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed

@Composable
fun MacLookupScreen(
    result: MacLookupResult?,
    locationResult: BssidGeolocationResult?,
    locationLoading: Boolean,
    locationError: String?,
    onLookup: (String) -> Unit,
    onLookupLocation: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    val context = LocalContext.current
    val normalizedInput = normalizeBssid(input)
    val valid = normalizedInput != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
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
                    onValueChange = { input = it.uppercase() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("BSSID / AP MAC address") },
                    placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                    isError = input.isNotBlank() && !valid,
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            normalizedInput?.let { target ->
                                onLookup(target)
                                onLookupLocation(target)
                            }
                        },
                        enabled = valid && !locationLoading,
                    ) {
                        if (locationLoading) {
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
                    if (result != null || locationResult != null || locationError != null) {
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
            }
        }

        if (result != null) {
            MacLookupResultCard(result)
        }

        when {
            locationLoading -> {
                LocationLoadingCard()
            }
            locationError != null -> {
                LocationErrorCard(locationError)
            }
            locationResult != null -> {
                LocationResultCard(
                    result = locationResult,
                    onOpenMaps = { openGoogleMaps(context, locationResult) },
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
            Text("Result", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            InfoLine("MAC", result.normalizedMac)
            InfoLine("OUI prefix", result.ouiPrefix ?: "—")
            InfoLine(
                label = "Vendor",
                value = result.vendor ?: if (result.databaseReady) "Unknown / not in database" else "Database not downloaded",
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
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("Querying BSSID location database...", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LocationErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, StatusRed),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Location lookup", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = StatusRed)
        }
    }
}

@Composable
private fun LocationResultCard(
    result: BssidGeolocationResult,
    onOpenMaps: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Approximate location", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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

private fun openGoogleMaps(context: Context, result: BssidGeolocationResult) {
    val uri = Uri.parse(
        "https://www.google.com/maps/search/?api=1&query=" +
            "${result.latitude},${result.longitude}"
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
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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
