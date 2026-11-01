package com.swp81x.nrsuite.core.flasher

/**
 * True when the USB descriptor belongs to NRSuite's running Arduino TinyUSB
 * composite firmware rather than the ESP ROM USB-Serial/JTAG download port.
 *
 * The DTR/RTS reset sequence used by esptool only works against the ROM
 * USB-Serial/JTAG peripheral. Arduino's USBCDC implementation has a separate
 * four-step line-state sequence that restarts the running firmware into the
 * ROM bootloader.
 */
internal fun looksLikeTinyUsbFirmware(
    productName: String?,
    interfaceNames: List<String?>,
): Boolean {
    if (productName?.contains("TinyUSB", ignoreCase = true) == true) return true
    return interfaceNames.any { name ->
        name?.contains("TinyUSB", ignoreCase = true) == true
    }
}

/**
 * Ask the running Arduino TinyUSB CDC firmware to reboot into the ROM
 * bootloader using the line-state sequence implemented by USBCDC::_onLineState.
 *
 * Required transitions:
 *   DTR=0, RTS=1 -> state 1
 *   DTR=1, RTS=1 -> state 2
 *   DTR=1, RTS=0 -> state 3
 *   DTR=0, RTS=0 -> usb_persist_restart(RESTART_BOOTLOADER)
 *
 * The caller must expect the USB device to disconnect and re-enumerate as the
 * ROM USB-Serial/JTAG interface. Flashing cannot continue on this transport.
 */
internal fun triggerTinyUsbBootloaderReboot(transport: FlasherTransport) {
    transport.open()
    try {
        Thread.sleep(TINYUSB_RESET_SETTLE_MS)
        transport.setRts(true)
        Thread.sleep(TINYUSB_RESET_STEP_MS)
        transport.setDtr(true)
        Thread.sleep(TINYUSB_RESET_STEP_MS)
        transport.setRts(false)
        Thread.sleep(TINYUSB_RESET_STEP_MS)
        transport.setDtr(false)
        // Return promptly; the caller must wait for USB re-enumeration.
        Thread.sleep(TINYUSB_RESET_FLUSH_MS)
    } finally {
        runCatching { transport.close() }
    }
}

private const val TINYUSB_RESET_SETTLE_MS = 150L
private const val TINYUSB_RESET_STEP_MS = 100L
private const val TINYUSB_RESET_FLUSH_MS = 50L
