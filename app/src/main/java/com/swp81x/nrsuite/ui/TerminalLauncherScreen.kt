package com.swp81x.nrsuite.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.swp81x.nrsuite.core.terminal.TerminalShortcut
import com.swp81x.nrsuite.core.terminal.TerminalShortcutRunMode
import com.swp81x.nrsuite.core.terminal.terminalShortcutParameterNames
import com.swp81x.nrsuite.ui.components.NrDangerButton
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.components.NrPrimaryButton
import com.swp81x.nrsuite.ui.components.NrQuietButton
import com.swp81x.nrsuite.ui.components.NrSecondaryButton
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant

private sealed interface ShortcutRoute {
    data object Manage : ShortcutRoute
    data object Categories : ShortcutRoute
    data class Editor(val shortcut: TerminalShortcut?) : ShortcutRoute
}

private enum class ShortcutRouteKey {
    MANAGE,
    CATEGORIES,
    EDITOR,
}

@Composable
internal fun TerminalLauncherScreen(
    shortcuts: List<TerminalShortcut>,
    categories: List<String>,
    onClose: () -> Unit,
    onUpsertShortcut: (TerminalShortcut) -> Unit,
    onDeleteShortcut: (Long) -> Unit,
    onAddCategory: (String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onRenameCategory: (String, String) -> Unit,
    initialCreateShortcut: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var routeKeyName by rememberSaveable {
        mutableStateOf(
            if (initialCreateShortcut) {
                ShortcutRouteKey.EDITOR.name
            } else {
                ShortcutRouteKey.MANAGE.name
            },
        )
    }
    var editorShortcutId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingDelete by remember { mutableStateOf<TerminalShortcut?>(null) }
    val routeKey = runCatching { ShortcutRouteKey.valueOf(routeKeyName) }
        .getOrDefault(ShortcutRouteKey.MANAGE)
    val editorShortcut = editorShortcutId?.let { id ->
        shortcuts.firstOrNull { it.id == id }
    }
    val currentRoute = when (routeKey) {
        ShortcutRouteKey.MANAGE -> ShortcutRoute.Manage
        ShortcutRouteKey.CATEGORIES -> ShortcutRoute.Categories
        ShortcutRouteKey.EDITOR -> ShortcutRoute.Editor(editorShortcut)
    }

    BackHandler {
        when (routeKey) {
            ShortcutRouteKey.MANAGE -> onClose()
            ShortcutRouteKey.CATEGORIES,
            ShortcutRouteKey.EDITOR -> routeKeyName = ShortcutRouteKey.MANAGE.name
        }
    }

    when (val current = currentRoute) {
        ShortcutRoute.Manage -> ManageShortcutsContent(
            shortcuts = shortcuts,
            onBack = onClose,
            onNew = {
                editorShortcutId = null
                routeKeyName = ShortcutRouteKey.EDITOR.name
            },
            onEdit = { shortcut ->
                editorShortcutId = shortcut.id
                routeKeyName = ShortcutRouteKey.EDITOR.name
            },
            onDelete = { pendingDelete = it },
            onTogglePin = { shortcut ->
                onUpsertShortcut(shortcut.copy(pinHome = !shortcut.pinHome))
            },
            onManageCategories = { routeKeyName = ShortcutRouteKey.CATEGORIES.name },
            modifier = modifier,
        )
        ShortcutRoute.Categories -> ManageCategoriesContent(
            categories = categories,
            shortcuts = shortcuts,
            onBack = { routeKeyName = ShortcutRouteKey.MANAGE.name },
            onAdd = onAddCategory,
            onRename = onRenameCategory,
            onDelete = onDeleteCategory,
            modifier = modifier,
        )
        is ShortcutRoute.Editor -> ShortcutEditorContent(
            initial = current.shortcut,
            categories = categories,
            onBack = { routeKeyName = ShortcutRouteKey.MANAGE.name },
            onSave = { shortcut ->
                onUpsertShortcut(shortcut)
                routeKeyName = ShortcutRouteKey.MANAGE.name
            },
            onDelete = current.shortcut?.let { shortcut ->
                { pendingDelete = shortcut }
            },
            onAddCategory = onAddCategory,
            modifier = modifier,
        )
    }


    pendingDelete?.let { shortcut ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete shortcut?") },
            text = { Text("Remove “${shortcut.name}”? This cannot be undone.") },
            confirmButton = {
                NrDangerButton(
                    text = "Delete",
                    onClick = {
                        pendingDelete = null
                        onDeleteShortcut(shortcut.id)
                        routeKeyName = ShortcutRouteKey.MANAGE.name
                    },
                    filled = true,
                )
            },
            dismissButton = {
                NrQuietButton(text = "Cancel", onClick = { pendingDelete = null })
            },
        )
    }
}

