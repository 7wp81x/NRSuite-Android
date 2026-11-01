package com.swp81x.nrsuite.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.portal.PortalVisitor
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.util.copyWithToast
import androidx.compose.ui.platform.LocalContext

@Composable
fun PortalVisitorsTab(
    visitors: List<PortalVisitor>,
    onClearVisitors: () -> Unit,
    modifier: Modifier = Modifier,
    emptyText: String = "Visitors will appear here after a client requests a portal page.",
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Devices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Visitors (${visitors.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        clipboard.copyWithToast(
                            context,
                            visitors.joinToString("\n") {
                                "${it.lastSeen}  MAC=${it.mac}  IP=${it.ip}  " +
                                    "requests=${it.visitCount}  ${it.requestType}  UA=${it.userAgent}"
                            },
                            "Visitors copied",
                        )
                    },
                    enabled = visitors.isNotEmpty(),
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy visitors")
                }
                IconButton(onClick = onClearVisitors, enabled = visitors.isNotEmpty()) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear visitors")
                }
            }
            Spacer(Modifier.height(8.dp))
            if (visitors.isEmpty()) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visitors.asReversed()) { visitor ->
                        VisitorCard(visitor)
                    }
                }
            }
        }
    }
}

@Composable
private fun VisitorCard(visitor: PortalVisitor) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = visitor.mac,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${visitor.visitCount} request(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = NrOnSurfaceVariant,
                )
            }
            Text(
                text = "IP: ${visitor.ip}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
            )
            Text(
                text = "Last: ${visitor.lastSeen.ifBlank { "unknown" }} · ${visitor.requestType}",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            if (visitor.userAgent.isNotBlank()) {
                Text(
                    text = "UA: ${visitor.userAgent}",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }
    }
}
