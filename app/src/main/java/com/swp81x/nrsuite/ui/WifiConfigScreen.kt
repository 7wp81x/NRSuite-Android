package com.swp81x.nrsuite.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.wifi.WIFI_REGULATORY_COUNTRIES
import com.swp81x.nrsuite.core.wifi.WifiBand
import com.swp81x.nrsuite.ui.components.WifiBandSelector
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.StatusAmber

@Composable
fun WifiConfigScreen(
    connected: ConnectionState.Connected?,
    band: WifiBand,
    showBandSelector: Boolean,
    fiveGhzAvailable: Boolean,
    fiveGhzUnavailableMessage: String,
    onBandSelected: (WifiBand) -> Unit,
    onCountrySelected: (String) -> Unit,
    captureOutputLabel: String,
    hasCustomCaptureOutput: Boolean,
    onChooseCaptureOutput: () -> Unit,
    onResetCaptureOutput: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var countryMenu by remember { mutableStateOf(false) }
    val countryLabel = connected?.country?.let { code ->
        WIFI_REGULATORY_COUNTRIES.firstOrNull { it.first == code }?.second ?: code
    } ?: "Not connected"

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NrSurface),
            border = BorderStroke(0.5.dp, NrOutline),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Regulatory country", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Controls the channels the ESP32 firmware allows. Select your actual location. " +
                        "This is not a region unlock.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusAmber,
                )
                Spacer(Modifier.height(10.dp))
                Box {
                    OutlinedButton(
                        onClick = { countryMenu = true },
                        enabled = connected != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(countryLabel, modifier = Modifier.weight(1f))
                    }
                    DropdownMenu(
                        expanded = countryMenu,
                        onDismissRequest = { countryMenu = false },
                    ) {
                        WIFI_REGULATORY_COUNTRIES.forEach { (code, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    countryMenu = false
                                    onCountrySelected(code)
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (connected?.allowed5gChannels.isNullOrEmpty()) {
                        "Allowed 5 GHz channels: none reported"
                    } else {
                        "Allowed 5 GHz channels: ${connected.allowed5gChannels.joinToString(", ")}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NrSurface),
            border = BorderStroke(0.5.dp, NrOutline),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Capture output", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "WiFi captures use the global NRSuite root by default. " +
                        "You can choose a separate folder for Packet Sniffer PCAP files. " +
                        "Files are saved under Pcap/.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(
                        text = captureOutputLabel,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = if (hasCustomCaptureOutput) NrOnSurfaceVariant else StatusAmber,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onChooseCaptureOutput) {
                        Text("Choose")
                    }
                }
                if (hasCustomCaptureOutput) {
                    TextButton(onClick = onResetCaptureOutput) {
                        Text("Use global root")
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (showBandSelector) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurface),
                border = BorderStroke(0.5.dp, NrOutline),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Preferred scan band", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Used by scanning tools unless a tool explicitly overrides it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    WifiBandSelector(
                        selectedBand = band,
                        fiveGhzAvailable = fiveGhzAvailable,
                        onBandSelected = onBandSelected,
                        onUnavailableBandClicked = {
                            Toast.makeText(
                                context,
                                fiveGhzUnavailableMessage,
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        enabled = connected != null,
                    )
                }
            }
        }
    }
}
