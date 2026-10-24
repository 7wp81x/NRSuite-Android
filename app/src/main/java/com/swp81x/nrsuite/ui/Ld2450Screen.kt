package com.swp81x.nrsuite.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.sensors.Ld2450Motion
import com.swp81x.nrsuite.core.sensors.Ld2450Target
import com.swp81x.nrsuite.core.sensors.Ld2450UiState
import com.swp81x.nrsuite.ui.components.ModuleStatusCard
import com.swp81x.nrsuite.ui.components.ModuleStatusState
import com.swp81x.nrsuite.ui.theme.CategoryDetectionBlue
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun Ld2450Screen(
    connected: Boolean,
    state: Ld2450UiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRefresh: () -> Unit,
    onMaxRangeChange: (Int) -> Unit,
    onZoneAlertChange: (Boolean) -> Unit,
    onMeshBroadcastChange: (Boolean) -> Unit,
    onHeartbeatEnabledChange: (Boolean) -> Unit,
    onScanDurationChange: (Int) -> Unit,
    onSleepDurationChange: (Int) -> Unit,
    onLimitDotCountChange: (Boolean) -> Unit,
    onMaxDotsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var configExpanded by remember { mutableStateOf(true) }
    var radarExpanded by remember { mutableStateOf(false) }
    var heartbeatPhase by remember { mutableStateOf(HeartbeatPhase.DORMANT) }
    var displayDots by remember { mutableStateOf<List<PersistedRadarDot>>(emptyList()) }
    var nextDotId by remember { mutableLongStateOf(0L) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scanWave = remember { Animatable(0f) }

    LaunchedEffect(connected) {
        if (connected) onRefresh()
    }
    LaunchedEffect(state.running) {
        if (state.running) configExpanded = false
    }

    // Drives fade alpha even when the target stream is idle.
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(50)
        }
    }

    // Heartbeat: scan window, then dormant/fade window, repeat.
    LaunchedEffect(state.heartbeatEnabled, state.scanDurationMs, state.sleepDurationMs) {
        if (!state.heartbeatEnabled) {
            heartbeatPhase = HeartbeatPhase.DORMANT
            displayDots = emptyList()
            scanWave.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            heartbeatPhase = HeartbeatPhase.SCANNING
            scanWave.snapTo(0f)
            scanWave.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = state.scanDurationMs,
                    easing = LinearEasing,
                ),
            )
            heartbeatPhase = HeartbeatPhase.DORMANT
            delay(state.sleepDurationMs.toLong())
        }
    }

    // Position-based dot persistence. Slots are deliberately ignored: the
    // LD2450 slot number is not a stable target identity.
    LaunchedEffect(
        state.targets,
        state.heartbeatEnabled,
        heartbeatPhase,
        state.limitDotCount,
        state.maxDots,
    ) {
        if (!state.heartbeatEnabled || heartbeatPhase != HeartbeatPhase.SCANNING) {
            return@LaunchedEffect
        }
        val now = System.currentTimeMillis()
        val fadeDurationMs = state.sleepDurationMs.toLong() + DOT_FADE_EXTRA_MS
        val dots = displayDots.toMutableList().also { list ->
            list.removeAll { now - it.lastSeenMs >= fadeDurationMs }
        }

        val candidates = mutableListOf<DotCandidate>()
        dots.forEachIndexed { dotIndex, dot ->
            state.targets.forEachIndexed { targetIndex, target ->
                val dx = target.xCm - dot.xCm
                val dy = target.yCm - dot.yCm
                val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                if (distance <= DOT_MATCH_RADIUS_CM) {
                    candidates.add(DotCandidate(dotIndex, targetIndex, distance))
                }
            }
        }
        candidates.sortBy { it.distance }

        val matchedDots = mutableSetOf<Int>()
        val matchedTargets = mutableSetOf<Int>()
        candidates.forEach { candidate ->
            if (candidate.dotIndex in matchedDots || candidate.targetIndex in matchedTargets) {
                return@forEach
            }
            matchedDots.add(candidate.dotIndex)
            matchedTargets.add(candidate.targetIndex)
            val target = state.targets[candidate.targetIndex]
            val previous = dots[candidate.dotIndex]
            dots[candidate.dotIndex] = previous.copy(
                xCm = previous.xCm + (target.xCm - previous.xCm) * 0.6f,
                yCm = previous.yCm + (target.yCm - previous.yCm) * 0.6f,
                lastSeenMs = now,
            )
        }

        state.targets.forEachIndexed { targetIndex, target ->
            if (targetIndex in matchedTargets) return@forEachIndexed
            if (state.limitDotCount && dots.size >= state.maxDots) return@forEachIndexed
            dots.add(
                PersistedRadarDot(
                    id = nextDotId++,
                    xCm = target.xCm,
                    yCm = target.yCm,
                    lastSeenMs = now,
                ),
            )
        }
        displayDots = dots
    }

    val dotsForPlot = if (state.heartbeatEnabled) {
        displayDots
    } else {
        state.targets.mapIndexed { index, target ->
            PersistedRadarDot(
                id = index.toLong(),
                xCm = target.xCm,
                yCm = target.yCm,
                lastSeenMs = nowMs,
            )
        }
    }
    val fadeDurationMs = state.sleepDurationMs.toLong() + DOT_FADE_EXTRA_MS
    val scanWaveProgress =
        if (state.heartbeatEnabled && heartbeatPhase == HeartbeatPhase.SCANNING) {
            scanWave.value
        } else {
            null
        }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            Ld2450StatusCard(
                connected = connected,
                state = state,
                heartbeatPhase = heartbeatPhase,
                dotCount = dotsForPlot.size,
            )

            Spacer(Modifier.height(10.dp))

            Ld2450ConfigZone(
                connected = connected,
                expanded = configExpanded,
                state = state,
                onToggle = { configExpanded = !configExpanded },
                onMaxRangeChange = onMaxRangeChange,
                onZoneAlertChange = onZoneAlertChange,
                onMeshBroadcastChange = onMeshBroadcastChange,
                onHeartbeatEnabledChange = onHeartbeatEnabledChange,
                onScanDurationChange = onScanDurationChange,
                onSleepDurationChange = onSleepDurationChange,
                onLimitDotCountChange = onLimitDotCountChange,
                onMaxDotsChange = onMaxDotsChange,
            )

            Spacer(Modifier.height(10.dp))

            Ld2450RadarCard(
                dots = dotsForPlot,
                maxRangeCm = state.maxRangeCm,
                alertZoneBreach = state.alertZoneBreach,
                nowMs = nowMs,
                fadeDurationMs = fadeDurationMs,
                heartbeatEnabled = state.heartbeatEnabled,
                scanWaveProgress = scanWaveProgress,
                expanded = radarExpanded,
                onToggleExpanded = { radarExpanded = !radarExpanded },
            )

            Spacer(Modifier.height(10.dp))

            Ld2450AlertFooter(alertZoneBreach = state.alertZoneBreach)

            Spacer(Modifier.height(80.dp))
        }

        if (connected) {
            ModuleActionFab(
                onClick = {
                    if (state.running) onStop() else onStart()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                enabled = !state.starting && !state.stopping,
                containerColor = if (state.running) StatusRed else MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                if (state.starting || state.stopping) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        imageVector = if (state.running) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (state.running) "Stop mmWave radar" else "Start mmWave radar",
                    )
                }
            }
        }
    }
}

