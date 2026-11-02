package com.swp81x.nrsuite.core.session

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
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
        val heapMinFree: Long? = null,
        val heapMaxAlloc: Long? = null,
        val psramFree: Long? = null,
        val psramTotal: Long? = null,
        val chipCores: Int? = null,
        val cpuMhz: Int? = null,
        val flashSize: Long? = null,
        val sdkVersion: String? = null,
        val protocolVersion: Int? = null,
        val protocolMinor: Int? = null,
    ) : ConnectionState

    data class Failed(val message: String) : ConnectionState
}
