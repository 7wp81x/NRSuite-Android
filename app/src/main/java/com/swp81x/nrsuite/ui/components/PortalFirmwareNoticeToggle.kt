package com.swp81x.nrsuite.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.ui.theme.NrOnSurface
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import kotlinx.coroutines.delay

/**
 * Controls the firmware-rendered PortalManager::noticePage shown before the
 * uploaded captive-portal HTML. This in-memory preference intentionally resets
 * to ON whenever the app process restarts.
 */
@Composable
fun PortalFirmwareNoticeToggle(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDisableConfirmation by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(5) }

    LaunchedEffect(showDisableConfirmation) {
        if (showDisableConfirmation) {
            countdown = 5
            while (countdown > 0) {
                delay(1_000)
                countdown--
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Show firmware test notice",
                style = MaterialTheme.typography.bodyMedium,
                color = NrOnSurface,
            )
            Text(
                text = if (enabled) {
                    "The firmware notice page is shown before portal HTML."
                } else {
                    "Portal HTML opens directly; firmware notice is disabled for this app run."
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) NrOnSurfaceVariant else StatusAmber,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { checked ->
                if (checked) {
                    onEnabledChange(true)
                } else {
                    countdown = 5
                    showDisableConfirmation = true
                }
            },
        )
    }

    if (showDisableConfirmation) {
        AlertDialog(
            onDismissRequest = { showDisableConfirmation = false },
            title = { Text("Disable firmware test notice?") },
            text = {
                Text(
                    "Use this only in a controlled environment with networks and devices " +
                        "you own or have explicit written permission to test. " +
                        "You are responsible for complying with local law.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEnabledChange(false)
                        showDisableConfirmation = false
                    },
                    enabled = countdown == 0,
                ) {
                    Text(if (countdown > 0) "OK (${countdown}s)" else "OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
