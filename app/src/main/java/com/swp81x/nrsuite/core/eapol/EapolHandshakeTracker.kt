package com.swp81x.nrsuite.core.eapol

/**
 * Tracks M1-M4 indicator state per BSSID + station.
 *
 * The original single EapolHandshake instance could mix messages from several
 * clients or retries. Evil Twin UI badges should represent one coherent
 * exchange, while password verification uses WpaHandshakeTracker separately.
 */
class EapolHandshakeTracker {
    data class Update(
        val bssid: String?,
        val station: String?,
        val handshake: EapolHandshake?,
    )

    private val states = LinkedHashMap<String, EapolHandshake>()

    fun onFrame(frame: ByteArray): Update {
        val addresses = parseAddresses(frame) ?: return Update(null, null, null)
        val key = "${addresses.bssid}|${addresses.station}"
        val state = states.getOrPut(key) { EapolHandshake() }
        val before = state.copy()
        EapolParser.parse(frame, state)
        return Update(
            bssid = addresses.bssid,
            station = addresses.station,
            handshake = if (state != before) state.copy() else null,
        )
    }

    private data class Addresses(val bssid: String, val station: String)

    private fun parseAddresses(frame: ByteArray): Addresses? {
        if (frame.size < 8) return null
        val radiotapLength = (frame[2].toInt() and 0xFF) or
            ((frame[3].toInt() and 0xFF) shl 8)
        val macBase = radiotapLength
        if (frame.size < macBase + 24) return null

        val fc0 = frame[macBase].toInt() and 0xFF
        if (((fc0 and 0x0C) shr 2) != 2) return null
        val fc1 = frame[macBase + 1].toInt() and 0xFF
        val toDs = (fc1 and 0x01) != 0
        val fromDs = (fc1 and 0x02) != 0

        val addr1 = mac(frame, macBase + 4)
        val addr2 = mac(frame, macBase + 10)
        val addr3 = mac(frame, macBase + 16)
        return when {
            toDs && !fromDs -> Addresses(bssid = addr1, station = addr2)
            !toDs && fromDs -> Addresses(bssid = addr2, station = addr1)
            !toDs && !fromDs -> Addresses(bssid = addr3, station = addr2)
            else -> Addresses(bssid = addr1, station = addr2)
        }
    }

    private fun mac(frame: ByteArray, offset: Int): String =
        (0 until 6).joinToString(":") { index ->
            "%02X".format(frame[offset + index].toInt() and 0xFF)
        }
}
