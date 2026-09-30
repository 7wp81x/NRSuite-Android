package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.ble.TrackerObservation
import com.swp81x.nrsuite.core.history.HistoryLevel
import kotlinx.coroutines.flow.update

// Tracker Detector: consumes BLE Scanner advertisement events and flags
// Find My / AirTag-style manufacturer payloads.

private const val TRACKER_PATTERN_A = "4C001219"
private const val TRACKER_PATTERN_B = "1EFF4C00"

internal fun MainViewModel.detectTrackerCandidate(
    address: String,
    name: String?,
    rssi: Int,
    manufacturerData: String?,
) {
    if (address.isBlank() || manufacturerData.isNullOrBlank()) return

    val payload = manufacturerData.uppercase().replace(" ", "")
    val isTracker = payload.contains(TRACKER_PATTERN_A) || payload.contains(TRACKER_PATTERN_B)
    if (!isTracker) return

    val now = timeHmNow()
    val existing = _trackerObservations.value.firstOrNull { it.address == address }
    val updated = existing?.copy(
        name = name ?: existing.name,
        rssi = rssi,
        manufacturerData = manufacturerData,
        lastSeen = now,
        sightings = existing.sightings + 1,
    ) ?: TrackerObservation(
        address = address,
        name = name,
        rssi = rssi,
        manufacturerData = manufacturerData,
        firstSeen = now,
        lastSeen = now,
        sightings = 1,
    )

    if (existing == null) {
        appendLog("Potential tracker detected: $address (${name ?: "unnamed"}, $rssi dBm).")
        addHistory(
            "tracker_detector",
            "Tracker candidate detected: ${name ?: address}",
            HistoryLevel.ERROR,
        )
    }

    _trackerObservations.update { current ->
        (listOf(updated) + current.filterNot { it.address == address }).take(200)
    }
}

internal fun MainViewModel.clearTrackersImpl() {
    _trackerObservations.value = emptyList()
}
