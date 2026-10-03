package com.swp81x.nrsuite.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.ui.theme.CategoryBleBlue
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOnSurface
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed
import java.util.UUID

@Composable
fun MeshSetupScreen(
    connected: Boolean,
    initialized: Boolean,
    role: String,
    active: Boolean,
    nodeId: String,
    passphrase: String,
    keyId: String,
    passphraseMatch: Boolean?,
    checkingPassphrase: Boolean,
    provisioning: Boolean,
    setupMessage: String?,
    lastError: String?,
    hasStoredCredentials: Boolean,
    storedKeyId: String?,
    hasGlobalCredentials: Boolean,
    globalKeyId: String?,
    onRefresh: () -> Unit,
    onPassphraseChange: (String) -> Unit,
    onKeyIdChange: (String) -> Unit,
    onCheckPassphrase: (String) -> Unit,
    onProvision: (passphrase: String, keyId: String?) -> Unit,
    onProvisionWithSavedCredentials: () -> Unit,
    onAuthenticateAndActivate: (String) -> Unit,
    onAuthenticateStored: () -> Unit,
    onForgetPassphrase: () -> Unit,
    onClearKeys: () -> Unit,
    onOpenNetwork: () -> Unit,
    onClearSetupMessage: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmPassphrase by remember { mutableStateOf("") }
    var showPassphrase by remember { mutableStateOf(false) }
    var confirmReplace by remember { mutableStateOf(false) }
    var confirmClearKeys by remember { mutableStateOf(false) }

    LaunchedEffect(connected) {
        if (connected) onRefresh()
    }

    val passphraseValid = passphrase.length >= 8
    val passphrasesMatch = confirmPassphrase.isNotBlank() && passphrase == confirmPassphrase
    val canCheck = connected && initialized && passphraseValid && !checkingPassphrase && !provisioning
    val canProvision = connected && !provisioning && passphraseValid && passphrasesMatch
    val canAuthenticate = connected && initialized && passphraseMatch == true &&
        passphraseValid && !provisioning && !active
    val canProvisionSaved = connected && !provisioning
    val useSavedCredentials = initialized && hasStoredCredentials && passphrase.isBlank()
    val canAuthenticateStored = connected && useSavedCredentials && !provisioning && !active

    fun generatePassphrase() {
        val generated = UUID.randomUUID().toString().replace("-", "")
        onPassphraseChange(generated)
        confirmPassphrase = generated
        showPassphrase = true
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            MeshSetupStatusCard(
                connected = connected,
                initialized = initialized,
                role = role,
                active = active,
                nodeId = nodeId,
                passphraseMatch = passphraseMatch,
            )

            Spacer(Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurface),
                border = BorderStroke(0.5.dp, NrOutline),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Mesh passphrase",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NrOnSurface,
                    )
                    Text(
                        text = "The passphrase is never sent over USB. The app derives the mesh keys on the phone " +
                            "and stores only those keys on the ESP32.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = onPassphraseChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = connected && !provisioning && !checkingPassphrase,
                        singleLine = true,
                        label = { Text("Mesh passphrase") },
                        visualTransformation = if (showPassphrase) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (passphrase.isBlank()) {
                            OutlinedButton(
                                onClick = { generatePassphrase() },
                                enabled = !provisioning && !checkingPassphrase,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Generate passphrase")
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        OutlinedButton(
                            onClick = { showPassphrase = !showPassphrase },
                            enabled = passphrase.isNotEmpty(),
                        ) {
                            Text(if (showPassphrase) "Hide" else "Show")
                        }
                    }

                    OutlinedTextField(
                        value = confirmPassphrase,
                        onValueChange = { confirmPassphrase = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = connected && !provisioning && !checkingPassphrase,
                        singleLine = true,
                        isError = confirmPassphrase.isNotBlank() && !passphrasesMatch,
                        label = { Text("Confirm passphrase") },
                        supportingText = {
                            if (confirmPassphrase.isNotBlank() && !passphrasesMatch) {
                                Text("Passphrases do not match.", color = StatusRed)
                            }
                        },
                        visualTransformation = if (showPassphrase) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                    )

                    OutlinedTextField(
                        value = keyId,
                        onValueChange = onKeyIdChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = connected && !provisioning && !checkingPassphrase,
                        singleLine = true,
                        label = { Text("Key label / ID (optional)") },
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = StatusAmber.copy(alpha = 0.10f)),
                        border = BorderStroke(0.5.dp, StatusAmber.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            text = "The raw passphrase is not stored. Encrypted mesh credentials are saved on this phone for future activation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurface,
                            modifier = Modifier.padding(10.dp),
                        )
                    }

                    if (!setupMessage.isNullOrBlank()) {
                        MeshSetupMessage(
                            message = setupMessage,
                            match = passphraseMatch,
                            onDismiss = onClearSetupMessage,
                        )
                    }

                    if (!initialized) {
                        if (hasGlobalCredentials) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = StatusGreen.copy(alpha = 0.10f)),
                                border = BorderStroke(0.5.dp, StatusGreen.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text(
                                    text = "Saved mesh credentials are available on this phone" +
                                        (globalKeyId?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "") +
                                        ". You can set up this new node without re-entering the passphrase.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NrOnSurface,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                            Button(
                                onClick = onProvisionWithSavedCredentials,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = canProvisionSaved,
                                colors = ButtonDefaults.buttonColors(containerColor = CategoryBleBlue),
                            ) {
                                Text(if (provisioning) "Setting up device..." else "Set up using saved credentials")
                            }
                            Text(
                                text = "Or enter a new passphrase below to create/join a different mesh.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NrOnSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = { onProvision(passphrase, keyId.ifBlank { null }) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canProvision,
                            colors = ButtonDefaults.buttonColors(containerColor = NrAccent),
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(if (provisioning) "Setting up device..." else "Set up this device")
                        }
                    } else if (useSavedCredentials) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = StatusGreen.copy(alpha = 0.10f)),
                            border = BorderStroke(0.5.dp, StatusGreen.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(
                                text = "Saved credentials found for this node" +
                                    (storedKeyId?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "") +
                                    ". No need to re-enter the passphrase.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NrOnSurface,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                        if (active) {
                            Button(
                                onClick = onOpenNetwork,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = CategoryBleBlue),
                            ) {
                                Text("Open Mesh Network")
                            }
                        } else {
                            Button(
                                onClick = onAuthenticateStored,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = canAuthenticateStored,
                                colors = ButtonDefaults.buttonColors(containerColor = CategoryBleBlue),
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("Authenticate & Activate")
                            }
                        }
                    } else {
                        when (passphraseMatch) {
                            true -> {
                                Text(
                                    text = "This device is already set up with this passphrase.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StatusGreen,
                                )
                                if (active) {
                                    Button(
                                        onClick = onOpenNetwork,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = CategoryBleBlue),
                                    ) {
                                        Text("Open Mesh Network")
                                    }
                                } else {
                                    Button(
                                        onClick = { onAuthenticateAndActivate(passphrase) },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = canAuthenticate,
                                        colors = ButtonDefaults.buttonColors(containerColor = CategoryBleBlue),
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.size(8.dp))
                                        Text("Authenticate & Activate")
                                    }
                                }
                            }

                            false -> {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = StatusRed.copy(alpha = 0.10f)),
                                    border = BorderStroke(0.5.dp, StatusRed.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Text(
                                        text = "This device is already provisioned with a different passphrase. " +
                                            "Replacing its keys will remove it from the current mesh.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NrOnSurface,
                                        modifier = Modifier.padding(10.dp),
                                    )
                                }
                                OutlinedButton(
                                    onClick = { confirmReplace = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = canProvision,
                                ) {
                                    Text("Replace device keys")
                                }
                            }

                            null -> {
                                Text(
                                    text = "This device is already provisioned. Check the stored passphrase before " +
                                        "making any changes.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StatusAmber,
                                )
                                Button(
                                    onClick = { onCheckPassphrase(passphrase) },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = canCheck,
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusAmber),
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(8.dp))
                                    Text(if (checkingPassphrase) "Checking passphrase..." else "Check passphrase")
                                }
                                TextButton(
                                    onClick = { confirmReplace = true },
                                    enabled = canProvision,
                                ) {
                                    Text("Replace existing keys anyway", color = StatusRed)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurface),
                border = BorderStroke(0.5.dp, NrOutline),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Session / device",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NrOnSurface,
                    )
                    Text(
                        text = "The node can keep its mesh keys across reboots. The app needs the passphrase again " +
                            "after it is restarted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onForgetPassphrase,
                            enabled = passphrase.isNotEmpty() || keyId.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Forget saved credentials")
                        }
                        OutlinedButton(
                            onClick = onOpenNetwork,
                            enabled = connected,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Mesh Network")
                        }
                    }
                }
            }

            if (initialized) {
                Spacer(Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = StatusRed.copy(alpha = 0.08f)),
                    border = BorderStroke(0.5.dp, StatusRed.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Danger zone",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusRed,
                        )
                        Text(
                            text = "Clearing device keys removes mesh capability from the ESP32. Keys stay in NVS " +
                                "when mesh is simply deactivated or stopped.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { confirmClearKeys = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = connected && !provisioning,
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Clear device keys")
                        }
                    }
                }
            }

            if (!lastError.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = StatusRed.copy(alpha = 0.12f)),
                    border = BorderStroke(0.5.dp, StatusRed.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Mesh error",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusRed,
                        )
                        Text(
                            text = lastError,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = NrOnSurface,
                        )
                        TextButton(onClick = onClearError) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Replace existing mesh keys?") },
            text = {
                Text(
                    "This device is already provisioned. Replacing its keys will remove it from the current mesh " +
                        "until other nodes are provisioned with the new passphrase too."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReplace = false
                        onProvision(passphrase, keyId.ifBlank { null })
                    },
                ) {
                    Text("Replace keys", color = StatusRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReplace = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (confirmClearKeys) {
        AlertDialog(
            onDismissRequest = { confirmClearKeys = false },
            title = { Text("Clear mesh keys?") },
            text = {
                Text(
                    "This removes the derived keys from the device NVS. The node will remain standalone until it is " +
                        "re-provisioned with the same or a new passphrase."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearKeys = false
                        onClearKeys()
                    },
                ) {
                    Text("Clear keys", color = StatusRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearKeys = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun MeshSetupStatusCard(
    connected: Boolean,
    initialized: Boolean,
    role: String,
    active: Boolean,
    nodeId: String,
    passphraseMatch: Boolean?,
) {
    val statusColor = when {
        !connected -> StatusNeutral
        !initialized -> StatusAmber
        passphraseMatch == true -> StatusGreen
        passphraseMatch == false -> StatusRed
        active -> CategoryBleBlue
        else -> StatusNeutral
    }
    val statusText = when {
        !connected -> "Device needed"
        !initialized -> "Not provisioned"
        passphraseMatch == true -> "Setup complete"
        passphraseMatch == false -> "Different passphrase"
        else -> "Already provisioned"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SettingsInputAntenna,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Mesh Setup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NrOnSurface,
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(statusColor, RoundedCornerShape(50)),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Node ID", style = MaterialTheme.typography.bodySmall, color = NrOnSurfaceVariant)
                Text(
                    text = nodeId.ifBlank { "-" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = NrOnSurface,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Role", style = MaterialTheme.typography.bodySmall, color = NrOnSurfaceVariant)
                Text(role, style = MaterialTheme.typography.bodySmall, color = NrOnSurface)
            }
        }
    }
}

@Composable
private fun MeshSetupMessage(
    message: String,
    match: Boolean?,
    onDismiss: () -> Unit,
) {
    val color = when (match) {
        true -> StatusGreen
        false -> StatusRed
        null -> StatusAmber
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurface,
            )
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}
