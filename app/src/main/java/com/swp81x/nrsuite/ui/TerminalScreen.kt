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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.swp81x.nrsuite.core.terminal.DEFAULT_TERMINAL_PORT
import com.swp81x.nrsuite.core.terminal.TERMUX_PACKAGE_NAME
import com.swp81x.nrsuite.core.terminal.TerminalConfig
import com.swp81x.nrsuite.core.terminal.TerminalHostKeyMismatch
import com.swp81x.nrsuite.core.terminal.TerminalKeyInfo
import com.swp81x.nrsuite.core.terminal.TerminalConnectionState
import com.swp81x.nrsuite.core.terminal.TerminalSessionEntry
import com.swp81x.nrsuite.core.terminal.TerminalUiSettings
import com.swp81x.nrsuite.ui.components.NrDangerButton
import com.swp81x.nrsuite.ui.components.NrPrimaryButton
import com.swp81x.nrsuite.ui.components.NrQuietButton
import com.swp81x.nrsuite.ui.components.NrSecondaryButton
import com.swp81x.nrsuite.ui.theme.NrTheme
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusRed
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.launch

private const val TERMUX_DOWNLOAD_URL = "https://github.com/termux/termux-app/releases/tag/v0.118.2"
private val terminalSetupCommands =
    "pkg update -y && pkg upgrade -y ; pkg install openssh termux-services -y ; " +
        "termux-setup-storage ; mkdir -p ~/.ssh ; rm -f ~/.ssh/nrsuite ~/.ssh/nrsuite.pub ; " +
        "ssh-keygen -t ed25519 -f ~/.ssh/nrsuite -N '' -C nrsuite-app ; " +
        "touch ~/.ssh/authorized_keys ; sed -i '/nrsuite-app/d' ~/.ssh/authorized_keys ; " +
        "printf 'from=\"127.0.0.1,::1\",no-port-forwarding,no-agent-forwarding," +
        "no-X11-forwarding %s\\n' \"$(cat ~/.ssh/nrsuite.pub)\" >> ~/.ssh/authorized_keys ; " +
        "chmod 700 ~/.ssh ; chmod 600 ~/.ssh/authorized_keys ; " +
        "mkdir -p ~/storage/shared/NRSuite ; " +
        "cp ~/.ssh/nrsuite ~/storage/shared/NRSuite/nrsuite_ssh_key ; " +
        "cp ~/.ssh/nrsuite.pub ~/storage/shared/NRSuite/nrsuite_ssh_key.pub ; " +
        "sv-enable sshd ; sv up sshd"

