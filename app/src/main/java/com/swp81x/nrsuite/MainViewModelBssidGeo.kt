package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.geo.BssidGeolocationClient
import com.swp81x.nrsuite.core.geo.normalizeBssid
import kotlinx.coroutines.launch

internal fun MainViewModel.lookupBssidGeolocationImpl(rawBssid: String) {
    val bssid = normalizeBssid(rawBssid)
    _macLookupResult.value = ouiDatabaseRepository.detailedLookup(rawBssid)
    if (bssid == null) {
        _bssidGeolocationResult.value = null
        _bssidGeolocationError.value = "Enter a valid BSSID / AP MAC address."
        _bssidGeolocationLoading.value = false
        return
    }

    _bssidGeolocationResult.value = null
    _bssidGeolocationError.value = null
    _bssidGeolocationLoading.value = true

    scope.launch {
        try {
            val result = BssidGeolocationClient.lookup(bssid)
            _bssidGeolocationResult.value = result
            _bssidGeolocationError.value = if (result == null) {
                "BSSID not found in the location database. It may be new, private, or unregistered."
            } else {
                null
            }
        } catch (t: Throwable) {
            _bssidGeolocationResult.value = null
            _bssidGeolocationError.value = "Location lookup failed: ${t.message ?: "unknown error"}"
        } finally {
            _bssidGeolocationLoading.value = false
        }
    }
}

internal fun MainViewModel.clearBssidGeolocationImpl() {
    _macLookupResult.value = null
    _bssidGeolocationResult.value = null
    _bssidGeolocationError.value = null
    _bssidGeolocationLoading.value = false
}
