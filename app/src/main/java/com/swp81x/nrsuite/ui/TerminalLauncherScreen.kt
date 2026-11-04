package com.swp81x.nrsuite.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.swp81x.nrsuite.core.terminal.TerminalConnectionState
import com.swp81x.nrsuite.core.terminal.TerminalSessionEntry
import com.swp81x.nrsuite.core.terminal.TerminalShortcut
import com.swp81x.nrsuite.core.terminal.TerminalShortcutRunMode
import com.swp81x.nrsuite.ui.components.NrDangerButton
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.components.NrPrimaryButton
import com.swp81x.nrsuite.ui.components.NrQuietButton
import com.swp81x.nrsuite.ui.components.NrSecondaryButton
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen

@Composable
internal fun TerminalLauncherScreen(
    connectionState: TerminalConnectionState,
    sessions: List<TerminalSessionEntry>,
    shortcuts: List<TerminalShortcut>,
    categories: List<String>,
    onOpenTerminal: () -> Unit,
    onOpenSession: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onRunShortcut: (TerminalShortcut) -> Unit,
    onUpsertShortcut: (TerminalShortcut) -> Unit,
    onDeleteShortcut: (Long) -> Unit,
    onAddCategory: (String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var editingShortcut by remember { mutableStateOf<TerminalShortcut?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showCategoryManager by remember { mutableStateOf(false) }
    var pendingRun by remember { mutableStateOf<TerminalShortcut?>(null) }

    val filteredShortcuts = shortcuts.filter { shortcut ->
        (selectedCategory == null || shortcut.category == selectedCategory) &&
            (search.isBlank() ||
                shortcut.name.contains(search, ignoreCase = true) ||
                shortcut.command.contains(search, ignoreCase = true) ||
                shortcut.description.contains(search, ignoreCase = true))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, NrOutline.copy(alpha = 0.55f)),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Terminal Launcher",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = when (connectionState) {
                                is TerminalConnectionState.Connected -> "Local Termux · connected"
                                is TerminalConnectionState.Connecting -> "Connecting to Termux…"
                                is TerminalConnectionState.Failed -> "Connection failed"
                                else -> "Terminal is not connected"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when (connectionState) {
                                is TerminalConnectionState.Connected -> StatusGreen
                                is TerminalConnectionState.Failed -> MaterialTheme.colorScheme.error
                                else -> NrOnSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Terminal Settings")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NrPrimaryButton(
                        text = "Open Terminal",
                        onClick = onOpenTerminal,
                        compact = true,
                    )
                    NrSecondaryButton(
                        text = "New Shortcut",
                        onClick = {
                            editingShortcut = null
                            showEditor = true
                        },
                        leadingIcon = Icons.Default.Add,
                        compact = true,
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Terminal Shortcuts",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            NrQuietButton(
                text = "Categories",
                onClick = { showCategoryManager = true },
                compact = true,
            )
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search shortcuts") },
        )

        if (categories.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                NrFilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = "All",
                )
                categories.forEach { category ->
                    NrFilterChip(
                        selected = selectedCategory == category,
                        onClick = {
                            selectedCategory = if (selectedCategory == category) null else category
                        },
                        label = category,
                    )
                }
            }
        }

        if (filteredShortcuts.isEmpty()) {
            Text(
                text = if (shortcuts.isEmpty()) {
                    "No terminal shortcuts yet. Create one to run commands from the launcher or Home."
                } else {
                    "No shortcuts match the current filter."
                },
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
        } else {
            filteredShortcuts.forEach { shortcut ->
                ShortcutRow(
                    shortcut = shortcut,
                    onRun = {
                        if (shortcut.confirmation) {
                            pendingRun = shortcut
                        } else {
                            onRunShortcut(shortcut)
                        }
                    },
                    onEdit = {
                        editingShortcut = shortcut
                        showEditor = true
                    },
                    onDelete = { onDeleteShortcut(shortcut.id) },
                )
            }
        }
    }

    if (showEditor) {
        ShortcutEditorDialog(
            initial = editingShortcut,
            categories = categories,
            onDismiss = { showEditor = false },
            onSave = { shortcut ->
                showEditor = false
                onUpsertShortcut(shortcut)
            },
        )
    }

    if (showCategoryManager) {
        CategoryManagerDialog(
            categories = categories,
            onAdd = onAddCategory,
            onDelete = onDeleteCategory,
            onDismiss = { showCategoryManager = false },
        )
    }

    pendingRun?.let { shortcut ->
        AlertDialog(
            onDismissRequest = { pendingRun = null },
            title = { Text("Run shortcut?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(shortcut.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        shortcut.command,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (shortcut.description.isNotBlank()) {
                        Text(
                            shortcut.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                NrPrimaryButton(
                    text = "Run",
                    onClick = {
                        pendingRun = null
                        onRunShortcut(shortcut)
                    },
                )
            },
            dismissButton = {
                NrQuietButton(text = "Cancel", onClick = { pendingRun = null })
            },
        )
    }
}

@Composable
private fun ShortcutRow(
    shortcut: TerminalShortcut,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, NrOutline.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Terminal,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    shortcut.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    shortcut.command,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = NrOnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (shortcut.description.isNotBlank()) {
                    Text(
                        shortcut.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = NrOnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onRun) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Run ${shortcut.name}")
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit ${shortcut.name}")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete ${shortcut.name}")
            }
        }
    }
}

