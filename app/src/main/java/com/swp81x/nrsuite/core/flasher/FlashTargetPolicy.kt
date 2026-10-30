package com.swp81x.nrsuite.core.flasher

/**
 * Select the esptool-compatible reset strategy for a USB serial target.
 *
 * esptool uses the USB-JTAG-Serial reset sequence when the USB PID is 0x1001
 * (Espressif USB-Serial/JTAG, normally VID 0x303A). External bridge chips use
 * the classic DTR/RTS auto-reset circuit. Do not classify every CDC-ACM device
 * as USB-JTAG: some UART bridge boards also expose CDC-ACM but still use the
 * classic reset circuit.
 */
internal fun recommendedEsp32ResetMode(vendorId: Int, productId: Int): Esp32Flasher.ResetMode {
    val isEspressifUsbJtagSerial = vendorId == 0x303A && productId == 0x1001
    return if (isEspressifUsbJtagSerial) {
        Esp32Flasher.ResetMode.USB_JTAG
    } else {
        Esp32Flasher.ResetMode.CLASSIC
    }
}
