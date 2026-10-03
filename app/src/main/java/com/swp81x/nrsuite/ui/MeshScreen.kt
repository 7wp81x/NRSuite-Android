package com.swp81x.nrsuite.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.mesh.MeshNodeStatus
import com.swp81x.nrsuite.ui.components.NetworkStatusBadge
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.CategoryBleBlue
import com.swp81x.nrsuite.ui.theme.CategoryMeshIndigo
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrBackground
import com.swp81x.nrsuite.ui.theme.NrOnSurface
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed
import java.util.UUID

enum class MeshTab { Setup, Network }

enum class MeshSetupStep { Connect, Passphrase, Activate }

enum class ActionTone { Positive, Warning, Neutral }

private fun currentMeshStep(connected: Boolean, initialized: Boolean): MeshSetupStep =
    when {
        !connected -> MeshSetupStep.Connect
        !initialized -> MeshSetupStep.Passphrase
        else -> MeshSetupStep.Activate
    }

private fun meshRoleColor(role: String): Color = when (role) {
    "master" -> StatusGreen
    "client" -> CategoryMeshIndigo
    "candidate" -> StatusAmber
    else -> StatusNeutral
}

private fun actionToneColor(tone: ActionTone): Color = when (tone) {
    ActionTone.Positive -> StatusGreen
    ActionTone.Warning -> StatusRed
    ActionTone.Neutral -> StatusAmber
}

