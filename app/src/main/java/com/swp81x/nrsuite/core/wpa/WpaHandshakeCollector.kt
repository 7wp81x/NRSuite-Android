package com.swp81x.nrsuite.core.wpa

/**
 * Collects complete M1/M2 WPA2 handshakes from a packet stream.
 *
 * Unlike the single-state parser used by the original cracker, this collector
 * keeps multiple candidate exchanges and pairs retransmissions by
 * BSSID + station + replay counter. It is intentionally limited to M1/M2
 * because that is the material required by the app's offline verifier and the
 * Hashcat 22000 EAPOL pair export.
 */
class WpaHandshakeCollector {
    private data class SessionKey(
        val bssid: String,
        val station: String,
        val replayCounter: Long?,
    )

    private val pendingM1 = mutableMapOf<SessionKey, WpaHandshake>()
    private val pendingM2 = mutableMapOf<SessionKey, WpaHandshake>()
    private val completed = linkedMapOf<String, WpaHandshake>()

    fun addFrame(frame: ByteArray) {
        val delta = WpaHandshakeParser.parse(frame, WpaHandshake())

        val m1Bssid = delta.m1Bssid
        val m1Station = delta.m1Station
        if (m1Bssid != null && m1Station != null && delta.aNonce != null) {
            val key = SessionKey(
                bssid = m1Bssid.toHex(),
                station = m1Station.toHex(),
                replayCounter = delta.m1ReplayCounter,
            )
            pendingM1[key] = delta
            pendingM2.remove(key)?.let { combineAndStore(delta, it) }
        }

        val m2Bssid = delta.m2Bssid
        val m2Station = delta.m2Station
        if (m2Bssid != null && m2Station != null &&
            delta.sNonce != null && delta.eapolM2 != null && delta.mic != null
        ) {
            val key = SessionKey(
                bssid = m2Bssid.toHex(),
                station = m2Station.toHex(),
                replayCounter = delta.m2ReplayCounter,
            )
            pendingM2[key] = delta
            pendingM1[key]?.let { combineAndStore(it, delta) }
        }
    }

    fun finish(): List<WpaHandshake> = completed.values.toList()

    private fun combineAndStore(m1: WpaHandshake, m2: WpaHandshake) {
        val combined = WpaHandshake(
            m1Bssid = m1.m1Bssid?.copyOf(),
            m1Station = m1.m1Station?.copyOf(),
            aNonce = m1.aNonce?.copyOf(),
            m1ReplayCounter = m1.m1ReplayCounter,
            m2Bssid = m2.m2Bssid?.copyOf(),
            m2Station = m2.m2Station?.copyOf(),
            sNonce = m2.sNonce?.copyOf(),
            eapolM2 = m2.eapolM2?.copyOf(),
            mic = m2.mic?.copyOf(),
            m2ReplayCounter = m2.m2ReplayCounter,
            keyDescriptorVersion = m2.keyDescriptorVersion,
        )
        if (!combined.isComplete) return
        val bssid = combined.bssid ?: return
        val station = combined.station ?: return
        val mic = combined.mic ?: return
        val key = buildString {
            append(bssid.toHex())
            append('|')
            append(station.toHex())
            append('|')
            append(combined.m1ReplayCounter ?: combined.m2ReplayCounter ?: -1L)
            append('|')
            append(mic.toHex())
        }
        completed[key] = combined.copyHandshake()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
}
