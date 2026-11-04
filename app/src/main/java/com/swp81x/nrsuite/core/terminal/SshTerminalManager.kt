package com.swp81x.nrsuite.core.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.terminal.TextStyle
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.swp81x.nrsuite.core.notifications.NrSuiteNotifications

data class TerminalConfig(
    val host: String = DEFAULT_TERMINAL_HOST,
    val port: Int = DEFAULT_TERMINAL_PORT,
    val username: String = "",
    val password: String = "",
)

data class TerminalSessionEntry(
    val id: Int,
    val title: String,
    val session: TerminalSession,
)

sealed interface TerminalConnectionState {
    data class Disconnected(val message: String? = null) : TerminalConnectionState
    data object Connecting : TerminalConnectionState
    data class Connected(val host: String, val port: Int, val username: String) :
        TerminalConnectionState
    data class Failed(val message: String) : TerminalConnectionState
}

const val TERMUX_PACKAGE_NAME = "com.termux"
const val DEFAULT_TERMINAL_HOST = "127.0.0.1"
const val DEFAULT_TERMINAL_PORT = 8022

internal class SshTerminalManager(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("nrsuite_terminal", Context.MODE_PRIVATE)
    private val terminalPreferences = TerminalPreferences(context)
    private val _settings = MutableStateFlow(terminalPreferences.load())
    val settings: StateFlow<TerminalUiSettings> = _settings.asStateFlow()
    private val _hasPrivateKey = MutableStateFlow(terminalPreferences.hasPrivateKey())
    val hasPrivateKey: StateFlow<Boolean> = _hasPrivateKey.asStateFlow()
    private val _keyInfo = MutableStateFlow(buildKeyInfo())
    val keyInfo: StateFlow<TerminalKeyInfo?> = _keyInfo.asStateFlow()
    private val writeMutex = Mutex()
    private val pinnedHostKeys = PinnedHostKeyRepository(preferences)
    private val managedSessions = linkedMapOf<Int, ManagedTerminalSession>()
    private var nextSessionId = 1
    private var activeSessionId: Int? = null

    @Volatile
    private var sshSession: Session? = null

    @Volatile
    private var screenUpdateListener: (() -> Unit)? = null

    private val _state = MutableStateFlow<TerminalConnectionState>(
        TerminalConnectionState.Disconnected(),
    )
    val state: StateFlow<TerminalConnectionState> = _state.asStateFlow()

    private val _sessions = MutableStateFlow<List<TerminalSessionEntry>>(emptyList())
    val sessions: StateFlow<List<TerminalSessionEntry>> = _sessions.asStateFlow()

    fun setTerminalViewUpdateListener(listener: (() -> Unit)?) {
        screenUpdateListener = listener
    }

    fun savedHost(): String = preferences.getString(KEY_HOST, DEFAULT_TERMINAL_HOST)
        ?: DEFAULT_TERMINAL_HOST

    fun savedPort(): Int = preferences.getInt(KEY_PORT, DEFAULT_TERMINAL_PORT)
        .coerceIn(1, 65535)

    fun savedUsername(): String = preferences.getString(KEY_USERNAME, "").orEmpty()

    fun termuxSshUsername(): String {
        return runCatching {
            val appInfo = context.packageManager.getApplicationInfo(TERMUX_PACKAGE_NAME, 0)
            val uid = appInfo.uid
            val userId = uid / 100_000
            val appId = uid % 100_000
            "u${userId}_a${appId - 10_000}"
        }.getOrDefault("nrsuite")
    }

    fun savedConnectionConfig(): TerminalConfig? {
        val username = termuxSshUsername()
        val password = terminalPreferences.loadPassword().orEmpty()
        if (username.isBlank() || (password.isBlank() && !terminalPreferences.hasPrivateKey())) {
            return null
        }
        return TerminalConfig(
            host = savedHost(),
            port = savedPort(),
            username = username,
            password = password,
        )
    }

    fun importPrivateKey(
        uri: Uri,
        publicKeyUri: Uri? = null,
        passphrase: String,
        deleteAfterImport: Boolean = false,
        onResult: (Boolean, String) -> Unit = { _, _ -> },
    ) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Could not read the selected private key.")
                    val publicBytes = publicKeyUri?.let { keyUri ->
                        context.contentResolver.openInputStream(keyUri)?.use { it.readBytes() }
                    }
                    // Parse the key with JSch before persisting it so malformed or
                    // wrong-passphrase keys fail loudly instead of breaking autoconnect.
                    JSch().addIdentity(
                        "nrsuite-key-validation",
                        bytes,
                        publicBytes,
                        passphrase.toByteArray(Charsets.UTF_8),
                    )
                    terminalPreferences.savePrivateKey(
                        "Termux SSH key",
                        bytes,
                        publicBytes,
                        passphrase,
                    )
                    if (deleteAfterImport) {
                        runCatching { context.contentResolver.delete(uri, null, null) }
                        publicKeyUri?.let { keyUri ->
                            runCatching { context.contentResolver.delete(keyUri, null, null) }
                        }
                    }
                }
            }.onSuccess {
                _hasPrivateKey.value = true
                _keyInfo.value = buildKeyInfo()
                onResult(true, "SSH key imported and validated.")
            }.onFailure { error ->
                Log.e("NRSuiteTerminal", "Private key import failed", error)
                onResult(false, "SSH key import failed: ${error.message ?: "unknown error"}")
            }
        }
    }

    private fun buildKeyInfo(): TerminalKeyInfo? {
        val key = terminalPreferences.loadPrivateKey() ?: return null
        val material = key.publicBytes ?: key.bytes
        val digest = MessageDigest.getInstance("SHA-256").digest(material)
        val fingerprint = "SHA256:" + Base64.encodeToString(
            digest,
            Base64.NO_WRAP or Base64.NO_PADDING,
        )
        val publicText = key.publicBytes?.toString(Charsets.UTF_8)?.trim()
        return TerminalKeyInfo(
            name = key.name,
            fingerprint = fingerprint,
            publicKeyText = publicText,
        )
    }

    fun clearPrivateKey() {
        terminalPreferences.clearPrivateKey()
        _hasPrivateKey.value = false
        _keyInfo.value = null
    }

    fun updateSettings(settings: TerminalUiSettings) {
        terminalPreferences.save(settings)
        _settings.value = settings
        managedSessions.values.forEach { managed ->
            managed.terminalSession.getEmulator()?.setCursorStyle()
        }
        screenUpdateListener?.invoke()
    }

    fun saveConnectionPreferences(config: TerminalConfig) {
        preferences.edit()
            .putString(KEY_HOST, config.host.trim())
            .putInt(KEY_PORT, config.port.coerceIn(1, 65535))
            .putString(KEY_USERNAME, config.username.trim())
            .apply()
    }

    fun isTermuxInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE_NAME, 0)
    }.isSuccess

    fun clearPinnedHostKey() {
        preferences.edit().remove(KEY_PINNED_HOST_KEY).apply()
    }

    fun connect(config: TerminalConfig) {
        if (_state.value is TerminalConnectionState.Connecting ||
            _state.value is TerminalConnectionState.Connected
        ) {
            return
        }
        val requestedHost = config.host.trim().ifBlank { DEFAULT_TERMINAL_HOST }
        val isLocalHost = requestedHost == "127.0.0.1" ||
            requestedHost.equals("localhost", ignoreCase = true)
        val normalized = config.copy(
            host = requestedHost,
            username = if (isLocalHost) termuxSshUsername() else config.username.trim(),
            password = config.password,
        )
        saveConnectionPreferences(normalized)
        if (_settings.value.savePassword) {
            terminalPreferences.savePassword(normalized.password)
        } else {
            terminalPreferences.clearPassword()
        }
        _state.value = TerminalConnectionState.Connecting
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    openSshSession(normalized)
                    openShellSession(normalized)
                }
            }
            result.onSuccess { first ->
                managedSessions[first.id] = first
                publishSessions()
                _state.value = TerminalConnectionState.Connected(
                    host = normalized.host,
                    port = normalized.port,
                    username = normalized.username,
                )
                first.start()
            }.onFailure { error ->
                closeTransport()
                _state.value = TerminalConnectionState.Failed(
                    terminalErrorMessage(error, normalized),
                )
            }
        }
    }

    fun newSession(initialCommand: String? = null, closeAfterCommand: Boolean = false) {
        if (_state.value !is TerminalConnectionState.Connected) return
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { openShellSession(null) }
            }.onSuccess { newSession ->
                managedSessions[newSession.id] = newSession
                activeSessionId = newSession.id
                publishSessions()
                newSession.start()
                if (!initialCommand.isNullOrBlank()) {
                    val suffix = if (closeAfterCommand) "; exit\r" else "\r"
                    newSession.terminalSession.write(initialCommand + suffix)
                }
            }.onFailure { error ->
                Log.w("NRSuiteTerminal", "Could not open new shell session", error)
            }
        }
    }

    fun setActiveSession(id: Int) {
        activeSessionId = id
    }

    fun runShortcutCommand(
        command: String,
        runMode: TerminalShortcutRunMode,
        execute: Boolean,
    ) {
        if (command.isBlank()) return
        when (runMode) {
            TerminalShortcutRunMode.CURRENT_SESSION -> {
                val session = managedSessions[activeSessionId]?.terminalSession
                    ?: managedSessions.values.lastOrNull()?.terminalSession
                session?.write(if (execute) command + "\r" else command)
            }
            TerminalShortcutRunMode.INSERT_ONLY -> {
                val session = managedSessions[activeSessionId]?.terminalSession
                    ?: managedSessions.values.lastOrNull()?.terminalSession
                session?.write(command)
            }
            TerminalShortcutRunMode.NEW_SESSION -> newSession(command, false)
            TerminalShortcutRunMode.NEW_TEMPORARY_SESSION -> newSession(command, true)
            TerminalShortcutRunMode.BACKGROUND -> runBackgroundCommand(command)
        }
    }

    private fun runBackgroundCommand(command: String) {
        val activeSsh = sshSession
        if (activeSsh == null || !activeSsh.isConnected) {
            NrSuiteNotifications.notifyTaskAlert(
                context,
                "Terminal shortcut failed",
                "Termux SSH is not connected.",
            )
            return
        }
        scope.launch {
            val output = ByteArrayOutputStream()
            val exitCode = runCatching {
                withContext(Dispatchers.IO) {
                    val channel = activeSsh.openChannel("exec") as ChannelExec
                    channel.setCommand(command)
                    val input = channel.inputStream
                    channel.connect(15_000)
                    val buffer = ByteArray(4096)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read > 0) output.write(buffer, 0, read)
                    }
                    val code = channel.exitStatus
                    channel.disconnect()
                    code
                }
            }.getOrElse { -1 }
            val text = output.toString(Charsets.UTF_8.name()).trim()
            val summary = if (exitCode == 0) {
                "Completed successfully."
            } else {
                "Exited with code $exitCode."
            }
            NrSuiteNotifications.notifyTaskAlert(
                context,
                "Terminal shortcut finished",
                buildString {
                    append(summary)
                    if (text.isNotEmpty()) {
                        append("\n")
                        append(text.take(500))
                    }
                },
            )
        }
    }

    fun closeSession(id: Int) {
        val managed = managedSessions.remove(id) ?: return
        managed.close("Session closed")
        publishSessions()
        if (managedSessions.isEmpty()) {
            disconnect("Disconnected")
        }
    }

    fun disconnect(message: String? = null) {
        managedSessions.values.toList().forEach { it.close(message ?: "Disconnected") }
        managedSessions.clear()
        publishSessions()
        closeTransport()
        _state.value = TerminalConnectionState.Disconnected(message)
    }

    fun applyColorScheme(
        palette: IntArray,
        foreground: Int,
        background: Int,
        cursor: Int,
    ) {
        val defaults = TerminalColors.COLOR_SCHEME.mDefaultColors
        val count = minOf(palette.size, 16)
        for (i in 0 until count) defaults[i] = palette[i]
        defaults[TextStyle.COLOR_INDEX_FOREGROUND] = foreground
        defaults[TextStyle.COLOR_INDEX_BACKGROUND] = background
        defaults[TextStyle.COLOR_INDEX_CURSOR] = cursor
        managedSessions.values.forEach { managed ->
            managed.terminalSession.getEmulator()?.mColors?.reset()
        }
        screenUpdateListener?.invoke()
    }

    private fun openSshSession(config: TerminalConfig): Session {
        if (config.username.isBlank()) {
            throw IllegalArgumentException("Enter the Termux SSH username.")
        }
        if (config.password.isEmpty() && !terminalPreferences.hasPrivateKey()) {
            throw IllegalArgumentException("Enter the Termux SSH password or import an SSH key.")
        }
        val authJsch = JSch()
        terminalPreferences.loadPrivateKey()?.let { key ->
            authJsch.addIdentity(
                "nrsuite-terminal-key",
                key.bytes,
                key.publicBytes,
                key.passphrase.toByteArray(Charsets.UTF_8),
            )
        }
        return authJsch.getSession(config.username, config.host, config.port).apply {
            if (config.password.isNotEmpty()) setPassword(config.password)
            setHostKeyRepository(pinnedHostKeys)
            setConfig("StrictHostKeyChecking", "yes")
            setConfig("PreferredAuthentications", "publickey,password,keyboard-interactive")
            if (config.host == "127.0.0.1" || config.host.equals("localhost", ignoreCase = true)) {
                setHostKeyAlias("nrsuite")
            }
            timeout = 15_000
            connect(15_000)
            sshSession = this
        }
    }

    private fun openShellSession(@Suppress("UNUSED_PARAMETER") config: TerminalConfig?): ManagedTerminalSession {
        val activeSsh = sshSession ?: throw IllegalStateException("SSH session is not connected.")
        val channel = (activeSsh.openChannel("shell") as ChannelShell).apply {
            setPty(true)
            setPtyType("xterm-256color")
            setPtySize(80, 24, 0, 0)
            connect(15_000)
        }
        val id = nextSessionId++
        val terminal = TerminalSession(terminalSessionClient)
        return ManagedTerminalSession(
            id = id,
            title = "Session $id",
            channel = channel,
            terminalSession = terminal,
        )
    }

    private fun publishSessions() {
        _sessions.value = managedSessions.values.mapIndexed { index, managed ->
            TerminalSessionEntry(
                id = managed.id,
                title = "Tab ${index + 1}",
                session = managed.terminalSession,
            )
        }
        if (activeSessionId !in managedSessions) {
            activeSessionId = managedSessions.values.lastOrNull()?.id
        }
    }

    private fun closeTransport() {
        runCatching { sshSession?.disconnect() }
        sshSession = null
    }

    private fun terminalErrorMessage(error: Throwable, config: TerminalConfig): String {
        val raw = error.message.orEmpty()
        if (raw.contains("Auth fail", ignoreCase = true) ||
            raw.contains("auth", ignoreCase = true) && raw.contains("fail", ignoreCase = true)
        ) {
            return "Authentication failed. Check the Termux username and password."
        }
        if (raw.contains("HostKey", ignoreCase = true) ||
            raw.contains("host key", ignoreCase = true)
        ) {
            return "The pinned Termux SSH host key changed. Reinstalling Termux creates " +
                "a new key; clear the pinned key below if that was intentional."
        }
        if (error is IllegalArgumentException) return raw
        return "Could not connect to ${config.host}:${config.port}. " +
            "Start SSH in Termux with: sshd\n" +
            (raw.takeIf { it.isNotBlank() } ?: "")
    }

    private inner class ManagedTerminalSession(
        val id: Int,
        val title: String,
        private val channel: ChannelShell,
        val terminalSession: TerminalSession,
    ) {
        private var readerJob: Job? = null
        private var output: OutputStream? = null

        fun start() {
            terminalSession.setExternalTransport(
                object : TerminalSession.ExternalTerminalTransport {
                    override fun write(data: ByteArray, offset: Int, count: Int) {
                        scope.launch(Dispatchers.IO) {
                            writeMutex.withLock {
                                runCatching {
                                    output?.write(data, offset, count)
                                    output?.flush()
                                }
                            }
                        }
                    }

                    override fun resize(
                        columns: Int,
                        rows: Int,
                        cellWidthPixels: Int,
                        cellHeightPixels: Int,
                    ) {
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                channel.setPtySize(
                                    columns.coerceIn(2, 500),
                                    rows.coerceIn(1, 300),
                                    cellWidthPixels,
                                    cellHeightPixels,
                                )
                            }
                        }
                    }

                    override fun disconnect() {
                        close("Session closed")
                    }
                },
            )
            output = runCatching { channel.outputStream }.getOrNull()
            val input: InputStream = runCatching { channel.inputStream }.getOrNull() ?: return
            readerJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(8192)
                try {
                    while (isActive) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read > 0) terminalSession.feedOutput(buffer, 0, read)
                    }
                    if (isActive) {
                        terminalSession.markDisconnected("SSH session ended")
                        scope.launch { removeManagedSession(this@ManagedTerminalSession) }
                    }
                } catch (error: IOException) {
                    if (isActive) {
                        terminalSession.markDisconnected("SSH connection lost")
                        scope.launch { removeManagedSession(this@ManagedTerminalSession) }
                    }
                }
            }
        }

        fun close(message: String) {
            readerJob?.cancel()
            readerJob = null
            terminalSession.markDisconnected(message)
            runCatching { output?.close() }
            output = null
            runCatching { channel.disconnect() }
        }
    }

    private fun removeManagedSession(managed: ManagedTerminalSession) {
        managedSessions.remove(managed.id)
        publishSessions()
        if (managedSessions.isEmpty()) {
            disconnect("SSH session ended.")
        }
    }

    private val terminalSessionClient = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {
            screenUpdateListener?.invoke()
        }

        override fun onTitleChanged(changedSession: TerminalSession) {
            screenUpdateListener?.invoke()
        }

        override fun onSessionFinished(finishedSession: TerminalSession) {
            screenUpdateListener?.invoke()
        }

        override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("Terminal", text))
        }

        override fun onPasteTextFromClipboard(session: TerminalSession?) {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            val text = clipboard?.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
            if (!text.isNullOrEmpty()) session?.write(text)
        }

        override fun onBell(session: TerminalSession) = Unit

        override fun onColorsChanged(session: TerminalSession) {
            screenUpdateListener?.invoke()
        }

        override fun onTerminalCursorStateChange(state: Boolean) {
            screenUpdateListener?.invoke()
        }

        override fun setTerminalShellPid(session: TerminalSession, pid: Int) = Unit

        override fun getTerminalCursorStyle(): Int? = _settings.value.cursorStyle

        override fun logError(tag: String, message: String) {
            Log.e(tag, message)
        }

        override fun logWarn(tag: String, message: String) {
            Log.w(tag, message)
        }

        override fun logInfo(tag: String, message: String) {
            Log.i(tag, message)
        }

        override fun logDebug(tag: String, message: String) {
            Log.d(tag, message)
        }

        override fun logVerbose(tag: String, message: String) {
            Log.v(tag, message)
        }

        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
            Log.e(tag, message, e)
        }

        override fun logStackTrace(tag: String, e: Exception) {
            Log.e(tag, "Terminal stack trace", e)
        }
    }

    private class PinnedHostKeyRepository(
        private val preferences: SharedPreferences,
    ) : HostKeyRepository {
        override fun check(host: String?, key: ByteArray?): Int {
            if (key == null) return HostKeyRepository.NOT_INCLUDED
            val saved = preferences.getString(KEY_PINNED_HOST_KEY, null)
            if (saved.isNullOrBlank()) {
                save(key)
                return HostKeyRepository.OK
            }
            val savedBytes = runCatching {
                Base64.decode(saved, Base64.NO_WRAP)
            }.getOrNull() ?: return HostKeyRepository.CHANGED
            return if (savedBytes.contentEquals(key)) {
                HostKeyRepository.OK
            } else {
                HostKeyRepository.CHANGED
            }
        }

        override fun add(hostkey: HostKey?, ui: UserInfo?) {
            hostkey?.getKey()?.let { encoded ->
                runCatching { Base64.decode(encoded, Base64.NO_WRAP) }
                    .getOrNull()
                    ?.let(::save)
            }
        }

        override fun remove(host: String?, type: String?) = Unit

        override fun remove(host: String?, type: String?, key: ByteArray?) = Unit

        override fun getKnownHostsRepositoryID(): String = "nrsuite-termux-pinned"

        override fun getHostKey(): Array<HostKey> = emptyArray()

        override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()

        private fun save(key: ByteArray) {
            preferences.edit()
                .putString(KEY_PINNED_HOST_KEY, Base64.encodeToString(key, Base64.NO_WRAP))
                .apply()
        }
    }

    private companion object {
        const val KEY_HOST = "ssh_host"
        const val KEY_PORT = "ssh_port"
        const val KEY_USERNAME = "ssh_username"
        const val KEY_PINNED_HOST_KEY = "ssh_pinned_host_key"
    }
}
