package com.swp81x.nrsuite.core.flasher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TinyUsbBootloaderTest {

    @Test
    fun `detects running TinyUSB composite firmware`() {
        assertTrue(
            looksLikeTinyUsbFirmware(
                productName = "Espressif ESP32-S3-DevKitC-1-N8",
                interfaceNames = listOf("TinyUSB MSC", "TinyUSB HID", "TinyUSB CDC", null),
            ),
        )
        assertTrue(
            looksLikeTinyUsbFirmware(
                productName = "TinyUSB CDC device",
                interfaceNames = emptyList(),
            ),
        )
        assertFalse(
            looksLikeTinyUsbFirmware(
                productName = "USB JTAG/serial debug unit",
                interfaceNames = listOf("CDC data", "Vendor specific"),
            ),
        )
    }

    @Test
    fun `uses the Arduino USBCDC bootloader restart sequence`() {
        val transport = FakeTriggerTransport()
        triggerTinyUsbBootloaderReboot(transport)

        assertTrue(transport.opened)
        assertEquals(
            listOf(
                "DTR=false RTS=true",
                "DTR=true RTS=true",
                "DTR=true RTS=false",
                "DTR=false RTS=false",
            ),
            transport.lineEvents,
        )
        assertTrue(transport.closed)
    }

    private class FakeTriggerTransport : FlasherTransport {
        override var isOpen: Boolean = false
            private set
        override var currentBaudRate: Int = 115_200
            private set

        var opened = false
        var closed = false
        var dtrState = false
        var rtsState = false
        val lineEvents = mutableListOf<String>()

        override fun open() {
            opened = true
            isOpen = true
        }

        override fun close() {
            closed = true
            isOpen = false
        }

        override fun setBaudRate(baudRate: Int) {
            currentBaudRate = baudRate
        }

        override fun read(buffer: ByteArray, timeoutMs: Int): Int = 0

        override fun write(data: ByteArray, timeoutMs: Int) = Unit

        override fun flushInput() = Unit

        override fun flushOutput() = Unit

        override fun setDtr(value: Boolean) {
            dtrState = value
            lineEvents += "DTR=$dtrState RTS=$rtsState"
        }

        override fun setRts(value: Boolean) {
            rtsState = value
            lineEvents += "DTR=$dtrState RTS=$rtsState"
        }
    }
}
