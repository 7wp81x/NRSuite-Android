package com.swp81x.nrsuite.core.usb

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import java.io.IOException

/**
 * [NrTransport] backed by usb-serial-for-android.
 *
 * Supports the generic CDC ACM driver, CP210x, CH34x, FTDI, and other drivers
 * registered by the library's default prober.
 */
class UsbSerialTransport(
    private val usbManager: UsbManager,
    private val driver: UsbSerialDriver,
    private val baudRate: Int = DEFAULT_BAUD_RATE,
) : NrTransport {

    private var connection: UsbDeviceConnection? = null
    private var port: UsbSerialPort? = null

    override val isOpen: Boolean
        get() = port?.isOpen == true

    @Synchronized
    override fun open() {
        if (isOpen) return
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
        } catch (t: Throwable) {
            runCatching { openedPort.close() }
            runCatching { openedConnection.close() }
            throw if (t is IOException) t else IOException("Could not configure serial port", t)
        }

        connection = openedConnection
        port = openedPort

        // Native CDC devices often need DTR/RTS asserted before they send data.
        // Some UART-bridge drivers do not support these calls; ignore failures.
        runCatching { openedPort.setDTR(true) }
        runCatching { openedPort.setRTS(true) }
    }

    @Synchronized
    override fun read(buffer: ByteArray, timeoutMs: Int): Int {
        val activePort = port ?: throw IOException("Serial port is not open")
        return activePort.read(buffer, timeoutMs)
    }

    @Synchronized
    override fun write(data: ByteArray, timeoutMs: Int) {
        val activePort = port ?: throw IOException("Serial port is not open")
        activePort.write(data, timeoutMs)
    }

    @Synchronized
    override fun close() {
        runCatching { port?.close() }
        runCatching { connection?.close() }
        port = null
        connection = null
    }

    companion object {
        const val DEFAULT_BAUD_RATE = 115200
    }
}
