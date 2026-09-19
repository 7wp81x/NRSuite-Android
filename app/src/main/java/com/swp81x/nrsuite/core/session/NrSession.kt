package com.swp81x.nrsuite.core.session

import com.swp81x.nrsuite.core.protocol.Frame
import com.swp81x.nrsuite.core.protocol.FrameCodec
import com.swp81x.nrsuite.core.protocol.FrameDecoder
import com.swp81x.nrsuite.core.protocol.FrameType
import com.swp81x.nrsuite.core.usb.NrTransport
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * High-level NRSuite bridge session.
 *
 * Responsibilities:
 *  - correlate CMD/RESP frames by id,
 *  - emit async EVENT frames,
 *  - emit raw PCAP frames and ACK them back to the firmware,
 *  - expose a simple [ConnectionState] stream.
 */
class NrSession(
    private val transport: NrTransport,
    private val scope: CoroutineScope,
    private val defaultTimeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    private val writeMutex = Mutex()
    private val decoder = FrameDecoder()
    private val nextFrameId = AtomicInteger(1)
    private val pendingResponses = ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<JSONObject>(extraBufferCapacity = 128)
    val events: SharedFlow<JSONObject> = _events.asSharedFlow()

    private val _pcap = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val pcap: SharedFlow<ByteArray> = _pcap.asSharedFlow()

    private val _logs = MutableSharedFlow<String>(extraBufferCapacity = 128)
    val logs: SharedFlow<String> = _logs.asSharedFlow()

    private var readerJob: Job? = null
    @Volatile private var closed = false

    suspend fun connect() {
        if (_state.value is ConnectionState.Connected) return

        closed = false
        _state.value = ConnectionState.Connecting
        try {
            withContext(Dispatchers.IO) { transport.open() }
            readerJob = scope.launch(Dispatchers.IO) { readLoop() }

            val pong = sendCommand("PING", timeoutMs = 3_000)
            if (pong?.optBoolean("ok") != true) {
                disconnect()
                _state.value = ConnectionState.Failed("No valid PING response from device")
                return
            }

            val status = sendCommand("STATUS", timeoutMs = 5_000)
            val chip = status?.optString("chip")?.takeIf { it.isNotBlank() }
            val firmware = status?.optString("fw")?.takeIf { it.isNotBlank() }
            val features = mutableSetOf<String>()
            status?.optJSONArray("features")?.let { array ->
                for (index in 0 until array.length()) {
                    val value = array.optString(index)
                    if (value.isNotBlank()) features += value
                }
            }
            _state.value = ConnectionState.Connected(
                chip = chip,
                firmwareVersion = firmware,
                features = features,
            )
            log("Connected to ${chip ?: "NRSuite device"}")
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            disconnect()
            _state.value = ConnectionState.Failed(t.message ?: "Connection failed")
        }
    }

    suspend fun disconnect() {
        closed = true
        readerJob?.cancel()
        readerJob = null
        pendingResponses.values.forEach { it.cancel() }
        pendingResponses.clear()
        withContext(NonCancellable + Dispatchers.IO) {
            runCatching { transport.close() }
        }
        _state.value = ConnectionState.Disconnected
    }

    suspend fun scanWifi(timeoutMs: Long = 30_000): Int? {
        val response = sendCommand("SCAN_WIFI", timeoutMs = timeoutMs) ?: return null
        if (!response.optBoolean("ok")) return -1
        return response.optInt("count", 0)
    }

    suspend fun sendCommand(
        cmd: String,
        args: JSONObject? = null,
        timeoutMs: Long = defaultTimeoutMs,
    ): JSONObject? {
        val id = allocateFrameId()
        val deferred = CompletableDeferred<JSONObject>()
        pendingResponses[id] = deferred

        val payload = JSONObject().apply {
            put("cmd", cmd)
            put("args", args ?: JSONObject())
        }.toString().toByteArray(Charsets.UTF_8)

        return try {
            sendFrame(FrameType.COMMAND, id, payload)
            withTimeoutOrNull(timeoutMs) { deferred.await() }
        } finally {
            pendingResponses.remove(id)
        }
    }

    /**
     * Fire-and-forget command used for latency-sensitive realtime HID input.
     * The firmware still sends a response, but this side does not wait for it.
     */
    suspend fun sendCommandNoWait(
        cmd: String,
        args: JSONObject? = null,
    ): Boolean {
        val id = allocateFrameId()
        val payload = JSONObject().apply {
            put("cmd", cmd)
            put("args", args ?: JSONObject())
        }.toString().toByteArray(Charsets.UTF_8)

        return runCatching {
            sendFrame(FrameType.COMMAND, id, payload)
        }.isSuccess
    }

    private fun allocateFrameId(): Int = synchronized(nextFrameId) {
        if (nextFrameId.get() >= 0xFF) {
            nextFrameId.set(1)
        }
        nextFrameId.getAndIncrement()
    }

    private suspend fun sendFrame(type: FrameType, id: Int, payload: ByteArray) {
        val encoded = FrameCodec.encode(type, id, payload)
        writeMutex.withLock {
            transport.write(encoded, WRITE_TIMEOUT_MS)
        }
    }

    private suspend fun readLoop() {
        val buffer = ByteArray(READ_BUFFER_SIZE)
        try {
            while (!closed &&
                currentCoroutineContext().isActive &&
                transport.isOpen
            ) {
                val count = transport.read(buffer, READ_TIMEOUT_MS)
                if (count > 0) {
                    val frames = decoder.feed(buffer.copyOf(count))
                    for (frame in frames) {
                        handleFrame(frame)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            if (!closed) {
                log("Reader stopped: ${t.message ?: t.javaClass.simpleName}")
                // A USB unplug surfaces as a read/write exception. Treat it as
                // a clean disconnect instead of a red "Connection problem".
                _state.value = ConnectionState.Disconnected
            }
        }
    }

    private suspend fun handleFrame(frame: Frame) {
        when (frame.type) {
            FrameType.RESPONSE -> handleResponse(frame)
            FrameType.EVENT -> handleEvent(frame)
            FrameType.PCAP -> handlePcap(frame)
            FrameType.ACK, FrameType.COMMAND, FrameType.HTML -> Unit
        }
    }

    private fun handleResponse(frame: Frame) {
        val json = parseJson(frame)
        if (json == null) {
            log("Invalid JSON response for frame id=${frame.id}")
            return
        }
        pendingResponses.remove(frame.id)?.complete(json)
    }

    private suspend fun handleEvent(frame: Frame) {
        val json = parseJson(frame) ?: return
        _events.emit(json)
    }

    private suspend fun handlePcap(frame: Frame) {
        _pcap.emit(frame.payload)
        // Firmware uses a small sliding window; ACK after the frame has been
        // accepted by the application.
        val ack = JSONObject().put("chunk", frame.id).toString().toByteArray(Charsets.UTF_8)
        sendFrame(FrameType.ACK, 0, ack)
    }

    private fun parseJson(frame: Frame): JSONObject? {
        return runCatching { JSONObject(frame.payloadAsString) }.getOrNull()
    }

    private fun log(message: String) {
        _logs.tryEmit(message)
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 8_000L
        private const val READ_TIMEOUT_MS = 250
        private const val WRITE_TIMEOUT_MS = 1_000
        private const val READ_BUFFER_SIZE = 4096
    }
}