private enum class HeartbeatPhase {
    SCANNING,
    DORMANT,
}

private data class PersistedRadarDot(
    val id: Long,
    val xCm: Float,
    val yCm: Float,
    val lastSeenMs: Long,
)

private data class DotCandidate(
    val dotIndex: Int,
    val targetIndex: Int,
    val distance: Float,
)

private const val DOT_MATCH_RADIUS_CM = 25f
private const val DOT_FADE_EXTRA_MS = 500L

@Composable
private fun Ld2450StatusCard(
    connected: Boolean,
    state: Ld2450UiState,
    heartbeatPhase: HeartbeatPhase,
    dotCount: Int,
) {
    val statusState = when {
        !connected -> ModuleStatusState.DISCONNECTED
        state.errorKind == "parse_error" -> ModuleStatusState.ALERT
        state.running && state.errorKind == "no_data" -> ModuleStatusState.ALERT
        state.running -> ModuleStatusState.RUNNING
        else -> ModuleStatusState.READY
    }
    val title = when {
        !connected -> "Disconnected"
        state.errorKind == "parse_error" -> "Parse errors"
        state.running && state.errorKind == "no_data" -> "Sensor not responding"
        state.running && !state.hasData -> "Waiting for data"
        state.heartbeatEnabled && heartbeatPhase == HeartbeatPhase.SCANNING -> "Heartbeat · scanning"
        state.heartbeatEnabled -> "Heartbeat · dormant"
        state.running && state.targets.isEmpty() -> "Monitoring"
        state.running -> "Tracking"
        else -> "Ready"
    }
    val subtitle = when {
        !connected -> "Connect a device before starting the radar."
        state.errorKind == "parse_error" ->
            "${state.invalidFrames} invalid frame(s) · ${state.droppedBytes} dropped byte(s)"
        state.running && state.errorKind == "no_data" ->
            "No LD2450 frames received yet."
        state.running && !state.hasData -> "Waiting for the first valid frame."
        state.heartbeatEnabled && heartbeatPhase == HeartbeatPhase.SCANNING ->
            "Live scan window · $dotCount dot(s) visible"
        state.heartbeatEnabled ->
            "Fading last scan · $dotCount dot(s) visible"
        state.running && state.targets.isEmpty() -> "No targets detected"
        state.running -> "${state.targets.size} target(s) · ${state.validFrames} frame(s)"
        else -> "HLK-LD2450 presence and target tracking"
    }

    ModuleStatusCard(
        icon = Icons.Default.Radar,
        title = title,
        subtitle = subtitle,
        state = statusState,
    ) {
        if (connected && (state.validFrames > 0 || state.invalidFrames > 0)) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "FRAMES ${state.validFrames}   INVALID ${state.invalidFrames}   DROPPED ${state.droppedBytes}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Ld2450ConfigZone(
    connected: Boolean,
    expanded: Boolean,
    state: Ld2450UiState,
    onToggle: () -> Unit,
    onMaxRangeChange: (Int) -> Unit,
    onZoneAlertChange: (Boolean) -> Unit,
    onMeshBroadcastChange: (Boolean) -> Unit,
    onHeartbeatEnabledChange: (Boolean) -> Unit,
    onScanDurationChange: (Int) -> Unit,
    onSleepDurationChange: (Int) -> Unit,
    onLimitDotCountChange: (Boolean) -> Unit,
    onMaxDotsChange: (Int) -> Unit,
) {
    val controlsEnabled = connected && !state.running

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
                        text = "Radar configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Display, heartbeat, and alert options",
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
                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Max range",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Radar plot scale · ${state.maxRangeCm} cm",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { onMaxRangeChange(state.maxRangeCm - 50) },
                        enabled = controlsEnabled && state.maxRangeCm > 100,
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease max range")
                    }
                    Text(
                        text = "${state.maxRangeCm / 100f}m",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    )
                    IconButton(
                        onClick = { onMaxRangeChange(state.maxRangeCm + 50) },
                        enabled = controlsEnabled && state.maxRangeCm < 800,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase max range")
                    }
                }

                Slider(
                    value = state.maxRangeCm.toFloat(),
                    onValueChange = { onMaxRangeChange(it.roundToInt()) },
                    valueRange = 100f..800f,
                    enabled = controlsEnabled,
                )

                Spacer(Modifier.height(8.dp))

                ConfigSwitchRow(
                    title = "Heartbeat mode",
                    subtitle = "Scan window, then fade dots before the next scan.",
                    checked = state.heartbeatEnabled,
                    enabled = controlsEnabled,
                    onCheckedChange = onHeartbeatEnabledChange,
                )

                if (state.heartbeatEnabled) {
                    ConfigStepperRow(
                        title = "Scan duration",
                        subtitle = "Live radar sampling window",
                        value = formatSeconds(state.scanDurationMs),
                        onDecrease = { onScanDurationChange(state.scanDurationMs - 250) },
                        onIncrease = { onScanDurationChange(state.scanDurationMs + 250) },
                        decreaseEnabled = controlsEnabled && state.scanDurationMs > 500,
                        increaseEnabled = controlsEnabled && state.scanDurationMs < 3000,
                    )
                    ConfigStepperRow(
                        title = "Sleep duration",
                        subtitle = "Dormant / fade window",
                        value = formatSeconds(state.sleepDurationMs),
                        onDecrease = { onSleepDurationChange(state.sleepDurationMs - 500) },
                        onIncrease = { onSleepDurationChange(state.sleepDurationMs + 500) },
                        decreaseEnabled = controlsEnabled && state.sleepDurationMs > 500,
                        increaseEnabled = controlsEnabled && state.sleepDurationMs < 5000,
                    )
                    ConfigSwitchRow(
                        title = "Limit dots",
                        subtitle = "Cap how many persistent dots stay on the fan.",
                        checked = state.limitDotCount,
                        enabled = controlsEnabled,
                        onCheckedChange = onLimitDotCountChange,
                    )
                    if (state.limitDotCount) {
                        ConfigStepperRow(
                            title = "Max dots",
                            subtitle = "New detections are ignored once this cap is reached.",
                            value = state.maxDots.toString(),
                            onDecrease = { onMaxDotsChange(state.maxDots - 1) },
                            onIncrease = { onMaxDotsChange(state.maxDots + 1) },
                            decreaseEnabled = controlsEnabled && state.maxDots > 1,
                            increaseEnabled = controlsEnabled && state.maxDots < 32,
                        )
                    }
                    Text(
                        text = "Match radius: ${DOT_MATCH_RADIUS_CM.toInt()} cm",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                }

                ConfigSwitchRow(
                    title = "Zone alert",
                    subtitle = "Requires firmware zone support; not available yet.",
                    checked = state.zoneAlertEnabled,
                    enabled = false,
                    onCheckedChange = onZoneAlertChange,
                )

                ConfigSwitchRow(
                    title = "Mesh broadcast",
                    subtitle = "Disabled on the feature/non-mesh build.",
                    checked = state.meshBroadcastEnabled,
                    enabled = false,
                    onCheckedChange = onMeshBroadcastChange,
                )
            }
        }
    }
}

