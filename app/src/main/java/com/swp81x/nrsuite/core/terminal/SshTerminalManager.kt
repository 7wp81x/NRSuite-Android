package com.swp81x.nrsuite.core.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
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

data class TerminalConfig(
    val host: String = DEFAULT_TERMINAL_HOST,
    val port: Int = DEFAULT_TERMINAL_PORT,
    val username: String = "",
    val password: String = "",
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
    private val jsch = JSch()
    private val writeMutex = Mutex()
    private val pinnedHostKeys = PinnedHostKeyRepository(preferences)

    private val _state = MutableStateFlow<TerminalConnectionState>(
        TerminalConnectionState.Disconnected(),
    )
    val state: StateFlow<TerminalConnectionState> = _state.asStateFlow()

    // The native Termux TerminalSession/Emulator keeps the full screen and
    // scrollback state across tab switches.
    @Volatile
    private var terminalSession: TerminalSession? = null
    @Volatile
    private var screenUpdateListener: (() -> Unit)? = null

    fun currentTerminalSession(): TerminalSession? = terminalSession

    fun setTerminalViewUpdateListener(listener: (() -> Unit)?) {
        screenUpdateListener = listener
    }

    private var session: Session? = null
    private var channel: ChannelShell? = null
    private var channelInput: InputStream? = null
    private var channelOutput: OutputStream? = null
    private var readerJob: Job? = null
    private var connectJob: Job? = null
    private var columns = 80
    private var rows = 24

    fun savedHost(): String = preferences.getString(KEY_HOST, DEFAULT_TERMINAL_HOST)
        ?: DEFAULT_TERMINAL_HOST

    fun savedPort(): Int = preferences.getInt(KEY_PORT, DEFAULT_TERMINAL_PORT)
        .coerceIn(1, 65535)

    fun savedUsername(): String = preferences.getString(KEY_USERNAME, "").orEmpty()

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
        val normalized = config.copy(
            host = config.host.trim().ifBlank { DEFAULT_TERMINAL_HOST },
            username = config.username.trim(),
            password = config.password,
        )
        saveConnectionPreferences(normalized)
        terminalSession?.finishIfRunning()
        terminalSession = TerminalSession(terminalSessionClient).apply {
            setExternalTransport(sessionTransport)
        }
        _state.value = TerminalConnectionState.Connecting
        connectJob?.cancel()
        connectJob = scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    openSshSession(normalized)
                }
            }
            result.onSuccess { connected ->
                if (!isActive) {
                    closeTransport()
                    return@onSuccess
                }
                _state.value = TerminalConnectionState.Connected(
                    host = normalized.host,
                    port = normalized.port,
                    username = normalized.username,
                )
                startReader(connected)
            }.onFailure { error ->
                if (isActive) {
                    closeTransport()
                    _state.value = TerminalConnectionState.Failed(
                        terminalErrorMessage(error, normalized),
                    )
                }
            }
        }
    }

    fun disconnect(message: String? = null) {
        connectJob?.cancel()
        connectJob = null
        readerJob?.cancel()
        readerJob = null
        terminalSession?.markDisconnected(message ?: "Disconnected")
        closeTransport()
        _state.value = TerminalConnectionState.Disconnected(message)
    }

    fun sendInput(text: String) {
        if (text.isEmpty()) return
        val bytes = text.toByteArray(Charsets.UTF_8)
        scope.launch(Dispatchers.IO) {
            writeMutex.withLock {
                runCatching { channelOutput?.write(bytes); channelOutput?.flush() }
            }
        }
    }

    fun resize(columns: Int, rows: Int) {
        this.columns = columns.coerceIn(2, 500)
        this.rows = rows.coerceIn(1, 300)
        scope.launch(Dispatchers.IO) {
            runCatching {
                channel?.setPtySize(
                    this@SshTerminalManager.columns,
                    this@SshTerminalManager.rows,
                    0,
                    0,
                )
            }
        }
    }

    private fun openSshSession(config: TerminalConfig): Session {
        if (config.username.isBlank()) {
            throw IllegalArgumentException("Enter the Termux SSH username.")
        }
        if (config.password.isEmpty()) {
            throw IllegalArgumentException("Enter the Termux SSH password.")
        }
        val newSession = jsch.getSession(config.username, config.host, config.port).apply {
            setPassword(config.password)
            setHostKeyRepository(pinnedHostKeys)
            setConfig("StrictHostKeyChecking", "yes")
            setConfig("PreferredAuthentications", "password,keyboard-interactive")
            timeout = 15_000
            connect(15_000)
        }
        val newChannel = (newSession.openChannel("shell") as ChannelShell).apply {
            setPty(true)
            setPtyType("xterm-256color")
            setPtySize(columns, rows, 0, 0)
            connect(15_000)
        }
        session = newSession
        channel = newChannel
        channelInput = newChannel.inputStream
        channelOutput = newChannel.outputStream
        return newSession
    }

    private fun startReader(@Suppress("UNUSED_PARAMETER") connectedSession: Session) {
        val input = channelInput ?: return
        readerJob?.cancel()
        readerJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(8192)
            try {
                while (isActive) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read > 0) {
                        terminalSession?.feedOutput(buffer, 0, read)
                    }
                }
                if (isActive) {
                    terminalSession?.markDisconnected("SSH session ended")
                    _state.value = TerminalConnectionState.Disconnected(
                        "SSH session ended.",
                    )
                    closeTransport()
                }
            } catch (error: IOException) {
                if (isActive) {
                    terminalSession?.markDisconnected("SSH connection lost")
                    _state.value = TerminalConnectionState.Failed(
                        "SSH connection lost: ${error.message ?: "I/O error"}",
                    )
                    closeTransport()
                }
            }
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

        override fun getTerminalCursorStyle(): Int? = null

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

    private val sessionTransport = object : TerminalSession.ExternalTerminalTransport {
        override fun write(data: ByteArray, offset: Int, count: Int) {
            scope.launch(Dispatchers.IO) {
                writeMutex.withLock {
                    runCatching {
                        channelOutput?.write(data, offset, count)
                        channelOutput?.flush()
                    }
                }
            }
        }

        override fun resize(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
            this@SshTerminalManager.columns = columns.coerceIn(2, 500)
            this@SshTerminalManager.rows = rows.coerceIn(1, 300)
            scope.launch(Dispatchers.IO) {
                runCatching {
                    channel?.setPtySize(
                        this@SshTerminalManager.columns,
                        this@SshTerminalManager.rows,
                        cellWidthPixels,
                        cellHeightPixels,
                    )
                }
            }
        }

        override fun disconnect() {
            disconnect("Disconnected")
        }
    }

    private fun closeTransport() {
        runCatching { channelInput?.close() }
        runCatching { channelOutput?.close() }
        channelInput = null
        channelOutput = null
        runCatching { channel?.disconnect() }
        channel = null
        runCatching { session?.disconnect() }
        session = null
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
        if (error is IllegalArgumentException) {
            return raw
        }
        return "Could not connect to ${config.host}:${config.port}. " +
            "Start SSH in Termux with: sshd\n${raw.takeIf { it.isNotBlank() } ?: ""}".trim()
    }

    private class PinnedHostKeyRepository(
        private val preferences: SharedPreferences,
    ) : HostKeyRepository {
        override fun check(host: String?, key: ByteArray?): Int {
            if (key == null) return HostKeyRepository.NOT_INCLUDED
            val saved = preferences.getString(KEY_PINNED_HOST_KEY, null)
            if (saved.isNullOrBlank()) {
                // Trust-on-first-use for the local Termux SSH daemon. Later
                // connections require the same key.
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
