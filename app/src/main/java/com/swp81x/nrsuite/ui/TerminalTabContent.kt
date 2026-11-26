package com.swp81x.nrsuite.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.swp81x.nrsuite.core.terminal.TerminalConfig
import com.swp81x.nrsuite.core.terminal.TerminalConnectionState
import com.swp81x.nrsuite.core.terminal.TerminalKeyInfo
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
    keyInfo: TerminalKeyInfo?,
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
    onForceReconnect: () -> Unit,
    onNewSession: () -> Unit,
    onCloseSession: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onActiveSessionChanged: (Int) -> Unit,
    onUpdateSettings: (TerminalUiSettings) -> Unit,
    onSetUpdateListener: ((() -> Unit)?) -> Unit,
    onSetColorScheme: (IntArray, Int, Int, Int) -> Unit,
    onClearPinnedHostKey: () -> Unit,
    onTrustChangedHostKey: () -> Unit,
    onImportKeyFromRoot: () -> Unit,
    onRunShortcut: (TerminalShortcut) -> Unit,
    onUpsertShortcut: (TerminalShortcut) -> Unit,
    onDeleteShortcut: (Long) -> Unit,
    onAddCategory: (String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onRenameCategory: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val connected = connectionState is TerminalConnectionState.Connected
    var showShortcutManager by rememberSaveable { mutableStateOf(false) }
    var createNewShortcut by rememberSaveable { mutableStateOf(false) }

    if (showShortcutManager) {
        TerminalLauncherScreen(
            shortcuts = shortcuts,
            categories = categories,
            onClose = {
                showShortcutManager = false
                createNewShortcut = false
            },
            onUpsertShortcut = onUpsertShortcut,
            onDeleteShortcut = onDeleteShortcut,
            onAddCategory = onAddCategory,
            onDeleteCategory = onDeleteCategory,
            onRenameCategory = onRenameCategory,
            initialCreateShortcut = createNewShortcut,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            TerminalScreen(
                connectionState = connectionState,
                terminalSessions = sessions,
                terminalSettings = settings,
                hasPrivateKey = hasPrivateKey,
                keyInfo = keyInfo,
                initialSessionId = sessionToOpen,
                initialHost = initialHost,
                initialPort = initialPort,
                initialUsername = initialUsername,
                onConnect = onConnect,
                onDisconnect = onDisconnect,
                onForceReconnect = onForceReconnect,
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
                onTrustChangedHostKey = onTrustChangedHostKey,
                onImportKeyFromRoot = onImportKeyFromRoot,
                modifier = Modifier.fillMaxSize(),
            )

            if (showLauncher && connected) {
                TerminalShortcutQuickSheet(
                    shortcuts = shortcuts,
                    categories = categories,
                    onDismiss = { onShowLauncher(false) },
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
                    onAdd = {
                        createNewShortcut = true
                        showShortcutManager = true
                        onShowLauncher(false)
                    },
                    onManage = {
                        createNewShortcut = false
                        showShortcutManager = true
                        onShowLauncher(false)
                    },
                    onOpenSettings = {
                        onShowLauncher(false)
                        onOpenSettings()
                    },
                )
            }
        }
    }
}