@Composable
private fun ConfigSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun ConfigStepperRow(
    title: String,
    subtitle: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseEnabled: Boolean,
    increaseEnabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
        }
        IconButton(onClick = onDecrease, enabled = decreaseEnabled) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease $title")
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )
        IconButton(onClick = onIncrease, enabled = increaseEnabled) {
            Icon(Icons.Default.Add, contentDescription = "Increase $title")
        }
    }
}

@Composable
private fun Ld2450RadarCard(
    dots: List<PersistedRadarDot>,
    maxRangeCm: Int,
    alertZoneBreach: Boolean,
    nowMs: Long,
    fadeDurationMs: Long,
    heartbeatEnabled: Boolean,
    scanWaveProgress: Float?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Radar view",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (heartbeatEnabled) {
                            "Heartbeat dots · range 0-${formatMeters(maxRangeCm)} m"
                        } else {
                            "120° fan · range 0-${formatMeters(maxRangeCm)} m"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                if (alertZoneBreach) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = "Alert zone breach",
                        tint = StatusRed,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onToggleExpanded) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse radar" else "Expand radar",
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Ld2450RadarPlot(
                dots = dots,
                maxRangeCm = maxRangeCm,
                nowMs = nowMs,
                fadeDurationMs = fadeDurationMs,
                heartbeatEnabled = heartbeatEnabled,
                scanWaveProgress = scanWaveProgress,
                expanded = expanded,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "0",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
                Text(
                    text = "${formatMeters(maxRangeCm / 2)} m",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
                Text(
                    text = "${formatMeters(maxRangeCm)} m",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Ld2450RadarPlot(
    dots: List<PersistedRadarDot>,
    maxRangeCm: Int,
    nowMs: Long,
    fadeDurationMs: Long,
    heartbeatEnabled: Boolean,
    scanWaveProgress: Float?,
    expanded: Boolean,
) {
    val primary = MaterialTheme.colorScheme.primary
    val grid = NrOutline
    val maxRange = maxRangeCm.coerceAtLeast(100).toFloat()

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (expanded) 1.05f else 1.25f),
    ) {
        val width = size.width
        val height = size.height
        val origin = Offset(width / 2f, height * 0.93f)
        // The 120-degree wedge can safely use more of the canvas width than
        // the old 0.47 factor; expanded mode gets a little more again.
        val horizontalFactor = if (expanded) 0.57f else 0.54f
        val outerRadius = minOf(width * horizontalFactor, height * 0.86f)
        val ringWidth = 1.dp.toPx()
        val guideWidth = 1.dp.toPx()

        fun project(xCm: Float, yCm: Float): Offset = Offset(
            x = origin.x + (xCm / maxRange) * outerRadius,
            y = origin.y - (yCm / maxRange) * outerRadius,
        )

        drawArc(
            color = primary.copy(alpha = 0.07f),
            startAngle = -150f,
            sweepAngle = 120f,
            useCenter = true,
            topLeft = Offset(origin.x - outerRadius, origin.y - outerRadius),
            size = Size(outerRadius * 2f, outerRadius * 2f),
        )

        for (ring in 1..3) {
            val radius = outerRadius * ring / 3f
            drawArc(
                color = grid.copy(alpha = if (ring == 3) 0.75f else 0.45f),
                startAngle = -150f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(origin.x - radius, origin.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = if (ring == 3) ringWidth * 1.4f else ringWidth),
            )
        }

        val guideAngles = listOf(-150f, -90f, -30f)
        guideAngles.forEach { degrees ->
            val radians = degrees * PI.toFloat() / 180f
            val end = Offset(
                x = origin.x + cos(radians) * outerRadius,
                y = origin.y + sin(radians) * outerRadius,
            )
            drawLine(
                color = grid.copy(alpha = 0.55f),
                start = origin,
                end = end,
                strokeWidth = guideWidth,
                cap = StrokeCap.Round,
            )
        }

        // Scan wave: a soft sector plus an expanding ring that reaches the fan edge.
        if (scanWaveProgress != null) {
            val progress = scanWaveProgress.coerceIn(0f, 1f)
            val waveRadius = outerRadius * progress
            drawArc(
                color = StatusGreen.copy(alpha = (1f - progress) * 0.10f),
                startAngle = -150f,
                sweepAngle = 120f,
                useCenter = true,
                topLeft = Offset(origin.x - waveRadius, origin.y - waveRadius),
                size = Size(waveRadius * 2f, waveRadius * 2f),
            )
            drawArc(
                color = StatusGreen.copy(alpha = (1f - progress) * 0.55f),
                startAngle = -150f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(origin.x - waveRadius, origin.y - waveRadius),
                size = Size(waveRadius * 2f, waveRadius * 2f),
                style = Stroke(width = 2.dp.toPx()),
            )

            val secondProgress = (progress + 0.5f) % 1f
            if (secondProgress > 0.001f) {
                val secondRadius = outerRadius * secondProgress
                drawArc(
                    color = StatusGreen.copy(alpha = (1f - secondProgress) * 0.28f),
                    startAngle = -150f,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = Offset(origin.x - secondRadius, origin.y - secondRadius),
                    size = Size(secondRadius * 2f, secondRadius * 2f),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }

        drawCircle(
            color = primary.copy(alpha = 0.14f),
            radius = 12.dp.toPx(),
            center = origin,
        )
        drawCircle(
            color = primary,
            radius = 5.dp.toPx(),
            center = origin,
        )

        // Green blurred dots. Matched dots are refreshed and stay visible;
        // unmatched dots fade slowly according to their last-seen time.
        dots.forEach { dot ->
            val ageMs = (nowMs - dot.lastSeenMs).coerceAtLeast(0L)
            val fade = if (heartbeatEnabled && fadeDurationMs > 0L) {
                (1f - ageMs.toFloat() / fadeDurationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                1f
            }
            if (fade <= 0.02f) return@forEach

            val point = project(dot.xCm, dot.yCm)
            val pulse = 1f + 0.08f * sin(nowMs / 280f + dot.id.toFloat() * 0.8f)
            val glowRadius = (18.dp.toPx() * pulse * (0.75f + 0.25f * fade))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        StatusGreen.copy(alpha = 0.42f * fade),
                        StatusGreen.copy(alpha = 0f),
                    ),
                    center = point,
                    radius = glowRadius,
                ),
                radius = glowRadius,
                center = point,
            )
            drawCircle(
                color = StatusGreen.copy(alpha = 0.22f * fade),
                radius = 9.dp.toPx() * pulse,
                center = point,
            )
            drawCircle(
                color = StatusGreen.copy(alpha = 0.70f * fade),
                radius = 5.dp.toPx(),
                center = point,
            )
            drawCircle(
                color = StatusGreen.copy(alpha = 0.95f * fade),
                radius = 2.5.dp.toPx(),
                center = point,
            )
        }
    }
}

@Composable
private fun Ld2450TargetListCard(
    state: Ld2450UiState,
    connected: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "Targets",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (state.targets.isEmpty()) {
                    "No targets detected"
                } else {
                    "${state.targets.size} target(s) in view"
                },
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            if (!connected) {
                EmptyTargetState(
                    title = "Device disconnected",
                    body = "Connect the ESP32 bridge to receive radar frames.",
                )
            } else if (state.targets.isEmpty()) {
                EmptyTargetState(
                    title = "No targets detected",
                    body = when {
                        state.running && state.errorKind == "no_data" ->
                            "The sensor is active but no valid frames have arrived."
                        state.running -> "Move a target into the 120° field of view."
                        else -> "Start monitoring to detect targets."
                    },
                )
            } else {
                state.targets.forEachIndexed { index, target ->
                    Ld2450TargetRow(target = target)
                    if (index != state.targets.lastIndex) {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTargetState(
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.Radar,
            contentDescription = null,
            tint = NrOnSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = NrOnSurfaceVariant,
        )
    }
}

@Composable
private fun Ld2450TargetRow(target: Ld2450Target) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "TARGET ${target.slot}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = target.motion.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = motionColor(target.motion),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "X ${formatSignedCm(target.xCm)}   Y ${formatSignedCm(target.yCm)}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
            )
            Text(
                text = "DIST ${formatCm(target.distanceCm)}   SPD ${formatSignedCmPerSecond(target.speedCmS)}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
            )
            Text(
                text = "RES ${target.resolutionMm} mm",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Ld2450AlertFooter(alertZoneBreach: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    color = if (alertZoneBreach) StatusRed else StatusGreen,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (alertZoneBreach) "Alert zone breach" else "No alert zone breach",
            style = MaterialTheme.typography.bodySmall,
            color = if (alertZoneBreach) StatusRed else NrOnSurfaceVariant,
            fontWeight = if (alertZoneBreach) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

private fun motionColor(motion: Ld2450Motion): Color = when (motion) {
    Ld2450Motion.APPROACHING -> StatusGreen
    Ld2450Motion.STATIONARY -> StatusNeutral
    Ld2450Motion.RECEDING -> StatusAmber
}

private fun formatCm(value: Float): String =
    String.format(Locale.US, "%.1f cm", value)

private fun formatSignedCm(value: Float): String =
    String.format(Locale.US, "%+.1f cm", value)

private fun formatSignedCmPerSecond(value: Int): String =
    String.format(Locale.US, "%+d cm/s", value)

private fun formatMeters(valueCm: Int): String =
    String.format(Locale.US, "%.1f", valueCm / 100f)

private fun formatSeconds(valueMs: Int): String =
    String.format(Locale.US, "%.1fs", valueMs / 1000f)
