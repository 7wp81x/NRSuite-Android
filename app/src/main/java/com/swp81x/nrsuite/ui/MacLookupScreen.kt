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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.oui.MacLookupResult
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed

private val MAC_REGEX = Regex("^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$")

@Composable
fun MacLookupScreen(
    result: MacLookupResult?,
    onLookup: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    val valid = MAC_REGEX.matches(input.trim())

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
                Text("MAC / OUI lookup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Look up the OUI prefix and vendor for a MAC address using the offline database.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.uppercase() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("MAC address") },
                    placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                    isError = input.isNotBlank() && !valid,
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onLookup(input.trim()) },
                        enabled = valid,
                    ) {
                        Text("Look up")
                    }
                    if (result != null) {
                        OutlinedButton(onClick = onClear) {
                            Text("Clear")
                        }
                    }
                }
            }
        }

        if (result != null) {
            MacLookupResultCard(result)
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
                valueColor = if (result.vendor != null) NrAccent else StatusAmber,
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
