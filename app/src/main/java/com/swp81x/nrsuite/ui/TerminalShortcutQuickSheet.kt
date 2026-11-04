package com.swp81x.nrsuite.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.swp81x.nrsuite.core.terminal.resolveTerminalShortcutCommand
import com.swp81x.nrsuite.core.terminal.terminalShortcutParameterLabel
import com.swp81x.nrsuite.core.terminal.terminalShortcutParameterNames
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.components.NrPrimaryButton
import com.swp81x.nrsuite.ui.components.NrQuietButton
import com.swp81x.nrsuite.ui.components.NrSecondaryButton
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TerminalShortcutQuickSheet(
    shortcuts: List<TerminalShortcut>,
    categories: List<String>,
    onDismiss: () -> Unit,
    onRunShortcut: (TerminalShortcut) -> Unit,
    onAdd: () -> Unit,
    onManage: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var parameterShortcut by remember { mutableStateOf<TerminalShortcut?>(null) }
    var parameterNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var parameterValues by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingConfirmation by remember { mutableStateOf<TerminalShortcut?>(null) }

    fun runShortcut(shortcut: TerminalShortcut) {
        val names = terminalShortcutParameterNames(shortcut.command)
        if (names.isNotEmpty()) {
            parameterShortcut = shortcut
            parameterNames = names
            parameterValues = List(names.size) { "" }
        } else if (shortcut.confirmation) {
            pendingConfirmation = shortcut
        } else {
            onRunShortcut(shortcut)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        if (parameterShortcut != null) {
            ShortcutParameterForm(
                shortcut = parameterShortcut!!,
                parameterNames = parameterNames,
                parameterValues = parameterValues,
                onValueChange = { index, value ->
                    parameterValues = parameterValues.toMutableList().also { it[index] = value }
                },
                onBack = {
                    parameterShortcut = null
                    parameterNames = emptyList()
                    parameterValues = emptyList()
                },
                onRun = {
                    val shortcut = parameterShortcut
                    if (shortcut != null) {
                        val values = parameterNames.zip(parameterValues).toMap()
                        val resolved = resolveTerminalShortcutCommand(
                            shortcut.command,
                            values,
                        )
                        parameterShortcut = null
                        parameterNames = emptyList()
                        parameterValues = emptyList()
                        onRunShortcut(shortcut.copy(command = resolved))
                    }
                },
            )
        } else {
            ShortcutQuickList(
                shortcuts = shortcuts,
                categories = categories,
                onRunShortcut = ::runShortcut,
                onDismiss = onDismiss,
                onAdd = onAdd,
                onManage = onManage,
                onOpenSettings = onOpenSettings,
            )
        }
    }

    pendingConfirmation?.let { shortcut ->
        AlertDialog(
            onDismissRequest = { pendingConfirmation = null },
            title = { Text("Run shortcut?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(shortcut.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        shortcut.command,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
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
                        pendingConfirmation = null
                        onRunShortcut(shortcut)
                    },
                )
            },
            dismissButton = {
                NrQuietButton(
                    text = "Cancel",
                    onClick = { pendingConfirmation = null },
                )
            },
        )
    }
}

@Composable
private fun ShortcutQuickList(
    shortcuts: List<TerminalShortcut>,
    categories: List<String>,
    onRunShortcut: (TerminalShortcut) -> Unit,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onManage: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val filteredShortcuts = shortcuts.filter { shortcut ->
        (selectedCategory == null || shortcut.category == selectedCategory) &&
            (search.isBlank() ||
                shortcut.name.contains(search, ignoreCase = true) ||
                shortcut.command.contains(search, ignoreCase = true) ||
                shortcut.description.contains(search, ignoreCase = true))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Terminal,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Terminal Shortcuts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Tap a shortcut to run it. Use {{name}} in commands for parameters.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "New shortcut")
            }
            IconButton(onClick = onManage) {
                Icon(Icons.Default.Edit, contentDescription = "Manage shortcuts")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Terminal Settings")
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search shortcuts") },
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
                    "No terminal shortcuts yet. Tap + to create one."
                } else {
                    "No shortcuts match the current filter."
                },
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filteredShortcuts, key = { it.id }) { shortcut ->
                    QuickShortcutRow(
                        shortcut = shortcut,
                        onRun = { onRunShortcut(shortcut) },
                    )
                }
            }
        }
        Spacer(Modifier.size(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NrSecondaryButton(
                text = "Manage shortcuts",
                onClick = onManage,
                compact = true,
            )
            NrQuietButton(
                text = "Close",
                onClick = onDismiss,
                compact = true,
            )
        }
    }
}

@Composable
private fun QuickShortcutRow(
    shortcut: TerminalShortcut,
    onRun: () -> Unit,
) {
    val parameterCount = terminalShortcutParameterNames(shortcut.command).size
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
                Text(
                    buildString {
                        if (shortcut.category.isNotBlank()) append(shortcut.category)
                        if (parameterCount > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("$parameterCount parameter")
                            if (parameterCount != 1) append("s")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onRun) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Run ${shortcut.name}")
            }
        }
    }
}

@Composable
private fun ShortcutParameterForm(
    shortcut: TerminalShortcut,
    parameterNames: List<String>,
    parameterValues: List<String>,
    onValueChange: (Int, String) -> Unit,
    onBack: () -> Unit,
    onRun: () -> Unit,
) {
    val resolvedCommand = resolveTerminalShortcutCommand(
        shortcut.command,
        parameterNames.zip(parameterValues).toMap(),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back to shortcuts")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    shortcut.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Enter shortcut parameters",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }

        parameterNames.forEachIndexed { index, name ->
            OutlinedTextField(
                value = parameterValues.getOrElse(index) { "" },
                onValueChange = { onValueChange(index, it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(terminalShortcutParameterLabel(name)) },
                placeholder = { Text(name) },
            )
        }

        Text(
            "Command preview",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            resolvedCommand,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NrPrimaryButton(
                text = "Run",
                onClick = onRun,
                enabled = parameterValues.all { it.isNotBlank() },
            )
            NrQuietButton(text = "Cancel", onClick = onBack, compact = true)
        }
    }
}

@Composable
internal fun TerminalShortcutParameterDialog(
    shortcut: TerminalShortcut,
    onDismiss: () -> Unit,
    onRun: (TerminalShortcut) -> Unit,
) {
    val names = remember(shortcut.id, shortcut.command) {
        terminalShortcutParameterNames(shortcut.command)
    }
    var values by remember(shortcut.id, shortcut.command) {
        mutableStateOf(List(names.size) { "" })
    }
    val preview = resolveTerminalShortcutCommand(
        shortcut.command,
        names.zip(values).toMap(),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(shortcut.name) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Enter shortcut parameters",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
                names.forEachIndexed { index, name ->
                    OutlinedTextField(
                        value = values.getOrElse(index) { "" },
                        onValueChange = { value ->
                            values = values.toMutableList().also { it[index] = value }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(terminalShortcutParameterLabel(name)) },
                    )
                }
                Text(
                    "Command preview",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    preview,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
        },
        confirmButton = {
            NrPrimaryButton(
                text = "Run",
                onClick = {
                    onRun(
                        shortcut.copy(
                            command = resolveTerminalShortcutCommand(
                                shortcut.command,
                                names.zip(values).toMap(),
                            ),
                        ),
                    )
                },
                enabled = values.all { it.isNotBlank() },
            )
        },
        dismissButton = {
            NrQuietButton(text = "Cancel", onClick = onDismiss)
        },
    )
}
