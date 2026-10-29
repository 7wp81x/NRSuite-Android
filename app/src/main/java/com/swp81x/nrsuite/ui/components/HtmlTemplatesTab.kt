package com.swp81x.nrsuite.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.swp81x.nrsuite.ui.theme.NrOnSurface
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusRed

@Composable
fun HtmlTemplatesTab(
    templates: List<HtmlTemplate>,
    message: String?,
    messageIsError: Boolean,
    enabled: Boolean,
    selectedName: String?,
    onImport: () -> Unit,
    onUse: (HtmlTemplate) -> Unit,
    onRename: (HtmlTemplate, String) -> Unit,
    onDelete: (HtmlTemplate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var renameTarget by remember { mutableStateOf<HtmlTemplate?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<HtmlTemplate?>(null) }

    Column(
        modifier = modifier.fillMaxSize().padding(12.dp),
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
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "HTML Templates",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = NrOnSurface,
                        )
                        Text(
                            text = "${templates.size} saved · 24 KB limit · duplicate content is rejected",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = onImport, enabled = enabled) {
                        Text("Import")
                    }
                }
                message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (messageIsError) StatusRed else StatusGreen,
                    )
                }
            }
        }

        if (templates.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurface),
                border = BorderStroke(0.5.dp, NrOutline),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "No saved templates",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = NrOnSurface,
                    )
                    Text(
                        text = "Import an HTML file to reuse it for Captive Portal and Evil Twin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(templates, key = { it.id }) { template ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = NrSurface),
                        border = BorderStroke(0.5.dp, NrOutline),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = template.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = NrOnSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${template.sizeBytes} bytes · SHA-256 ${template.sha256.take(12)}…",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = NrOnSurfaceVariant,
                                        maxLines = 1,
                                    )
                                    if (template.name == selectedName) {
                                        Text(
                                            text = "Selected",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = StatusGreen,
                                        )
                                    }
                                }
                                TextButton(onClick = { onUse(template) }, enabled = enabled) {
                                    Text("Use")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                TextButton(
                                    onClick = {
                                        renameTarget = template
                                        renameText = template.name
                                    },
                                    enabled = enabled,
                                ) {
                                    Text("Rename")
                                }
                                TextButton(onClick = { deleteTarget = template }, enabled = enabled) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { template ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename template") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Template name") },
                    singleLine = true,
                    isError = renameText.isBlank(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clean = renameText.trim()
                        if (clean.isNotBlank()) {
                            onRename(template, clean)
                            renameTarget = null
                        }
                    },
                    enabled = renameText.isNotBlank(),
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            },
        )
    }

    deleteTarget?.let { template ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete template?") },
            text = { Text("Delete \"${template.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(template)
                        deleteTarget = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}
