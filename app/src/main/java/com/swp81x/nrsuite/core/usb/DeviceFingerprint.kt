package com.swp81x.nrsuite.core.usb

/**
 * Stable key for a logical USB device. Kept separate from [android.hardware.usb.UsbDevice]
 * so the composition rule can be unit-tested without Android framework classes.
 *
 * Device-name/device-id are included because many ESP32 boards report a
 * placeholder serial number ("0"), which would otherwise collide when two
 * identical boards are attached at the same time.
 */
internal fun buildDeviceFingerprintKey(
    serial: String?,
    vendorId: Int,
    productId: Int,
    manufacturer: String?,
    productName: String?,
    deviceName: String?,
    deviceId: Int,
): String = listOf(
    serial.orEmpty(),
    vendorId.toString(),
    productId.toString(),
    manufacturer.orEmpty(),
    productName.orEmpty(),
    deviceName.orEmpty(),
    deviceId.toString(),
).joinToString("|")
