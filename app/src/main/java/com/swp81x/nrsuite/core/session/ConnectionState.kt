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
    ) : ConnectionState

    data class Failed(val message: String) : ConnectionState
}
