package com.swp81x.nrsuite.core.ble

/**
 * Identifies the Android module that owns the single firmware-side BLE scan.
 * The underlying radio scan is shared, but only the owning module should show
 * scan-in-progress controls or be able to stop it.
 */
enum class BleScanOwner(
    val moduleId: String,
    val label: String,
) {
    SCANNER("ble_scanner", "BLE Scanner"),
    TRACKER_DETECTOR("tracker_detector", "Tracker Detector"),
    PROFILE("ble_profile", "BLE GATT Profile"),
}
