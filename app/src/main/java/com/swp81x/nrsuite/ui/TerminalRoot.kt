package com.swp81x.nrsuite.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.swp81x.nrsuite.MainViewModel

@Composable
internal fun TerminalRoot(
    viewModel: MainViewModel,
    showLauncher: Boolean,
    sessionToOpen: Int?,
    onShowLauncher: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val connectionState by viewModel.terminalState.collectAsState()
    val sessions by viewModel.terminalSessions.collectAsState()
    val settings by viewModel.terminalSettings.collectAsState()
    val hasPrivateKey by viewModel.terminalHasPrivateKey.collectAsState()
    val keyInfo by viewModel.terminalKeyInfo.collectAsState()
    val shortcuts by viewModel.terminalShortcuts.collectAsState()
    val categories by viewModel.terminalShortcutCategories.collectAsState()
    LaunchedEffect(connectionState, hasPrivateKey) {
        if (connectionState !is com.swp81x.nrsuite.core.terminal.TerminalConnectionState.Connected &&
            !hasPrivateKey
        ) {
            while (isActive) {
                viewModel.importTerminalPrivateKeyFromRoot { success, message ->
                    if (success) Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
                delay(2_500)
            }
        }
    }

    TerminalTabContent(
        connectionState = connectionState,
        sessions = sessions,
        settings = settings,
        hasPrivateKey = hasPrivateKey,
        keyInfo = keyInfo,
        shortcuts = shortcuts,
        categories = categories,
        showLauncher = showLauncher,
        sessionToOpen = sessionToOpen,
        initialHost = viewModel.savedTerminalHost(),
        initialPort = viewModel.savedTerminalPort(),
        initialUsername = viewModel.savedTerminalUsername(),
        onShowLauncher = onShowLauncher,
        onOpenSession = { id ->
            onShowLauncher(false)
            viewModel.setActiveTerminalSession(id)
        },
        onConnect = viewModel::connectTerminal,
        onDisconnect = { viewModel.disconnectTerminal() },
        onNewSession = viewModel::newTerminalSession,
        onCloseSession = viewModel::closeTerminalSession,
        onOpenSettings = onOpenSettings,
        onActiveSessionChanged = viewModel::setActiveTerminalSession,
        onUpdateSettings = viewModel::updateTerminalSettings,
        onSetUpdateListener = viewModel::setTerminalViewUpdateListener,
        onSetColorScheme = viewModel::setTerminalColorScheme,
        onClearPinnedHostKey = {
            viewModel.clearTerminalPinnedHostKey()
            Toast.makeText(
                context,
                "Pinned SSH host key cleared.",
                Toast.LENGTH_SHORT,
            ).show()
        },
        onImportKeyFromRoot = {
            viewModel.importTerminalPrivateKeyFromRoot { success, message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        },
        onRunShortcut = { shortcut ->
            viewModel.runTerminalShortcutCommand(
                command = shortcut.command,
                runMode = shortcut.runMode,
                execute = true,
            )
        },
        onUpsertShortcut = viewModel::upsertTerminalShortcut,
        onDeleteShortcut = viewModel::deleteTerminalShortcut,
        onAddCategory = viewModel::addTerminalShortcutCategory,
        onDeleteCategory = viewModel::deleteTerminalShortcutCategory,
        onRenameCategory = viewModel::renameTerminalShortcutCategory,
        modifier = modifier,
    )
}

@Composable
internal fun TerminalSettingsRoot(
    viewModel: MainViewModel,
    onManageShortcuts: () -> Unit,
    onClearPinnedHostKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.terminalSettings.collectAsState()
    val hasPrivateKey by viewModel.terminalHasPrivateKey.collectAsState()
    TerminalSettingsRoute(
        settings = settings,
        hasPrivateKey = hasPrivateKey,
        onUpdateSettings = viewModel::updateTerminalSettings,
        onManageShortcuts = onManageShortcuts,
        onImportPrivateKey = viewModel::importTerminalPrivateKey,
        onClearPrivateKey = viewModel::clearTerminalPrivateKey,
        onClearPinnedHostKey = onClearPinnedHostKey,
        modifier = modifier,
    )
}
