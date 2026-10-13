package com.swp81x.nrsuite.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
    modifier: Modifier = Modifier,
) {
    var configExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(connected) {
        if (connected) onRefresh()
    }
    LaunchedEffect(state.running) {
        if (state.running) configExpanded = false
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
            )

            Spacer(Modifier.height(10.dp))

            Ld2450RadarCard(
                targets = state.targets,
                maxRangeCm = state.maxRangeCm,
                alertZoneBreach = state.alertZoneBreach,
            )

            Spacer(Modifier.height(10.dp))

            Ld2450TargetListCard(
                state = state,
                connected = connected,
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

@Composable
private fun Ld2450StatusCard(
    connected: Boolean,
    state: Ld2450UiState,
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
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Radar configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Display range and alert options",
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
                        enabled = connected && !state.running && state.maxRangeCm > 100,
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease max range")
                    }
                    Text(
                        text = "${state.maxRangeCm / 100f}m",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    )
                    IconButton(
                        onClick = { onMaxRangeChange(state.maxRangeCm + 50) },
                        enabled = connected && !state.running && state.maxRangeCm < 800,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase max range")
                    }
                }

                Slider(
                    value = state.maxRangeCm.toFloat(),
                    onValueChange = { onMaxRangeChange(it.roundToInt()) },
                    valueRange = 100f..800f,
                    enabled = connected && !state.running,
                )

                Spacer(Modifier.height(8.dp))

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
private fun Ld2450RadarCard(
    targets: List<Ld2450Target>,
    maxRangeCm: Int,
    alertZoneBreach: Boolean,
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
                        text = "120° fan · range 0-${formatMeters(maxRangeCm)} m",
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
            }
            Spacer(Modifier.height(8.dp))
            Ld2450RadarPlot(
                targets = targets,
                maxRangeCm = maxRangeCm,
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
    targets: List<Ld2450Target>,
    maxRangeCm: Int,
) {
    val primary = MaterialTheme.colorScheme.primary
    val grid = NrOutline
    val maxRange = maxRangeCm.coerceAtLeast(100).toFloat()

    // Keep a short trail per target slot so fast movement leaves a soft
    // afterimage. The trail is stored in radar centimetres, not screen pixels.
    val trailStore = remember { mutableStateMapOf<Int, MutableList<Pair<Float, Float>>>() }
    LaunchedEffect(targets) {
        targets.forEach { target ->
            val trail = trailStore.getOrPut(target.slot) { mutableListOf() }
            val point = target.xCm to target.yCm
            if (trail.lastOrNull() != point) {
                trail.add(point)
                while (trail.size > 10) trail.removeAt(0)
            }
        }
        val presentSlots = targets.map { it.slot }.toSet()
        trailStore.keys.toList().forEach { slot ->
            if (slot !in presentSlots) trailStore.remove(slot)
        }
    }

    // Expanding radar pings / heartbeat rings around each target.
    val pulseTransition = rememberInfiniteTransition(label = "ld2450-pulse")
    val pulsePhase by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ld2450-pulse-phase",
    )

    // Stable slot order keeps animateFloatAsState calls in the same positions
    // across recompositions, which gives each target a small interpolation/ease.
    val animatedTargets = mutableListOf<AnimatedRadarTarget>()
    for (slot in 1..3) {
        val target = targets.firstOrNull { it.slot == slot }
        val targetX = target?.xCm ?: 0f
        val targetY = target?.yCm ?: 0f
        val x by animateFloatAsState(
            targetValue = targetX,
            animationSpec = tween(durationMillis = 130),
            label = "ld2450-x-$slot",
        )
        val y by animateFloatAsState(
            targetValue = targetY,
            animationSpec = tween(durationMillis = 130),
            label = "ld2450-y-$slot",
        )
        if (target != null) {
            animatedTargets.add(AnimatedRadarTarget(target = target, xCm = x, yCm = y))
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.35f),
    ) {
        val width = size.width
        val height = size.height
        val origin = Offset(width / 2f, height * 0.93f)
        val outerRadius = minOf(width * 0.47f, height * 0.86f)
        val ringWidth = 1.dp.toPx()
        val guideWidth = 1.dp.toPx()

        fun project(xCm: Float, yCm: Float): Offset = Offset(
            x = origin.x + (xCm / maxRange) * outerRadius,
            y = origin.y - (yCm / maxRange) * outerRadius,
        )

        fun colorFor(slot: Int): Color = when (slot) {
            2 -> StatusAmber
            3 -> CategoryDetectionBlue
            else -> primary
        }

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

        // Trails and motion blur go behind the current target pips.
        animatedTargets.forEach { target ->
            val color = colorFor(target.target.slot)
            val trail = trailStore[target.target.slot].orEmpty()
            if (trail.size >= 2) {
                for (index in 1 until trail.size) {
                    val age = index.toFloat() / trail.lastIndex
                    val start = project(trail[index - 1].first, trail[index - 1].second)
                    val end = project(trail[index].first, trail[index].second)
                    drawLine(
                        color = color.copy(alpha = 0.11f * age),
                        start = start,
                        end = end,
                        strokeWidth = (4f + 5f * age).dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }

                trail.dropLast(1).forEachIndexed { index, (xCm, yCm) ->
                    val age = (index + 1).toFloat() / trail.size
                    drawCircle(
                        color = color.copy(alpha = 0.15f * age),
                        radius = (3.5f + 3.5f * age).dp.toPx(),
                        center = project(xCm, yCm),
                    )
                }
            }

            val previous = trail.dropLast(1).lastOrNull()
            if (previous != null) {
                val previousPoint = project(previous.first, previous.second)
                val point = project(target.xCm, target.yCm)
                val distance = (point - previousPoint).getDistance()
                if (distance > 2.dp.toPx()) {
                    val speedBoost = (abs(target.target.speedCmS) / 60f).coerceIn(0f, 1f)
                    drawLine(
                        color = color.copy(alpha = 0.12f + 0.16f * speedBoost),
                        start = previousPoint,
                        end = point,
                        strokeWidth = (4f + 8f * speedBoost).dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }

        animatedTargets.forEach { target ->
            val point = project(target.xCm, target.yCm)
            val color = colorFor(target.target.slot)
            val speedBoost = (abs(target.target.speedCmS) / 60f).coerceIn(0f, 1f)

            // Heartbeat halo: gentle scale pulse plus two expanding ping rings.
            val beat = 1f + 0.10f * sin(pulsePhase * 2f * PI.toFloat() + target.target.slot.toFloat())
            drawCircle(
                color = color.copy(alpha = 0.14f + 0.06f * speedBoost),
                radius = 14.dp.toPx() * beat,
                center = point,
            )

            val firstPhase = (pulsePhase + target.target.slot * 0.13f) % 1f
            drawCircle(
                color = color.copy(alpha = (1f - firstPhase) * 0.42f),
                radius = (6.dp.toPx() + firstPhase * 24.dp.toPx()),
                center = point,
                style = Stroke(width = 1.5.dp.toPx()),
            )

            val secondPhase = (firstPhase + 0.5f) % 1f
            drawCircle(
                color = color.copy(alpha = (1f - secondPhase) * 0.26f),
                radius = (6.dp.toPx() + secondPhase * 24.dp.toPx()),
                center = point,
                style = Stroke(width = 1.dp.toPx()),
            )

            drawCircle(
                color = color.copy(alpha = 0.38f),
                radius = 9.dp.toPx(),
                center = point,
            )
            drawCircle(
                color = color,
                radius = 4.5.dp.toPx(),
                center = point,
            )
        }
    }
}

private data class AnimatedRadarTarget(
    val target: Ld2450Target,
    val xCm: Float,
    val yCm: Float,
)

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
