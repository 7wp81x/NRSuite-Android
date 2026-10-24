package com.swp81x.nrsuite.core.rfid

/**
 * One read-only ISO14443A observation received from the ESP32 PN532 module.
 *
 * [observedAtMs] is the Android wall-clock receipt time. The firmware's
 * uptime-based timestamp is retained separately for diagnostics.
 */
data class RfidTagObservation(
    val uid: String,
    val atqa: String,
    val sak: String,
    val type: String,
    val observedAtMs: Long,
    val firmwareTimestampMs: Long? = null,
    val readOnly: Boolean = true,
)

data class RfidUiState(
    val connected: Boolean = false,
    val configured: Boolean = false,
    val interfaceName: String = "i2c",
    val sdaGpio: Int? = null,
    val sclGpio: Int? = null,
    val firmwareVersion: String? = null,
    val scanning: Boolean = false,
    val starting: Boolean = false,
    val stopping: Boolean = false,
    val configSaving: Boolean = false,
    val availableGpios: List<Int> = emptyList(),
    val tags: List<RfidTagObservation> = emptyList(),
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val lastUpdatedAtMs: Long = 0L,
) {
    val running: Boolean get() = scanning || starting
    val lastTag: RfidTagObservation? get() = tags.firstOrNull()
}
