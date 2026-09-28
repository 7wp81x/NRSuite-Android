package com.swp81x.nrsuite.core.session

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(
        val chip: String?,
        val firmwareVersion: String?,
        val features: Set<String> = emptySet(),
        val deviceId: String? = null,
    ) : ConnectionState

    data class Failed(val message: String) : ConnectionState
}
