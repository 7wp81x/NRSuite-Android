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
import kotlinx.coroutines.delay
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
    @Volatile private var lastInboundDataAtMs = 0L

    suspend fun connect() {
        if (_state.value is ConnectionState.Connected) return

        closed = false
        lastInboundDataAtMs = 0L
        android.util.Log.i("NRSuiteWire", "NrSession.connect start")
        _state.value = ConnectionState.Connecting
        try {
            withContext(Dispatchers.IO) { transport.open() }
            readerJob = scope.launch(Dispatchers.IO) { readLoop() }

            // UART bridges can take a moment after the port opens and modem
            // lines change before host->device data is forwarded. This also
            // gives native USB CDC devices time after a reconnect.
            delay(HANDSHAKE_SETTLE_MS)

            var pong = pingUntilResponds()
            if (pong?.optBoolean("ok") != true) {
                // The serial-monitor workaround shows the device may still be
                // booting or the first TX window may be missed. If it starts
                // sending heartbeats/events, wait for that sign of life and
                // retry the PING once.
                log("No PING yet; waiting for device activity before retrying")
                if (awaitDeviceActivity(DEVICE_ACTIVITY_TIMEOUT_MS)) {
                    android.util.Log.i("NRSuiteWire", "device activity observed; retrying PING")
                    delay(POST_ACTIVITY_SETTLE_MS)
                    pong = pingUntilResponds()
                } else {
                    android.util.Log.i("NRSuiteWire", "no device activity observed during retry wait")
                }
            }
            if (pong?.optBoolean("ok") != true) {
                log("No valid PING response after handshake retries")
                disconnect()
                _state.value = ConnectionState.Failed("No valid PING response from device")
                return
            }

            val status = sendCommand("STATUS", timeoutMs = 5_000)
            val chip = status?.optString("chip")?.takeIf { it.isNotBlank() }
            val firmware = status?.optString("fw")?.takeIf { it.isNotBlank() }
            val deviceId = status?.optString("device_id")?.takeIf { it.isNotBlank() }
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
                deviceId = deviceId,
            )
            log(
                "Connected to ${chip ?: "NRSuite device"}" +
                    (deviceId?.let { " ($it)" } ?: "")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            val message = t.message ?: "Connection failed"
            log("Connection failed: $message")
            disconnect()
            _state.value = ConnectionState.Failed(message)
        }
    }

    /**
     * Prepare an already-open transport for a second handshake. This clears
     * stale endpoint toggles on native USB bridges without a close/reopen.
     */
    suspend fun prepareForReuse() {
        withContext(Dispatchers.IO) {
            runCatching { transport.prepareForReuse() }
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
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                transport.write(encoded, WRITE_TIMEOUT_MS)
            }
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
                    lastInboundDataAtMs = System.currentTimeMillis()
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

    private suspend fun pingUntilResponds(): JSONObject? {
        var pong: JSONObject? = null
        for (attempt in 0 until MAX_PING_ATTEMPTS) {
            if (attempt > 0) delay(PING_RETRY_DELAY_MS)
            android.util.Log.d("NRSuiteWire", "sending PING attempt ${attempt + 1}")
            // Keep the PING payload off the USB max-packet boundary. Some
            // CH34x/Android bulk stacks do not flush a 32-byte full packet
            // until a short packet arrives. The firmware ignores extra args.
            pong = sendCommand(
                "PING",
                org.json.JSONObject().put("pad", attempt),
                timeoutMs = PING_TIMEOUT_MS,
            )
            android.util.Log.d(
                "NRSuiteWire",
                "PING attempt ${attempt + 1} result=${pong?.toString() ?: "timeout"}",
            )
            if (pong?.optBoolean("ok") == true) break
        }
        return pong
    }

    private suspend fun awaitDeviceActivity(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (lastInboundDataAtMs > 0L) return true
            delay(100)
        }
        return lastInboundDataAtMs > 0L
    }

    private fun log(message: String) {
        _logs.tryEmit(message)
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 8_000L
        private const val READ_TIMEOUT_MS = 250
        private const val WRITE_TIMEOUT_MS = 1_000
        private const val READ_BUFFER_SIZE = 4096

        private const val HANDSHAKE_SETTLE_MS = 500L
        private const val PING_TIMEOUT_MS = 2_000L
        private const val MAX_PING_ATTEMPTS = 4
        private const val PING_RETRY_DELAY_MS = 500L
        private const val DEVICE_ACTIVITY_TIMEOUT_MS = 5_000L
        private const val POST_ACTIVITY_SETTLE_MS = 250L
    }
}
