package com.swp81x.nrsuite.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.exp
import kotlin.math.roundToInt
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
                    ChannelSpectrumChart(
                        aps = aps,
                        selectedChannel = selectedChannel,
                        onSelectChannel = { selectedChannel = it },
                    )
                    selectedStat?.let { stat ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = NrSurface),
                            border = BorderStroke(0.5.dp, NrOutline),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "Selected · Channel ${stat.channel}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = NrAccent,
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
private fun ChannelSpectrumChart(
    aps: List<AnalyzerAp>,
    selectedChannel: Int?,
    onSelectChannel: (Int) -> Unit,
) {
    val channels = remember(aps) { aps.map { it.channel }.distinct().sorted() }
    if (channels.isEmpty()) return
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    val scrollState = rememberScrollState()
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val gridColor = NrOutline.copy(alpha = 0.45f)
    val channelLabelColor = NrOnSurfaceVariant.toArgb()

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
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Signal overlap",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = NrOnSurface,
                    )
                    Text(
                        text = "Overlapping AP signal profiles by channel. Tap a channel to inspect it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = { zoom = (zoom - 0.25f).coerceAtLeast(0.75f) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp),
                ) { Text("−") }
                Text(
                    text = "${"%.2f".format(zoom)}×",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
                TextButton(
                    onClick = { zoom = (zoom + 0.25f).coerceAtMost(4f) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp),
                ) { Text("+") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SpectrumLegendDot(color = primary, label = "2.4 GHz")
                SpectrumLegendDot(color = secondary, label = "5 GHz")
            }

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val chartWidth = maxOf(
                    maxWidth,
                    (channels.size.coerceAtLeast(4) * 54).dp * zoom,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState),
                ) {
                    Canvas(
                        modifier = Modifier
                            .width(chartWidth)
                            .height(200.dp)
                            .pointerInput(channels) {
                                detectTapGestures { offset ->
                                    val leftPadding = 18.dp.toPx()
                                    val rightPadding = 18.dp.toPx()
                                    val usableWidth = size.width - leftPadding - rightPadding
                                    val step = if (channels.size > 1) {
                                        usableWidth / (channels.size - 1)
                                    } else {
                                        0f
                                    }
                                    val index = if (step <= 0f) {
                                        0
                                    } else {
                                        ((offset.x - leftPadding) / step)
                                            .roundToInt()
                                            .coerceIn(channels.indices)
                                    }
                                    onSelectChannel(channels[index])
                                }
                            },
                    ) {
                        val left = 18.dp.toPx()
                        val right = size.width - 18.dp.toPx()
                        val top = 12.dp.toPx()
                        val bottom = size.height - 30.dp.toPx()
                        val plotHeight = bottom - top
                        val step = if (channels.size > 1) {
                            (right - left) / (channels.size - 1)
                        } else {
                            0f
                        }
                        val drawStep = if (step > 0f) step else size.width * 0.28f
                        val channelIndex = channels.withIndex().associate { it.value to it.index }
                        val selectedIndex = selectedChannel?.let(channelIndex::get)

                        repeat(5) { stripe ->
                            val y = top + plotHeight * stripe / 4f
                            drawLine(
                                color = gridColor,
                                start = Offset(left, y),
                                end = Offset(right, y),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }

                        if (selectedIndex != null) {
                            val x = if (channels.size > 1) left + selectedIndex * step else (left + right) / 2f
                            val bandWidth = drawStep.coerceAtLeast(34.dp.toPx())
                            drawRoundRect(
                                color = primary.copy(alpha = 0.10f),
                                topLeft = Offset(x - bandWidth / 2f, top),
                                size = Size(bandWidth, plotHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                            )
                        }

                        aps.forEach { ap ->
                            val index = channelIndex[ap.channel] ?: return@forEach
                            val centerX = if (channels.size > 1) {
                                left + index * step
                            } else {
                                (left + right) / 2f
                            }
                            val halfWidth = drawStep * 0.95f
                            val amplitude = ((ap.rssi + 100f) / 68f).coerceIn(0.16f, 1f)
                            val peakY = bottom - amplitude * plotHeight
                            val color = if (ap.channel <= 14) primary else secondary
                            val samples = 22
                            val curve = Path()
                            val fill = Path()
                            fill.moveTo(centerX - halfWidth, bottom)
                            for (sample in 0..samples) {
                                val t = sample / samples.toFloat()
                                val x = centerX - halfWidth + (halfWidth * 2f * t)
                                val normalized = (x - centerX) / (drawStep * 0.48f)
                                val y = bottom - (bottom - peakY) * exp(-0.5f * normalized * normalized)
                                if (sample == 0) {
                                    curve.moveTo(x, y)
                                } else {
                                    curve.lineTo(x, y)
                                }
                                fill.lineTo(x, y)
                            }
                            fill.lineTo(centerX + halfWidth, bottom)
                            fill.close()
                            drawPath(
                                path = fill,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        color.copy(alpha = 0.20f),
                                        color.copy(alpha = 0.02f),
                                    ),
                                    startY = top,
                                    endY = bottom,
                                ),
                            )
                            drawPath(
                                path = curve,
                                color = color.copy(alpha = 0.78f),
                                style = Stroke(width = 1.5.dp.toPx()),
                            )
                            drawCircle(
                                color = color,
                                radius = 2.4.dp.toPx(),
                                center = Offset(centerX, peakY),
                            )
                        }

                        val labelPaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                            textSize = 10.sp.toPx()
                            color = channelLabelColor
                        }
                        channels.forEachIndexed { index, channel ->
                            val x = if (channels.size > 1) left + index * step else (left + right) / 2f
                            drawContext.canvas.nativeCanvas.drawText(
                                "CH $channel",
                                x,
                                bottom + 20.dp.toPx(),
                                labelPaint,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpectrumLegendDot(
    color: Color,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, RoundedCornerShape(50)),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NrOnSurfaceVariant,
        )
    }
}