@Composable
fun MeshScreen(
    connected: Boolean,
    initialized: Boolean,
    role: String,
    active: Boolean,
    sessionId: Long?,
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
    nodes: List<MeshNodeStatus>,
    onRefresh: () -> Unit,
    onPassphraseChange: (String) -> Unit,
    onKeyIdChange: (String) -> Unit,
    onCheckPassphrase: (String) -> Unit,
    onProvision: (passphrase: String, keyId: String?) -> Unit,
    onProvisionWithSavedCredentials: () -> Unit,
    onAuthenticateAndActivate: (String) -> Unit,
    onAuthenticateStored: () -> Unit,
    onDeactivate: () -> Unit,
    onForgetPassphrase: () -> Unit,
    onClearKeys: () -> Unit,
    onClearNodes: () -> Unit,
    onClearSetupMessage: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var confirmPassphrase by remember { mutableStateOf("") }
    var showPassphrase by remember { mutableStateOf(false) }
    var confirmReplace by remember { mutableStateOf(false) }
    var confirmClearKeys by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(false) }

    val tab = if (selectedTab == 0) MeshTab.Setup else MeshTab.Network
    val step = currentMeshStep(connected, initialized)
    val useSavedCredentials = initialized && hasStoredCredentials && passphrase.isBlank()
    val passphraseValid = passphrase.length >= 8
    val passphrasesMatch = confirmPassphrase.isNotBlank() && passphrase == confirmPassphrase
    val canProvision = connected && !provisioning && passphraseValid && passphrasesMatch
    val canCheck = connected && initialized && passphraseValid && !checkingPassphrase && !provisioning
    val canActivateStored = connected && initialized && useSavedCredentials && !provisioning && !active
    val canActivateWithPassphrase = connected && initialized && passphraseMatch == true &&
        passphraseValid && !provisioning && !active
    val canActivate = active || canActivateStored || canActivateWithPassphrase
    val fabEnabled = connected && canActivate
    val statusColor = meshRoleColor(role)

    val clipboard = LocalClipboardManager.current
    val statusSummary = remember(
        connected,
        initialized,
        role,
        sessionId,
        nodeId,
        active,
        nodes,
    ) {
        buildString {
            appendLine("NRSuite Mesh")
            appendLine("connected=$connected")
            appendLine("initialized=$initialized")
            appendLine("role=$role")
            appendLine("active=$active")
            appendLine("node_id=${nodeId.ifBlank { "-" }}")
            appendLine("session_id=${sessionId ?: "-"}")
            appendLine("peer_count=${nodes.count { it.online && it.role == "client" }}")
            if (nodes.isNotEmpty()) {
                appendLine("nodes=")
                nodes.forEach { node ->
                    appendLine("  ${node.nodeId} ${node.role} online=${node.online} rssi=${node.rssi ?: "-"}")
                }
            }
        }.trimEnd()
    }

    fun generatePassphrase() {
        val generated = UUID.randomUUID().toString().replace("-", "")
        onPassphraseChange(generated)
        confirmPassphrase = generated
        showPassphrase = true
    }

    LaunchedEffect(connected) {
        if (connected) onRefresh()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Setup") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Network") },
                )
            }

            Spacer(Modifier.height(10.dp))

            StatusIndicator(
                label = nodeId.ifBlank { "-" },
                subtitle = if (active) "active · session #${sessionId ?: "-"}" else "inactive",
                color = statusColor,
                trailingBadge = role
                    .takeIf { it in setOf("master", "client", "candidate") }
                    ?.uppercase(),
                trailingBadgeColor = statusColor,
                monospace = true,
            )

            Spacer(Modifier.height(10.dp))

            if (tab == MeshTab.Setup) {
                MeshSetupTab(
                    step = step,
                    connected = connected,
                    initialized = initialized,
                    active = active,
                    passphrase = passphrase,
                    keyId = keyId,
                    confirmPassphrase = confirmPassphrase,
                    onConfirmPassphraseChange = { confirmPassphrase = it },
                    showPassphrase = showPassphrase,
                    onToggleShowPassphrase = { showPassphrase = !showPassphrase },
                    passphraseValid = passphraseValid,
                    passphrasesMatch = passphrasesMatch,
                    passphraseMatch = passphraseMatch,
                    checkingPassphrase = checkingPassphrase,
                    provisioning = provisioning,
                    setupMessage = setupMessage,
                    hasStoredCredentials = hasStoredCredentials,
                    storedKeyId = storedKeyId,
                    hasGlobalCredentials = hasGlobalCredentials,
                    globalKeyId = globalKeyId,
                    useSavedCredentials = useSavedCredentials,
                    canProvision = canProvision,
                    canCheck = canCheck,
                    onPassphraseChange = onPassphraseChange,
                    onKeyIdChange = onKeyIdChange,
                    onCheckPassphrase = onCheckPassphrase,
                    onProvision = onProvision,
                    onProvisionWithSavedCredentials = onProvisionWithSavedCredentials,
                    onGeneratePassphrase = { generatePassphrase() },
                    onReplaceKeys = { confirmReplace = true },
                    onRefresh = onRefresh,
                    onSwitchToNetwork = { selectedTab = 1 },
                    onClearSetupMessage = onClearSetupMessage,
                )
            } else {
                MeshNetworkTab(
                    connected = connected,
                    initialized = initialized,
                    nodes = nodes,
                    statusSummary = statusSummary,
                    onRefresh = onRefresh,
                    onClearNodes = onClearNodes,
                    onCopyStatus = { clipboard.setText(AnnotatedString(statusSummary)) },
                    onSwitchToSetup = { selectedTab = 0 },
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { advancedExpanded = !advancedExpanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Advanced",
                    style = MaterialTheme.typography.labelMedium,
                    color = NrOnSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (advancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = NrOnSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (advancedExpanded) {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = NrSurface),
                    border = BorderStroke(0.5.dp, NrOutline),
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
                            text = "Clearing device keys removes mesh capability from the ESP32. " +
                                "Forgetting credentials removes the encrypted copy on this phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { confirmClearKeys = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = connected && initialized && !provisioning,
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Clear device keys")
                        }
                        OutlinedButton(
                            onClick = onForgetPassphrase,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = hasGlobalCredentials || hasStoredCredentials ||
                                passphrase.isNotBlank() || keyId.isNotBlank(),
                        ) {
                            Text("Forget saved credentials")
                        }
                    }
                }
            }

            if (!lastError.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = NrSurface),
                    border = BorderStroke(0.5.dp, NrOutline),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Mesh error",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusRed,
                        )
                        SelectionContainer {
                            Text(
                                text = lastError,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = NrOnSurface,
                            )
                        }
                        TextButton(onClick = onClearError) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }

        FloatingActionButton(
            onClick = {
                if (!fabEnabled) return@FloatingActionButton
                when {
                    active -> onDeactivate()
                    useSavedCredentials -> onAuthenticateStored()
                    else -> onAuthenticateAndActivate(passphrase)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .alpha(if (fabEnabled) 1f else 0.4f),
            containerColor = if (active) StatusRed else NrAccent,
        ) {
            Icon(
                imageVector = if (active) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (active) "Deactivate mesh" else "Authenticate and activate mesh",
            )
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
private fun MeshSetupTab(
    step: MeshSetupStep,
    connected: Boolean,
    initialized: Boolean,
    active: Boolean,
    passphrase: String,
    keyId: String,
    confirmPassphrase: String,
    onConfirmPassphraseChange: (String) -> Unit,
    showPassphrase: Boolean,
    onToggleShowPassphrase: () -> Unit,
    passphraseValid: Boolean,
    passphrasesMatch: Boolean,
    passphraseMatch: Boolean?,
    checkingPassphrase: Boolean,
    provisioning: Boolean,
    setupMessage: String?,
    hasStoredCredentials: Boolean,
    storedKeyId: String?,
    hasGlobalCredentials: Boolean,
    globalKeyId: String?,
    useSavedCredentials: Boolean,
    canProvision: Boolean,
    canCheck: Boolean,
    onPassphraseChange: (String) -> Unit,
    onKeyIdChange: (String) -> Unit,
    onCheckPassphrase: (String) -> Unit,
    onProvision: (passphrase: String, keyId: String?) -> Unit,
    onProvisionWithSavedCredentials: () -> Unit,
    onGeneratePassphrase: () -> Unit,
    onReplaceKeys: () -> Unit,
    onRefresh: () -> Unit,
    onSwitchToNetwork: () -> Unit,
    onClearSetupMessage: () -> Unit,
) {
    MeshStepIndicator(step)

    Spacer(Modifier.height(10.dp))

    when (step) {
        MeshSetupStep.Connect -> MeshStepCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Connect an NRSuite device over USB to continue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NrOnSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        MeshSetupStep.Passphrase -> MeshStepCard {
            Text(
                text = "Set up this device",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = NrOnSurface,
            )

            if (hasGlobalCredentials) {
                SavedCredentialsCard(
                    keyLabel = globalKeyId,
                    onUse = onProvisionWithSavedCredentials,
                )
                MeshOrDivider()
            }

            MeshPassphraseFields(
                passphrase = passphrase,
                keyId = keyId,
                confirmPassphrase = confirmPassphrase,
                onConfirmPassphraseChange = onConfirmPassphraseChange,
                showPassphrase = showPassphrase,
                onToggleShowPassphrase = onToggleShowPassphrase,
                passphraseValid = passphraseValid,
                passphrasesMatch = passphrasesMatch,
                enabled = connected && !provisioning && !checkingPassphrase,
                onPassphraseChange = onPassphraseChange,
                onKeyIdChange = onKeyIdChange,
                onGeneratePassphrase = onGeneratePassphrase,
            )

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

            if (!setupMessage.isNullOrBlank()) {
                MeshSetupMessage(message = setupMessage, onDismiss = onClearSetupMessage)
            }
        }

        MeshSetupStep.Activate -> {
            if (active) {
                MeshActionCard(
                    title = "Activated",
                    description = "Switch to the Network tab to view nodes.",
                    tone = ActionTone.Positive,
                    buttonLabel = "View Network",
                    buttonEnabled = true,
                    onClick = onSwitchToNetwork,
                )
            } else {
                val tone: ActionTone
                val title: String
                val description: String
                val buttonLabel: String
                val buttonEnabled: Boolean
                val buttonAction: () -> Unit

                when {
                    useSavedCredentials -> {
                        tone = ActionTone.Positive
                        title = "Saved credentials found"
                        description = "This node can authenticate without re-entering the passphrase. " +
                            "Use the FAB to activate."
                        buttonLabel = "Refresh status"
                        buttonEnabled = connected
                        buttonAction = onRefresh
                    }
                    passphraseMatch == true -> {
                        tone = ActionTone.Positive
                        title = "Ready to activate"
                        description = "Identity verified with this passphrase. Use the FAB to activate."
                        buttonLabel = "Refresh status"
                        buttonEnabled = connected
                        buttonAction = onRefresh
                    }
                    passphraseMatch == false -> {
                        tone = ActionTone.Warning
                        title = "Different passphrase"
                        description = "This device is already provisioned with a different passphrase. " +
                            "Replacing its keys will remove it from the current mesh."
                        buttonLabel = "Replace device keys"
                        buttonEnabled = canProvision
                        buttonAction = onReplaceKeys
                    }
                    else -> {
                        tone = ActionTone.Neutral
                        title = "Already provisioned"
                        description = "Enter the stored passphrase and verify it before activating."
                        buttonLabel = "Check passphrase"
                        buttonEnabled = canCheck
                        buttonAction = { onCheckPassphrase(passphrase) }
                    }
                }

                MeshActionCard(
                    title = title,
                    description = description,
                    tone = tone,
                    buttonLabel = buttonLabel,
                    buttonEnabled = buttonEnabled,
                    onClick = buttonAction,
                )

                if (!useSavedCredentials && passphraseMatch != true) {
                    Spacer(Modifier.height(8.dp))
                    MeshPassphraseFields(
                        passphrase = passphrase,
                        keyId = keyId,
                        confirmPassphrase = confirmPassphrase,
                        onConfirmPassphraseChange = onConfirmPassphraseChange,
                        showPassphrase = showPassphrase,
                        onToggleShowPassphrase = onToggleShowPassphrase,
                        passphraseValid = passphraseValid,
                        passphrasesMatch = passphrasesMatch,
                        enabled = connected && !provisioning && !checkingPassphrase,
                        onPassphraseChange = onPassphraseChange,
                        onKeyIdChange = onKeyIdChange,
                        onGeneratePassphrase = onGeneratePassphrase,
                    )
                }

                if (!setupMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    MeshSetupMessage(message = setupMessage, onDismiss = onClearSetupMessage)
                }
            }
        }
    }
}

@Composable
private fun MeshNetworkTab(
    connected: Boolean,
    initialized: Boolean,
    nodes: List<MeshNodeStatus>,
    statusSummary: String,
    onRefresh: () -> Unit,
    onClearNodes: () -> Unit,
    onCopyStatus: () -> Unit,
    onSwitchToSetup: () -> Unit,
) {
    if (!initialized) {
        MeshStepCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Mesh is not set up on this device",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = StatusAmber,
                )
                Text(
                    text = "Provision the shared passphrase first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                OutlinedButton(
                    onClick = onSwitchToSetup,
                    enabled = connected,
                ) {
                    Text("Go to Setup")
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Network control",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = NrOnSurface,
            )
            Text(
                text = "Role and activation are managed by app control and master election.",
                style = MaterialTheme.typography.bodySmall,
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
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Nodes (${nodes.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = NrOnSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onRefresh,
                    enabled = connected,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh mesh status")
                }
            }

            if (nodes.isEmpty()) {
                MeshEmptyNodesState()
            } else {
                nodes.forEach { node ->
                    MeshNodeRow(node)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = onCopyStatus,
                    enabled = nodes.isNotEmpty(),
                ) {
                    Text("Copy status")
                }
                TextButton(
                    onClick = onClearNodes,
                    enabled = nodes.isNotEmpty(),
                ) {
                    Text("Clear node list")
                }
            }
        }
    }
}

@Composable
private fun MeshStepIndicator(current: MeshSetupStep) {
    val steps = MeshSetupStep.entries
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        steps.forEachIndexed { index, step ->
            val done = index < current.ordinal
            val active = step == current
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        color = when {
                            done -> StatusGreen
                            active -> CategoryMeshIndigo
                            else -> Color.Transparent
                        },
                        shape = CircleShape,
                    )
                    .then(
                        if (!done && !active) {
                            Modifier.border(1.dp, NrOutline, CircleShape)
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed step",
                        tint = NrBackground,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) NrBackground else NrOnSurfaceVariant,
                    )
                }
            }
            if (index < steps.lastIndex) {
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .weight(1f)
                        .background(NrOutline),
                )
            }
        }
    }
}

