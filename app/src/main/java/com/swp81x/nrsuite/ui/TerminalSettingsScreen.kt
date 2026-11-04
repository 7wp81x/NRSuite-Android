package com.swp81x.nrsuite.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.terminal.TerminalUiSettings
import com.swp81x.nrsuite.ui.components.NrPrimaryButton
import com.swp81x.nrsuite.ui.components.NrQuietButton
import com.swp81x.nrsuite.ui.components.NrSecondaryButton
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant

@Composable
internal fun TerminalSettingsRoute(
    settings: TerminalUiSettings,
    hasPrivateKey: Boolean,
    onUpdateSettings: (TerminalUiSettings) -> Unit,
    onManageShortcuts: () -> Unit,
    onImportPrivateKey: (Uri, String) -> Unit,
    onClearPrivateKey: () -> Unit,
    onClearPinnedHostKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TerminalSettingsScreen(
        settings = settings,
        hasPrivateKey = hasPrivateKey,
        onUpdateSettings = onUpdateSettings,
        onManageShortcuts = onManageShortcuts,
        onImportPrivateKey = onImportPrivateKey,
        onClearPrivateKey = onClearPrivateKey,
        onClearPinnedHostKey = onClearPinnedHostKey,
        modifier = modifier,
    )
}

@Composable
internal fun TerminalSettingsScreen(
    settings: TerminalUiSettings,
    hasPrivateKey: Boolean,
    onUpdateSettings: (TerminalUiSettings) -> Unit,
    onManageShortcuts: () -> Unit,
    onImportPrivateKey: (Uri, String) -> Unit,
    onClearPrivateKey: () -> Unit,
    onClearPinnedHostKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingKeyUri by remember { mutableStateOf<Uri?>(null) }
    var keyPassphrase by remember { mutableStateOf("") }
    val keyPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        pendingKeyUri = uri
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsCard(title = "Home and launcher") {
            SettingsToggleRow(
                title = "Show Terminal Launcher on Home",
                description = "Adds an optional shortcut card to the Home dashboard.",
                checked = settings.showLauncherOnHome,
                onCheckedChange = {
                    onUpdateSettings(settings.copy(showLauncherOnHome = it))
                },
            )
            NrPrimaryButton(
                text = "Manage terminal shortcuts",
                onClick = onManageShortcuts,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SettingsCard(title = "Connection") {
            SettingsToggleRow(
                title = "Autoconnect",
                description = "Connect automatically when the Terminal tab is opened, if a saved password exists.",
                checked = settings.autoconnect,
                onCheckedChange = {
                    onUpdateSettings(settings.copy(autoconnect = it))
                },
            )
            SettingsToggleRow(
                title = "Save password securely",
                description = "Stores the Termux password using Android Keystore. Disable to forget it.",
                checked = settings.savePassword,
                onCheckedChange = {
                    onUpdateSettings(settings.copy(savePassword = it))
                },
            )
            NrSecondaryButton(
                text = "Clear pinned SSH host key",
                onClick = onClearPinnedHostKey,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SettingsCard(title = "SSH key") {
            Text(
                text = if (hasPrivateKey) {
                    "A private key is saved and will be tried before password authentication."
                } else {
                    "Import an OpenSSH/PEM private key. Password auth remains available as fallback."
                },
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NrPrimaryButton(
                    text = if (hasPrivateKey) "Replace key" else "Import key",
                    onClick = { keyPicker.launch(arrayOf("application/octet-stream", "*/*")) },
                    compact = true,
                )
                if (hasPrivateKey) {
                    NrSecondaryButton(
                        text = "Remove key",
                        onClick = onClearPrivateKey,
                        compact = true,
                    )
                }
            }
        }

        SettingsCard(title = "Appearance") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Default font size", fontWeight = FontWeight.Medium)
                    Text(
                        "${settings.defaultFontSize} dp",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                NrQuietButton(
                    text = "−",
                    onClick = {
                        onUpdateSettings(
                            settings.copy(
                                defaultFontSize = (settings.defaultFontSize - 1).coerceAtLeast(9),
                            ),
                        )
                    },
                    compact = true,
                )
                Spacer(Modifier.width(6.dp))
                NrQuietButton(
                    text = "+",
                    onClick = {
                        onUpdateSettings(
                            settings.copy(
                                defaultFontSize = (settings.defaultFontSize + 1).coerceAtMost(28),
                            ),
                        )
                    },
                    compact = true,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Cursor blink", fontWeight = FontWeight.Medium)
                    Text(
                        "${settings.cursorBlinkMs} ms · underline cursor",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                NrQuietButton(
                    text = "280 ms",
                    onClick = { onUpdateSettings(settings.copy(cursorBlinkMs = 280)) },
                    compact = true,
                )
                Spacer(Modifier.width(6.dp))
                NrQuietButton(
                    text = "500 ms",
                    onClick = { onUpdateSettings(settings.copy(cursorBlinkMs = 500)) },
                    compact = true,
                )
            }
        }
    }

    pendingKeyUri?.let { uri ->
        AlertDialog(
            onDismissRequest = {
                pendingKeyUri = null
                keyPassphrase = ""
            },
            title = { Text("Import SSH private key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter the key passphrase if the file is encrypted. Leave blank for an unencrypted key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = keyPassphrase,
                        onValueChange = { keyPassphrase = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Key passphrase") },
                    )
                }
            },
            confirmButton = {
                NrPrimaryButton(
                    text = "Import",
                    onClick = {
                        onImportPrivateKey(uri, keyPassphrase)
                        pendingKeyUri = null
                        keyPassphrase = ""
                    },
                )
            },
            dismissButton = {
                NrQuietButton(
                    text = "Cancel",
                    onClick = {
                        pendingKeyUri = null
                        keyPassphrase = ""
                    },
                )
            },
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, NrOutline.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
