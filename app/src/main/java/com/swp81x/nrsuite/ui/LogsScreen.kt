package com.swp81x.nrsuite.ui

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.WindowManager
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.swp81x.nrsuite.ui.theme.LogColorError
import com.swp81x.nrsuite.ui.theme.LogColorInfo
import com.swp81x.nrsuite.ui.theme.LogColorSuccess
import com.swp81x.nrsuite.ui.theme.LogColorUsb
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.history.HistoryEntry
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.swp81x.nrsuite.MainViewModel
import com.swp81x.nrsuite.NrSuiteApplication
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.usb.UsbSerialDevice
import com.swp81x.nrsuite.ui.components.ModuleCard
import com.swp81x.nrsuite.ui.components.ModuleCardSpec
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrOnSurface
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.swp81x.nrsuite.core.log.LogEntry
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.ui.theme.LogBgError

@Composable
internal fun LogsScreen(
    logs: List<LogEntry>,
    history: List<HistoryEntry>,
    onClearLogs: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    val filtered = logs.filter { selectedLevel == null || it.level == selectedLevel }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Runtime") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("History (${history.size})") })
        }

        if (selectedTab == 0) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NrSurfaceVariant.copy(alpha = 0.4f)),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Session log",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            val text = logs.joinToString("\n") { "${it.timestamp} [${it.tag}] ${it.message}" }
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(text))
                        }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy log") }
                        IconButton(onClick = {
                            val text = logs.joinToString("\n") { "${it.timestamp} [${it.tag}] ${it.message}" }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(intent, "Export log"))
                        }) { Icon(Icons.Default.Share, contentDescription = "Export log") }
                        IconButton(onClick = onClearLogs, enabled = logs.isNotEmpty()) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear log")
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val levels = listOf(null to "All", LogLevel.ERROR to "Errors", LogLevel.USB to "USB", LogLevel.SUCCESS to "Success")
                        levels.forEach { (level, label) ->
                            NrFilterChip(
                                selected = selectedLevel == level,
                                onClick = { selectedLevel = level },
                                label = label,
                            )
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    item { Text("No logs yet. Connect a device or run a module.", color = NrOnSurfaceVariant) }
                } else {
                    items(filtered.takeLast(300).reversed(), contentType = { "log" }) { entry ->
                        val textColor = when (entry.level) {
                            LogLevel.ERROR -> LogColorError
                            LogLevel.SUCCESS -> LogColorSuccess
                            LogLevel.USB -> LogColorUsb
                            LogLevel.INFO -> LogColorInfo
                        }
                        val bg = if (entry.level == LogLevel.ERROR) LogBgError else Color.Transparent
                        SelectionContainer {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(bg, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    text = entry.timestamp,
                                    fontSize = 11.sp,
                                    color = NrOnSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(56.dp),
                                )
                                Text(
                                    text = "[${entry.tag}] ${entry.message}",
                                    fontSize = 12.sp,
                                    color = textColor,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f),
                                    softWrap = true,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Session history",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            val text = history.joinToString("\n") { "${it.timestamp} [${it.module}] ${it.summary}" }
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(text))
                        }, enabled = history.isNotEmpty()) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy history") }
                        IconButton(onClick = {
                            val text = history.joinToString("\n") { "${it.timestamp} [${it.module}] ${it.summary}" }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(intent, "Export history"))
                        }, enabled = history.isNotEmpty()) { Icon(Icons.Default.Share, contentDescription = "Export history") }
                        IconButton(onClick = onClearHistory, enabled = history.isNotEmpty()) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear history")
                        }
                    }
                }
                if (history.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = NrOnSurfaceVariant,
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("No sessions yet", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Run a module to record your first session.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NrOnSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(history, contentType = { "history" }) { entry ->
                        val color = when (entry.level) {
                            HistoryLevel.SUCCESS -> LogColorSuccess
                            HistoryLevel.ERROR -> LogColorError
                            HistoryLevel.INFO -> LogColorInfo
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = NrSurface),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.5.dp, NrOutline),
                        ) {
                            SelectionContainer {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text(
                                        text = entry.timestamp,
                                        fontSize = 11.sp,
                                        color = NrOnSurfaceVariant,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.width(56.dp),
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = "[${entry.module.uppercase()}]",
                                            fontSize = 12.sp,
                                            color = color,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = entry.summary,
                                            fontSize = 12.sp,
                                            color = NrOnSurfaceVariant,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = entry.level.name,
                                        fontSize = 10.sp,
                                        color = color,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
