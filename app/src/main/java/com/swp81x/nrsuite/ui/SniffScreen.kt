package com.swp81x.nrsuite.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.swp81x.nrsuite.core.eapol.EapolHandshake
import org.json.JSONObject
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.sniff.SniffRequest
import com.swp81x.nrsuite.core.wifi.WifiBand
import com.swp81x.nrsuite.core.wifi.bandForWifiChannel
import com.swp81x.nrsuite.core.wifi.normalizeWifiChannel
import com.swp81x.nrsuite.ui.components.ChannelModeToggle
import com.swp81x.nrsuite.ui.components.NetworkTargetRow
import com.swp81x.nrsuite.ui.components.WifiBandConfigSection
import com.swp81x.nrsuite.ui.components.WifiBandPill
import com.swp81x.nrsuite.ui.components.WifiChannelStepper
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed
import java.util.Locale

private val SNIFF_MAC_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

@Composable
fun SniffScreen(
    connected: Boolean,
    sniffing: Boolean,
    packetCount: Long,
    captureBytes: Long,
    capturePath: String?,
    handshake: EapolHandshake,
    scanning: Boolean,
    networks: List<JSONObject>,
    onScanWifi: () -> Unit,
    onStart: (SniffRequest) -> Unit,
    onStop: () -> Unit,
    showBandSelector: Boolean = false,
    fiveGhzAvailable: Boolean = false,
    fiveGhzUnavailableMessage: String = "",
    allowedChannels: List<Int> = emptyList(),
    modifier: Modifier = Modifier,
) {
    var configExpanded by remember { mutableStateOf(true) }
    var fixedMode by remember { mutableStateOf(true) }
    var channel by remember { mutableStateOf(6) }
    var band by rememberSaveable { mutableStateOf(WifiBand.AUTO) }
    var intervalMs by remember { mutableStateOf(300) }
    var deauthBeforeCapture by remember { mutableStateOf(false) }
    var eapolOnly by remember { mutableStateOf(false) }
    var captureMode by rememberSaveable { mutableIntStateOf(0) }
    var targetBssid by remember { mutableStateOf("") }
    var client by remember { mutableStateOf("FF:FF:FF:FF:FF:FF") }
    var deauthCount by remember { mutableStateOf(8) }
    var deauthIntervalMs by remember { mutableStateOf(50) }
    var deauthRetryDuringCapture by remember { mutableStateOf(true) }
    var deauthRetryIntervalMs by remember { mutableStateOf(5_000) }

    LaunchedEffect(sniffing) {
        if (sniffing) configExpanded = false
    }

    val targetCaptureMode = captureMode == 1
    val validTargetSelection =
        !targetCaptureMode || SNIFF_MAC_REGEX.matches(targetBssid.trim())
    val validClient = !targetCaptureMode ||
        !deauthBeforeCapture ||
        SNIFF_MAC_REGEX.matches(client.trim())
    val canStart = validTargetSelection && validClient

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            ConfigZone(
                connected = connected,
                sniffing = sniffing,
                expanded = configExpanded,
                fixedMode = fixedMode,
                channel = channel,
                band = band,
                intervalMs = intervalMs,
                deauthBeforeCapture = deauthBeforeCapture,
                eapolOnly = eapolOnly,
                captureMode = captureMode,
                targetBssid = targetBssid,
                client = client,
                deauthCount = deauthCount,
                deauthIntervalMs = deauthIntervalMs,
                deauthRetryDuringCapture = deauthRetryDuringCapture,
                deauthRetryIntervalMs = deauthRetryIntervalMs,
                validTargetSelection = validTargetSelection,
                validDeauthClient = validClient,
                onToggle = { configExpanded = !configExpanded },
                onCaptureModeChange = { captureMode = it },
                onModeChange = { requestedFixed ->
                    fixedMode = if (deauthBeforeCapture) true else requestedFixed
                },
                onChannelChange = { channel = normalizeWifiChannel(it) },
                onBandSelected = { band = it },
                onIntervalChange = { intervalMs = it.coerceIn(50, 1000) },
                onDeauthBeforeCaptureChange = {
                    deauthBeforeCapture = it
                    if (it) fixedMode = true
                },
                onEapolOnlyChange = { eapolOnly = it },
                scanning = scanning,
                networks = networks,
                onScanWifi = onScanWifi,
                showBandSelector = showBandSelector,
                fiveGhzAvailable = fiveGhzAvailable,
                fiveGhzUnavailableMessage = fiveGhzUnavailableMessage,
                allowedChannels = allowedChannels,
                onTargetBssidChange = { targetBssid = it },
                onClientChange = { client = it },
                onDeauthCountChange = { deauthCount = it.coerceIn(1, 200) },
                onDeauthIntervalChange = { deauthIntervalMs = it.coerceIn(10, 10_000) },
                onDeauthRetryDuringCaptureChange = { deauthRetryDuringCapture = it },
                onDeauthRetryIntervalChange = { deauthRetryIntervalMs = it.coerceIn(1_000, 60_000) },
            )

            Spacer(Modifier.height(10.dp))

            ResultZone(
                connected = connected,
                sniffing = sniffing,
                packetCount = packetCount,
                captureBytes = captureBytes,
                capturePath = capturePath,
                handshake = handshake,
                handshakeOnly = targetCaptureMode && eapolOnly,
            )
        }

        if (connected) {
            ModuleActionFab(
                onClick = {
                    if (sniffing) {
                        onStop()
                    } else if (canStart) {
                        val captureFixedMode =
                            targetCaptureMode || fixedMode || deauthBeforeCapture
                        onStart(
                            SniffRequest(
                                fixedMode = captureFixedMode,
                                channel = channel,
                                intervalMs = intervalMs,
                                deauthBeforeCapture = targetCaptureMode && deauthBeforeCapture,
                                targetBssid = if (targetCaptureMode) targetBssid.trim() else "",
                                eapolOnly = targetCaptureMode && eapolOnly,
                                targetNetworkOnly = targetCaptureMode,
                                client = if (targetCaptureMode) {
                                    client.trim()
                                } else {
                                    "FF:FF:FF:FF:FF:FF"
                                },
                                deauthCount = deauthCount,
                                deauthIntervalMs = deauthIntervalMs,
                                deauthRetryDuringCapture = deauthRetryDuringCapture,
                                deauthRetryIntervalMs = deauthRetryIntervalMs,
                                band = if (captureFixedMode) {
                                    bandForWifiChannel(channel).wireName
                                } else {
                                    band.wireName
                                },
                            )
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = if (sniffing) StatusRed else MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = if (sniffing) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (sniffing) "Stop sniffing" else "Start sniffing",
                )
            }
        }
    }
}

