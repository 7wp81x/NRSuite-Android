package com.swp81x.nrsuite.core.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber

data class UsbSerialDevice(
    val device: UsbDevice,
    val driver: UsbSerialDriver,
) {
    val displayName: String
        get() = device.productName?.takeIf { it.isNotBlank() } ?: "USB serial device"
}

object UsbSerialDeviceCatalog {
    fun list(usbManager: UsbManager): List<UsbSerialDevice> {
        return UsbSerialProber.getDefaultProber()
            .findAllDrivers(usbManager)
            .map { driver -> UsbSerialDevice(driver.device, driver) }
            .sortedBy { it.displayName.lowercase() }
    }

    fun find(usbManager: UsbManager, device: UsbDevice): UsbSerialDevice? {
        return list(usbManager).firstOrNull {
            it.device.deviceId == device.deviceId && it.device.deviceName == device.deviceName
        }
    }
}
