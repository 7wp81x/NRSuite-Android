package com.swp81x.nrsuite.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swp81x.nrsuite.core.history.HistoryEntry
import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.log.LogEntry
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.components.RoundedTabRow
import com.swp81x.nrsuite.ui.theme.LogBgError
import com.swp81x.nrsuite.ui.theme.LogColorError
import com.swp81x.nrsuite.ui.theme.LogColorInfo
import com.swp81x.nrsuite.ui.theme.LogColorSuccess
import com.swp81x.nrsuite.ui.theme.LogColorUsb
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed
import com.swp81x.nrsuite.ui.util.copyWithToast

@Composable
internal fun LogsScreen(
    logs: List<LogEntry>,
    history: List<HistoryEntry>,
    onClearLogs: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        RoundedTabRow(
            selectedIndex = selectedTab,
            tabs = listOf("Runtime (${logs.size})", "History (${history.size})"),
            onSelect = { selectedTab = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        if (selectedTab == 0) {
            RuntimeLogsTab(
                logs = logs,
                clipboardManager = clipboardManager,
                context = context,
                onClearLogs = onClearLogs,
            )
        } else {
            HistoryLogsTab(
                history = history,
                clipboardManager = clipboardManager,
                context = context,
                onClearHistory = onClearHistory,
            )
        }
    }
}

@Composable
private fun RuntimeLogsTab(
    logs: List<LogEntry>,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    context: android.content.Context,
    onClearLogs: () -> Unit,
) {
    var selectedLevelIndex by rememberSaveable { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var liveFollow by rememberSaveable { mutableStateOf(true) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var selectedLog by remember { mutableStateOf<LogEntry?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val levels = listOf(
        "All" to null,
        "Errors" to LogLevel.ERROR,
        "USB" to LogLevel.USB,
        "Success" to LogLevel.SUCCESS,
        "Info" to LogLevel.INFO,
    )
    val selectedLevel = levels[selectedLevelIndex].second
    val normalizedQuery = searchQuery.trim()
    val filtered = logs.asReversed().filter { entry ->
        (selectedLevel == null || entry.level == selectedLevel) &&
            (normalizedQuery.isBlank() ||
                entry.message.contains(normalizedQuery, ignoreCase = true) ||
                entry.tag.contains(normalizedQuery, ignoreCase = true) ||
                entry.timestamp.contains(normalizedQuery, ignoreCase = true))
    }
    LaunchedEffect(filtered.size, liveFollow) {
        if (liveFollow && filtered.isNotEmpty()) listState.animateScrollToItem(0)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (liveFollow) "Live" else "Paused",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (liveFollow) StatusGreen else StatusAmber,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "· ${filtered.size} shown · ${logs.size} buffered",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { liveFollow = !liveFollow }) {
                    Icon(
                        imageVector = if (liveFollow) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (liveFollow) "Pause live logs" else "Resume live logs",
                    )
                }
                Box {
                    IconButton(onClick = { overflowExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Log actions")
                    }
                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy visible logs") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            enabled = filtered.isNotEmpty(),
                            onClick = {
                                overflowExpanded = false
                                clipboardManager.copyWithToast(
                                    context,
                                    filtered.reversed().joinToString("\n") {
                                        "${it.timestamp} [${it.level.name}/${it.tag}] ${it.message}"
                                    },
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Export visible logs") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            enabled = filtered.isNotEmpty(),
                            onClick = {
                                overflowExpanded = false
                                val text = filtered.reversed().joinToString("\n") {
                                    "${it.timestamp} [${it.level.name}/${it.tag}] ${it.message}"
                                }
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, text)
                                        },
                                        "Export logs",
                                    ),
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Clear runtime logs") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            enabled = logs.isNotEmpty(),
                            onClick = {
                                overflowExpanded = false
                                showClearConfirm = true
                            },
                        )
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search message, tag, or time") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                levels.forEachIndexed { index, (label, _) ->
                    NrFilterChip(
                        selected = selectedLevelIndex == index,
                        onClick = { selectedLevelIndex = index },
                        label = label,
                    )
                }
            }
        }
        if (filtered.isEmpty()) {
            item { LogsEmptyState("No runtime logs yet", "Connect a device or run a module to see live output.") }
        } else {
            items(filtered) { entry ->
                TerminalLogRow(entry = entry, onClick = { selectedLog = entry })
            }
        }
    }

    selectedLog?.let { entry ->
        LogDetailDialog(
            entry = entry,
            onDismiss = { selectedLog = null },
            onCopy = {
                clipboardManager.copyWithToast(
                    context,
                    "${entry.timestamp} [${entry.level.name}/${entry.tag}] ${entry.message}",
                )
            },
        )
    }
    if (showClearConfirm) {
        ConfirmClearDialog(
            title = "Clear runtime logs?",
            text = "This removes the current ${logs.size} runtime log entries.",
            onConfirm = {
                showClearConfirm = false
                onClearLogs()
            },
            onDismiss = { showClearConfirm = false },
        )
    }
}

@Composable
private fun TerminalLogRow(
    entry: LogEntry,
    onClick: () -> Unit,
) {
    val levelColor = when (entry.level) {
        LogLevel.ERROR -> LogColorError
        LogLevel.SUCCESS -> LogColorSuccess
        LogLevel.USB -> LogColorUsb
        LogLevel.INFO -> LogColorInfo
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (entry.level == LogLevel.ERROR) {
                    LogBgError
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                },
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.timestamp,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = entry.level.name,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = levelColor,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(levelColor.copy(alpha = 0.12f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "[${entry.tag}]",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = if (entry.level == LogLevel.ERROR) LogColorError else MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LogDetailDialog(
    entry: LogEntry,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${entry.level.name} · ${entry.tag}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = entry.timestamp,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = entry.message,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCopy(); onDismiss() }) { Text("Copy") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun HistoryLogsTab(
    history: List<HistoryEntry>,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    context: android.content.Context,
    onClearHistory: () -> Unit,
) {
    var selectedLevelIndex by rememberSaveable { mutableIntStateOf(0) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<HistoryEntry?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val levels = listOf(
        "All" to null,
        "Success" to HistoryLevel.SUCCESS,
        "Info" to HistoryLevel.INFO,
        "Errors" to HistoryLevel.ERROR,
    )
    val selectedLevel = levels[selectedLevelIndex].second
    val filtered = history.filter { selectedLevel == null || it.level == selectedLevel }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${filtered.size} of ${history.size} sessions",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { overflowExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "History actions")
                }
                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { overflowExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Copy visible history") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        enabled = filtered.isNotEmpty(),
                        onClick = {
                            overflowExpanded = false
                            clipboardManager.copyWithToast(
                                context,
                                filtered.joinToString("\n") {
                                    "${it.timestamp} [${it.module}] ${it.level.name}: ${it.summary}"
                                },
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Export visible history") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        enabled = filtered.isNotEmpty(),
                        onClick = {
                            overflowExpanded = false
                            val text = filtered.joinToString("\n") {
                                "${it.timestamp} [${it.module}] ${it.level.name}: ${it.summary}"
                            }
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    },
                                    "Export history",
                                ),
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Clear history") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        enabled = history.isNotEmpty(),
                        onClick = {
                            overflowExpanded = false
                            showClearConfirm = true
                        },
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            levels.forEachIndexed { index, (label, _) ->
                NrFilterChip(
                    selected = selectedLevelIndex == index,
                    onClick = { selectedLevelIndex = index },
                    label = label,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (filtered.isEmpty()) {
                item { LogsEmptyState("No sessions yet", "Run a module to record your first session.") }
            } else {
                items(filtered, key = { it.id }) { entry ->
                    HistorySessionCard(entry = entry, onClick = { selectedEntry = entry })
                }
            }
        }
    }

    selectedEntry?.let { entry ->
        LogDetailDialog(
            entry = LogEntry(
                timestamp = entry.timestamp,
                level = when (entry.level) {
                    HistoryLevel.INFO -> LogLevel.INFO
                    HistoryLevel.SUCCESS -> LogLevel.SUCCESS
                    HistoryLevel.ERROR -> LogLevel.ERROR
                },
                tag = entry.module.uppercase(),
                message = entry.summary,
            ),
            onDismiss = { selectedEntry = null },
            onCopy = {
                clipboardManager.copyWithToast(
                    context,
                    "${entry.timestamp} [${entry.module}] ${entry.level.name}: ${entry.summary}",
                )
            },
        )
    }
    if (showClearConfirm) {
        ConfirmClearDialog(
            title = "Clear session history?",
            text = "This removes all ${history.size} recorded session entries.",
            onConfirm = {
                showClearConfirm = false
                onClearHistory()
            },
            onDismiss = { showClearConfirm = false },
        )
    }
}

@Composable
private fun HistorySessionCard(
    entry: HistoryEntry,
    onClick: () -> Unit,
) {
    val color = when (entry.level) {
        HistoryLevel.SUCCESS -> LogColorSuccess
        HistoryLevel.ERROR -> LogColorError
        HistoryLevel.INFO -> LogColorInfo
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.timestamp,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = entry.module.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = entry.level.name,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(color.copy(alpha = 0.12f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = entry.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LogsEmptyState(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.Terminal,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConfirmClearDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = StatusRed,
                    contentColor = Color.White,
                ),
            ) { Text("Clear") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
