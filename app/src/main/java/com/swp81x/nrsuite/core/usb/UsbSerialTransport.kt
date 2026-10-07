package com.swp81x.nrsuite.core.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.Ch34xSerialDriver
import com.hoho.android.usbserial.driver.CommonUsbSerialPort
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.Cp21xxSerialDriver
import com.hoho.android.usbserial.driver.FtdiSerialDriver
import com.hoho.android.usbserial.driver.ProlificSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import java.io.IOException

/**
 * [NrTransport] backed by usb-serial-for-android.
 *
 * Supports the generic CDC ACM driver, CP210x, CH34x, FTDI, and other drivers
 * registered by the library's default prober.
 *
 * For external UART bridge chips the Android Java bulk-transfer path has
 * proven unreliable on some devices: writes can complete at the API level
 * without reaching the ESP32. Those chips are configured through their normal
 * driver (baud rate / line state), then the interface is handed to the native
 * libusb fd transport, matching the working Termux/espbridge path.
 */
class UsbSerialTransport(
    private val usbManager: UsbManager,
    private val driver: UsbSerialDriver,
    private val baudRate: Int = DEFAULT_BAUD_RATE,
) : NrTransport {

    private var connection: UsbDeviceConnection? = null
    private var port: UsbSerialPort? = null
    private var rawInterface: UsbInterface? = null
    private var rawReadEndpoint: UsbEndpoint? = null
    private var rawWriteEndpoint: UsbEndpoint? = null
    private var nativeRawOpen = false
    private var ownerSession: UsbDeviceOwnerClient.Session? = null

    private val isCh34x: Boolean
        get() = driver is Ch34xSerialDriver

    private val isCp21xx: Boolean
        get() = driver is Cp21xxSerialDriver

    private val isFtdi: Boolean
        get() = driver is FtdiSerialDriver

    /**
     * Fallback path: CP210x/FTDI can also be configured by
     * usb-serial-for-android, then moved to the native libusb bulk path.
     */
    private val isLibraryConfiguredNativeRaw: Boolean
        get() = isCp21xx || isFtdi

    init {
        CommonUsbSerialPort.DEBUG = true
    }

    override val isOpen: Boolean
        get() = if (nativeRawOpen) {
            rawInterface != null
        } else {
            port?.isOpen == true
        }

    @Synchronized
    override fun open() {
        if (isOpen) return
        val usbDevice = driver.device
        Log.i(
            "NRSuiteWire",
            "open driver=${driver.javaClass.simpleName} vid=0x${usbDevice.vendorId.toString(16)} " +
                "pid=0x${usbDevice.productId.toString(16)} name=${usbDevice.deviceName}",
        )
        if (!usbManager.hasPermission(usbDevice)) {
            throw IOException("USB permission has not been granted for ${usbDevice.deviceName}")
        }

        if (isCh34x) {
            if (tryOpenNativeRaw(usbDevice, "CH34x") {
                    NativeUsbBridge.ch34xInit(ch34xDivisor(baudRate))
                }) return
        } else if (isCp21xx) {
            if (tryOpenNativeRaw(usbDevice, "CP210x") {
                    NativeUsbBridge.cp21xxInit(baudRate)
                }) return
        } else if (isFtdi) {
            if (tryOpenNativeRaw(usbDevice, "FTDI") {
                    NativeUsbBridge.ftdiInit(baudRate)
                }) return
        }

        val openedConnection = usbManager.openDevice(usbDevice)
            ?: throw IOException("Could not open USB device ${usbDevice.deviceName}")
        val openedPort = driver.ports.firstOrNull()
            ?: run {
                openedConnection.close()
                throw IOException("No serial port found on ${usbDevice.deviceName}")
            }

        try {
            openedPort.open(openedConnection)

            // Host-side close/reopen can leave the bulk endpoints halted on
            // some TinyUSB composite devices. Clear HALT on both endpoints
            // before configuring the port so a software reconnect can recover
            // without a physical unplug. The native path clears HALT again
            // through libusb after it takes ownership of the interface.
            clearJavaHalts(openedConnection, openedPort)

            openedPort.setParameters(
                baudRate,
                UsbSerialPort.DATABITS_8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE,
            )

            configureLineState(openedPort)
        } catch (t: Throwable) {
            runCatching { openedPort.close() }
            runCatching { openedConnection.close() }
            throw if (t is IOException) t else IOException("Could not configure serial port", t)
        }

        if (isLibraryConfiguredNativeRaw) {
            if (openLibraryConfiguredNativeRaw(usbDevice, openedConnection, openedPort)) {
                return
            }
            runCatching { openedPort.close() }
            runCatching { openedConnection.close() }
            throw IOException("Could not configure native raw transport for ${usbDevice.deviceName}")
        }

        connection = openedConnection
        port = openedPort
        Log.i("NRSuiteWire", "port opened read=${openedPort.readEndpoint.address} write=${openedPort.writeEndpoint.address}")
    }

    override fun read(buffer: ByteArray, timeoutMs: Int): Int {
        val count = if (nativeRawOpen) {
            val rawCount = NativeUsbBridge.read(buffer, timeoutMs)
            if (rawCount > 0 && driver is FtdiSerialDriver) {
                filterFtdiRead(buffer, rawCount)
            } else {
                rawCount
            }
        } else {
            val activePort = port ?: throw IOException("Serial port is not open")
            activePort.read(buffer, timeoutMs)
        }

        if (count > 0) {
            val preview = buildString {
                for (i in 0 until minOf(count, 64)) {
                    if (i > 0) append(' ')
                    append(String.format("%02X", buffer[i].toInt() and 0xFF))
                }
            }
            Log.d("NRSuiteWire", "RX $count bytes: $preview")

            // Raw boot/crash logs are ASCII text, not NRSuite frames. Log them
            // in full so a panic reason/backtrace is preserved in logcat.
            val startsWithFrame =
                count >= 2 &&
                    buffer[0] == 0xAD.toByte() &&
                    buffer[1] == 0xDE.toByte()
            if (!startsWithFrame) {
                val text = buildString {
                    for (i in 0 until count) {
                        val b = buffer[i].toInt() and 0xFF
                        append(
                            when {
                                b == 13 -> '\r'
                                b == 10 -> '\n'
                                b in 32..126 -> b.toChar()
                                else -> '.'
                            },
                        )
                    }
                }
                text.chunked(1000).forEachIndexed { index, chunk ->
                    Log.d("NRSuiteWire", "RXRAW[$index] $chunk")
                }
            }
        }
        return count
    }

    override fun write(data: ByteArray, timeoutMs: Int) {
        val preview = buildString {
            for (i in 0 until minOf(data.size, 64)) {
                if (i > 0) append(' ')
                append(String.format("%02X", data[i].toInt() and 0xFF))
            }
        }
        Log.d("NRSuiteWire", "TX ${data.size} bytes: $preview")
        if (nativeRawOpen) {
            if (!NativeUsbBridge.write(data, timeoutMs)) {
                throw IOException("Native libusb write failed")
            }
        } else {
            val activePort = port ?: throw IOException("Serial port is not open")
            activePort.write(data, timeoutMs)
        }
    }

    override fun prepareForReuse() {
        if (nativeRawOpen) {
            Log.d("NRSuiteWire", "resetting native USB endpoint toggles before reuse")
            runCatching { NativeUsbBridge.resetEndpoints() }
        }
    }

    @Synchronized
    override fun close() {
        if (nativeRawOpen) {
            Log.i("NRSuiteWire", "closing native raw USB transport")
            runCatching { NativeUsbBridge.close() }
            nativeRawOpen = false
            runCatching { ownerSession?.close() }
            ownerSession = null
            runCatching { connection?.close() }
            rawInterface = null
            rawReadEndpoint = null
            rawWriteEndpoint = null
            port = null
            connection = null
            return
        }

        // Deassert modem control lines before closing so native-USB CDC
        // devices see a clean host disconnect and can reinitialize.
        if (usesSafeCloseOrder) {
            // Drop RTS before DTR for all supported UART bridge and CDC-ACM
            // devices. On CH34x, dropping DTR first while RTS is still
            // asserted transitions through 0xBF (EN low), which pulses the
            // reset line and produces a POWERONRESET boot log on close.
            // Other auto-reset bridges can similarly glitch EN if RTS is
            // left asserted.
            Log.i("NRSuiteWire", "closing transport")
            runCatching { port?.setRTS(false) }
            runCatching { port?.setDTR(false) }
        } else {
            runCatching { port?.setDTR(false) }
            runCatching { port?.setRTS(false) }
        }
        runCatching { port?.close() }
        runCatching { connection?.close() }
        port = null
        connection = null
    }

    private fun ch34xDivisor(baud: Int): Int {
        var factor = 1532620800 / baud
        var divisor = 3
        while (factor > 0xFFF0 && divisor > 0) {
            factor = factor shr 3
            divisor -= 1
        }
        factor = 0x10000 - factor
        return ((factor and 0xFF00) or divisor) or 0x80
    }

    /**
     * Open a native raw UART bridge through the separate-process fd handoff,
     * then run the chip-specific native register init.
     *
     * Returns false instead of throwing so the caller can fall back to the
     * normal usb-serial-for-android path.
     */
    private fun tryOpenNativeRaw(
        usbDevice: UsbDevice,
        chipLabel: String,
        initNative: () -> Boolean,
    ): Boolean {
        var selectedInterface: UsbInterface? = null
        var selectedRead: UsbEndpoint? = null
        var selectedWrite: UsbEndpoint? = null

        for (index in 0 until usbDevice.interfaceCount) {
            val candidate = usbDevice.getInterface(index)
            var readEndpoint: UsbEndpoint? = null
            var writeEndpoint: UsbEndpoint? = null
            for (endpointIndex in 0 until candidate.endpointCount) {
                val endpoint = candidate.getEndpoint(endpointIndex)
                if (endpoint.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                if (endpoint.direction == UsbConstants.USB_DIR_IN) readEndpoint = endpoint
                else writeEndpoint = endpoint
            }
            if (readEndpoint != null && writeEndpoint != null) {
                selectedInterface = candidate
                selectedRead = readEndpoint
                selectedWrite = writeEndpoint
                break
            }
        }

        val iface = selectedInterface
        val readEndpoint = selectedRead
        val writeEndpoint = selectedWrite
        if (iface == null || readEndpoint == null || writeEndpoint == null) {
            Log.w("NRSuiteWire", "$chipLabel native handoff: no bulk endpoints")
            return false
        }

        Log.d(
            "NRSuiteWire",
            "$chipLabel endpoints read=${readEndpoint.address} " +
                "maxPacket=${readEndpoint.maxPacketSize} " +
                "write=${writeEndpoint.address} " +
                "maxPacket=${writeEndpoint.maxPacketSize}",
        )

        val session = try {
            UsbDeviceOwnerClient.openDevice(usbDevice.deviceName)
        } catch (t: Throwable) {
            Log.w("NRSuiteWire", "$chipLabel native handoff: owner service failed", t)
            return false
        }

        try {
            val fd = session.fd
            if (fd < 0) {
                throw IOException("$chipLabel native fd is invalid: $fd")
            }
            if (!NativeUsbBridge.open(fd, iface.id, readEndpoint.address, writeEndpoint.address)) {
                throw IOException("$chipLabel native libusb open failed")
            }
            nativeRawOpen = true

            if (!initNative()) {
                throw IOException("$chipLabel native init failed")
            }
        } catch (t: Throwable) {
            nativeRawOpen = false
            runCatching { NativeUsbBridge.close() }
            runCatching { session.close() }
            Log.w("NRSuiteWire", "$chipLabel native handoff failed; falling back", t)
            return false
        }

        ownerSession = session
        connection = null
        rawInterface = iface
        rawReadEndpoint = readEndpoint
        rawWriteEndpoint = writeEndpoint
        nativeRawOpen = true
        port = null
        Log.i(
            "NRSuiteWire",
            "raw $chipLabel opened read=${readEndpoint.address} " +
                "write=${writeEndpoint.address} native=libusb ownerFd=${session.fd}",
        )
        return true
    }

    /**
     * Configure a CP210x/FTDI port with usb-serial-for-android, release its
     * Java interface claim, and move the bulk endpoints to native libusb.
     *
     * The Java API control path is kept because it already contains the
     * per-chip baud and line-control sequences. Only bulk I/O moves native,
     * which is the layer that has been observed to report success while the
     * bytes never reach the ESP32.
     */
    private fun openLibraryConfiguredNativeRaw(
        usbDevice: UsbDevice,
        openedConnection: UsbDeviceConnection,
        openedPort: UsbSerialPort,
    ): Boolean {
        if (openedPort.portNumber !in 0 until usbDevice.interfaceCount) {
            Log.w("NRSuiteWire", "Native handoff: invalid port number ${openedPort.portNumber}")
            return false
        }
        val iface = usbDevice.getInterface(openedPort.portNumber)
        val epIn = openedPort.readEndpoint
        val epOut = openedPort.writeEndpoint
        if (epIn == null || epOut == null) {
            Log.w("NRSuiteWire", "Native handoff: missing bulk endpoints")
            return false
        }

        Log.d(
            "NRSuiteWire",
            "Native handoff ${driver.javaClass.simpleName}: " +
                "iface=${iface.id} read=${epIn.address}/${epIn.maxPacketSize} " +
                "write=${epOut.address}/${epOut.maxPacketSize}",
        )

        // Release the Java-side claim first. libusb must be the only owner
        // of the interface before it wraps the UsbDeviceConnection fd.
        val closedPort = runCatching { openedPort.close() }.isSuccess
        if (!closedPort) {
            runCatching { openedConnection.releaseInterface(iface) }
        }

        val fd = openedConnection.fileDescriptor
        if (fd >= 0 && NativeUsbBridge.open(fd, iface.id, epIn.address, epOut.address)) {
            connection = openedConnection
            rawInterface = iface
            rawReadEndpoint = epIn
            rawWriteEndpoint = epOut
            port = null
            nativeRawOpen = true
            Log.i(
                "NRSuiteWire",
                "raw ${driver.javaClass.simpleName} opened read=${epIn.address} " +
                    "write=${epOut.address} native=libusb",
            )
            return true
        }

        // Native handoff failed. Re-open the normal Java port so the board
        // remains usable instead of failing the whole connection.
        runCatching { NativeUsbBridge.close() }
        val fallback = runCatching {
            openedPort.open(openedConnection)
            openedPort.setParameters(
                baudRate,
                UsbSerialPort.DATABITS_8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE,
            )
            configureLineState(openedPort)
        }.isSuccess

        if (fallback) {
            connection = openedConnection
            port = openedPort
            rawInterface = null
            rawReadEndpoint = null
            rawWriteEndpoint = null
            nativeRawOpen = false
            Log.w(
                "NRSuiteWire",
                "Native raw handoff failed; using usb-serial-for-android fallback",
            )
            return true
        }
        return false
    }

    private fun clearJavaHalts(connection: UsbDeviceConnection, serialPort: UsbSerialPort) {
        runCatching {
            connection.controlTransfer(
                0x02, // Host-to-device | standard | endpoint recipient
                0x01, // CLEAR_FEATURE
                0x00, // ENDPOINT_HALT
                serialPort.readEndpoint.address,
                null,
                0,
                500,
            )
        }
        runCatching {
            connection.controlTransfer(
                0x02,
                0x01,
                0x00,
                serialPort.writeEndpoint.address,
                null,
                0,
                500,
            )
        }
    }

    private fun configureLineState(serialPort: UsbSerialPort) {
        if (usesDtrOnlyLineState) {
            // WCH CH340/CH341 and CDC-ACM devices (native ESP32 USB, CH9102,
            // etc.) idle with DTR asserted and RTS released.
            //
            // Release RTS first so a previous DTR+RTS state cannot keep the
            // chip reset while this port opens. On CH34x the combined state
            // maps to vendor byte 0x9F, and the DTR-first close order can
            // pulse EN through 0xBF.
            runCatching { serialPort.setRTS(false) }
            runCatching { serialPort.setDTR(true) }
        } else if (usesDualReleasedLineState) {
            // CP210x, FTDI, and Prolific bridge boards use the classic
            // ESP32 auto-reset circuit. The canonical esptool/esp-idf idle
            // state is both DTR and RTS deasserted. Raise IO0 first, then EN,
            // so a stale reset state cannot leave the chip in the bootloader.
            runCatching { serialPort.setDTR(false) }
            runCatching { serialPort.setRTS(false) }
        } else {
            // Unknown serial driver: preserve the previous best-effort state.
            runCatching { serialPort.setDTR(true) }
            runCatching { serialPort.setRTS(true) }
        }
    }

    /**
     * FTDI USB packets begin with two modem-status bytes. The vendor driver
     * strips them before returning data; raw libusb does not, so mirror that
     * filter for the native FTDI path.
     */
    private fun filterFtdiRead(buffer: ByteArray, rawCount: Int): Int {
        val packetSize = rawReadEndpoint?.maxPacketSize?.takeIf { it > 2 } ?: 64
        var source = 0
        var destination = 0
        while (source < rawCount) {
            val chunk = minOf(packetSize, rawCount - source)
            val payload = chunk - 2
            if (payload > 0) {
                System.arraycopy(buffer, source + 2, buffer, destination, payload)
                destination += payload
            }
            source += chunk
        }
        return destination
    }

    /**
     * WCH CH340/CH341 and CDC-ACM devices use DTR asserted, RTS released as
     * their known-good idle state.
     */
    private val usesDtrOnlyLineState: Boolean
        get() = driver is Ch34xSerialDriver || driver is CdcAcmSerialDriver

    /**
     * CP210x, FTDI, and Prolific bridge boards use the classic ESP32
     * auto-reset circuit. The canonical esptool/esp-idf idle state is both
     * DTR and RTS deasserted.
     */
    private val usesDualReleasedLineState: Boolean
        get() = driver is Cp21xxSerialDriver ||
            driver is FtdiSerialDriver ||
            driver is ProlificSerialDriver

    /**
     * True when the transport should release RTS before DTR on close to
     * avoid walking through the asserted-RTS/reset state.
     */
    private val usesSafeCloseOrder: Boolean
        get() = usesDtrOnlyLineState || usesDualReleasedLineState

    companion object {
        const val DEFAULT_BAUD_RATE = 115200

        /**
         * Drivers for which [UsbSerialTransport] can move bulk I/O to the
         * native libusb fd path. Used by the session lifecycle to decide
         * whether the already-open transport can be idled and reused.
         */
        fun supportsNativeRaw(driver: UsbSerialDriver): Boolean =
            driver is Ch34xSerialDriver ||
                driver is Cp21xxSerialDriver ||
                driver is FtdiSerialDriver
    }
}