@Composable
private fun ShortcutEditorDialog(
    initial: TerminalShortcut?,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (TerminalShortcut) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var command by remember { mutableStateOf(initial?.command.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var category by remember { mutableStateOf(initial?.category.orEmpty()) }
    var runMode by remember {
        mutableStateOf(initial?.runMode ?: TerminalShortcutRunMode.CURRENT_SESSION)
    }
    var confirmation by remember { mutableStateOf(initial?.confirmation ?: true) }
    var pinHome by remember { mutableStateOf(initial?.pinHome ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New terminal shortcut" else "Edit shortcut") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Name") },
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Command") },
                    minLines = 2,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Description") },
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Category") },
                    supportingText = {
                        if (categories.isNotEmpty()) {
                            Text(categories.joinToString(" · "), maxLines = 1)
                        }
                    },
                )
                Text("Run mode", style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TerminalShortcutRunMode.entries.chunked(2).forEach { rowModes ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowModes.forEach { mode ->
                                NrFilterChip(
                                    selected = runMode == mode,
                                    onClick = { runMode = mode },
                                    label = mode.label,
                                )
                            }
                        }
                    }
                }
                ToggleTextRow(
                    label = "Ask before running",
                    checked = confirmation,
                    onCheckedChange = { confirmation = it },
                )
                ToggleTextRow(
                    label = "Pin to Home",
                    checked = pinHome,
                    onCheckedChange = { pinHome = it },
                )
            }
        },
        confirmButton = {
            NrPrimaryButton(
                text = "Save",
                onClick = {
                    onSave(
                        TerminalShortcut(
                            id = initial?.id ?: System.currentTimeMillis(),
                            name = name.trim(),
                            command = command.trim(),
                            description = description.trim(),
                            category = category.trim(),
                            runMode = runMode,
                            confirmation = confirmation,
                            pinHome = pinHome,
                        ),
                    )
                },
                enabled = name.isNotBlank() && command.isNotBlank(),
            )
        },
        dismissButton = {
            NrQuietButton(text = "Cancel", onClick = onDismiss)
        },
    )
}

@Composable
private fun ToggleTextRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CategoryManagerDialog(
    categories: List<String>,
    onAdd: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newCategory by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Terminal categories") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(category, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onDelete(category) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete $category")
                        }
                    }
                }
                OutlinedTextField(
                    value = newCategory,
                    onValueChange = { newCategory = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("New category") },
                )
                NrSecondaryButton(
                    text = "Add category",
                    onClick = {
                        onAdd(newCategory)
                        newCategory = ""
                    },
                    enabled = newCategory.isNotBlank(),
                )
            }
        },
        confirmButton = {
            NrQuietButton(text = "Done", onClick = onDismiss)
        },
    )
}
