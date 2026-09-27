package com.swp81x.nrsuite.core.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DeviceFingerprintTest {
    @Test
    fun samePhysicalIdentityProducesSameKey() {
        val first = buildDeviceFingerprintKey(
            serial = "ABC123",
            vendorId = 0x303A,
            productId = 0x1001,
            manufacturer = "Espressif",
            productName = "NRSuite ESP32-S3",
            deviceName = "/dev/bus/usb/001/052",
            deviceId = 52,
        )
        val second = buildDeviceFingerprintKey(
            serial = "ABC123",
            vendorId = 0x303A,
            productId = 0x1001,
            manufacturer = "Espressif",
            productName = "NRSuite ESP32-S3",
            deviceName = "/dev/bus/usb/001/052",
            deviceId = 52,
        )
        assertEquals(first, second)
    }

    @Test
    fun differentDevicesProduceDifferentKeys() {
        val companion = buildDeviceFingerprintKey(
            serial = "ESP32-A",
            vendorId = 0x303A,
            productId = 0x1001,
            manufacturer = "Espressif",
            productName = "ESP32-S3",
            deviceName = "/dev/bus/usb/001/052",
            deviceId = 52,
        )
        val hidGadget = buildDeviceFingerprintKey(
            serial = "HID-B",
            vendorId = 0x1234,
            productId = 0x5678,
            manufacturer = "Example",
            productName = "BadUSB Gadget",
            deviceName = "/dev/bus/usb/001/054",
            deviceId = 54,
        )
        assertNotEquals(companion, hidGadget)
    }

    @Test
    fun identicalEsp32SerialZeroStillProducesDifferentKeys() {
        val first = buildDeviceFingerprintKey(
            serial = "0",
            vendorId = 0x303A,
            productId = 0x0002,
            manufacturer = "Espressif",
            productName = "ESP32-S2",
            deviceName = "/dev/bus/usb/001/052",
            deviceId = 52,
        )
        val second = buildDeviceFingerprintKey(
            serial = "0",
            vendorId = 0x303A,
            productId = 0x0002,
            manufacturer = "Espressif",
            productName = "ESP32-S2",
            deviceName = "/dev/bus/usb/001/054",
            deviceId = 54,
        )
        assertNotEquals(first, second)
    }
}
