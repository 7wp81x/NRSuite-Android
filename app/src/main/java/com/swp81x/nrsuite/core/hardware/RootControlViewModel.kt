package com.swp81x.nrsuite.core.hardware

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

data class RootControlState(
    val busy: Boolean = false,
    val rootGranted: Boolean = false,
    val output: String = "Choose Check root to request superuser access. Commands run locally on this phone.",
)

class RootControlViewModel : ViewModel() {
    private val _state = MutableStateFlow(RootControlState())
    val state = _state.asStateFlow()
    @Volatile private var activeProcess: Process? = null

    fun execute(action: RootAction) {
        if (_state.value.busy || (action != RootAction.ROOT_STATUS && !_state.value.rootGranted)) return
        _state.value = _state.value.copy(busy = true, output = "${action.label}: running...")
        viewModelScope.launch(Dispatchers.IO) {
            var process: Process? = null
            val output = StringBuilder()
            var truncated = false
            try {
                process = ProcessBuilder("su", "-c", RootControlCommands.command(action))
                    .redirectErrorStream(true).start()
                activeProcess = process
                process.outputStream.close()
                val stream = process.inputStream
                val reader = Thread {
                    try {
                        stream.reader().use { input ->
                            val buffer = CharArray(1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                synchronized(output) {
                                    val remaining = 32768 - output.length
                                    if (remaining > 0) output.append(buffer, 0, count.coerceAtMost(remaining))
                                    if (count > remaining) truncated = true
                                }
                            }
                        }
                    } catch (_: Exception) { /* Process/stream closed by deadline or owner. */ }
                }.apply { isDaemon = true; start() }
                val finished = process.waitFor(30, TimeUnit.SECONDS)
                if (!finished) process.destroyForcibly()
                reader.join(1000)
                runCatching { stream.close() }
                val text = synchronized(output) { output.toString() }
                val root = text.lineSequence().any { it.trim() == "NRSUITE_ROOT_OK" }
                val visible = text.lineSequence().filterNot { it.trim() == "NRSUITE_ROOT_OK" }.joinToString("\n").trim()
                val status = if (finished) "Exit ${process.exitValue()}" else "Timed out after 30 seconds. Check device state before retrying; an operation may have partially completed."
                _state.value = RootControlState(rootGranted = root,
                    output = "${action.label} | $status\n${visible.ifBlank { "No command output. Refresh status to verify state." }}" +
                        if (truncated) "\n[Output truncated]" else "")
            } catch (e: Exception) {
                _state.value = RootControlState(output = "${action.label}: ${e.javaClass.simpleName}. Check root installation and superuser permission.")
            } finally {
                process?.destroy()
                if (activeProcess === process) activeProcess = null
            }
        }
    }

    override fun onCleared() { activeProcess?.destroyForcibly() }
}
