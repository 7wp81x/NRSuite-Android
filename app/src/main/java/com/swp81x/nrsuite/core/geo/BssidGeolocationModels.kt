package com.swp81x.nrsuite.core.geo

enum class BssidGeolocationErrorKind {
    INVALID_INPUT,
    NOT_FOUND,
    NO_NETWORK,
    TIMEOUT,
    RATE_LIMITED,
    SERVICE_ERROR,
    PARSE_ERROR,
}

data class BssidGeolocationError(
    val kind: BssidGeolocationErrorKind,
    val message: String,
)

sealed interface BssidGeolocationState {
    data object Idle : BssidGeolocationState
    data class Loading(val bssid: String) : BssidGeolocationState
    data class Success(
        val result: BssidGeolocationResult,
        val fromCache: Boolean = false,
        val warning: String? = null,
    ) : BssidGeolocationState
    data class Failure(
        val bssid: String,
        val error: BssidGeolocationError,
    ) : BssidGeolocationState
}

data class BssidGeolocationHistoryEntry(
    val bssid: String,
    val vendor: String?,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double?,
    val source: String,
    val firstLookedUpAtMs: Long,
    val lastLookedUpAtMs: Long,
    val lookupCount: Int,
) {
    fun toResult(): BssidGeolocationResult = BssidGeolocationResult(
        bssid = bssid,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        source = source,
    )
}
