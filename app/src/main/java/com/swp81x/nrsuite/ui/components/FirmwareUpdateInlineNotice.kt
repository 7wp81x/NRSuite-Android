package com.swp81x.nrsuite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.update.FirmwareAssetDownloadState
import com.swp81x.nrsuite.core.update.FirmwareUpdateInfo
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusRed

@Composable
fun FirmwareUpdateInlineNotice(
    currentVersion: String?,
    update: FirmwareUpdateInfo?,
    downloadState: FirmwareAssetDownloadState,
    onDownload: () -> Unit,
    onOpenFlasher: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (update == null) return
    val downloading = downloadState is FirmwareAssetDownloadState.Downloading
    val downloaded = downloadState is FirmwareAssetDownloadState.Downloaded
    val failed = downloadState is FirmwareAssetDownloadState.Failed
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = if (failed) StatusRed else StatusAmber,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Firmware update available",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${currentVersion ?: update.currentVersion} → ${update.latest.tag}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (downloading) {
            CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        val label = when (downloadState) {
            is FirmwareAssetDownloadState.Downloading -> {
                downloadState.percent?.let { "Downloading ${it.toInt()}%" } ?: "Downloading…"
            }
            is FirmwareAssetDownloadState.Downloaded -> "Open Flasher"
            is FirmwareAssetDownloadState.Failed -> "Retry download"
            FirmwareAssetDownloadState.Idle -> "Download update"
        }
        Button(
            onClick = if (downloaded) onOpenFlasher else onDownload,
            enabled = !downloading,
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
        if (failed) {
            Text(
                text = (downloadState as FirmwareAssetDownloadState.Failed).message,
                style = MaterialTheme.typography.labelSmall,
                color = StatusRed,
            )
        } else if (downloaded) {
            Text(
                text = "Downloaded and ready to flash.",
                style = MaterialTheme.typography.labelSmall,
                color = StatusGreen,
            )
        }
    }
}