@Composable
private fun MeshStepCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun SavedCredentialsCard(keyLabel: String?, onUse: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Saved mesh credentials",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = NrOnSurface,
            )
            Text(
                text = "Use the encrypted mesh credentials already stored on this phone" +
                    (keyLabel?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "") +
                    " to set up this new node without re-entering the passphrase.",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            Button(
                onClick = onUse,
                colors = ButtonDefaults.buttonColors(containerColor = NrAccent),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Use saved credentials")
            }
        }
    }
}

@Composable
private fun MeshPassphraseFields(
    passphrase: String,
    keyId: String,
    confirmPassphrase: String,
    onConfirmPassphraseChange: (String) -> Unit,
    showPassphrase: Boolean,
    onToggleShowPassphrase: () -> Unit,
    passphraseValid: Boolean,
    passphrasesMatch: Boolean,
    enabled: Boolean,
    onPassphraseChange: (String) -> Unit,
    onKeyIdChange: (String) -> Unit,
    onGeneratePassphrase: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = passphrase,
            onValueChange = onPassphraseChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            isError = passphrase.isNotBlank() && !passphraseValid,
            label = { Text("Mesh passphrase") },
            supportingText = {
                if (passphrase.isNotBlank() && !passphraseValid) {
                    Text("Use at least 8 characters.", color = StatusRed)
                }
            },
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
                    onClick = onGeneratePassphrase,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Generate passphrase")
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            OutlinedButton(
                onClick = onToggleShowPassphrase,
                enabled = passphrase.isNotEmpty(),
            ) {
                Text(if (showPassphrase) "Hide" else "Show")
            }
        }
        OutlinedTextField(
            value = confirmPassphrase,
            onValueChange = onConfirmPassphraseChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
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
            enabled = enabled,
            singleLine = true,
            label = { Text("Key label / ID (optional)") },
        )
    }
}

