package com.swp81x.nrsuite.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.rfid.RfidUiState
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed

@Composable
fun RfidConfigScreen(
    connected: Boolean,
    state: RfidUiState,
    onRefresh: () -> Unit,
    onSave: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sdaMenuExpanded by remember { mutableStateOf(false) }
    var sclMenuExpanded by remember { mutableStateOf(false) }
    var selectedSdaGpio by rememberSaveable { mutableIntStateOf(state.sdaGpio ?: -1) }
    var selectedSclGpio by rememberSaveable { mutableIntStateOf(state.sclGpio ?: -1) }

    LaunchedEffect(connected) {
        if (connected) onRefresh()
    }
    LaunchedEffect(state.sdaGpio, state.sclGpio) {
        selectedSdaGpio = state.sdaGpio ?: -1
        selectedSclGpio = state.sclGpio ?: -1
    }

    val gpioOptions = state.availableGpios
    val samePin = selectedSdaGpio >= 0 && selectedSdaGpio == selectedSclGpio
    val canSave = connected &&
        !state.configSaving &&
        gpioOptions.isNotEmpty() &&
        selectedSdaGpio >= 0 &&
        selectedSclGpio >= 0 &&
        !samePin

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
                    Text(
                        text = "PN532 I2C connection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = onRefresh,
                        enabled = connected && !state.configSaving,
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Refresh")
                    }
                }
                StatusIndicator(
                    label = when {
                        !connected -> "ESP32 disconnected"
                        state.connected -> "PN532 detected"
                        state.configured -> "PN532 not detected"
                        else -> "PN532 not configured"
                    },
                    color = when {
                        !connected -> StatusRed
                        state.connected -> StatusGreen
                        state.configured -> StatusAmber
                        else -> StatusNeutral
                    },
                    subtitle = state.firmwareVersion?.let { "Firmware $it" },
                )
                Text(
                    text = "Use 3.3 V power and a common ground. The PN532 must be " +
                        "configured for I2C mode. IRQ and reset are not required.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NrSurface),
            border = BorderStroke(0.5.dp, NrOutline),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "GPIO configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Safe SDA/SCL choices are reported by the connected firmware.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )

                Text("PN532 SDA GPIO", style = MaterialTheme.typography.labelMedium)
                Box {
                    OutlinedButton(
                        onClick = { sdaMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = connected && !state.configSaving && gpioOptions.isNotEmpty(),
                    ) {
                        Text(
                            text = if (selectedSdaGpio >= 0) {
                                "GPIO $selectedSdaGpio"
                            } else {
                                "Select SDA GPIO"
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Choose PN532 SDA GPIO",
                        )
                    }
                    DropdownMenu(
                        expanded = sdaMenuExpanded,
                        onDismissRequest = { sdaMenuExpanded = false },
                    ) {
                        gpioOptions.forEach { gpio ->
                            DropdownMenuItem(
                                text = { Text("GPIO $gpio") },
                                enabled = gpio != selectedSclGpio,
                                onClick = {
                                    selectedSdaGpio = gpio
                                    sdaMenuExpanded = false
                                },
                            )
                        }
                    }
                }

                Text("PN532 SCL GPIO", style = MaterialTheme.typography.labelMedium)
                Box {
                    OutlinedButton(
                        onClick = { sclMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = connected && !state.configSaving && gpioOptions.isNotEmpty(),
                    ) {
                        Text(
                            text = if (selectedSclGpio >= 0) {
                                "GPIO $selectedSclGpio"
                            } else {
                                "Select SCL GPIO"
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Choose PN532 SCL GPIO",
                        )
                    }
                    DropdownMenu(
                        expanded = sclMenuExpanded,
                        onDismissRequest = { sclMenuExpanded = false },
                    ) {
                        gpioOptions.forEach { gpio ->
                            DropdownMenuItem(
                                text = { Text("GPIO $gpio") },
                                enabled = gpio != selectedSdaGpio,
                                onClick = {
                                    selectedSclGpio = gpio
                                    sclMenuExpanded = false
                                },
                            )
                        }
                    }
                }

                if (samePin) {
                    Text(
                        text = "SDA and SCL must use different GPIOs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusRed,
                    )
                }
                if (gpioOptions.isEmpty()) {
                    Text(
                        text = if (connected) {
                            "No safe GPIO list received yet. Refresh the status or update the firmware."
                        } else {
                            "Connect an NRSuite ESP32 to load the available GPIO list."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusAmber,
                    )
                }
                val optionsText = if (gpioOptions.isEmpty()) {
                    "No GPIO options available"
                } else {
                    "Available GPIOs: ${gpioOptions.joinToString(", ")}"
                }
                Text(
                    text = optionsText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = NrOnSurfaceVariant,
                )
                Button(
                    onClick = { onSave(selectedSdaGpio, selectedSclGpio) },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.configSaving) "Applying…" else "Apply I2C configuration")
                }
                Text(
                    text = "The configuration is stored in ESP32 NVS and restored on boot. " +
                        "Changing it stops any active RFID scan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }

        state.errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = StatusRed,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
        state.statusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}
