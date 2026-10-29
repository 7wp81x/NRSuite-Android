package com.swp81x.nrsuite.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.swp81x.nrsuite.ProbeObservation
import org.json.JSONObject

@Composable
internal fun ProbeMonitorRoute(
    connected: Boolean,
    running: Boolean,
    fixed: Boolean,
    channel: Int,
    entries: List<ProbeObservation>,
    frameCount: Long,
    allowedChannels: List<Int>,
    onStart: (Boolean, Int) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ProbeMonitorScreen(
        connected = connected,
        running = running,
        fixed = fixed,
        channel = channel,
        entries = entries,
        frameCount = frameCount,
        allowedChannels = allowedChannels,
        onStart = onStart,
        onStop = onStop,
        modifier = modifier,
    )
}

@Composable
internal fun ChannelAnalyzerRoute(
    connected: Boolean,
    scanning: Boolean,
    networks: List<JSONObject>,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ChannelAnalyzerScreen(
        connected = connected,
        scanning = scanning,
        networks = networks,
        onScan = onScan,
        modifier = modifier,
    )
}
