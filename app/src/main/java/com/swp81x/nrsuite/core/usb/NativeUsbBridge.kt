package com.swp81x.nrsuite.core.usb

/**
 * JNI bridge to libusb for the Android USB file descriptor path.
 *
 * The Android UsbDeviceConnection fd is opened through UsbManager first;
 * this wrapper only performs the libusb_wrap_sys_device() + bulk-transfer
 * layer used by the working Termux/espbridge no-root path.
 */
internal object NativeUsbBridge {
    init {
        System.loadLibrary("nrsuiteusb")
    }

    external fun open(fd: Int, interfaceNumber: Int, epIn: Int, epOut: Int): Boolean
    external fun read(buffer: ByteArray, timeoutMs: Int): Int
    external fun write(buffer: ByteArray, timeoutMs: Int): Boolean
    external fun ch34xInit(divisor: Int): Boolean
    external fun cp21xxInit(baudRate: Int): Boolean
    external fun ftdiInit(baudRate: Int): Boolean
    external fun resetEndpoints(): Boolean
    external fun close()
}
