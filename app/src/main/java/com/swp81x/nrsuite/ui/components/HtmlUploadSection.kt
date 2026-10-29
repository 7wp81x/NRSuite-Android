package com.swp81x.nrsuite.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.portal.HtmlTemplate
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusRed

@Composable
fun HtmlUploadSection(
    selectedName: String?,
    selectedSizeBytes: Long?,
    uploading: Boolean,
    progress: Int,
    completed: Boolean,
    enabled: Boolean,
    templates: List<HtmlTemplate>,
    onSelectTemplate: (String) -> Unit,
    onChoose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val warnSize = selectedSizeBytes != null && selectedSizeBytes > 16 * 1024L
    val invalidSize = selectedSizeBytes != null && selectedSizeBytes > 24 * 1024L

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { menuExpanded = true },
                    enabled = enabled && !uploading && templates.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "HTML template",
                                style = MaterialTheme.typography.labelSmall,
                                color = NrOnSurfaceVariant,
                            )
                            Text(
                                text = selectedName ?: "Select saved template",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (selectedName != null) FontWeight.Medium else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Open HTML template list",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    templates.forEach { template ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(template.name, maxLines = 1)
                                    Text(
                                        text = "${template.sizeBytes} B · ${template.sha256.take(10)}…",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = NrOnSurfaceVariant,
                                    )
                                }
                            },
                            onClick = {
                                menuExpanded = false
                                onSelectTemplate(template.id)
                            },
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = onChoose,
                enabled = enabled && !uploading,
            ) {
                Text("Choose HTML")
            }
        }

        when {
            uploading -> {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Uploading HTML... $progress%",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
            }
            selectedName != null && completed -> {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "✓ $selectedName uploaded",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = StatusGreen,
                )
            }
        }

        if (invalidSize) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Selected HTML exceeds the 24 KB firmware limit and cannot be uploaded.",
                style = MaterialTheme.typography.bodySmall,
                color = StatusRed,
            )
        } else if (warnSize) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${selectedSizeBytes} bytes is above 16 KB. The device heap will be " +
                    "preflighted before start and may still reject the page.",
                style = MaterialTheme.typography.bodySmall,
                color = StatusAmber,
            )
        }
    }
}
