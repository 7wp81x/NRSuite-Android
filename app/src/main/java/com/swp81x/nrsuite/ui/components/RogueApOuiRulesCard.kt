package com.swp81x.nrsuite.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.defense.OuiRule
import com.swp81x.nrsuite.core.defense.OuiRuleAction
import com.swp81x.nrsuite.core.defense.normalizeOuiPrefix
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.StatusRed

@Composable
fun RogueApOuiRulesCard(
    ouiRules: List<OuiRule>,
    onAddOuiRule: (ouiPrefix: String, label: String, action: OuiRuleAction) -> Unit,
    onDeleteOuiRule: (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddForm by remember { mutableStateOf(false) }
    var ouiInput by remember { mutableStateOf("") }
    var labelInput by remember { mutableStateOf("") }
    var actionInput by remember { mutableStateOf(OuiRuleAction.WHITELIST) }
    val normalizedOui = normalizeOuiPrefix(ouiInput)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Rogue AP Detector — OUI rules", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Whitelist common AP hardware in your area to reduce false alarms. " +
                    "Blacklist vendor prefixes known for rogue AP tools to flag them harder.",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            if (ouiRules.isEmpty()) {
                Text(
                    text = "No rules yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ouiRules.forEach { rule ->
                        OuiRuleRow(rule = rule, onDelete = { onDeleteOuiRule(rule.id) })
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            if (!showAddForm) {
                OutlinedButton(onClick = { showAddForm = true }) {
                    Text("Add rule")
                }
            } else {
                Column {
                    OutlinedTextField(
                        value = ouiInput,
                        onValueChange = { ouiInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("OUI prefix (AA:BB:CC)") },
                        isError = ouiInput.isNotBlank() && normalizedOui == null,
                        singleLine = true,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = labelInput,
                        onValueChange = { labelInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Label") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NrFilterChip(
                            selected = actionInput == OuiRuleAction.WHITELIST,
                            onClick = { actionInput = OuiRuleAction.WHITELIST },
                            label = "Whitelist",
                        )
                        NrFilterChip(
                            selected = actionInput == OuiRuleAction.BLACKLIST,
                            onClick = { actionInput = OuiRuleAction.BLACKLIST },
                            label = "Blacklist",
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val normalized = normalizeOuiPrefix(ouiInput) ?: return@Button
                                onAddOuiRule(normalized, labelInput.trim(), actionInput)
                                ouiInput = ""
                                labelInput = ""
                                actionInput = OuiRuleAction.WHITELIST
                                showAddForm = false
                            },
                            enabled = normalizedOui != null && labelInput.isNotBlank(),
                        ) { Text("Save") }
                        TextButton(onClick = { showAddForm = false }) { Text("Cancel") }
                    }
                }
            }
        }
    }
}

@Composable
private fun OuiRuleRow(rule: OuiRule, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = rule.ouiPrefix,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.SemiBold,
            )
            if (rule.label.isNotBlank()) {
                Text(
                    text = rule.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }
        val badgeColor = when (rule.action) {
            OuiRuleAction.WHITELIST -> NrAccent
            OuiRuleAction.BLACKLIST -> StatusRed
        }
        Row(
            modifier = Modifier
                .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                text = when (rule.action) {
                    OuiRuleAction.WHITELIST -> "Whitelist"
                    OuiRuleAction.BLACKLIST -> "Blacklist"
                },
                style = MaterialTheme.typography.labelSmall,
                color = badgeColor,
            )
        }
        IconButton(onClick = { confirmDelete = true }) {
            Icon(Icons.Default.Delete, contentDescription = "Delete OUI rule", tint = NrOnSurfaceVariant)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete rule?") },
            text = { Text("Remove the rule for ${rule.ouiPrefix}?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}
