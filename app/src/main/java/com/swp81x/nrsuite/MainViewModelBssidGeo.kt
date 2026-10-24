package com.swp81x.nrsuite

import android.util.Log
import com.swp81x.nrsuite.core.geo.BssidGeolocationClient
import com.swp81x.nrsuite.core.geo.BssidGeolocationError
import com.swp81x.nrsuite.core.geo.BssidGeolocationErrorKind
import com.swp81x.nrsuite.core.geo.BssidGeolocationException
import com.swp81x.nrsuite.core.geo.BssidGeolocationHistoryEntry
import com.swp81x.nrsuite.core.geo.BssidGeolocationResult
import com.swp81x.nrsuite.core.geo.BssidGeolocationState
import com.swp81x.nrsuite.core.geo.BssidNearbyAccessPoint
import com.swp81x.nrsuite.core.geo.BssidNearbyState
import com.swp81x.nrsuite.core.geo.normalizeBssid
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.launch

private const val NEARBY_RADIUS_METERS = 5_000.0
private const val MAX_NEARBY_APS = 50

internal fun MainViewModel.loadBssidGeolocationHistoryImpl() {
    _bssidGeolocationHistory.value = bssidGeolocationHistoryStore.load()
}

internal fun MainViewModel.lookupBssidGeolocationImpl(rawBssid: String) {
    val bssid = normalizeBssid(rawBssid)
    _bssidNearbyState.value = BssidNearbyState.Idle
    _macLookupResult.value = ouiDatabaseRepository.detailedLookup(rawBssid)
    if (bssid == null) {
        _bssidGeolocationState.value = BssidGeolocationState.Failure(
            bssid = rawBssid,
            error = BssidGeolocationError(
                BssidGeolocationErrorKind.INVALID_INPUT,
                "Enter a valid BSSID / AP MAC address.",
            ),
        )
        return
    }

    val cached = _bssidGeolocationHistory.value.firstOrNull { it.bssid == bssid }
    _bssidGeolocationState.value = BssidGeolocationState.Loading(bssid)

    scope.launch {
        try {
            val result = BssidGeolocationClient.lookup(bssid)
            if (result == null) {
                _bssidGeolocationState.value = cached?.let {
                    BssidGeolocationState.Success(
                        result = it.toResult(),
                        fromCache = true,
                        warning = "BSSID is no longer returned by the service. Showing the saved result.",
                    )
                } ?: BssidGeolocationState.Failure(
                    bssid = bssid,
                    error = BssidGeolocationError(
                        BssidGeolocationErrorKind.NOT_FOUND,
                        "BSSID not found in the location database. It may be new, private, or unregistered.",
                    ),
                )
                return@launch
            }

            val vendor = ouiDatabaseRepository.detailedLookup(bssid)?.vendor
            recordBssidGeolocationHistoryImpl(result, vendor)
            _bssidGeolocationState.value = BssidGeolocationState.Success(result)
        } catch (t: Throwable) {
            val error = when (t) {
                is BssidGeolocationException -> BssidGeolocationError(t.kind, t.message)
                else -> BssidGeolocationError(
                    BssidGeolocationErrorKind.SERVICE_ERROR,
                    t.message ?: "Location lookup failed.",
                )
            }
            _bssidGeolocationState.value = cached?.let {
                BssidGeolocationState.Success(
                    result = it.toResult(),
                    fromCache = true,
                    warning = "${error.message} Showing the cached result.",
                )
            } ?: BssidGeolocationState.Failure(bssid, error)
        }
    }
}

internal fun MainViewModel.selectBssidGeolocationHistoryImpl(bssid: String) {
    val entry = _bssidGeolocationHistory.value.firstOrNull {
        it.bssid.equals(bssid, ignoreCase = true)
    } ?: return
    _bssidNearbyState.value = BssidNearbyState.Idle
    _macLookupResult.value = ouiDatabaseRepository.detailedLookup(entry.bssid)
    _bssidGeolocationState.value = BssidGeolocationState.Success(
        result = entry.toResult(),
        fromCache = true,
    )
}

internal fun MainViewModel.deleteBssidGeolocationHistoryImpl(bssid: String) {
    _bssidGeolocationHistory.value = _bssidGeolocationHistory.value.filterNot {
        it.bssid.equals(bssid, ignoreCase = true)
    }
    persistBssidGeolocationHistoryImpl()
}

internal fun MainViewModel.clearBssidGeolocationHistoryImpl() {
    _bssidGeolocationHistory.value = emptyList()
    persistBssidGeolocationHistoryImpl()
}

internal fun MainViewModel.clearBssidGeolocationImpl() {
    _macLookupResult.value = null
    _bssidGeolocationState.value = BssidGeolocationState.Idle
    _bssidNearbyState.value = BssidNearbyState.Idle
}

