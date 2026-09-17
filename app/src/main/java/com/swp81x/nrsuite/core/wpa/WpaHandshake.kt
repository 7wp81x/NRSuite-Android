package com.swp81x.nrsuite.core.wpa

/**
 * Parsed WPA2 4-way handshake material.
 *
 * M1 and M2 are stored separately so verification only succeeds when both
 * frames belong to the same BSSID/station session. Replay counters are also
 * retained and compared when present.
 */
data class WpaHandshake(
    var m1Bssid: ByteArray? = null,
    var m1Station: ByteArray? = null,
    var aNonce: ByteArray? = null,
    var m1ReplayCounter: Long? = null,
    var m2Bssid: ByteArray? = null,
    var m2Station: ByteArray? = null,
    var sNonce: ByteArray? = null,
    var eapolM2: ByteArray? = null,
    var mic: ByteArray? = null,
    var m2ReplayCounter: Long? = null,
    var keyDescriptorVersion: Int = 2,
) {
    /** BSSID that is consistent across M1 and M2, or null if they disagree. */
    val bssid: ByteArray?
        get() = m1Bssid?.takeIf { m2Bssid == null || it.contentEquals(m2Bssid) }

    /** Station that is consistent across M1 and M2, or null if they disagree. */
    val station: ByteArray?
        get() = m1Station?.takeIf { m2Station == null || it.contentEquals(m2Station) }

    val isComplete: Boolean
        get() = m1Bssid != null &&
            m1Station != null &&
            aNonce != null &&
            m2Bssid != null &&
            m2Station != null &&
            sNonce != null &&
            eapolM2 != null &&
            mic != null &&
            bssid != null &&
            station != null &&
            replayCountersMatch()

    private fun replayCountersMatch(): Boolean {
        val m1 = m1ReplayCounter
        val m2 = m2ReplayCounter
        return (m1 == null && m2 == null) || (m1 != null && m2 != null && m1 == m2)
    }

    fun copyHandshake(): WpaHandshake = WpaHandshake(
        m1Bssid = m1Bssid?.copyOf(),
        m1Station = m1Station?.copyOf(),
        aNonce = aNonce?.copyOf(),
        m1ReplayCounter = m1ReplayCounter,
        m2Bssid = m2Bssid?.copyOf(),
        m2Station = m2Station?.copyOf(),
        sNonce = sNonce?.copyOf(),
        eapolM2 = eapolM2?.copyOf(),
        mic = mic?.copyOf(),
        m2ReplayCounter = m2ReplayCounter,
        keyDescriptorVersion = keyDescriptorVersion,
    )
}

data class EvilTwinResult(
    val password: String,
    val status: Status,
) {
    enum class Status { CORRECT, INCORRECT, PENDING, INVALID_LENGTH }
}
