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
import com.swp81x.nrsuite.core.sensors.Ld2450UiState
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed

@Composable
fun SensorConfigScreen(
    connected: Boolean,
    ld2450: Ld2450UiState,
    onRefresh: () -> Unit,
    onSetLd2450Pins: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(connected) {
        if (connected) onRefresh()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Sensor configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Hardware settings shared by sensor modules.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onRefresh,
                enabled = connected,
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

        Ld2450UartConfigCard(
            connected = connected,
            state = ld2450,
            onSetPins = onSetLd2450Pins,
        )

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Ld2450UartConfigCard(
    connected: Boolean,
    state: Ld2450UiState,
    onSetPins: (Int, Int) -> Unit,
) {
    var rxMenuExpanded by remember { mutableStateOf(false) }
    var txMenuExpanded by remember { mutableStateOf(false) }
    var selectedRxGpio by rememberSaveable { mutableIntStateOf(state.rxGpio ?: -1) }
    var selectedTxGpio by rememberSaveable { mutableIntStateOf(state.txGpio ?: -1) }

    LaunchedEffect(state.rxGpio, state.txGpio) {
        selectedRxGpio = state.rxGpio ?: -1
        selectedTxGpio = state.txGpio ?: -1
    }

    val samePin = selectedRxGpio >= 0 && selectedRxGpio == selectedTxGpio
    val canSave = connected &&
        !state.running &&
        !state.configSaving &&
        selectedRxGpio >= 0 &&
        selectedTxGpio >= 0 &&
        !samePin

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "LD2450 mmWave Radar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            StatusIndicator(
                label = when {
                    !connected -> "ESP32 disconnected"
                    state.running -> "Radar running"
                    state.configured -> "UART configured"
                    else -> "UART not configured"
                },
                color = when {
                    !connected -> StatusRed
                    state.running -> StatusGreen
                    state.configured -> StatusGreen
                    else -> StatusAmber
                },
                subtitle = when {
                    state.rxGpio != null && state.txGpio != null ->
                        "RX GPIO ${state.rxGpio} · TX GPIO ${state.txGpio} · 256000 baud"
                    else -> "Select the ESP32 UART pins for the radar."
                },
            )
            Text(
                text = "Sensor TX -> ESP32 RX GPIO. Sensor RX -> ESP32 TX GPIO. " +
                    "Use a stable 5 V sensor supply and a common ground. " +
                    "Do not use the ESP32 USB-UART bridge pins.",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("RX GPIO", style = MaterialTheme.typography.labelMedium)
                    Box {
                        OutlinedButton(
                            onClick = { rxMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = connected &&
                                !state.running &&
                                !state.configSaving &&
                                state.rxGpios.isNotEmpty(),
                        ) {
                            Text(
                                text = if (selectedRxGpio >= 0) {
                                    "GPIO $selectedRxGpio"
                                } else {
                                    "Select RX"
                                },
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Choose LD2450 RX GPIO",
                            )
                        }
                        DropdownMenu(
                            expanded = rxMenuExpanded,
                            onDismissRequest = { rxMenuExpanded = false },
                        ) {
                            state.rxGpios.forEach { gpio ->
                                DropdownMenuItem(
                                    text = { Text("GPIO $gpio") },
                                    enabled = gpio != selectedTxGpio,
                                    onClick = {
                                        selectedRxGpio = gpio
                                        rxMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("TX GPIO", style = MaterialTheme.typography.labelMedium)
                    Box {
                        OutlinedButton(
                            onClick = { txMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = connected &&
                                !state.running &&
                                !state.configSaving &&
                                state.txGpios.isNotEmpty(),
                        ) {
                            Text(
                                text = if (selectedTxGpio >= 0) {
                                    "GPIO $selectedTxGpio"
                                } else {
                                    "Select TX"
                                },
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Choose LD2450 TX GPIO",
                            )
                        }
                        DropdownMenu(
                            expanded = txMenuExpanded,
                            onDismissRequest = { txMenuExpanded = false },
                        ) {
                            state.txGpios.forEach { gpio ->
                                DropdownMenuItem(
                                    text = { Text("GPIO $gpio") },
                                    enabled = gpio != selectedRxGpio,
                                    onClick = {
                                        selectedTxGpio = gpio
                                        txMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (samePin) {
                Text(
                    text = "RX and TX must use different GPIOs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusRed,
                )
            }
            if (state.rxGpios.isEmpty()) {
                Text(
                    text = if (connected) {
                        "No safe GPIO list received from firmware. Refresh or update the firmware."
                    } else {
                        "Connect an NRSuite ESP32 to load the available GPIO list."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusAmber,
                )
            }
            Text(
                text = if (state.rxGpios.isEmpty()) {
                    "No GPIO options available"
                } else {
                    "Available GPIOs: ${state.rxGpios.joinToString(", ")}"
                },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
            )
            Button(
                onClick = { onSetPins(selectedRxGpio, selectedTxGpio) },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.configSaving) "Saving GPIOs…" else "Save UART configuration")
            }
            if (state.running) {
                Text(
                    text = "Stop the radar before changing its UART pins.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusAmber,
                )
            }
            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusRed,
                )
            }
        }
    }
}
