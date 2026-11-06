package com.swp81x.nrsuite.core.session

enum class ConnectionDiagnosticRecommendation {
    FLASH_FIRMWARE,
    RETRY,
    REPORT_BUG,
}

data class ConnectionDiagnostic(
    val permissionGranted: Boolean,
    val deviceDetected: Boolean,
    val portOpened: Boolean,
    val handshakeAttempts: Int,
    val sawDeviceActivity: Boolean,
    val sawRomBootLog: Boolean,
    val sawInvalidFirmwareHeader: Boolean,
    val sawProtocolFrame: Boolean,
    val detectedChip: String? = null,
    val transportError: String? = null,
    val rawLogPreview: String? = null,
) {
    val recommendation: ConnectionDiagnosticRecommendation
        get() = when {
            sawRomBootLog || sawInvalidFirmwareHeader || !sawProtocolFrame -> {
                if (portOpened) ConnectionDiagnosticRecommendation.FLASH_FIRMWARE
                else ConnectionDiagnosticRecommendation.RETRY
            }
            transportError != null -> ConnectionDiagnosticRecommendation.RETRY
            else -> ConnectionDiagnosticRecommendation.REPORT_BUG
        }

    val summary: String
        get() = when {
            sawInvalidFirmwareHeader || sawRomBootLog ->
                "The device appears to be in ROM bootloader or has no valid NRSuite firmware."
            !portOpened ->
                "The USB serial port could not be opened."
            transportError != null ->
                "The USB connection was interrupted while checking the device."
            !sawProtocolFrame ->
                "The device did not return an NRSuite protocol response."
            else ->
                "The device responded, but the NRSuite handshake did not complete."
        }
}

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Diagnosing : ConnectionState
    data class Connected(
        val chip: String?,
        val firmwareVersion: String?,
        val features: Set<String> = emptySet(),
        val deviceId: String? = null,
        val country: String? = null,
        val allowed2gChannels: List<Int> = emptyList(),
        val allowed5gChannels: List<Int> = emptyList(),
        val irTxGpio: Int? = null,
        val irRxGpio: Int? = null,
        val irTxGpios: List<Int> = emptyList(),
        val irRxGpios: List<Int> = emptyList(),
        val irRxActive: Boolean = false,
        // Device telemetry from STATUS. Nullable because older firmware only
        // reports a subset of these fields.
        val uptimeMs: Long? = null,
        val heapFree: Long? = null,
        val heapTotal: Long? = null,
        val heapUsed: Long? = null,
        val heapMinFree: Long? = null,
        val heapMaxAlloc: Long? = null,
        val psramFree: Long? = null,
        val psramTotal: Long? = null,
        val chipCores: Int? = null,
        val cpuMhz: Int? = null,
        val temperatureC: Double? = null,
        val flashSize: Long? = null,
        val sdkVersion: String? = null,
        val protocolVersion: Int? = null,
        val protocolMinor: Int? = null,
        val transport: String? = null,
    ) : ConnectionState

    data class Failed(
        val message: String,
        val diagnostic: ConnectionDiagnostic? = null,
    ) : ConnectionState
}
