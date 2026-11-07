package com.swp81x.nrsuite.core.ble

/**
 * Firmware can stop advertising once a central connects while the HID session
 * remains active. Pairing may also leave both advertising and authenticated
 * state false briefly. UI controls must use the firmware session-active flag.
 */
internal fun isBleHidSessionActive(
    sessionActive: Boolean,
    advertising: Boolean,
    connected: Boolean,
): Boolean = sessionActive || advertising || connected
