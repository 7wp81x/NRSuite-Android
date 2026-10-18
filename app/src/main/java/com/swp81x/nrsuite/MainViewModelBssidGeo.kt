package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.geo.BssidGeolocationClient
import com.swp81x.nrsuite.core.geo.BssidGeolocationError
import com.swp81x.nrsuite.core.geo.BssidGeolocationErrorKind
import com.swp81x.nrsuite.core.geo.BssidGeolocationException
import com.swp81x.nrsuite.core.geo.BssidGeolocationHistoryEntry
import com.swp81x.nrsuite.core.geo.BssidGeolocationState
import com.swp81x.nrsuite.core.geo.normalizeBssid
import kotlinx.coroutines.launch

internal fun MainViewModel.loadBssidGeolocationHistoryImpl() {
    _bssidGeolocationHistory.value = bssidGeolocationHistoryStore.load()
}

internal fun MainViewModel.lookupBssidGeolocationImpl(rawBssid: String) {
    val bssid = normalizeBssid(rawBssid)
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