@Composable
private fun ManageShortcutsContent(
    shortcuts: List<TerminalShortcut>,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onEdit: (TerminalShortcut) -> Unit,
    onDelete: (TerminalShortcut) -> Unit,
    onTogglePin: (TerminalShortcut) -> Unit,
    onManageCategories: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenHeader(title = "Manage Shortcuts", onBack = onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NrPrimaryButton(
                text = "New Shortcut",
                onClick = onNew,
                leadingIcon = Icons.Default.Add,
                compact = true,
            )
            NrSecondaryButton(
                text = "Categories",
                onClick = onManageCategories,
                compact = true,
            )
        }
        if (shortcuts.isEmpty()) {
            Text("No shortcuts configured.", color = NrOnSurfaceVariant)
        } else {
            shortcuts.forEach { shortcut ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    border = BorderStroke(1.dp, NrOutline.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(shortcut.name, fontWeight = FontWeight.Medium)
                            Text(
                                shortcut.command,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = NrOnSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                buildString {
                                    append(shortcut.runMode.label)
                                    if (shortcut.category.isNotBlank()) append(" · ${shortcut.category}")
                                    if (shortcut.pinHome) append(" · Pinned")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { onTogglePin(shortcut) }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = if (shortcut.pinHome) "Unpin" else "Pin to Home",
                                tint = if (shortcut.pinHome) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    NrOnSurfaceVariant
                                },
                            )
                        }
                        IconButton(onClick = { onEdit(shortcut) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit ${shortcut.name}")
                        }
                        IconButton(onClick = { onDelete(shortcut) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete ${shortcut.name}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutEditorContent(
    initial: TerminalShortcut?,
    categories: List<String>,
    onBack: () -> Unit,
    onSave: (TerminalShortcut) -> Unit,
    onDelete: (() -> Unit)?,
    onAddCategory: (String) -> Unit,
    modifier: Modifier,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var command by remember { mutableStateOf(initial?.command.orEmpty()) }
    val detectedParameters = terminalShortcutParameterNames(command)
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var category by remember { mutableStateOf(initial?.category.orEmpty()) }
    var runMode by remember {
        mutableStateOf(initial?.runMode ?: TerminalShortcutRunMode.CURRENT_SESSION)
    }
    var confirmation by remember { mutableStateOf(initial?.confirmation ?: true) }
    var pinHome by remember { mutableStateOf(initial?.pinHome ?: false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenHeader(
            title = if (initial == null) "New Shortcut" else "Edit Shortcut",
            onBack = onBack,
        )
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
            minLines = 4,
            maxLines = 12,
        )
        Text(
            text = "Use {{name}} for run-time parameters. Example: nmap -sV {{target}}",
            style = MaterialTheme.typography.labelSmall,
            color = NrOnSurfaceVariant,
        )
        if (detectedParameters.isNotEmpty()) {
            Text(
                text = "Detected parameters: ${detectedParameters.joinToString()}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Description") },
        )

        Text("Category", style = MaterialTheme.typography.labelMedium)
        Box {
            NrSecondaryButton(
                text = category.ifBlank { "Uncategorized" },
                onClick = { showCategoryMenu = true },
                leadingIcon = Icons.Default.ArrowDropDown,
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownMenu(
                expanded = showCategoryMenu,
                onDismissRequest = { showCategoryMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Uncategorized") },
                    onClick = {
                        category = ""
                        showCategoryMenu = false
                    },
                )
                categories.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item) },
                        onClick = {
                            category = item
                            showCategoryMenu = false
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("+ New category") },
                    onClick = {
                        showCategoryMenu = false
                        showNewCategoryDialog = true
                    },
                )
            }
        }

        Text("Run mode", style = MaterialTheme.typography.labelMedium)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TerminalShortcutRunMode.entries.chunked(2).forEach { modes ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    modes.forEach { mode ->
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
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank() && command.isNotBlank(),
        )
        onDelete?.let {
            NrDangerButton(
                text = "Delete shortcut",
                onClick = it,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showNewCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showNewCategoryDialog = false },
            title = { Text("New category") },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Category name") },
                )
            },
            confirmButton = {
                NrPrimaryButton(
                    text = "Create",
                    onClick = {
                        val normalized = newCategoryName.trim()
                        if (normalized.isNotBlank()) {
                            onAddCategory(normalized)
                            category = normalized
                        }
                        newCategoryName = ""
                        showNewCategoryDialog = false
                    },
                    enabled = newCategoryName.isNotBlank(),
                )
            },
            dismissButton = {
                NrQuietButton(
                    text = "Cancel",
                    onClick = {
                        newCategoryName = ""
                        showNewCategoryDialog = false
                    },
                )
            },
        )
    }
}

@Composable
private fun ManageCategoriesContent(
    categories: List<String>,
    shortcuts: List<TerminalShortcut>,
    onBack: () -> Unit,
    onAdd: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier,
) {
    var newCategory by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenHeader(title = "Manage Categories", onBack = onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newCategory,
                onValueChange = { newCategory = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("New category") },
            )
            NrPrimaryButton(
                text = "Add",
                onClick = {
                    onAdd(newCategory)
                    newCategory = ""
                },
                enabled = newCategory.isNotBlank(),
                compact = true,
            )
        }
        categories.forEach { category ->
            val count = shortcuts.count { it.category == category }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                border = BorderStroke(1.dp, NrOutline.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        category,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "$count shortcuts",
                        style = MaterialTheme.typography.labelSmall,
                        color = NrOnSurfaceVariant,
                    )
                    IconButton(
                        onClick = {
                            renameTarget = category
                            renameValue = category
                        },
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename $category")
                    }
                    IconButton(onClick = { deleteTarget = category }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete $category")
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename category") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    label = { Text("Category name") },
                )
            },
            confirmButton = {
                NrPrimaryButton(
                    text = "Rename",
                    onClick = {
                        onRename(target, renameValue)
                        renameTarget = null
                    },
                    enabled = renameValue.isNotBlank(),
                )
            },
            dismissButton = {
                NrQuietButton(text = "Cancel", onClick = { renameTarget = null })
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete category?") },
            text = { Text("Shortcuts in “$target” will be moved to Uncategorized.") },
            confirmButton = {
                NrDangerButton(
                    text = "Delete",
                    onClick = {
                        onDelete(target)
                        deleteTarget = null
                    },
                    filled = true,
                )
            },
            dismissButton = {
                NrQuietButton(text = "Cancel", onClick = { deleteTarget = null })
            },
        )
    }
}

@Composable
private fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
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
