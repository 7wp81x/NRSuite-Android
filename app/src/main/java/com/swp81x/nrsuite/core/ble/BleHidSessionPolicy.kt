package com.swp81x.nrsuite.core.ble

/**
 * Firmware can stop advertising once a central connects while the HID session
 * remains active. UI controls must therefore treat either state as active.
 */
internal fun isBleHidSessionActive(advertising: Boolean, connected: Boolean): Boolean =
    advertising || connected