@Composable
private fun ConfigZone(
    connected: Boolean,
    sniffing: Boolean,
    expanded: Boolean,
    captureMode: Int,
    fixedMode: Boolean,
    channel: Int,
    band: WifiBand,
    intervalMs: Int,
    deauthBeforeCapture: Boolean,
    eapolOnly: Boolean,
    targetBssid: String,
    client: String,
    deauthCount: Int,
    deauthIntervalMs: Int,
    deauthRetryDuringCapture: Boolean,
    deauthRetryIntervalMs: Int,
    validTargetSelection: Boolean,
    validDeauthClient: Boolean,
    onToggle: () -> Unit,
    onCaptureModeChange: (Int) -> Unit,
    onModeChange: (Boolean) -> Unit,
    onChannelChange: (Int) -> Unit,
    onBandSelected: (WifiBand) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onDeauthBeforeCaptureChange: (Boolean) -> Unit,
    onEapolOnlyChange: (Boolean) -> Unit,
    scanning: Boolean,
    networks: List<JSONObject>,
    onScanWifi: () -> Unit,
    showBandSelector: Boolean,
    fiveGhzAvailable: Boolean,
    fiveGhzUnavailableMessage: String,
    allowedChannels: List<Int>,
    onTargetBssidChange: (String) -> Unit,
    onClientChange: (String) -> Unit,
    onDeauthCountChange: (Int) -> Unit,
    onDeauthIntervalChange: (Int) -> Unit,
    onDeauthRetryDuringCaptureChange: (Boolean) -> Unit,
    onDeauthRetryIntervalChange: (Int) -> Unit,
) {
    val targetCaptureMode = captureMode == 1
    val selectedTarget = networks.firstOrNull {
        it.optString("bssid").equals(targetBssid.trim(), ignoreCase = true)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Capture configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            sniffing -> "Capture running"
                            targetCaptureMode -> "Targeted handshake capture"
                            else -> "Fixed channel or channel hopping"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                    )
                }
            }

            if (expanded) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Capture mode",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NrFilterChip(
                            selected = captureMode == 0,
                            onClick = { onCaptureModeChange(0) },
                            label = "Capture",
                            enabled = !sniffing,
                        )
                        NrFilterChip(
                            selected = captureMode == 1,
                            onClick = { onCaptureModeChange(1) },
                            label = "Target Capture",
                            enabled = !sniffing,
                        )
                    }

                    if (!targetCaptureMode) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Channel mode",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.height(6.dp))
                        ChannelModeToggle(
                            fixed = fixedMode,
                            enabled = !sniffing,
                            onFixedChange = onModeChange,
                        )

                        if (fixedMode) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Channel",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Spacer(Modifier.height(6.dp))
                            WifiChannelStepper(
                                value = channel,
                                enabled = !sniffing,
                                onChannelChange = onChannelChange,
                                allowedChannels = allowedChannels.ifEmpty {
                                    com.swp81x.nrsuite.core.wifi.WIFI_AUTO_CHANNELS
                                },
                            )
                        } else {
                            WifiBandConfigSection(
                                visible = showBandSelector,
                                band = band,
                                fiveGhzAvailable = fiveGhzAvailable,
                                fiveGhzUnavailableMessage = fiveGhzUnavailableMessage,
                                onBandSelected = onBandSelected,
                                enabled = !sniffing,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Hop interval",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onIntervalChange(intervalMs - 50) },
                                    enabled = !sniffing && intervalMs > 50,
                                ) {
                                    Text("-", style = MaterialTheme.typography.titleLarge)
                                }
                                Text(
                                    text = "$intervalMs ms",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                )
                                IconButton(
                                    onClick = { onIntervalChange(intervalMs + 50) },
                                    enabled = !sniffing && intervalMs < 1000,
                                ) {
                                    Text("+", style = MaterialTheme.typography.titleLarge)
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Target access point",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = "Scan and select a target. The capture channel follows the selected AP.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = onScanWifi,
                            enabled = connected && !sniffing && !scanning,
                        ) {
                            Text(if (scanning) "Scanning..." else "Scan WiFi for targets")
                        }

                        if (networks.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            networks.forEach { network ->
                                val ssid = network.optString("ssid").ifBlank { "(hidden)" }
                                val bssidValue = network.optString("bssid")
                                val channelValue = network.optInt("channel", 1)
                                NetworkTargetRow(
                                    ssid = ssid,
                                    bssid = bssidValue,
                                    channel = channelValue,
                                    rssi = network.optInt("rssi", -100),
                                    security = network.optString("security", "?"),
                                    wps = network.optBoolean("wps"),
                                    vendor = network.optString("vendor").takeIf { it.isNotBlank() },
                                    ouiWhitelisted = network.optBoolean("oui_whitelisted"),
                                    ouiBlacklisted = network.optBoolean("oui_blacklisted"),
                                    selected = targetBssid.trim().equals(bssidValue, ignoreCase = true),
                                    enabled = !sniffing,
                                    onClick = {
                                        onTargetBssidChange(bssidValue)
                                        onChannelChange(channelValue)
                                    },
                                    modifier = Modifier.padding(vertical = 3.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        if (validTargetSelection) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(NrSurfaceVariant, RoundedCornerShape(10.dp))
                                    .border(0.5.dp, NrOutline, RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = selectedTarget?.optString("ssid")
                                        ?.takeIf { it.isNotBlank() }
                                        ?: "(selected target)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = targetBssid.trim(),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                    color = NrOnSurfaceVariant,
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = "Channel $channel",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                        ),
                                        color = NrOnSurfaceVariant,
                                    )
                                    WifiBandPill(label = bandForWifiChannel(channel).label)
                                }
                            }
                        } else {
                            Text(
                                text = "Select a target AP before starting target capture.",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusAmber,
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Handshake-only capture",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = "Record EAPOL handshake frames plus one contextual beacon.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NrOnSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = eapolOnly,
                                onCheckedChange = onEapolOnlyChange,
                                enabled = !sniffing,
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Deauth assist during capture",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = "Nudge the selected client toward a new 4-way handshake.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NrOnSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = deauthBeforeCapture,
                                onCheckedChange = onDeauthBeforeCaptureChange,
                                enabled = !sniffing && validTargetSelection,
                            )
                        }

                        if (deauthBeforeCapture) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = client,
                                onValueChange = onClientChange,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !sniffing,
                                label = { Text("Client MAC (broadcast if blank)") },
                                isError = !validDeauthClient,
                                singleLine = true,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Count",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { onDeauthCountChange(deauthCount - 5) },
                                    enabled = !sniffing && deauthCount > 0,
                                ) { Text("-", style = MaterialTheme.typography.titleLarge) }
                                Text(
                                    text = if (deauthCount <= 0) "default" else deauthCount.toString(),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                                IconButton(
                                    onClick = { onDeauthCountChange(deauthCount + 5) },
                                    enabled = !sniffing,
                                ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Interval",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { onDeauthIntervalChange(deauthIntervalMs - 10) },
                                    enabled = !sniffing && deauthIntervalMs > 10,
                                ) { Text("-", style = MaterialTheme.typography.titleLarge) }
                                Text(
                                    text = "$deauthIntervalMs ms",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                                IconButton(
                                    onClick = { onDeauthIntervalChange(deauthIntervalMs + 10) },
                                    enabled = !sniffing && deauthIntervalMs < 10_000,
                                ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "Retry while capturing",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = "Repeats the burst after the sniffer is active until capture stops or a handshake is found.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NrOnSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = deauthRetryDuringCapture,
                                    onCheckedChange = onDeauthRetryDuringCaptureChange,
                                    enabled = !sniffing,
                                )
                            }
                            if (deauthRetryDuringCapture) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Retry interval",
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(
                                        onClick = {
                                            onDeauthRetryIntervalChange(deauthRetryIntervalMs - 1_000)
                                        },
                                        enabled = !sniffing && deauthRetryIntervalMs > 1_000,
                                    ) { Text("-", style = MaterialTheme.typography.titleLarge) }
                                    Text(
                                        text = "${deauthRetryIntervalMs / 1000}s",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                        ),
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                    )
                                    IconButton(
                                        onClick = {
                                            onDeauthRetryIntervalChange(deauthRetryIntervalMs + 1_000)
                                        },
                                        enabled = !sniffing && deauthRetryIntervalMs < 60_000,
                                    ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultZone(
    connected: Boolean,
    sniffing: Boolean,
    packetCount: Long,
    captureBytes: Long,
    capturePath: String?,
    handshake: EapolHandshake,
    handshakeOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    val statusColor = when {
        !connected -> StatusNeutral
        sniffing -> StatusGreen
        packetCount > 0 -> StatusAmber
        else -> StatusNeutral
    }
    val statusLabel = when {
        !connected -> "No device connected"
        sniffing -> "Capturing"
        packetCount > 0 -> "Stopped"
        else -> "Ready"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Live capture",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (handshakeOnly) {
                            "Handshake-only capture mode"
                        } else {
                            "All 802.11 packets"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                StatusIndicator(label = statusLabel, color = statusColor)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CaptureMetric(
                    label = "PACKETS",
                    value = packetCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                CaptureMetric(
                    label = "FILE SIZE",
                    value = formatCaptureSize(captureBytes),
                    modifier = Modifier.weight(1f),
                )
            }

            if (handshakeOnly) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "EAPOL handshake",
                        style = MaterialTheme.typography.labelLarge,
                        color = NrOnSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        HandshakeMilestone("M1", handshake.m1, Modifier.weight(1f))
                        HandshakeMilestone("M2", handshake.m2, Modifier.weight(1f))
                        HandshakeMilestone("M3", handshake.m3, Modifier.weight(1f))
                        HandshakeMilestone("M4", handshake.m4, Modifier.weight(1f))
                    }
                    Text(
                        text = if (handshake.isComplete) {
                            "Complete 4-way handshake captured."
                        } else {
                            "Waiting for the remaining handshake messages."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (handshake.isComplete) StatusGreen else NrOnSurfaceVariant,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "CAPTURE FILE",
                    style = MaterialTheme.typography.labelSmall,
                    color = NrOnSurfaceVariant,
                )
                Text(
                    text = capturePath ?: "Not started",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
            }

            if (connected && !sniffing && packetCount == 0L && capturePath == null) {
                Text(
                    text = "Tap the play button to start a capture.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CaptureMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(NrSurfaceVariant, RoundedCornerShape(10.dp))
            .border(0.5.dp, NrOutline, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NrOnSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HandshakeMilestone(
    label: String,
    present: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (present) StatusGreen else NrOnSurfaceVariant
    Text(
        text = "$label ${if (present) "✓" else "—"}",
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        fontWeight = FontWeight.SemiBold,
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(
                if (present) StatusGreen.copy(alpha = 0.12f) else NrSurfaceVariant,
                RoundedCornerShape(8.dp),
            )
            .border(0.5.dp, if (present) StatusGreen.copy(alpha = 0.45f) else NrOutline, RoundedCornerShape(8.dp))
            .padding(vertical = 7.dp),
    )
}

private fun formatCaptureSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
