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
    ) : ConnectionState

    data class Failed(val message: String) : ConnectionState
}
