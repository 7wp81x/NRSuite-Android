package com.swp81x.nrsuite.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.session.ConnectionDiagnostic
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed

@Composable
internal fun ConnectionDiagnosticSummary(
    diagnostic: ConnectionDiagnostic,
    onOpenFirmwareFlasher: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDetails by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = diagnostic.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        DiagnosticCheckRow("USB permission granted", diagnostic.permissionGranted)
        DiagnosticCheckRow("USB device detected", diagnostic.deviceDetected)
        DiagnosticCheckRow("Serial port opened", diagnostic.portOpened)
        DiagnosticCheckRow("NRSuite protocol response", diagnostic.sawProtocolFrame)
        DiagnosticCheckRow("Device activity observed", diagnostic.sawDeviceActivity)
        if (diagnostic.sawRomBootLog || diagnostic.sawInvalidFirmwareHeader) {
            DiagnosticCheckRow("ROM boot / invalid firmware log detected", false)
        }

        if (diagnostic.handshakeAttempts > 0) {
            Text(
                text = "Handshake attempts: ${diagnostic.handshakeAttempts}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        diagnostic.detectedChip?.let { chip ->
            Text(
                text = "Chip hint from boot log: $chip",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { onOpenFirmwareFlasher(diagnostic.detectedChip) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text("Open Firmware Flasher")
        }
        Text(
            text = "You can also retry from the device row below.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )

        if (!diagnostic.rawLogPreview.isNullOrBlank()) {
            TextButton(
                onClick = { showDetails = !showDetails },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Text(if (showDetails) "Hide diagnostic log" else "Show diagnostic log")
            }
            if (showDetails) {
                Text(
                    text = diagnostic.rawLogPreview,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    maxLines = 12,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticCheckRow(label: String, passed: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = if (passed) StatusGreen else StatusRed,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (passed) MaterialTheme.colorScheme.onSurface else StatusNeutral,
            fontWeight = if (passed) FontWeight.Normal else FontWeight.Medium,
        )
    }
}
