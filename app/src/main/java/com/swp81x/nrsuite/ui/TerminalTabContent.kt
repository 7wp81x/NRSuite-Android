package com.swp81x.nrsuite.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.swp81x.nrsuite.core.terminal.TerminalConfig
import com.swp81x.nrsuite.core.terminal.TerminalConnectionState
import com.swp81x.nrsuite.core.terminal.TerminalSessionEntry
import com.swp81x.nrsuite.core.terminal.TerminalShortcut
import com.swp81x.nrsuite.core.terminal.TerminalShortcutRunMode
import com.swp81x.nrsuite.core.terminal.TerminalUiSettings

@Composable
internal fun TerminalTabContent(
    connectionState: TerminalConnectionState,
    sessions: List<TerminalSessionEntry>,
    settings: TerminalUiSettings,
    hasPrivateKey: Boolean,
    shortcuts: List<TerminalShortcut>,
    categories: List<String>,
    showLauncher: Boolean,
    sessionToOpen: Int?,
    initialHost: String,
    initialPort: Int,
    initialUsername: String,
    onShowLauncher: (Boolean) -> Unit,
    onOpenSession: (Int) -> Unit,
    onConnect: (TerminalConfig) -> Unit,
    onDisconnect: () -> Unit,
    onNewSession: () -> Unit,
    onCloseSession: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onActiveSessionChanged: (Int) -> Unit,
    onUpdateSettings: (TerminalUiSettings) -> Unit,
    onSetUpdateListener: ((() -> Unit)?) -> Unit,
    onSetColorScheme: (IntArray, Int, Int, Int) -> Unit,
    onClearPinnedHostKey: () -> Unit,
    onRunShortcut: (TerminalShortcut) -> Unit,
    onUpsertShortcut: (TerminalShortcut) -> Unit,
    onDeleteShortcut: (Long) -> Unit,
    onAddCategory: (String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    if (showLauncher) {
        TerminalLauncherScreen(
            connectionState = connectionState,
            sessions = sessions,
            shortcuts = shortcuts,
            categories = categories,
            onOpenTerminal = { onShowLauncher(false) },
            onOpenSession = onOpenSession,
            onOpenSettings = onOpenSettings,
            onRunShortcut = { shortcut ->
                if (connectionState !is TerminalConnectionState.Connected) {
                    Toast.makeText(
                        context,
                        "Connect to Termux before running shortcuts.",
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    onRunShortcut(shortcut)
                    if (shortcut.runMode != TerminalShortcutRunMode.BACKGROUND) {
                        onShowLauncher(false)
                    }
                }
            },
            onUpsertShortcut = onUpsertShortcut,
            onDeleteShortcut = onDeleteShortcut,
            onAddCategory = onAddCategory,
            onDeleteCategory = onDeleteCategory,
            modifier = modifier,
        )
    } else {
        TerminalScreen(
            connectionState = connectionState,
            terminalSessions = sessions,
            terminalSettings = settings,
            hasPrivateKey = hasPrivateKey,
            initialSessionId = sessionToOpen,
            initialHost = initialHost,
            initialPort = initialPort,
            initialUsername = initialUsername,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
            onNewSession = onNewSession,
            onCloseSession = onCloseSession,
            onOpenLauncher = { onShowLauncher(true) },
            onOpenSettings = onOpenSettings,
            onActiveSessionChanged = onActiveSessionChanged,
            onUpdateSettings = onUpdateSettings,
            onSetUpdateListener = onSetUpdateListener,
            onSetColorScheme = onSetColorScheme,
            onClearPinnedHostKey = {
                onClearPinnedHostKey()
                Toast.makeText(
                    context,
                    "Pinned SSH host key cleared. Connect again to trust the new key.",
                    Toast.LENGTH_LONG,
                ).show()
            },
            modifier = modifier,
        )
    }
}
