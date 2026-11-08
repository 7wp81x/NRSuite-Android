package com.swp81x.nrsuite.core.flasher

/** Per-device flash lifecycle shown on the Home and Devices USB rows. */
enum class FirmwareFlashDevicePhase {
    FLASHING,
    FLASHED,
}

/**
 * Identifies the USB device that owns the current flash operation/result.
 *
 * The fingerprint is preferred for normal matching. Device id is kept as a
 * fallback because USB re-enumeration during flashing can change the descriptor
 * fingerprint while Android still assigns the same device id for the session.
 */
data class FirmwareFlashDeviceState(
    val deviceId: Int,
    val fingerprint: String,
    val phase: FirmwareFlashDevicePhase,
) {
    fun matches(deviceId: Int, fingerprint: String): Boolean =
        this.deviceId == deviceId ||
            (this.fingerprint.isNotBlank() && this.fingerprint == fingerprint)
}
