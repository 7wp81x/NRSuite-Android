package com.swp81x.nrsuite.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.swp81x.nrsuite.core.terminal.DEFAULT_TERMINAL_PORT
import com.swp81x.nrsuite.core.terminal.TERMUX_PACKAGE_NAME
import com.swp81x.nrsuite.core.terminal.TerminalConfig
import com.swp81x.nrsuite.core.terminal.TerminalConnectionState
import com.swp81x.nrsuite.core.terminal.TerminalSessionEntry
import com.swp81x.nrsuite.ui.components.NrDangerButton
import com.swp81x.nrsuite.ui.components.NrPrimaryButton
import com.swp81x.nrsuite.ui.components.NrQuietButton
import com.swp81x.nrsuite.ui.components.NrSecondaryButton
import com.swp81x.nrsuite.ui.theme.NrTheme
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusRed
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.launch

private const val TERMUX_FDROID_URL = "https://f-droid.org/packages/com.termux/"
private const val TERMUX_GITHUB_URL = "https://github.com/termux/termux-app"
private const val DEFAULT_TERMINAL_TEXT_SIZE = 13

private val terminalSetupCommands = """
pkg install openssh
passwd
sshd
whoami
""".trimIndent()

@Composable
internal fun TerminalScreen(
    connectionState: TerminalConnectionState,
    terminalSessions: List<TerminalSessionEntry>,
    initialHost: String,
    initialPort: Int,
    initialUsername: String,
    onConnect: (TerminalConfig) -> Unit,
    onDisconnect: () -> Unit,
    onNewSession: () -> Unit,
    onCloseSession: (Int) -> Unit,
    onSetUpdateListener: ((() -> Unit)?) -> Unit,
    onSetColorScheme: (IntArray, Int, Int, Int) -> Unit,
    onClearPinnedHostKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var host by rememberSaveable { mutableStateOf(initialHost) }
    var port by rememberSaveable { mutableStateOf(initialPort.toString()) }
    var username by rememberSaveable { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf("") }
    var refreshCount by remember { mutableIntStateOf(0) }
    val termuxInstalled = remember(refreshCount) {
        runCatching {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE_NAME, 0)
        }.isSuccess
    }

    when (connectionState) {
        is TerminalConnectionState.Connected -> {
            if (terminalSessions.isNotEmpty()) {
                ConnectedTerminal(
                    state = connectionState,
                    terminalSessions = terminalSessions,
                    onDisconnect = onDisconnect,
                    onNewSession = onNewSession,
                    onCloseSession = onCloseSession,
                    onSetUpdateListener = onSetUpdateListener,
                    onSetColorScheme = onSetColorScheme,
                    modifier = modifier,
                )
            }
        }
        else -> {
            TerminalSetup(
                state = connectionState,
                host = host,
                onHostChange = { host = it },
                port = port,
                onPortChange = { value -> port = value.filter(Char::isDigit).take(5) },
                username = username,
                onUsernameChange = { username = it },
                password = password,
                onPasswordChange = { password = it },
                termuxInstalled = termuxInstalled,
                onRefreshTermux = { refreshCount++ },
                onConnect = {
                    onConnect(
                        TerminalConfig(
                            host = host,
                            port = port.toIntOrNull() ?: DEFAULT_TERMINAL_PORT,
                            username = username,
                            password = password,
                        ),
                    )
                },
                onOpenTermux = {
                    val intent = context.packageManager
                        .getLaunchIntentForPackage(TERMUX_PACKAGE_NAME)
                    if (intent != null) {
                        context.startActivity(
                            Intent.createChooser(intent, "Open Termux"),
                        )
                    } else {
                        openBrowser(context, TERMUX_FDROID_URL)
                    }
                },
                onInstallTermux = { openBrowser(context, TERMUX_FDROID_URL) },
                onCopySetup = {
                    clipboard.setText(AnnotatedString(terminalSetupCommands))
                },
                onOpenTermuxProject = { openBrowser(context, TERMUX_GITHUB_URL) },
                onClearPinnedHostKey = onClearPinnedHostKey,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun TerminalSetup(
    state: TerminalConnectionState,
    host: String,
    onHostChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    termuxInstalled: Boolean,
    onRefreshTermux: () -> Unit,
    onConnect: () -> Unit,
    onOpenTermux: () -> Unit,
    onInstallTermux: () -> Unit,
    onCopySetup: () -> Unit,
    onOpenTermuxProject: () -> Unit,
    onClearPinnedHostKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Termux SSH terminal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (termuxInstalled) {
                                "Termux is installed on this device."
                            } else {
                                "Termux is required as the local Linux runtime."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    NrQuietButton(
                        text = "Recheck",
                        onClick = onRefreshTermux,
                        leadingIcon = Icons.Default.Refresh,
                        compact = true,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (termuxInstalled) {
                        NrSecondaryButton(
                            text = "Open Termux",
                            onClick = onOpenTermux,
                            leadingIcon = Icons.Default.OpenInNew,
                            compact = true,
                        )
                    } else {
                        NrPrimaryButton(
                            text = "Get Termux",
                            onClick = onInstallTermux,
                            leadingIcon = Icons.Default.Link,
                            compact = true,
                        )
                    }
                    NrQuietButton(
                        text = "Copy setup",
                        onClick = onCopySetup,
                        leadingIcon = Icons.Default.ContentCopy,
                        compact = true,
                    )
                }

                Text(
                    text = "In Termux run:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = terminalSetupCommands,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            RoundedCornerShape(8.dp),
                        )
                        .padding(10.dp),
                )
                Text(
                    text = "Use whoami in Termux for the username. SSH listens on localhost:8022.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onOpenTermuxProject) {
                    Text("Termux source / project")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Connection",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Termux username") },
                    placeholder = { Text("Output of whoami") },
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Termux password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = onHostChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Host") },
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = onPortChange,
                        modifier = Modifier.width(100.dp),
                        singleLine = true,
                        label = { Text("Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }

                if (state is TerminalConnectionState.Connecting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Connecting to Termux SSH…",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAmber,
                        )
                    }
                }
                if (state is TerminalConnectionState.Failed) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusRed,
                    )
                }
                if (state is TerminalConnectionState.Disconnected &&
                    !state.message.isNullOrBlank()
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                NrPrimaryButton(
                    text = "Connect to Termux",
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = username.isNotBlank() && password.isNotEmpty() &&
                        host.isNotBlank() && port.isNotBlank() &&
                        state !is TerminalConnectionState.Connecting,
                )
                Text(
                    text = "The password is kept in memory for this session only.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NrQuietButton(
                    text = "Clear pinned SSH host key",
                    onClick = onClearPinnedHostKey,
                    compact = true,
                )
            }
        }
    }
}

