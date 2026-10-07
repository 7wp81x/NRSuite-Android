package com.swp81x.nrsuite.core.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.Ch34xSerialDriver
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.Cp21xxSerialDriver
import com.hoho.android.usbserial.driver.FtdiSerialDriver
import com.hoho.android.usbserial.driver.ProlificSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber

data class UsbSerialDevice(
    val device: UsbDevice,
    val driver: UsbSerialDriver,
) {
    val displayName: String
        get() {
            val base = device.productName?.takeIf { it.isNotBlank() } ?: "USB Serial"
            val chip = driverChipLabel(driver) ?: return base
            return "$base ($chip)"
        }
}

private fun driverChipLabel(driver: UsbSerialDriver): String? = when (driver) {
    is Ch34xSerialDriver -> "CH340"
    is Cp21xxSerialDriver -> "CP210x"
    is FtdiSerialDriver -> "FTDI"
    is ProlificSerialDriver -> "PL2303"
    is CdcAcmSerialDriver -> "CDC-ACM"
    else -> driver.javaClass.simpleName
        .removeSuffix("SerialDriver")
        .takeIf { it.isNotBlank() }
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