internal fun MainViewModel.setBssidNearbyApsEnabledImpl(enabled: Boolean) {
    if (!enabled) {
        Log.d("BssidNearby", "Nearby AP discovery disabled")
        _bssidNearbyState.value = BssidNearbyState.Idle
        return
    }

    val location = (_bssidGeolocationState.value as? BssidGeolocationState.Success)
        ?.result
        ?: return
    val seedBssid = location.bssid

    bssidNearbyCache[seedBssid]?.let { cached ->
        _bssidNearbyState.value = BssidNearbyState.Success(
            seedBssid = seedBssid,
            accessPoints = cached,
            radiusMeters = NEARBY_RADIUS_METERS,
        )
        return
    }

    Log.d("BssidNearby", "Loading nearby APs for seed=$seedBssid")
    _bssidNearbyState.value = BssidNearbyState.Loading(seedBssid)
    scope.launch {
        try {
            val discovered = BssidGeolocationClient.lookupNearby(seedBssid)
            val nearby = discovered
                .asSequence()
                .filterNot { it.bssid.equals(seedBssid, ignoreCase = true) }
                .mapNotNull { ap ->
                    val distance = distanceMeters(
                        location.latitude,
                        location.longitude,
                        ap.latitude,
                        ap.longitude,
                    )
                    if (distance > NEARBY_RADIUS_METERS) {
                        null
                    } else {
                        BssidNearbyAccessPoint(
                            bssid = ap.bssid,
                            latitude = ap.latitude,
                            longitude = ap.longitude,
                            accuracyMeters = ap.accuracyMeters,
                            source = ap.source,
                            distanceMeters = distance,
                        )
                    }
                }
                .distinctBy { it.bssid }
                .sortedBy { it.distanceMeters }
                .take(MAX_NEARBY_APS)
                .toList()

            val current = _bssidNearbyState.value
            if (current !is BssidNearbyState.Loading ||
                !current.seedBssid.equals(seedBssid, ignoreCase = true)
            ) {
                return@launch
            }

            Log.d(
                "BssidNearby",
                "seed=$seedBssid rawResults=${discovered.size} filtered=${nearby.size}",
            )
            bssidNearbyCache[seedBssid] = nearby
            _bssidNearbyState.value = BssidNearbyState.Success(
                seedBssid = seedBssid,
                accessPoints = nearby,
                radiusMeters = NEARBY_RADIUS_METERS,
            )
        } catch (t: Throwable) {
            val current = _bssidNearbyState.value
            if (current !is BssidNearbyState.Loading ||
                !current.seedBssid.equals(seedBssid, ignoreCase = true)
            ) {
                return@launch
            }
            val error = when (t) {
                is BssidGeolocationException -> BssidGeolocationError(t.kind, t.message)
                else -> BssidGeolocationError(
                    BssidGeolocationErrorKind.SERVICE_ERROR,
                    t.message ?: "Nearby access-point lookup failed.",
                )
            }
            Log.e("BssidNearby", "Nearby AP lookup failed for seed=$seedBssid: ${error.message}", t)
            _bssidNearbyState.value = BssidNearbyState.Failure(seedBssid, error)
        }
    }
}

private fun distanceMeters(
    latitude1: Double,
    longitude1: Double,
    latitude2: Double,
    longitude2: Double,
): Double {
    val earthRadiusMeters = 6_371_000.0
    val lat1 = Math.toRadians(latitude1)
    val lat2 = Math.toRadians(latitude2)
    val deltaLat = Math.toRadians(latitude2 - latitude1)
    val deltaLon = Math.toRadians(longitude2 - longitude1)
    val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
        cos(lat1) * cos(lat2) * sin(deltaLon / 2) * sin(deltaLon / 2)
    return earthRadiusMeters * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

private fun MainViewModel.recordBssidGeolocationHistoryImpl(
    result: com.swp81x.nrsuite.core.geo.BssidGeolocationResult,
    vendor: String?,
) {
    val now = System.currentTimeMillis()
    val existing = _bssidGeolocationHistory.value.firstOrNull {
        it.bssid.equals(result.bssid, ignoreCase = true)
    }
    val updated = BssidGeolocationHistoryEntry(
        bssid = result.bssid,
        vendor = vendor ?: existing?.vendor,
        latitude = result.latitude,
        longitude = result.longitude,
        accuracyMeters = result.accuracyMeters,
        source = result.source,
        firstLookedUpAtMs = existing?.firstLookedUpAtMs ?: now,
        lastLookedUpAtMs = now,
        lookupCount = (existing?.lookupCount ?: 0) + 1,
    )
    _bssidGeolocationHistory.value = (
        listOf(updated) + _bssidGeolocationHistory.value.filterNot {
            it.bssid.equals(result.bssid, ignoreCase = true)
        }
        ).take(200)
    persistBssidGeolocationHistoryImpl()
}

private fun MainViewModel.persistBssidGeolocationHistoryImpl() {
    bssidGeolocationHistoryStore.save(_bssidGeolocationHistory.value)
}
