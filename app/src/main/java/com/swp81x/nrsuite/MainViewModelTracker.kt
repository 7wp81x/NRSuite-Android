package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.ble.MAX_TRACKER_OBSERVATIONS
import com.swp81x.nrsuite.core.ble.TrackerObservation
import com.swp81x.nrsuite.core.history.HistoryLevel
import kotlinx.coroutines.flow.update

// Tracker Detector: consumes BLE Scanner advertisement events and flags
// Find My / AirTag-style manufacturer payloads.

private data class TrackerSignature(
    val label: String,
    val pattern: String,
    val manufacturerPrefixOnly: Boolean = false,
)

private val TRACKER_SIGNATURES = listOf(
    TrackerSignature("Apple AirTag / Find My", "4C001219"),
    TrackerSignature("Apple AirTag / Find My", "1EFF4C00"),
    // Company IDs are little-endian in manufacturer data, but keep the report's
    // big-endian forms as a fallback for payloads that expose them that way.
    TrackerSignature("Samsung SmartTag", "7500", manufacturerPrefixOnly = true),
    TrackerSignature("Samsung SmartTag", "00750000"),
    TrackerSignature("Tile", "1801", manufacturerPrefixOnly = true),
    TrackerSignature("Tile", "01180000"),
)

internal fun MainViewModel.detectTrackerCandidate(
    address: String,
    name: String?,
    rssi: Int,
    manufacturerData: String?,
    rawPayload: String?,
) {
    if (address.isBlank()) return

    val manufacturer = manufacturerData.orEmpty().uppercase().replace(" ", "")
    val raw = rawPayload.orEmpty().uppercase().replace(" ", "")
    val signature = TRACKER_SIGNATURES.firstOrNull { candidate ->
        if (candidate.manufacturerPrefixOnly) {
            manufacturer.startsWith(candidate.pattern)
        } else {
            manufacturer.contains(candidate.pattern) || raw.contains(candidate.pattern)
        }
    } ?: return
    val trackerType = signature.label

    val payloadForDisplay = manufacturerData ?: rawPayload

    val now = timeHmNow()
    val existing = _trackerObservations.value.firstOrNull { it.address == address }
    val updated = existing?.copy(
        name = name ?: existing.name,
        rssi = rssi,
        manufacturerData = payloadForDisplay,
        lastSeen = now,
        sightings = existing.sightings + 1,
        trackerType = trackerType,
    ) ?: TrackerObservation(
        address = address,
        name = name,
        rssi = rssi,
        manufacturerData = payloadForDisplay,
        firstSeen = now,
        lastSeen = now,
        sightings = 1,
        trackerType = trackerType,
    )

    if (existing == null) {
        appendLog("Potential tracker detected: $address (${name ?: "unnamed"}, $rssi dBm, $trackerType).")
        addHistory(
            "tracker_detector",
            "Tracker candidate detected: ${name ?: address} ($trackerType)",
            HistoryLevel.ERROR,
        )
    }

    _trackerObservations.update { current ->
        (listOf(updated) + current.filterNot { it.address == address }).take(MAX_TRACKER_OBSERVATIONS)
    }
}

internal fun MainViewModel.clearTrackersImpl() {
    _trackerObservations.value = emptyList()
}
