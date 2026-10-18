package com.swp81x.nrsuite.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.swp81x.nrsuite.core.handshake.AutoHandshakeConfig
import com.swp81x.nrsuite.core.handshake.CapturedHandshakeEntry
import com.swp81x.nrsuite.core.handshake.HandshakeCrackStatus
import com.swp81x.nrsuite.core.handshake.PwnModeState
import com.swp81x.nrsuite.core.handshake.AutoHandshakeFeedEntry
import com.swp81x.nrsuite.core.handshake.AutoHandshakePhase
import com.swp81x.nrsuite.core.handshake.AutoHandshakeSession
import com.swp81x.nrsuite.core.handshake.AutoHandshakeTarget
import com.swp81x.nrsuite.core.handshake.AutoHandshakeTargetStatus
import com.swp81x.nrsuite.core.handshake.AutoHandshakeUiState
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.ui.components.ModuleStatusCard
import com.swp81x.nrsuite.ui.components.ModuleStatusState
import com.swp81x.nrsuite.ui.components.NetworkStatusBadge
import com.swp81x.nrsuite.ui.components.RoundedTabRow
import com.swp81x.nrsuite.ui.theme.LogColorError
import com.swp81x.nrsuite.ui.theme.LogColorInfo
import com.swp81x.nrsuite.ui.theme.LogColorSuccess
import com.swp81x.nrsuite.ui.theme.LogColorUsb
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed
import com.swp81x.nrsuite.ui.util.securityColor
import com.swp81x.nrsuite.ui.util.signalQualityColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun NrugochiScreen(
    connected: Boolean,
    state: AutoHandshakeUiState,
    sessions: List<AutoHandshakeSession>,
    feed: List<AutoHandshakeFeedEntry>,
    pwnMode: PwnModeState,
    captures: List<CapturedHandshakeEntry>,
    onStart: (AutoHandshakeConfig) -> Unit,
    onStop: () -> Unit,
    onResumeSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onClearSessions: () -> Unit,
    onPwnModeEnabledChange: (Boolean) -> Unit,
    onAddPwnPassword: (String) -> Unit,
    onRemovePwnPassword: (String) -> Unit,
    onCrackCapture: (CapturedHandshakeEntry) -> Unit,
    onDeleteCapture: (CapturedHandshakeEntry) -> Unit,
    onResetStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(0) }
    var configExpanded by remember { mutableStateOf(true) }

    var allChannels by remember { mutableStateOf(true) }
    var deauthAssist by remember { mutableStateOf(true) }
    var skipCapturedMacs by remember { mutableStateOf(true) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var maxApAttempts by remember { mutableStateOf(4) }
    var deauthFramesPerAp by remember { mutableStateOf(8) }
    var deauthIntervalMs by remember { mutableStateOf(50) }
    var channelWaitMs by remember { mutableStateOf(6000) }
    var channelWaitNoDeauthMs by remember { mutableStateOf(1500) }
    var rescanDelayMs by remember { mutableStateOf(1500) }

    var showAuthorization by remember { mutableStateOf(true) }

    fun buildConfig(): AutoHandshakeConfig = AutoHandshakeConfig(
        allChannels = allChannels,
        deauthAssist = deauthAssist,
        skipCapturedMacs = skipCapturedMacs,
        maxApAttemptsPerCycle = maxApAttempts.coerceIn(1, 20),
        deauthFramesPerAp = deauthFramesPerAp.coerceIn(1, 20),
        deauthIntervalMs = deauthIntervalMs.coerceIn(5, 10_000),
        channelWaitMs = channelWaitMs.coerceIn(500, 60_000).toLong(),
        channelWaitNoDeauthMs = channelWaitNoDeauthMs.coerceIn(250, 30_000).toLong(),
        rescanDelayMs = rescanDelayMs.coerceIn(250, 60_000).toLong(),
    )

    LaunchedEffect(state.running) {
        if (state.running) configExpanded = false
    }


    val stopping = state.running && state.phase == AutoHandshakePhase.STOPPING

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
        ) {
            item {
                ModuleStatusCard(
                    icon = Icons.Default.Key,
                    title = "NRuGochi",
                    subtitle = nrugochiSubtitle(connected, state),
                    state = when {
                        !connected -> ModuleStatusState.DISCONNECTED
                        state.error != null -> ModuleStatusState.ALERT
                        state.running -> ModuleStatusState.RUNNING
                        else -> ModuleStatusState.READY
                    },
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
            item { NrugochiBodyCard(state) }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                RoundedTabRow(
                    selectedIndex = selectedTab,
                    tabs = listOf("NRuGochi", "Captured"),
                    onSelect = { selectedTab = it },
                )
            }
            if (selectedTab == 0) {
                item { Spacer(Modifier.height(8.dp)) }
                item {
                    NrugochiConfigCard(
                        connected = connected,
                        state = state,
                        configExpanded = configExpanded,
                        allChannels = allChannels,
                        deauthAssist = deauthAssist,
                        skipCapturedMacs = skipCapturedMacs,
                        advancedExpanded = advancedExpanded,
                        maxApAttempts = maxApAttempts,
                        deauthFramesPerAp = deauthFramesPerAp,
                        deauthIntervalMs = deauthIntervalMs,
                        channelWaitMs = channelWaitMs,
                        channelWaitNoDeauthMs = channelWaitNoDeauthMs,
                        rescanDelayMs = rescanDelayMs,
                        onToggleConfig = { configExpanded = !configExpanded },
                        onAllChannelsChange = { allChannels = it },
                        onDeauthAssistChange = { deauthAssist = it },
                        onSkipCapturedMacsChange = { skipCapturedMacs = it },
                        onToggleAdvanced = { advancedExpanded = !advancedExpanded },
                        onResetStats = onResetStats,
                        onMaxApAttemptsChange = { maxApAttempts = it },
                        onDeauthFramesChange = { deauthFramesPerAp = it },
                        onDeauthIntervalChange = { deauthIntervalMs = it },
                        onChannelWaitChange = { channelWaitMs = it },
                        onChannelWaitNoDeauthChange = { channelWaitNoDeauthMs = it },
                        onRescanDelayChange = { rescanDelayMs = it },
                    )
                }
                item { Spacer(Modifier.height(10.dp)) }
                item {
                    NrugochiPwnModeCard(
                        pwnMode = pwnMode,
                        onEnabledChange = onPwnModeEnabledChange,
                        onAddPassword = onAddPwnPassword,
                        onRemovePassword = onRemovePwnPassword,
                    )
                }
                item { Spacer(Modifier.height(10.dp)) }
                item { NrugochiTargetsCard(state) }
                item { Spacer(Modifier.height(10.dp)) }
                item { NrugochiLiveCard(state) }
                item { Spacer(Modifier.height(10.dp)) }
                item { NrugochiFeedCard(feed) }
            } else {
                item { Spacer(Modifier.height(8.dp)) }
                item { NrugochiCapturedTab(captures, onCrackCapture, onDeleteCapture) }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }

        if (connected && !showAuthorization) {
            ModuleActionFab(
                onClick = {
                    if (!stopping) {
                        if (state.running) {
                            onStop()
                        } else {
                            onStart(buildConfig())
                        }
                    }
                },
                enabled = !stopping,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = when {
                    stopping -> StatusAmber
                    state.running -> StatusRed
                    else -> MaterialTheme.colorScheme.primary
                },
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                if (stopping) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        imageVector = if (state.running) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (state.running) "Stop NRuGochi" else "Start NRuGochi",
                    )
                }
            }
        }
    }

    if (showAuthorization) {
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
            title = { Text("NRuGochi — authorized use warning") },
            text = {
                Text(
                    "NRuGochi performs autonomous channel hopping, active deauthentication, and WPA2 " +
                        "handshake capture. Use it only on networks you own or have explicit written " +
                        "permission to test. Do not use it against third-party networks.",
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


@Composable
private fun NrugochiBodyCard(state: AutoHandshakeUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(88.dp)
                        .height(54.dp)
                        .background(NrSurfaceVariant, RoundedCornerShape(14.dp))
                        .border(0.5.dp, NrOutline, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.reactionFace,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                        softWrap = false,
                        color = when {
                            state.error != null -> StatusRed
                            state.progress.valid -> StatusGreen
                            state.running -> MaterialTheme.colorScheme.primary
                            else -> NrOnSurfaceVariant
                        },
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.reactionPhrase,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            state.error != null -> StatusRed
                            state.progress.valid -> StatusGreen
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NrugochiStat("CH", state.currentChannel?.toString() ?: "--", Modifier.weight(1f))
                NrugochiStat("HS CYCLE", state.cycleHandshakeCount.toString(), Modifier.weight(1f))
                NrugochiStat("TODAY", state.stats.todayCaptures.toString(), Modifier.weight(1f))
                NrugochiStat("TOTAL", state.stats.totalCaptures.toString(), Modifier.weight(1f))
            }
            if (!state.stats.lastBssid.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Last: ${state.stats.lastBssid}  ${state.stats.lastCapturedAt.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NrugochiStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NrOnSurfaceVariant,
        )
    }
}

@Composable
private fun NrugochiConfigCard(
    connected: Boolean,
    state: AutoHandshakeUiState,
    configExpanded: Boolean,
    allChannels: Boolean,
    deauthAssist: Boolean,
    skipCapturedMacs: Boolean,
    advancedExpanded: Boolean,
    maxApAttempts: Int,
    deauthFramesPerAp: Int,
    deauthIntervalMs: Int,
    channelWaitMs: Int,
    channelWaitNoDeauthMs: Int,
    rescanDelayMs: Int,
    onToggleConfig: () -> Unit,
    onAllChannelsChange: (Boolean) -> Unit,
    onDeauthAssistChange: (Boolean) -> Unit,
    onSkipCapturedMacsChange: (Boolean) -> Unit,
    onToggleAdvanced: () -> Unit,
    onResetStats: () -> Unit,
    onMaxApAttemptsChange: (Int) -> Unit,
    onDeauthFramesChange: (Int) -> Unit,
    onDeauthIntervalChange: (Int) -> Unit,
    onChannelWaitChange: (Int) -> Unit,
    onChannelWaitNoDeauthChange: (Int) -> Unit,
    onRescanDelayChange: (Int) -> Unit,
) {
    val fieldsEnabled = connected && !state.running

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "NRuGochi configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (deauthAssist) {
                            "Channel hunting with active deauth"
                        } else {
                            "Channel hunting, passive capture"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (deauthAssist) StatusAmber else NrOnSurfaceVariant,
                    )
                }
                IconButton(onClick = onToggleConfig) {
                    Icon(
                        imageVector = if (configExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (configExpanded) "Collapse" else "Expand",
                    )
                }
            }

            if (configExpanded) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("All channels", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Off = 1/6/11 only. On = every scanned channel.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = allChannels,
                        enabled = fieldsEnabled,
                        onCheckedChange = onAllChannelsChange,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Active deauth assist", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Sends authorized deauth bursts before capture.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = deauthAssist,
                        enabled = fieldsEnabled,
                        onCheckedChange = onDeauthAssistChange,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Skip already captured MACs", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "If a BSSID already has HS_*.pcap, skip it automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = skipCapturedMacs,
                        enabled = fieldsEnabled,
                        onCheckedChange = onSkipCapturedMacsChange,
                    )
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onToggleAdvanced, enabled = fieldsEnabled) {
                    Text(if (advancedExpanded) "Hide advanced" else "Advanced options")
                }
                if (advancedExpanded) {
                    NumberField(
                        label = "Max AP attempts per cycle",
                        value = maxApAttempts,
                        enabled = fieldsEnabled,
                        onValueChange = onMaxApAttemptsChange,
                    )
                    Spacer(Modifier.height(6.dp))
                    NumberField(
                        label = "Deauth frames per AP",
                        value = deauthFramesPerAp,
                        enabled = fieldsEnabled,
                        onValueChange = onDeauthFramesChange,
                    )
                    Text(
                        text = "Frames sent to each eligible AP on every channel pass. " +
                            "The default is 8; a single frame is often ignored, especially on 5 GHz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    NumberField(
                        label = "Deauth interval (ms)",
                        value = deauthIntervalMs,
                        enabled = fieldsEnabled,
                        onValueChange = onDeauthIntervalChange,
                    )
                    Spacer(Modifier.height(6.dp))
                    NumberField(
                        label = "Channel wait after deauth (ms)",
                        value = channelWaitMs,
                        enabled = fieldsEnabled,
                        onValueChange = onChannelWaitChange,
                    )
                    Spacer(Modifier.height(6.dp))
                    NumberField(
                        label = "Channel wait passive (ms)",
                        value = channelWaitNoDeauthMs,
                        enabled = fieldsEnabled,
                        onValueChange = onChannelWaitNoDeauthChange,
                    )
                    Spacer(Modifier.height(6.dp))
                    NumberField(
                        label = "Rescan delay (ms)",
                        value = rescanDelayMs,
                        enabled = fieldsEnabled,
                        onValueChange = onRescanDelayChange,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onResetStats,
                        enabled = fieldsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Reset capture statistics")
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { text ->
            text.filter { it.isDigit() }.take(6).toIntOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NrugochiPwnModeCard(
    pwnMode: PwnModeState,
    onEnabledChange: (Boolean) -> Unit,
    onAddPassword: (String) -> Unit,
    onRemovePassword: (String) -> Unit,
) {
    var newPassword by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Pwn Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (pwnMode.enabled) {
                            "New captures are checked against your saved passwords."
                        } else {
                            "Disabled by default."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (pwnMode.enabled) StatusGreen else NrOnSurfaceVariant,
                    )
                }
                Switch(
                    checked = pwnMode.enabled,
                    onCheckedChange = onEnabledChange,
                )
            }

            if (pwnMode.enabled) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Password") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            onAddPassword(newPassword)
                            newPassword = ""
                        },
                        enabled = pwnMode.passwords.size < 10,
                    ) {
                        Text("Add")
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (pwnMode.passwords.isEmpty()) {
                    Text(
                        text = "No passwords saved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                } else {
                    pwnMode.passwords.forEach { password ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = password,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onRemovePassword(password) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove password")
                            }
                        }
                    }
                }
                Text(
                    text = "Stored on this device only. Maximum 10 passwords.",
                    style = MaterialTheme.typography.labelSmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NrugochiCapturedTab(
    captures: List<CapturedHandshakeEntry>,
    onCrackCapture: (CapturedHandshakeEntry) -> Unit,
    onDeleteCapture: (CapturedHandshakeEntry) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "Captured handshakes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${captures.size} saved file(s)",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (captures.isEmpty()) {
                Text(
                    text = "No captured handshakes yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else {
                captures.forEach { capture ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
                        border = BorderStroke(0.5.dp, NrOutline),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = capture.ssid.ifBlank { "(hidden)" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = capture.bssid,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                        ),
                                        color = NrOnSurfaceVariant,
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onCrackCapture(capture) },
                                    enabled = capture.status != HandshakeCrackStatus.CRACKED,
                                ) {
                                    Text("Crack")
                                }
                                IconButton(onClick = { onDeleteCapture(capture) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete capture")
                                }
                            }
                            Text(
                                text = "ch ${capture.channel}  ·  ${capture.capturedAt}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = NrOnSurfaceVariant,
                            )
                            when (capture.status) {
                                HandshakeCrackStatus.CRACKED -> Text(
                                    text = "CRACKED: ${capture.password.orEmpty()}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = StatusGreen,
                                )
                                HandshakeCrackStatus.FAILED -> Text(
                                    text = "Checked: no match",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StatusAmber,
                                )
                                HandshakeCrackStatus.UNCLEARED -> Text(
                                    text = "Not checked",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NrOnSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun NrugochiTargetsCard(state: AutoHandshakeUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "Recon targets",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            if (state.targets.isEmpty()) {
                Text(
                    text = "Waiting for the first scan. Channels are ranked by AP density, then APs by RSSI.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else {
                state.targets.take(12).forEach { target ->
                    NrugochiTargetRow(target)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun NrugochiTargetRow(target: AutoHandshakeTarget) {
    val statusColor = when (target.status) {
        AutoHandshakeTargetStatus.CAPTURED -> StatusGreen
        AutoHandshakeTargetStatus.ACTIVE -> StatusAmber
        AutoHandshakeTargetStatus.NO_HANDSHAKE -> StatusRed
        AutoHandshakeTargetStatus.INTERRUPTED -> StatusAmber
        AutoHandshakeTargetStatus.UNSUPPORTED -> StatusNeutral
        AutoHandshakeTargetStatus.SKIPPED -> StatusNeutral
        AutoHandshakeTargetStatus.PENDING -> NrOnSurfaceVariant
    }
    val securityTint = securityColor(target.target.security)
    val signalTint = signalQualityColor(target.target.rssi)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = target.target.ssid.ifBlank { "(hidden)" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = target.target.bssid,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = NrOnSurfaceVariant,
                    )
                }
                NetworkStatusBadge(text = target.status.name, color = statusColor)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "ch ${target.target.channel}  ·  ${target.target.rssi} dBm  ·  " +
                    "${target.target.security}  ·  attempts ${target.attempts.size}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = signalTint,
            )
            if (target.attempts.isNotEmpty()) {
                val last = target.attempts.last()
                Text(
                    text = "last: ${if (last.valid) "captured" else "no handshake"}  " +
                        "pkts=${last.packetCount}  deauth=${last.deauthBursts}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = securityTint,
                )
            }
        }
    }
}

@Composable
private fun NrugochiLiveCard(state: AutoHandshakeUiState) {
    if (!state.running && state.progress.packetCount == 0L) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "Live channel",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "ch ${state.currentChannel ?: "--"}  ·  " +
                    "packets ${state.progress.packetCount}  ·  eapol ${state.progress.eapolCount}  ·  " +
                    "deauth ${state.progress.deauthBursts}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NetworkStatusBadge(
                    text = "M1 ${if (state.progress.m1Found) "OK" else "—"}",
                    color = if (state.progress.m1Found) StatusGreen else StatusNeutral,
                )
                NetworkStatusBadge(
                    text = "M2 ${if (state.progress.m2Found) "OK" else "—"}",
                    color = if (state.progress.m2Found) StatusGreen else StatusNeutral,
                )
                if (state.progress.valid) {
                    NetworkStatusBadge(text = "VALID", color = StatusGreen)
                }
            }
        }
    }
}

@Composable
private fun NrugochiFeedCard(feed: List<AutoHandshakeFeedEntry>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "NRuGochi activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            if (feed.isEmpty()) {
                Text(
                    text = "No events yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else {
                feed.takeLast(10).forEach { entry ->
                    Text(
                        text = "${formatNrugochiTime(entry.timestampMs)}  ${entry.message}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = when (entry.level) {
                            LogLevel.SUCCESS -> LogColorSuccess
                            LogLevel.ERROR -> LogColorError
                            LogLevel.USB -> LogColorUsb
                            LogLevel.INFO -> LogColorInfo
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NrugochiSessionsTab(
    state: AutoHandshakeUiState,
    sessions: List<AutoHandshakeSession>,
    onResumeSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onRequestClear: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Saved sessions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (sessions.isNotEmpty()) {
                    OutlinedButton(onClick = onRequestClear, enabled = !state.running) {
                        Text("Clear")
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            if (sessions.isEmpty()) {
                Text(
                    text = "No saved sessions yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else {
                sessions.take(12).forEach { session ->
                    NrugochiSessionRow(
                        session = session,
                        running = state.running,
                        onResumeSession = onResumeSession,
                        onDeleteSession = onDeleteSession,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun NrugochiSessionRow(
    session: AutoHandshakeSession,
    running: Boolean,
    onResumeSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
) {
    val captured = session.targets.count { it.status == AutoHandshakeTargetStatus.CAPTURED }
    val attempts = session.targets.sumOf { it.attempts.size }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                text = "Session ${session.id}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${session.createdAt}  state=${session.state.name.lowercase()}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
            )
            Text(
                text = "targets=${session.targets.size}  captured=$captured  attempts=$attempts",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onResumeSession(session.id) },
                    enabled = !running,
                ) {
                    Text("Resume")
                }
                IconButton(
                    onClick = { onDeleteSession(session.id) },
                    enabled = !running,
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete session")
                }
            }
        }
    }
}

private fun nrugochiSubtitle(connected: Boolean, state: AutoHandshakeUiState): String {
    if (!connected) return "Connect a device to start NRuGochi."
    if (!state.running) {
        return state.error ?: "Ready."
    }
    return when (state.phase) {
        AutoHandshakePhase.RECON -> "Scanning channels..."
        AutoHandshakePhase.INTERACT -> "Channel ${state.currentChannel ?: "--"} · ${state.cycleHandshakeCount} handshake(s)"
        AutoHandshakePhase.SCANNING -> "Scanning..."
        AutoHandshakePhase.SURVEYING -> "Surveying..."
        AutoHandshakePhase.RANKING -> "Ranking..."
        AutoHandshakePhase.TARGET_READY -> "Selecting next target..."
        AutoHandshakePhase.CAPTURING -> "Capturing..."
        AutoHandshakePhase.DEAUTH_ASSIST -> "Deauth assist..."
        AutoHandshakePhase.HANDSHAKE_FOUND -> "Handshake found."
        AutoHandshakePhase.SAVING -> "Saving..."
        AutoHandshakePhase.STOPPING -> "Stopping safely..."
        AutoHandshakePhase.PAUSED -> "Paused."
        AutoHandshakePhase.COMPLETED -> "Completed."
        AutoHandshakePhase.FAILED -> state.error ?: "Needs attention."
        AutoHandshakePhase.INTERRUPTED -> state.error ?: "Interrupted."
        AutoHandshakePhase.IDLE -> "Idle."
    }
}

private fun formatNrugochiTime(ms: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))