@Composable
private fun ConnectedTerminal(
    state: TerminalConnectionState.Connected,
    terminalSessions: List<TerminalSessionEntry>,
    onDisconnect: () -> Unit,
    onNewSession: () -> Unit,
    onCloseSession: (Int) -> Unit,
    onSetUpdateListener: ((() -> Unit)?) -> Unit,
    onSetColorScheme: (IntArray, Int, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val nrColors = NrTheme.colors
    val terminalViews = remember { mutableStateMapOf<Int, TerminalView>() }
    val pagerState = rememberPagerState(pageCount = { terminalSessions.size })
    val coroutineScope = rememberCoroutineScope()
    var terminalMenuExpanded by remember { mutableStateOf(false) }
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }
    var terminalTextSize by rememberSaveable { mutableIntStateOf(DEFAULT_TERMINAL_TEXT_SIZE) }
    val activeSession = terminalSessions.getOrNull(
        pagerState.currentPage.coerceIn(0, terminalSessions.lastIndex),
    ) ?: terminalSessions.first()

    val palette = remember(colorScheme, nrColors) {
        intArrayOf(
            colorScheme.surface.toArgb(),
            colorScheme.error.toArgb(),
            nrColors.statusGreen.toArgb(),
            nrColors.statusAmber.toArgb(),
            nrColors.statusInfo.toArgb(),
            colorScheme.secondary.toArgb(),
            colorScheme.tertiary.toArgb(),
            colorScheme.onSurface.toArgb(),
            colorScheme.onSurfaceVariant.toArgb(),
            colorScheme.error.toArgb(),
            nrColors.statusGreen.toArgb(),
            nrColors.statusAmber.toArgb(),
            nrColors.statusInfo.toArgb(),
            colorScheme.secondary.toArgb(),
            colorScheme.tertiary.toArgb(),
            colorScheme.onSurface.toArgb(),
        )
    }
    LaunchedEffect(palette, colorScheme, nrColors) {
        onSetColorScheme(
            palette,
            colorScheme.onSurface.toArgb(),
            colorScheme.background.toArgb(),
            colorScheme.primary.toArgb(),
        )
    }
    LaunchedEffect(terminalSessions.size) {
        if (pagerState.currentPage > terminalSessions.lastIndex) {
            pagerState.scrollToPage(terminalSessions.lastIndex.coerceAtLeast(0))
        }
    }
    DisposableEffect(Unit) {
        onSetUpdateListener {
            terminalViews.values.forEach { view ->
                view.post { view.onScreenUpdated() }
            }
        }
        onDispose { onSetUpdateListener(null) }
    }

    val showKeyboard: () -> Unit = {
        terminalViews[activeSession.id]?.let { view ->
            view.requestFocus()
            view.requestFocusFromTouch()
            view.post {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                    as? InputMethodManager
                imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }
    val hideKeyboard: () -> Unit = {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as? InputMethodManager
        imm?.hideSoftInputFromWindow(
            terminalViews[activeSession.id]?.windowToken,
            0,
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                TextButton(onClick = { terminalMenuExpanded = true }) {
                    Text(
                        text = activeSession.title,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        maxLines = 1,
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Session menu")
                }
                DropdownMenu(
                    expanded = terminalMenuExpanded,
                    onDismissRequest = { terminalMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("New session") },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                        onClick = {
                            terminalMenuExpanded = false
                            onNewSession()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Close current session") },
                        enabled = terminalSessions.size > 1,
                        onClick = {
                            terminalMenuExpanded = false
                            onCloseSession(activeSession.id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Increase font size") },
                        onClick = {
                            terminalMenuExpanded = false
                            terminalTextSize = (terminalTextSize + 1).coerceAtMost(28)
                            terminalViews[activeSession.id]?.setTextSize(terminalTextSize)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Decrease font size") },
                        enabled = terminalTextSize > 9,
                        onClick = {
                            terminalMenuExpanded = false
                            terminalTextSize = (terminalTextSize - 1).coerceAtLeast(9)
                            terminalViews[activeSession.id]?.setTextSize(terminalTextSize)
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Disconnect SSH") },
                        onClick = {
                            terminalMenuExpanded = false
                            onDisconnect()
                        },
                    )
                }
            }
            if (terminalSessions.size > 1) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(
                                (pagerState.currentPage - 1).coerceAtLeast(0),
                            )
                        }
                    },
                    enabled = pagerState.currentPage > 0,
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous session")
                }
                Text(
                    text = "${pagerState.currentPage + 1} / ${terminalSessions.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(
                                (pagerState.currentPage + 1)
                                    .coerceAtMost(terminalSessions.lastIndex),
                            )
                        }
                    },
                    enabled = pagerState.currentPage < terminalSessions.lastIndex,
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next session")
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = showKeyboard) {
                Icon(Icons.Default.Keyboard, contentDescription = "Show keyboard")
            }
            IconButton(onClick = hideKeyboard) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Hide keyboard")
            }
            IconButton(onClick = onNewSession) {
                Icon(Icons.Default.Add, contentDescription = "New terminal session")
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            val entry = terminalSessions[page]
            NativeTerminalPage(
                entry = entry,
                terminalTextSize = terminalTextSize,
                onViewReady = { view -> terminalViews[entry.id] = view },
                onViewDisposed = { terminalViews.remove(entry.id) },
                onShowKeyboard = showKeyboard,
                onZoom = { increase ->
                    terminalTextSize = (terminalTextSize + if (increase) 1 else -1)
                        .coerceIn(9, 28)
                    terminalViews[entry.id]?.setTextSize(terminalTextSize)
                },
                onCtrlRead = {
                    val value = ctrlActive
                    if (value) ctrlActive = false
                    value
                },
                onAltRead = {
                    val value = altActive
                    if (value) altActive = false
                    value
                },
                onSwipe = { direction ->
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(
                            (pagerState.currentPage + direction)
                                .coerceIn(0, terminalSessions.lastIndex),
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        TerminalExtraKeys(
            ctrlActive = ctrlActive,
            altActive = altActive,
            onToggleCtrl = {
                ctrlActive = !ctrlActive
                showKeyboard()
            },
            onToggleAlt = {
                altActive = !altActive
                showKeyboard()
            },
            onKey = { sequence ->
                activeSession.session.write(sequence)
                showKeyboard()
            },
        )
    }
}

@Composable
private fun NativeTerminalPage(
    entry: TerminalSessionEntry,
    terminalTextSize: Int,
    onViewReady: (TerminalView) -> Unit,
    onViewDisposed: () -> Unit,
    onShowKeyboard: () -> Unit,
    onZoom: (Boolean) -> Unit,
    onCtrlRead: () -> Boolean,
    onAltRead: () -> Boolean,
    onSwipe: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewState = remember { mutableStateOf<TerminalView?>(null) }
    val client = remember {
        NativeTerminalViewClient(
            onSingleTap = onShowKeyboard,
            onZoom = onZoom,
            readControl = onCtrlRead,
            readAlt = onAltRead,
            onSwipe = onSwipe,
        )
    }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TerminalView(viewContext, null).apply {
                setTextSize(terminalTextSize)
                setTerminalViewClient(client)
                attachSession(entry.session)
                isFocusable = true
                isFocusableInTouchMode = true
                requestFocus()
                postInvalidate()
                viewState.value = this
                onViewReady(this)
            }
        },
        update = { view ->
            viewState.value = view
            view.setTextSize(terminalTextSize)
            if (view.currentSession !== entry.session) {
                view.attachSession(entry.session)
            }
            view.postInvalidate()
        },
    )
    DisposableEffect(entry.id) {
        onDispose {
            viewState.value = null
            onViewDisposed()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TerminalExtraKeys(
    ctrlActive: Boolean,
    altActive: Boolean,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onKey: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            TerminalExtraKey("ESC", onClick = { onKey("\u001b") })
            TerminalExtraKey("/", onClick = { onKey("/") })
            TerminalExtraKey(
                label = "-",
                onClick = { onKey("-") },
                onLongClick = { onKey("|") },
            )
            TerminalExtraKey("HOME", onClick = { onKey("\u001b[H") })
            TerminalExtraKey("UP", onClick = { onKey("\u001b[A") })
            TerminalExtraKey("END", onClick = { onKey("\u001b[F") })
            TerminalExtraKey("PGUP", onClick = { onKey("\u001b[5~") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            TerminalExtraKey("TAB", onClick = { onKey("\t") })
            TerminalExtraKey("CTRL", selected = ctrlActive, onClick = onToggleCtrl)
            TerminalExtraKey("ALT", selected = altActive, onClick = onToggleAlt)
            TerminalExtraKey("LEFT", onClick = { onKey("\u001b[D") })
            TerminalExtraKey("DOWN", onClick = { onKey("\u001b[B") })
            TerminalExtraKey("RIGHT", onClick = { onKey("\u001b[C") })
            TerminalExtraKey("PGDN", onClick = { onKey("\u001b[6~") })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.TerminalExtraKey(
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .height(34.dp)
            .background(background, RoundedCornerShape(5.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            ),
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

private class NativeTerminalViewClient(
    private val onSingleTap: () -> Unit,
    private val onZoom: (Boolean) -> Unit,
    private val readControl: () -> Boolean,
    private val readAlt: () -> Boolean,
    private val onSwipe: (Int) -> Unit,
) : TerminalViewClient {
    override fun onScale(scale: Float): Float {
        if (scale < 0.9f || scale > 1.1f) {
            onZoom(scale > 1f)
            return 1f
        }
        return scale
    }

    override fun onSingleTapUp(e: MotionEvent) {
        onSingleTap()
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    override fun shouldEnforceCharBasedInput(): Boolean = false

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) = Unit

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession?): Boolean = false

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false

    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun onSessionSwipe(direction: Int) {
        onSwipe(direction)
    }

    override fun readControlKey(): Boolean = readControl()

    override fun readAltKey(): Boolean = readAlt()

    override fun readShiftKey(): Boolean = false

    override fun readFnKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean = false

    override fun onEmulatorSet() = Unit

    override fun logError(tag: String, message: String) = Unit

    override fun logWarn(tag: String, message: String) = Unit

    override fun logInfo(tag: String, message: String) = Unit

    override fun logDebug(tag: String, message: String) = Unit

    override fun logVerbose(tag: String, message: String) = Unit

    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) = Unit

    override fun logStackTrace(tag: String, e: Exception) = Unit
}

private fun openBrowser(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
