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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.swp81x.nrsuite.core.ir.IrJammerUiState
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusRed

@Composable
fun IrJammerScreen(
    state: IrJammerUiState,
    txAvailable: Boolean,
    onStart: (durationMs: Int) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAuthorization by remember { mutableStateOf(true) }
    var selectedDurationMs by rememberSaveable { mutableIntStateOf(10_000) }
    var durationMenu by remember { mutableStateOf(false) }
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(view, state.running) {
        val previous = view.keepScreenOn
        view.keepScreenOn = state.running
        onDispose { view.keepScreenOn = previous }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) onStop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onStop()
        }
    }

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
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = "IR Jammer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Authorized, time-bounded IR interference testing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }

        if (!state.running) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurface),
                border = BorderStroke(0.5.dp, NrOutline),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Run duration", style = MaterialTheme.typography.labelMedium)
                    Box {
                        OutlinedButton(
                            onClick = { durationMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = if (selectedDurationMs <= 0) {
                                    "Unlimited while on this screen"
                                } else {
                                    "${selectedDurationMs / 1000} seconds"
                                },
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose duration")
                        }
                        DropdownMenu(
                            expanded = durationMenu,
                            onDismissRequest = { durationMenu = false },
                        ) {
                            listOf(0, 5_000, 10_000, 15_000, 20_000, 30_000).forEach { duration ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (duration == 0) {
                                                "Unlimited while on this screen"
                                            } else {
                                                "${duration / 1000} seconds"
                                            },
                                        )
                                    },
                                    onClick = {
                                        selectedDurationMs = duration
                                        durationMenu = false
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { onStart(selectedDurationMs) },
                        enabled = txAvailable,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusGreen),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Start IR Jammer")
                    }
                    Text(
                        text = "Stops when you leave this screen, background the app, or press Stop. " +
                            "Unlimited mode never runs in the background.",
                        style = MaterialTheme.typography.labelSmall,
                        color = NrOnSurfaceVariant,
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = StatusRed.copy(alpha = 0.14f)),
                border = BorderStroke(1.dp, StatusRed),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "JAMMING ACTIVE",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = StatusRed,
                    )
                    Text(
                        text = if (state.unlimited) {
                            "Unlimited · manual stop required"
                        } else {
                            "${state.remainingSeconds}s remaining"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Output: ${state.backendLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NrOnSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onStop,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed),
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("STOP JAMMER")
                    }
                }
            }
        }

        state.errorMessage?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
                border = BorderStroke(0.5.dp, StatusRed),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusRed,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        state.message?.takeIf { !state.running }?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = NrOnSurfaceVariant,
            )
        }

        Spacer(Modifier.height(20.dp))
    }

    if (showAuthorization) {
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
            title = { Text("IR Jammer — authorized use warning") },
            text = {
                Text(
                    "The IR Jammer intentionally interferes with nearby IR-controlled devices. " +
                        "Use it only on devices you own or have explicit permission to test. " +
                        "Do not use it to disrupt third-party equipment. Unlimited mode runs only " +
                        "while this screen is active and stops when the app is backgrounded.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showAuthorization = false }) {
                    Text("I Understand")
                }
            },
        )
    }
}