@Composable
private fun MeshOrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .height(1.dp)
                .weight(1f)
                .background(NrOutline),
        )
        Text(
            text = "OR ENTER MANUALLY",
            style = MaterialTheme.typography.labelSmall,
            color = NrOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Box(
            modifier = Modifier
                .height(1.dp)
                .weight(1f)
                .background(NrOutline),
        )
    }
}

@Composable
private fun MeshActionCard(
    title: String,
    description: String,
    tone: ActionTone,
    buttonLabel: String,
    buttonEnabled: Boolean,
    onClick: () -> Unit,
) {
    val toneColor = actionToneColor(tone)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = toneColor,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            Button(
                onClick = onClick,
                enabled = buttonEnabled,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = toneColor),
            ) {
                Text(buttonLabel)
            }
        }
    }
}

@Composable
private fun MeshSetupMessage(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
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

@Composable
private fun MeshEmptyNodesState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Default.SettingsInputAntenna,
            contentDescription = null,
            tint = NrOnSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = "No nodes yet",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = NrOnSurface,
        )
        Text(
            text = "The elected master appears here after activation.",
            style = MaterialTheme.typography.bodySmall,
            color = NrOnSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MeshNodeRow(node: MeshNodeStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (node.online) StatusGreen else StatusNeutral, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = node.nodeId,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = NrOnSurface,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NetworkStatusBadge(
                        text = node.role.ifBlank { "unknown" }.uppercase(),
                        color = meshRoleColor(node.role),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = node.rssi?.takeIf { it != 0 }?.let { "$it dBm" } ?: "RSSI -",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = NrOnSurfaceVariant,
                    )
                }
            }
            Text(
                text = if (node.online) "online" else "offline",
                style = MaterialTheme.typography.labelSmall,
                color = if (node.online) StatusGreen else StatusNeutral,
            )
        }
    }
}
