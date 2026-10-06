package com.swp81x.nrsuite.core.usb

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.Ch34xSerialDriver
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

            // Host-side close/reopen can leave the bulk endpoints halted on
            // some TinyUSB composite devices. Clear HALT on both endpoints
            // before configuring the port so a software reconnect can recover
            // without a physical unplug.
            runCatching {
                openedConnection.controlTransfer(
                    0x02, // Host-to-device | standard | endpoint recipient
                    0x01, // CLEAR_FEATURE
                    0x00, // ENDPOINT_HALT
                    openedPort.readEndpoint.address,
                    null,
                    0,
                    500,
                )
            }
            runCatching {
                openedConnection.controlTransfer(
                    0x02,
                    0x01,
                    0x00,
                    openedPort.writeEndpoint.address,
                    null,
                    0,
                    500,
                )
            }

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

        if (usesDtrOnlyLineState) {
            // WCH CH340/CH341 and CDC-ACM devices (native ESP32 USB, CH9102,
            // etc.) idle with DTR asserted and RTS released.
            //
            // Release RTS first so a previous DTR+RTS state cannot keep the
            // chip reset while this port opens. On CH34x the combined state
            // maps to vendor byte 0x9F, and the DTR-first close order can
            // pulse EN through 0xBF.
            runCatching { openedPort.setRTS(false) }
            runCatching { openedPort.setDTR(true) }
        } else if (usesDualReleasedLineState) {
            // CP210x, FTDI, and Prolific bridge boards use the classic
            // ESP32 auto-reset circuit. The canonical esptool/esp-idf idle
            // state is both DTR and RTS deasserted. Raise IO0 first, then EN,
            // so a stale reset state cannot leave the chip in the bootloader.
            runCatching { openedPort.setDTR(false) }
            runCatching { openedPort.setRTS(false) }
        } else {
            // Unknown serial driver: preserve the previous best-effort state.
            runCatching { openedPort.setDTR(true) }
            runCatching { openedPort.setRTS(true) }
        }
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
        // Deassert modem control lines before closing so native-USB CDC
        // devices see a clean host disconnect and can reinitialize.
        if (usesSafeCloseOrder) {
            // Drop RTS before DTR for all supported UART bridge and CDC-ACM
            // devices. On CH34x, dropping DTR first while RTS is still
            // asserted transitions through 0xBF (EN low), which pulses the
            // reset line and produces a POWERONRESET boot log on close.
            // Other auto-reset bridges can similarly glitch EN if RTS is
            // left asserted.
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
    }
}
