package com.swp81x.nrsuite.core.defense

/**
 * A deauth/disassoc frame detected by the firmware's deauth detector.
 */
data class DeauthAlert(
    val subtype: String,
    val subtypeCode: Int,
    val bssid: String,
    val source: String,
    val client: String,
    val channel: Int,
    val rssi: Int,
    val reason: Int,
    val uptimeMs: Long,
    val receivedAt: String,
) {
    val isDeauth: Boolean get() = subtypeCode == 0x0C
}
