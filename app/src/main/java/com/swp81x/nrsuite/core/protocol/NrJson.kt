package com.swp81x.nrsuite.core.protocol

/**
 * Small helper for the JSON payloads used by NRSuite commands and events.
 */
object NrJson {
    const val COMMAND_PING = "PING"
    const val COMMAND_STATUS = "STATUS"
    const val COMMAND_SCAN_WIFI = "SCAN_WIFI"
    const val COMMAND_START_SNIFF = "START_SNIFF"
    const val COMMAND_STOP_SNIFF = "STOP_SNIFF"
    const val COMMAND_DEAUTH = "DEAUTH"
    const val COMMAND_START_BEACON = "START_BEACON"
    const val COMMAND_STOP_BEACON = "STOP_BEACON"
    const val COMMAND_BEACON_STATUS = "BEACON_STATUS"
    const val COMMAND_PORTAL_STATUS = "PORTAL_STATUS"
}
