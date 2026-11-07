package com.swp81x.nrsuite.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.session.BleHidHeapFailure
import com.swp81x.nrsuite.core.session.DeviceMemoryHealth
import com.swp81x.nrsuite.core.session.DeviceMemoryPressure
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed
import java.util.Locale

fun formatMemoryBytes(bytes: Long?): String {
    if (bytes == null) return "Not reported"
    if (bytes >= 1024L * 1024L) {
        return String.format(Locale.US, "%.2f MiB", bytes / (1024.0 * 1024.0))
    }
    if (bytes >= 1024L) {
        return String.format(Locale.US, "%.1f KiB", bytes / 1024.0)
    }
    return "$bytes B"
}

/**
 * Current-state memory banner for the Devices screen. This is intentionally a
 * state-aware card rather than a modal alert so a persistent low-memory
 * condition does not repeatedly interrupt the user.
 */
@Composable
fun DeviceMemoryBanner(
    health: DeviceMemoryHealth,
    canRestart: Boolean,
    restartInProgress: Boolean,
    onStopModules: () -> Unit,
    onRestartEsp32: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val critical = health.pressure == DeviceMemoryPressure.CRITICAL
    val color = when (health.pressure) {
        DeviceMemoryPressure.CRITICAL -> StatusRed
        DeviceMemoryPressure.WARNING -> StatusAmber
        DeviceMemoryPressure.NORMAL -> StatusNeutral
    }
    val title = if (critical) "Critical device memory" else "Low device memory"

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.58f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = color,
                    )
                    Text(
                        text = "Free heap ${formatMemoryBytes(health.freeHeap)} · " +
                            "largest block ${formatMemoryBytes(health.largestBlock)}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (critical) {
                    "Firmware operations may fail or exhaust the heap. Stop active modules before starting another radio task."
                } else {
                    "Stop unused modules before starting another radio task to avoid allocation failures."
                },
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            if (!canRestart) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Restart requires NRSuite firmware with REBOOT support.",
                    style = MaterialTheme.typography.labelSmall,
                    color = NrOnSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NrCompactSecondaryButton(
                    text = "Stop modules",
                    onClick = onStopModules,
                    leadingIcon = Icons.Default.Stop,
                    modifier = Modifier.weight(1f),
                    enabled = !restartInProgress,
                )
                NrCompactDangerButton(
                    text = if (restartInProgress) "Restarting…" else "Restart ESP32",
                    onClick = onRestartEsp32,
                    leadingIcon = Icons.Default.Refresh,
                    modifier = Modifier.weight(1f),
                    enabled = canRestart && !restartInProgress,
                )
            }
        }
    }
}

/** Compact, neutral telemetry shown when heap pressure is within limits. */
@Composable
fun DeviceMemorySummaryRow(
    health: DeviceMemoryHealth,
    modifier: Modifier = Modifier,
) {
    val telemetryMissing = health.freeHeap == null && health.largestBlock == null
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Memory",
            style = MaterialTheme.typography.bodySmall,
            color = NrOnSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (telemetryMissing) {
                "Not reported"
            } else {
                "${formatMemoryBytes(health.freeHeap)} free · " +
                    "${formatMemoryBytes(health.largestBlock)} largest"
            },
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * User-decision dialog shown only when firmware actively rejects BLE HID start
 * because the live heap cannot satisfy its allocation guard.
 */
@Composable
fun BleHidLowHeapDialog(
    failure: BleHidHeapFailure,
    canRestart: Boolean,
    restartInProgress: Boolean,
    onRestartEsp32: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("BLE HID needs more memory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "The firmware refused to start BLE HID because the current heap cannot safely allocate the radio stack.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                MemoryValueRow("Free heap", formatMemoryBytes(failure.freeHeap), StatusRed)
                MemoryValueRow("Largest block", formatMemoryBytes(failure.largestBlock), StatusRed)
                Text(
                    text = "Required: at least ${formatMemoryBytes(BleHidHeapFailure.REQUIRED_FREE_HEAP_BYTES)} free " +
                        "and ${formatMemoryBytes(BleHidHeapFailure.REQUIRED_LARGEST_BLOCK_BYTES)} largest block.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
                Text(
                    text = "Stop other active radio modules and try again. If memory does not recover, restart the ESP32 explicitly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
                if (!canRestart) {
                    Text(
                        text = "This firmware does not advertise REBOOT support. Update the firmware to enable restart from the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusAmber,
                    )
                }
            }
        },
        confirmButton = {
            NrDangerButton(
                text = if (restartInProgress) "Restarting…" else "Restart ESP32",
                onClick = onRestartEsp32,
                enabled = canRestart && !restartInProgress,
                filled = true,
                compact = true,
            )
        },
        dismissButton = {
            NrQuietButton(
                text = "Cancel",
                onClick = onDismiss,
                compact = true,
            )
        },
    )
}

@Composable
private fun MemoryValueRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = color,
        )
    }
}
