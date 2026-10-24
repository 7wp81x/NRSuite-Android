package com.swp81x.nrsuite.core.wpa

/**
 * Tracks WPA2 4-way handshake material for multiple BSSID/station sessions.
 *
 * [WpaHandshakeParser] is intentionally stateful around one target because the
 * original capture/verify path targeted one BSSID at a time. Auto Handshake can
 * see more than one client on the same BSSID, so this tracker keys parsed M1/M2
 * material by BSSID + station and merges it into independent [WpaHandshake]
 * instances.
 */
class WpaHandshakeTracker {

    private val states = LinkedHashMap<String, WpaHandshake>()

    data class Update(
        val bssid: String?,
        val station: String?,
        val m1Found: Boolean,
        val m2Found: Boolean,
        val valid: Boolean,
        val completeHandshake: WpaHandshake?,
    )

    fun onFrame(frame: ByteArray): Update {
        val delta = WpaHandshakeParser.parse(frame, WpaHandshake())
        val bssidBytes = delta.m1Bssid ?: delta.m2Bssid
        val stationBytes = delta.m1Station ?: delta.m2Station
        if (bssidBytes == null || stationBytes == null) {
            return Update(
                bssid = null,
                station = null,
                m1Found = false,
                m2Found = false,
                valid = false,
                completeHandshake = null,
            )
        }

        val bssid = bssidBytes.toMacString()
        val station = stationBytes.toMacString()
        val key = "$bssid|$station"
        val state = states.getOrPut(key) { WpaHandshake() }

        delta.m1Bssid?.let { state.m1Bssid = it.copyOf() }
        delta.m1Station?.let { state.m1Station = it.copyOf() }
        delta.aNonce?.let { state.aNonce = it.copyOf() }
        delta.m1ReplayCounter?.let { state.m1ReplayCounter = it }

        delta.m2Bssid?.let { state.m2Bssid = it.copyOf() }
        delta.m2Station?.let { state.m2Station = it.copyOf() }
        delta.sNonce?.let { state.sNonce = it.copyOf() }
        delta.eapolM2?.let { state.eapolM2 = it.copyOf() }
        delta.mic?.let { state.mic = it.copyOf() }
        delta.m2ReplayCounter?.let { state.m2ReplayCounter = it }
        if (delta.m2Bssid != null) {
            state.keyDescriptorVersion = delta.keyDescriptorVersion
        }

        val valid = state.isComplete
        return Update(
            bssid = bssid,
            station = station,
            m1Found = state.m1Bssid != null,
            m2Found = state.m2Bssid != null,
            valid = valid,
            completeHandshake = if (valid) state.copyHandshake() else null,
        )
    }

    fun snapshot(): List<WpaHandshake> = states.values.map { it.copyHandshake() }

    private fun ByteArray.toMacString(): String =
        joinToString(":") { value -> String.format("%02X", value.toInt() and 0xFF) }
}