@Composable
internal fun TerminalScreen(
    connectionState: TerminalConnectionState,
    terminalSessions: List<TerminalSessionEntry>,
    terminalSettings: TerminalUiSettings,
    hasPrivateKey: Boolean,
    keyInfo: TerminalKeyInfo?,
    initialSessionId: Int? = null,
    initialHost: String,
    initialPort: Int,
    initialUsername: String,
    onConnect: (TerminalConfig) -> Unit,
    onDisconnect: () -> Unit,
    onForceReconnect: () -> Unit,
    onNewSession: () -> Unit,
    onCloseSession: (Int) -> Unit,
    onOpenLauncher: () -> Unit,
    onOpenSettings: () -> Unit,
    onActiveSessionChanged: (Int) -> Unit,
    onUpdateSettings: (TerminalUiSettings) -> Unit,
    onSetUpdateListener: ((() -> Unit)?) -> Unit,
    onSetColorScheme: (IntArray, Int, Int, Int) -> Unit,
    onClearPinnedHostKey: () -> Unit,
    onTrustChangedHostKey: () -> Unit,
    onImportKeyFromRoot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var host by rememberSaveable { mutableStateOf(initialHost) }
    var port by rememberSaveable { mutableStateOf(initialPort.toString()) }
    var username by rememberSaveable { mutableStateOf(initialUsername) }
    var refreshCount by remember { mutableIntStateOf(0) }
    val termuxInstalled = remember(refreshCount) {
        runCatching {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE_NAME, 0)
        }.isSuccess
    }

    if (connectionState is TerminalConnectionState.Connecting &&
        terminalSettings.autoconnect &&
        hasPrivateKey
    ) {
        TerminalConnectingScreen(
            host = host,
            port = port,
            username = username,
            keyInfo = keyInfo,
            terminalTextSize = terminalSettings.defaultFontSize,
            onCancel = onDisconnect,
            modifier = modifier,
        )
        return
    }

    when (connectionState) {
        is TerminalConnectionState.Connected -> {
            if (terminalSessions.isNotEmpty()) {
                ConnectedTerminal(
                    state = connectionState,
                    terminalSessions = terminalSessions,
                    terminalSettings = terminalSettings,
                    initialSessionId = initialSessionId,
                    onDisconnect = onDisconnect,
                    onForceReconnect = onForceReconnect,
                    onNewSession = onNewSession,
                    onCloseSession = onCloseSession,
                    onOpenLauncher = onOpenLauncher,
                    onOpenSettings = onOpenSettings,
                    onActiveSessionChanged = onActiveSessionChanged,
                    onUpdateSettings = onUpdateSettings,
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
                hasPrivateKey = hasPrivateKey,
                keyInfo = keyInfo,
                termuxInstalled = termuxInstalled,
                onRefreshTermux = { refreshCount++ },
                onConnect = {
                    onConnect(
                        TerminalConfig(
                            host = host,
                            port = port.toIntOrNull() ?: DEFAULT_TERMINAL_PORT,
                            username = username,
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
                        openBrowser(context, TERMUX_DOWNLOAD_URL)
                    }
                },
                onInstallTermux = { openBrowser(context, TERMUX_DOWNLOAD_URL) },
                onCopySetup = {
                    clipboard.setText(AnnotatedString(terminalSetupCommands))
                },
                onClearPinnedHostKey = onClearPinnedHostKey,
                onTrustChangedHostKey = onTrustChangedHostKey,
                onOpenSettings = onOpenSettings,
                onImportKeyFromRoot = onImportKeyFromRoot,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun TerminalConnectingScreen(
    host: String,
    port: String,
    username: String,
    keyInfo: TerminalKeyInfo?,
    terminalTextSize: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusView = remember { mutableStateOf<TerminalView?>(null) }
    val statusSessionClient = remember {
        connectingTerminalSessionClient {
            val view = statusView.value
            view?.post { view.onScreenUpdated() }
        }
    }
    val statusSession = remember(host, port, username, keyInfo?.fingerprint, statusSessionClient) {
        TerminalSession(statusSessionClient)
    }
    val viewClient = remember {
        NativeTerminalViewClient(
            onSingleTap = {},
            onZoom = {},
            readControl = { false },
            readAlt = { false },
            onSwipe = {},
        )
    }
    LaunchedEffect(statusSession, host, port, username, keyInfo) {
        val statusText = buildString {
            append("\r\n")
            append("\u001b[1;36mTermux SSH\u001b[0m\r\n\r\n")
            append("Host:   $host\r\n")
            append("Port:   $port\r\n")
            append("User:   $username\r\n")
            keyInfo?.let { info ->
                append("Key:    ${info.name}\r\n")
                append("SHA256: ${info.fingerprint.removePrefix("SHA256:")}\r\n")
            }
            append("\r\n")
            append("\u001b[33mConnecting...\u001b[0m\r\n")
            append("The terminal will open automatically when the SSH connection is ready.\r\n")
        }
        val bytes = statusText.toByteArray(Charsets.UTF_8)
        statusSession.feedOutput(bytes, 0, bytes.size)
    }
    DisposableEffect(statusSession) {
        onDispose { statusSession.finishIfRunning() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                TerminalView(viewContext, null).apply {
                    statusView.value = this
                    setTextSize(terminalTextSize)
                    setTerminalViewClient(viewClient)
                    attachSession(statusSession)
                    setTerminalCursorBlinkerState(false, false)
                    isFocusable = false
                    isFocusableInTouchMode = false
                    isClickable = false
                    postInvalidate()
                }
            },
            update = { view ->
                statusView.value = view
                view.setTextSize(terminalTextSize)
                view.setTerminalViewClient(viewClient)
                if (view.currentSession !== statusSession) {
                    view.attachSession(statusSession)
                }
                view.setTerminalCursorBlinkerState(false, false)
                view.postInvalidate()
            },
        )
        TextButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        ) {
            Text("Cancel")
        }
    }
}

private fun connectingTerminalSessionClient(
    onTextChanged: () -> Unit,
): TerminalSessionClient = object : TerminalSessionClient {
    override fun onTextChanged(changedSession: TerminalSession) = onTextChanged()
    override fun onTitleChanged(changedSession: TerminalSession) = Unit
    override fun onSessionFinished(finishedSession: TerminalSession) = Unit
    override fun onCopyTextToClipboard(session: TerminalSession, text: String) = Unit
    override fun onPasteTextFromClipboard(session: TerminalSession?) = Unit
    override fun onBell(session: TerminalSession) = Unit
    override fun onColorsChanged(session: TerminalSession) = Unit
    override fun onTerminalCursorStateChange(state: Boolean) = Unit
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) = Unit
    override fun getTerminalCursorStyle(): Int? = 0
    override fun logError(tag: String, message: String) = Unit
    override fun logWarn(tag: String, message: String) = Unit
    override fun logInfo(tag: String, message: String) = Unit
    override fun logDebug(tag: String, message: String) = Unit
    override fun logVerbose(tag: String, message: String) = Unit
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) = Unit
    override fun logStackTrace(tag: String, e: Exception) = Unit
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
    hasPrivateKey: Boolean,
    keyInfo: TerminalKeyInfo?,
    termuxInstalled: Boolean,
    onRefreshTermux: () -> Unit,
    onConnect: () -> Unit,
    onOpenTermux: () -> Unit,
    onInstallTermux: () -> Unit,
    onCopySetup: () -> Unit,
    onClearPinnedHostKey: () -> Unit,
    onTrustChangedHostKey: () -> Unit,
    onOpenSettings: () -> Unit,
    onImportKeyFromRoot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    var dismissedHostKeyFingerprint by rememberSaveable { mutableStateOf<String?>(null) }
    val hostKeyMismatch = (state as? TerminalConnectionState.Failed)?.hostKeyMismatch
    if (hostKeyMismatch != null &&
        dismissedHostKeyFingerprint != hostKeyMismatch.newFingerprint
    ) {
        HostKeyMismatchDialog(
            mismatch = hostKeyMismatch,
            onDismiss = {
                dismissedHostKeyFingerprint = hostKeyMismatch.newFingerprint
            },
            onTrust = {
                dismissedHostKeyFingerprint = hostKeyMismatch.newFingerprint
                onTrustChangedHostKey()
            },
        )
    }
    val openSettings = {
        focusManager.clearFocus()
        softwareKeyboardController?.hide()
        onOpenSettings()
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
                            text = "Termux SSH",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    NrQuietButton(
                        text = "Recheck",
                        onClick = onRefreshTermux,
                        leadingIcon = Icons.Default.Refresh,
                        compact = true,
                    )
                    IconButton(onClick = openSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Terminal Settings")
                    }
                }

                Text(
                    text = if (termuxInstalled) "Termux installed" else "Termux not installed",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (termuxInstalled) StatusGreen else StatusAmber,
                )
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
                            text = "Download Termux",
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
                    text = "Run this once in Termux, approve storage access, then import the key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                if (state is TerminalConnectionState.Disconnected && state.message.isNullOrBlank()) {
                    Text(
                        text = "Setup required. Configure Termux and connect below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusAmber,
                    )
                }
                if (state is TerminalConnectionState.Failed) {
                    Text(
                        text = "Connection failed. Check Termux setup and try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusRed,
                    )
                }
                Text(
                    text = "SSH user: $username",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (hasPrivateKey && keyInfo != null) {
                    Text(
                        text = "SSH key: ${keyInfo.name}",
                        style = MaterialTheme.typography.labelMedium,
                        color = StatusGreen,
                    )
                    Text(
                        text = keyInfo.fingerprint,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = "No SSH key loaded. The app checks the NRSuite root automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusAmber,
                    )
                }
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
                    if (state.hostKeyMismatch != null) {
                        NrQuietButton(
                            text = "Review changed SSH host key",
                            onClick = {
                                dismissedHostKeyFingerprint = null
                            },
                            compact = true,
                        )
                    }
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

                NrQuietButton(
                    text = "Check for SSH key again",
                    onClick = onImportKeyFromRoot,
                    leadingIcon = Icons.Default.Refresh,
                    compact = true,
                )
                NrPrimaryButton(
                    text = "Connect to Termux",
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = username.isNotBlank() && hasPrivateKey &&
                        host.isNotBlank() && port.isNotBlank() &&
                        state !is TerminalConnectionState.Connecting,
                )
                Text(
                    text = "Private key auth only. The key is stored encrypted on this device.",
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
private fun HostKeyMismatchDialog(
    mismatch: TerminalHostKeyMismatch,
    onDismiss: () -> Unit,
    onTrust: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SSH host key changed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "The SSH server presented a different host key than the one pinned " +
                        "for this device. This can happen after reinstalling Termux, but " +
                        "it can also indicate an interception attempt.",
                )
                Text(
                    text = "${mismatch.host}:${mismatch.port}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                )
                mismatch.oldFingerprint?.let { oldFingerprint ->
                    Text(
                        text = "Pinned: $oldFingerprint",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "New: ${mismatch.newFingerprint}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = StatusAmber,
                )
                Text(
                    text = "Only trust the new key if you intentionally reinstalled Termux " +
                        "or changed its SSH host key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            NrPrimaryButton(
                text = "Trust and reconnect",
                onClick = onTrust,
            )
        },
        dismissButton = {
            NrQuietButton(text = "Cancel", onClick = onDismiss)
        },
    )
}

@Composable
private fun ConnectedTerminal(
    state: TerminalConnectionState.Connected,
    terminalSessions: List<TerminalSessionEntry>,
    terminalSettings: TerminalUiSettings,
    initialSessionId: Int?,
    onDisconnect: () -> Unit,
    onForceReconnect: () -> Unit,
    onNewSession: () -> Unit,
    onCloseSession: (Int) -> Unit,
    onOpenLauncher: () -> Unit,
    onOpenSettings: () -> Unit,
    onActiveSessionChanged: (Int) -> Unit,
    onUpdateSettings: (TerminalUiSettings) -> Unit,
    onSetUpdateListener: ((() -> Unit)?) -> Unit,
    onSetColorScheme: (IntArray, Int, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val colorScheme = MaterialTheme.colorScheme
    val nrColors = NrTheme.colors
    val terminalViews = remember { mutableStateMapOf<Int, TerminalView>() }
    val pagerState = rememberPagerState(pageCount = { terminalSessions.size })
    val coroutineScope = rememberCoroutineScope()
    var terminalMenuExpanded by remember { mutableStateOf(false) }
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }
    val terminalTextSize = terminalSettings.defaultFontSize
    val activeSession = terminalSessions.getOrNull(
        pagerState.currentPage.coerceIn(0, terminalSessions.lastIndex),
    ) ?: terminalSessions.first()
    var previousSessionCount by remember { mutableIntStateOf(terminalSessions.size) }
    LaunchedEffect(initialSessionId, terminalSessions.size) {
        val index = terminalSessions.indexOfFirst { it.id == initialSessionId }
        if (index >= 0) pagerState.animateScrollToPage(index)
    }
    val inputMethodManager = remember(context) {
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    }
    fun focusActiveTerminal(showIme: Boolean) {
        val activeView = terminalViews[activeSession.id] ?: return
        terminalViews.values.forEach { view ->
            if (view !== activeView) view.clearFocus()
        }
        activeView.requestFocusFromTouch()
        activeView.post {
            activeView.requestFocus()
            activeView.onScreenUpdated()
            inputMethodManager?.restartInput(activeView)
            if (showIme) {
                inputMethodManager?.showSoftInput(
                    activeView,
                    InputMethodManager.SHOW_IMPLICIT,
                )
            }
        }
    }
    LaunchedEffect(pagerState.currentPage, terminalSessions, imeVisible) {
        activeSession?.let { onActiveSessionChanged(it.id) }
        focusActiveTerminal(showIme = imeVisible)
    }
    LaunchedEffect(terminalSessions.size) {
        if (terminalSessions.size > previousSessionCount && terminalSessions.isNotEmpty()) {
            pagerState.animateScrollToPage(terminalSessions.lastIndex)
        }
        previousSessionCount = terminalSessions.size
    }

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
        focusActiveTerminal(showIme = true)
    }
    val hideKeyboard: () -> Unit = {
        val activeView = terminalViews[activeSession.id]
        activeView?.clearFocus()
        inputMethodManager?.hideSoftInputFromWindow(activeView?.windowToken, 0)
    }
    val openSettings: () -> Unit = {
        hideKeyboard()
        onOpenSettings()
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
                        text = { Text("New tab") },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                        onClick = {
                            terminalMenuExpanded = false
                            onNewSession()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Close current tab") },
                        leadingIcon = {
                            Icon(Icons.Default.Close, contentDescription = null)
                        },
                        enabled = terminalSessions.size > 1,
                        onClick = {
                            terminalMenuExpanded = false
                            onCloseSession(activeSession.id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Terminal Launcher") },
                        leadingIcon = { Icon(Icons.Default.GridView, contentDescription = null) },
                        onClick = {
                            terminalMenuExpanded = false
                            onOpenLauncher()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Terminal Settings") },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        onClick = {
                            terminalMenuExpanded = false
                            openSettings()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Force reconnect") },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        onClick = {
                            terminalMenuExpanded = false
                            onForceReconnect()
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Disconnect SSH") },
                        leadingIcon = { Icon(Icons.Default.LinkOff, contentDescription = null) },
                        onClick = {
                            terminalMenuExpanded = false
                            onDisconnect()
                        },
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenLauncher) {
                Icon(Icons.Default.GridView, contentDescription = "Terminal Launcher")
            }
            IconButton(
                onClick = { if (imeVisible) hideKeyboard() else showKeyboard() },
            ) {
                Icon(
                    imageVector = if (imeVisible) {
                        Icons.Default.KeyboardArrowDown
                    } else {
                        Icons.Default.Keyboard
                    },
                    contentDescription = if (imeVisible) "Hide keyboard" else "Show keyboard",
                    tint = if (imeVisible) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(onClick = openSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Terminal Settings")
            }
            IconButton(onClick = onNewSession) {
                Icon(Icons.Default.Add, contentDescription = "New tab")
            }
        }

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            val entry = terminalSessions[page]
            NativeTerminalPage(
                entry = entry,
                isActive = page == pagerState.currentPage,
                terminalTextSize = terminalTextSize,
                cursorBlinkMs = terminalSettings.cursorBlinkMs,
                onViewReady = { view ->
                    terminalViews[entry.id] = view
                    if (entry.id == activeSession.id) {
                        view.post {
                            view.requestFocus()
                            view.requestFocusFromTouch()
                        }
                    }
                },
                onViewDisposed = { terminalViews.remove(entry.id) },
                onShowKeyboard = showKeyboard,
                onZoom = { increase ->
                    onUpdateSettings(
                        terminalSettings.copy(
                            defaultFontSize = (terminalTextSize + if (increase) 1 else -1)
                                .coerceIn(9, 34),
                        ),
                    )
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

        if (imeVisible) {
            TerminalExtraKeys(
                modifier = Modifier,
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
}

@Composable
private fun NativeTerminalPage(
    entry: TerminalSessionEntry,
    isActive: Boolean,
    terminalTextSize: Int,
    cursorBlinkMs: Int,
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
    val latestShowKeyboard by rememberUpdatedState(onShowKeyboard)
    val latestZoom by rememberUpdatedState(onZoom)
    val latestCtrlRead by rememberUpdatedState(onCtrlRead)
    val latestAltRead by rememberUpdatedState(onAltRead)
    val latestSwipe by rememberUpdatedState(onSwipe)
    val client = remember {
        NativeTerminalViewClient(
            onSingleTap = { latestShowKeyboard() },
            onZoom = { latestZoom(it) },
            readControl = { latestCtrlRead() },
            readAlt = { latestAltRead() },
            onSwipe = { latestSwipe(it) },
        )
    }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TerminalView(viewContext, null).apply {
                setTextSize(terminalTextSize)
                setTerminalViewClient(client)
                setTerminalCursorBlinkerRate(cursorBlinkMs)
                attachSession(entry.session)
                setTerminalCursorBlinkerState(true, true)
                isFocusable = true
                isFocusableInTouchMode = true
                if (isActive) {
                    post {
                        requestFocus()
                        requestFocusFromTouch()
                    }
                }
                postInvalidate()
                viewState.value = this
                onViewReady(this)
            }
        },
        update = { view ->
            viewState.value = view
            view.setTextSize(terminalTextSize)
            view.setTerminalViewClient(client)
            view.setTerminalCursorBlinkerRate(cursorBlinkMs)
            if (view.currentSession !== entry.session) {
                view.attachSession(entry.session)
            }
            if (isActive && !view.hasFocus()) {
                view.post {
                    view.requestFocus()
                    view.requestFocusFromTouch()
                }
            }
            view.setTerminalCursorBlinkerState(true, true)
            view.postInvalidate()
        },
    )
    DisposableEffect(entry.id) {
        onDispose {
            viewState.value?.setTerminalCursorBlinkerState(false, false)
            viewState.value = null
            onViewDisposed()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TerminalExtraKeys(
    modifier: Modifier = Modifier,
    ctrlActive: Boolean,
    altActive: Boolean,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onKey: (String) -> Unit,
) {
    Column(
        modifier = modifier
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
            TerminalExtraKey("↑", onClick = { onKey("\u001b[A") })
            TerminalExtraKey("END", onClick = { onKey("\u001b[F") })
            TerminalExtraKey("PGUP", onClick = { onKey("\u001b[5~") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            TerminalExtraKey("TAB", onClick = { onKey("\t") })
            TerminalExtraKey("CTRL", selected = ctrlActive, onClick = onToggleCtrl)
            TerminalExtraKey("ALT", selected = altActive, onClick = onToggleAlt)
            TerminalExtraKey("←", onClick = { onKey("\u001b[D") })
            TerminalExtraKey("↓", onClick = { onKey("\u001b[B") })
            TerminalExtraKey("→", onClick = { onKey("\u001b[C") })
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
        if (scale < 0.97f || scale > 1.03f) {
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

    override fun onDoubleTap(): Boolean {
        onSingleTap()
        return true
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
