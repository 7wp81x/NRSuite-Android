package com.swp81x.nrsuite.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.wifi.bandForWifiChannel
import com.swp81x.nrsuite.ui.components.NetworkTargetRow
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.components.NrStatusAction
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOnSurface
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import org.json.JSONObject

private data class AnalyzerAp(
    val ssid: String,
    val bssid: String,
    val channel: Int,
    val rssi: Int,
    val security: String,
    val wps: Boolean,
    val vendor: String?,
    val ouiWhitelisted: Boolean,
    val ouiBlacklisted: Boolean,
)

private data class ChannelStat(
    val channel: Int,
    val accessPoints: Int,
    val strongestRssi: Int,
    val openCount: Int,
    val wpsCount: Int,
)

@Composable
fun ChannelAnalyzerScreen(
    connected: Boolean,
    scanning: Boolean,
    networks: List<JSONObject>,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var bandFilter by rememberSaveable { mutableIntStateOf(0) }
    var selectedChannel by rememberSaveable { mutableStateOf<Int?>(null) }

    val aps = remember(networks) {
        networks.mapNotNull { network ->
            val bssid = network.optString("bssid")
            val channel = network.optInt("channel", 0)
            if (bssid.isBlank() || channel <= 0) {
                null
            } else {
                AnalyzerAp(
                    ssid = network.optString("ssid").ifBlank { "(hidden)" },
                    bssid = bssid,
                    channel = channel,
                    rssi = network.optInt("rssi", -127),
                    security = network.optString("security", "?"),
                    wps = network.optBoolean("wps"),
                    vendor = network.optString("vendor").takeIf { it.isNotBlank() },
                    ouiWhitelisted = network.optBoolean("oui_whitelisted"),
                    ouiBlacklisted = network.optBoolean("oui_blacklisted"),
                )
            }
        }.sortedByDescending { it.rssi }
    }
    val stats = remember(aps) {
        aps.groupBy { it.channel }
            .map { (channel, channelAps) ->
                ChannelStat(
                    channel = channel,
                    accessPoints = channelAps.size,
                    strongestRssi = channelAps.maxOf { it.rssi },
                    openCount = channelAps.count {
                        it.security.equals("OPEN", ignoreCase = true) ||
                            it.security.equals("NONE", ignoreCase = true)
                    },
                    wpsCount = channelAps.count { it.wps },
                )
            }
            .sortedBy { it.channel }
    }
    val filteredAps = remember(aps, bandFilter) {
        when (bandFilter) {
            1 -> aps.filter { it.channel <= 14 }
            2 -> aps.filter { it.channel > 14 }
            else -> aps
        }
    }
    val stats2g = stats.filter { it.channel <= 14 }
    val stats5g = stats.filter { it.channel > 14 }
    val selectedStat = stats.firstOrNull { it.channel == selectedChannel }
    val maxAps = stats.maxOfOrNull { it.accessPoints }?.coerceAtLeast(1) ?: 1
    val strongestRssi = aps.maxOfOrNull { it.rssi }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
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
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.BarChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Channel Analyzer",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = NrOnSurface,
                            )
                            Text(
                                text = when {
                                    scanning -> "Scanning allowed channels..."
                                    aps.isEmpty() -> "Tap Scan to populate the analyzer."
                                    else -> "${aps.size} APs across ${stats.size} channels"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = NrOnSurfaceVariant,
                            )
                        }
                    }
                    if (!connected) {
                        Text(
                            text = "Connect a device before scanning.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAmber,
                        )
                    }
                    if (aps.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SummaryMetric("APs", aps.size.toString(), Modifier.weight(1f))
                            SummaryMetric("Strongest", strongestRssi?.let { "$it dBm" } ?: "—", Modifier.weight(1f))
                            SummaryMetric("Open", aps.count { it.security.equals("OPEN", true) || it.security.equals("NONE", true) }.toString(), Modifier.weight(1f))
                            SummaryMetric("WPS", aps.count { it.wps }.toString(), Modifier.weight(1f))
                        }
                    }
                }
            }

            when {
                aps.isEmpty() -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = NrSurface),
                        border = BorderStroke(0.5.dp, NrOutline),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(
                            Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = if (scanning) "Scanning..." else "No APs found yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = NrOnSurface,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (scanning) {
                                    "Looking for access points on allowed channels."
                                } else {
                                    "Tap the scan button to populate signal and channel data."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = NrOnSurfaceVariant,
                            )
                        }
                    }
                }
                else -> {
                    if (stats2g.isNotEmpty()) {
                        ChannelBarChart(
                            title = "2.4 GHz",
                            stats = stats2g,
                            maxAps = maxAps,
                            selectedChannel = selectedChannel,
                            onSelectChannel = { selectedChannel = it },
                        )
                    }
                    if (stats5g.isNotEmpty()) {
                        ChannelBarChart(
                            title = "5 GHz",
                            stats = stats5g,
                            maxAps = maxAps,
                            selectedChannel = selectedChannel,
                            onSelectChannel = { selectedChannel = it },
                        )
                    }
                    selectedStat?.let { stat ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = NrSurface),
                            border = BorderStroke(1.dp, NrAccent.copy(alpha = 0.65f)),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "Channel ${stat.channel}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = NrOnSurface,
                                    )
                                    Text(
                                        text = "${bandForWifiChannel(stat.channel).label} · " +
                                            "${stat.accessPoints} AP(s) · strongest ${stat.strongestRssi} dBm",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = NrOnSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = "open ${stat.openCount} · WPS ${stat.wpsCount}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = StatusAmber,
                                )
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = NrSurface),
                        border = BorderStroke(0.5.dp, NrOutline),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Nearby APs",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                    color = NrOnSurface,
                                )
                                Text(
                                    text = "${filteredAps.size}/${aps.size}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = NrOnSurfaceVariant,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                NrFilterChip(
                                    selected = bandFilter == 0,
                                    onClick = { bandFilter = 0 },
                                    label = "All",
                                )
                                NrFilterChip(
                                    selected = bandFilter == 1,
                                    onClick = { bandFilter = 1 },
                                    label = "2.4 GHz",
                                )
                                NrFilterChip(
                                    selected = bandFilter == 2,
                                    onClick = { bandFilter = 2 },
                                    label = "5 GHz",
                                    enabled = stats5g.isNotEmpty(),
                                )
                            }
                            if (filteredAps.isEmpty()) {
                                Text(
                                    text = "No APs match this band filter.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NrOnSurfaceVariant,
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 320.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    items(filteredAps, key = { it.bssid }) { ap ->
                                        NetworkTargetRow(
                                            ssid = ap.ssid,
                                            bssid = ap.bssid,
                                            channel = ap.channel,
                                            rssi = ap.rssi,
                                            security = ap.security,
                                            wps = ap.wps,
                                            vendor = ap.vendor,
                                            ouiWhitelisted = ap.ouiWhitelisted,
                                            ouiBlacklisted = ap.ouiBlacklisted,
                                            selected = selectedChannel == ap.channel,
                                            showSelectionIndicator = false,
                                            onClick = { selectedChannel = ap.channel },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(84.dp))
        }

        if (connected) {
            ModuleActionFab(
                onClick = { if (!scanning) onScan() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                statusAction = NrStatusAction.START,
            ) {
                if (scanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Scan channels",
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NrOnSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.SemiBold,
            color = NrOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChannelBarChart(
    title: String,
    stats: List<ChannelStat>,
    maxAps: Int,
    selectedChannel: Int?,
    onSelectChannel: (Int) -> Unit,
) {
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
                color = NrOnSurface,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .height(148.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                stats.forEach { stat ->
                    val selected = selectedChannel == stat.channel
                    val barHeight = (18f + (84f * stat.accessPoints.toFloat() / maxAps.toFloat())).dp
                    Column(
                        modifier = Modifier
                            .width(44.dp)
                            .clickable { onSelectChannel(stat.channel) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(
                            text = stat.accessPoints.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = if (selected) NrAccent else NrOnSurfaceVariant,
                        )
                        Spacer(Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(barHeight)
                                .background(
                                    if (selected) NrAccent else NrAccent.copy(alpha = 0.58f),
                                    RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp),
                                ),
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = "CH ${stat.channel}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) NrAccent else NrOnSurface,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
