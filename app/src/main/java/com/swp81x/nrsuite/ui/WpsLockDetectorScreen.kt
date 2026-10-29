package com.swp81x.nrsuite.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiFind
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
import com.swp81x.nrsuite.core.wps.WpsLockAccessPoint
import com.swp81x.nrsuite.core.wps.WpsLockState
import com.swp81x.nrsuite.ui.components.NetworkStatusBadge
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.theme.NrOnSurface
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusInfo
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed
import com.swp81x.nrsuite.ui.util.signalQualityColor
import com.swp81x.nrsuite.ui.util.securityColor

@Composable
fun WpsLockDetectorScreen(
    connected: Boolean,
    scanning: Boolean,
    accessPoints: List<WpsLockAccessPoint>,
    error: String?,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var selectedBssid by rememberSaveable { mutableStateOf<String?>(null) }

    val filtered = remember(accessPoints, filter) {
        when (filter) {
            1 -> accessPoints.filter { it.lockState == WpsLockState.LOCKED }
            2 -> accessPoints.filter { it.lockState == WpsLockState.LIKELY_UNLOCKED }
            3 -> accessPoints.filter { it.lockState == WpsLockState.UNKNOWN }
            else -> accessPoints
        }
    }
    val selected = remember(accessPoints, selectedBssid) {
        accessPoints.firstOrNull { it.bssid.equals(selectedBssid, ignoreCase = true) }
    }
    val lockedCount = accessPoints.count { it.lockState == WpsLockState.LOCKED }
    val likelyUnlockedCount = accessPoints.count { it.lockState == WpsLockState.LIKELY_UNLOCKED }
    val unknownCount = accessPoints.count { it.lockState == WpsLockState.UNKNOWN }

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
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.WifiFind,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "WPS Lock Detector",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = NrOnSurface,
                            )
                            Text(
                                text = when {
                                    scanning -> "Scanning WPS access points..."
                                    accessPoints.isEmpty() -> "Scan to inspect nearby WPS lock state."
                                    else -> "${accessPoints.size} WPS AP(s) · $lockedCount locked · $likelyUnlockedCount likely unlocked"
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
                    if (accessPoints.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SummaryCell("Locked", lockedCount.toString(), StatusRed, Modifier.weight(1f))
                            SummaryCell("Likely unlocked", likelyUnlockedCount.toString(), StatusGreen, Modifier.weight(1f))
                            SummaryCell("Unknown", unknownCount.toString(), StatusNeutral, Modifier.weight(1f))
                        }
                    }
                    error?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusRed,
                        )
                    }
                }
            }

            if (shouldShowSelected(selected, accessPoints)) {
                selected?.let { ap ->
                    WpsLockDetailCard(ap)
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
                            text = "WPS access points",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            color = NrOnSurface,
                        )
                        Text(
                            text = "${filtered.size}/${accessPoints.size}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = NrOnSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NrFilterChip(
                            selected = filter == 0,
                            onClick = { filter = 0 },
                            label = "All",
                        )
                        NrFilterChip(
                            selected = filter == 1,
                            onClick = { filter = 1 },
                            label = "Locked",
                            selectedColor = StatusRed,
                        )
                        NrFilterChip(
                            selected = filter == 2,
                            onClick = { filter = 2 },
                            label = "Likely unlocked",
                            selectedColor = StatusGreen,
                        )
                        NrFilterChip(
                            selected = filter == 3,
                            onClick = { filter = 3 },
                            label = "Unknown",
                        )
                    }

                    if (accessPoints.isEmpty()) {
                        Text(
                            text = if (scanning) {
                                "Scanning for WPS beacons and probe responses."
                            } else {
                                "No WPS access points yet. Tap Scan to start."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    } else if (filtered.isEmpty()) {
                        Text(
                            text = "No WPS APs match this filter.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(filtered, key = { it.bssid }) { ap ->
                                WpsLockApRow(
                                    ap = ap,
                                    selected = ap.bssid.equals(selectedBssid, ignoreCase = true),
                                    onClick = {
                                        selectedBssid = if (ap.bssid.equals(selectedBssid, ignoreCase = true)) {
                                            null
                                        } else {
                                            ap.bssid
                                        }
                                    },
                                )
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
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                if (scanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Default.Search, contentDescription = "Scan WPS access points")
                }
            }
        }
    }
}

private fun shouldShowSelected(
    selected: WpsLockAccessPoint?,
    accessPoints: List<WpsLockAccessPoint>,
): Boolean = selected != null && accessPoints.isNotEmpty()

@Composable
private fun SummaryCell(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(Modifier.padding(9.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = NrOnSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        }
    }
}

@Composable
private fun WpsLockApRow(
    ap: WpsLockAccessPoint,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val lockColor = when (ap.lockState) {
        WpsLockState.LOCKED -> StatusRed
        WpsLockState.LIKELY_UNLOCKED -> StatusGreen
        WpsLockState.UNKNOWN -> StatusNeutral
    }
    val lockLabel = when (ap.lockState) {
        WpsLockState.LOCKED -> "LOCKED"
        WpsLockState.LIKELY_UNLOCKED -> "LIKELY UNLOCKED"
        WpsLockState.UNKNOWN -> "UNKNOWN"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                NrSurfaceVariant
            },
        ),
        border = BorderStroke(
            0.5.dp,
            if (selected) MaterialTheme.colorScheme.primary else NrOutline,
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ap.ssid,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = NrOnSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                NetworkStatusBadge(text = lockLabel, color = lockColor)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = ap.bssid,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
            )
            Text(
                text = "ch ${ap.channel} · ${bandForWifiChannel(ap.channel).label} · ${ap.rssi} dBm",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = signalQualityColor(ap.rssi),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ap.security,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = securityColor(ap.security),
                )
                ap.vendor?.let { vendor ->
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "· $vendor",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = NrOnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun WpsLockDetailCard(ap: WpsLockAccessPoint) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = when (ap.lockState) {
                        WpsLockState.LOCKED -> StatusRed
                        WpsLockState.LIKELY_UNLOCKED -> StatusGreen
                        WpsLockState.UNKNOWN -> StatusNeutral
                    },
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = ap.ssid,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = NrOnSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when (ap.lockState) {
                        WpsLockState.LOCKED -> "Locked"
                        WpsLockState.LIKELY_UNLOCKED -> "Likely unlocked"
                        WpsLockState.UNKNOWN -> "Unknown"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when (ap.lockState) {
                        WpsLockState.LOCKED -> StatusRed
                        WpsLockState.LIKELY_UNLOCKED -> StatusGreen
                        WpsLockState.UNKNOWN -> StatusNeutral
                    },
                )
            }
            DetailLine("BSSID", ap.bssid)
            DetailLine("Vendor", ap.vendor ?: "Unknown")
            DetailLine("Channel", "${ap.channel} · ${bandForWifiChannel(ap.channel).label}")
            DetailLine("Security", ap.security)
            DetailLine("WPS version", if (ap.wpsVersion > 0) "0x%02X".format(ap.wpsVersion) else "Unknown")
            DetailLine("Config methods", "0x%04X".format(ap.configMethods))
            DetailLine("Selected registrar", if (ap.selectedRegistrar) "Yes" else "No")
            DetailLine(
                "Lock attribute",
                when {
                    !ap.wpsParsed -> "WPS IE could not be fully parsed"
                    ap.lockReported -> if (ap.apSetupLocked) "AP Setup Locked reported" else "Not locked reported"
                    else -> "Not advertised; likely unlocked"
                },
            )
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NrOnSurfaceVariant,
            modifier = Modifier.width(112.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = NrOnSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
