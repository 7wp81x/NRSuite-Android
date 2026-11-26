package com.swp81x.nrsuite.core.flasher

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import java.io.IOException

/**
 * [FlasherTransport] backed directly by usb-serial-for-android.
 */
class UsbSerialFlasherTransport(
    private val usbManager: UsbManager,
    private val driver: UsbSerialDriver,
    private val baudRate: Int = 115200,
    private val onLog: (String) -> Unit = {},
) : FlasherTransport {

    private var connection: UsbDeviceConnection? = null
    private var port: UsbSerialPort? = null
    private var dtrState = false
    private var rtsState = false
    private var activeBaudRate = baudRate

    override val isOpen: Boolean
        get() = port?.isOpen == true

    override val currentBaudRate: Int
        get() = activeBaudRate

    override fun open() {
        if (isOpen) return
        val startedMs = System.nanoTime() / 1_000_000L
        val usbDevice = driver.device
        if (!usbManager.hasPermission(usbDevice)) {
            throw IOException("USB permission has not been granted for ${usbDevice.deviceName}")
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
            openedPort.setParameters(
                baudRate,
                UsbSerialPort.DATABITS_8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE,
            )
            activeBaudRate = baudRate
            // Put the auto-reset circuit into esptool's idle state before any
            // reset sequence. Raise IO0 first, then EN, so opening the port
            // cannot leave the chip held in reset or download mode.
            openedPort.setDTR(false)
            openedPort.setRTS(false)
            dtrState = false
            rtsState = false
            // Purge both directions. A previous app/session may have left a
            // partial SLIP response or stale bytes queued in either endpoint.
            runCatching { openedPort.purgeHwBuffers(true, true) }
        } catch (t: Throwable) {
            runCatching { openedPort.close() }
            runCatching { openedConnection.close() }
            throw if (t is IOException) t else IOException("Could not configure serial port", t)
        }

        connection = openedConnection
        port = openedPort
        val elapsedMs = (System.nanoTime() / 1_000_000L) - startedMs
        onLog("USB serial port open/config completed in ${elapsedMs} ms at $baudRate baud; DTR=false RTS=false.")
    }

    override fun setBaudRate(baudRate: Int) {
        val activePort = port ?: throw IOException("Serial port is not open")
        activePort.setParameters(
            baudRate,
            UsbSerialPort.DATABITS_8,
            UsbSerialPort.STOPBITS_1,
            UsbSerialPort.PARITY_NONE,
        )
        activeBaudRate = baudRate
        onLog("USB serial baud rate changed to $baudRate.")
    }

    override fun read(buffer: ByteArray, timeoutMs: Int): Int {
        val activePort = port ?: throw IOException("Serial port is not open")
        return activePort.read(buffer, timeoutMs)
    }

    override fun write(data: ByteArray, timeoutMs: Int) {
        val activePort = port ?: throw IOException("Serial port is not open")
        activePort.write(data, timeoutMs)
    }

    override fun flushInput() {
        runCatching { port?.purgeHwBuffers(true, false) }
    }

    override fun flushOutput() {
        runCatching { port?.purgeHwBuffers(false, true) }
    }

    override fun setDtr(value: Boolean) {
        runCatching { port?.setDTR(value) }
        dtrState = value
        onLog("Reset line DTR=${value} (RTS=$rtsState).")
    }

    override fun setRts(value: Boolean) {
        runCatching { port?.setRTS(value) }
        rtsState = value
        onLog("Reset line RTS=${value} (DTR=$dtrState).")
    }

    override fun close() {
        // Release both control lines before closing. This prevents a bridge
        // driver's close-time line transition from accidentally resetting the
        // chip or leaving BOOT asserted.
        runCatching { port?.setDTR(false) }
        runCatching { port?.setRTS(false) }
        runCatching { port?.close() }
        runCatching { connection?.close() }
        port = null
        connection = null
        dtrState = false
        rtsState = false
        activeBaudRate = baudRate
    }
}
