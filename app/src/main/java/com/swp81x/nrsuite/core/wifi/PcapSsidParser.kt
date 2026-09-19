package com.swp81x.nrsuite.core.wifi

/**
 * One SSID/BSSID pair discovered in a management frame.
 */
data class DetectedSsid(
    val ssid: String,
    val bssid: String,
)

/**
 * Extracts SSID information elements from radiotap + 802.11 management frames.
 *
 * EAPOL-Key frames do not carry an SSID, so this parser relies on surrounding
 * beacons, probe responses, and association request frames. If a capture
 * contains only EAPOL frames, no SSID will be detected.
 */
object PcapSsidParser {

    fun parse(frame: ByteArray): DetectedSsid? {
        if (frame.size < RADIOTAP_MIN + MAC_HEADER_SIZE) return null

        val radiotapLength = u16(frame, 2)
        if (radiotapLength < RADIOTAP_MIN) return null
        val macBase = radiotapLength
        if (macBase + MAC_HEADER_SIZE > frame.size) return null

        val fc0 = frame[macBase].toInt() and 0xFF
        val frameType = (fc0 shr 2) and 0x03
        if (frameType != TYPE_MANAGEMENT) return null

        val subtype = (fc0 shr 4) and 0x0F
        val ieOffset = when (subtype) {
            SUBTYPE_PROBE_REQUEST -> macBase + MAC_HEADER_SIZE
            SUBTYPE_PROBE_RESPONSE, SUBTYPE_BEACON -> macBase + MAC_HEADER_SIZE + BEACON_FIXED_PARAMS
            SUBTYPE_ASSOC_REQUEST, SUBTYPE_REASSOC_REQUEST -> macBase + MAC_HEADER_SIZE + ASSOC_FIXED_PARAMS
            else -> return null
        }
        if (ieOffset + 2 > frame.size) return null

        val bssid = when (subtype) {
            SUBTYPE_BEACON, SUBTYPE_PROBE_RESPONSE, SUBTYPE_ASSOC_REQUEST, SUBTYPE_REASSOC_REQUEST -> {
                macAddress(frame, macBase + 16)
            }
            else -> ""
        }

        var offset = ieOffset
        while (offset + 2 <= frame.size) {
            val elementId = frame[offset].toInt() and 0xFF
            val elementLength = frame[offset + 1].toInt() and 0xFF
            offset += 2
            if (offset + elementLength > frame.size) break

            if (elementId == IE_SSID) {
                if (elementLength > 0) {
                    val ssid = String(frame, offset, elementLength, Charsets.UTF_8)
                    if (ssid.isNotBlank()) return DetectedSsid(ssid, bssid)
                }
                return null
            }
            offset += elementLength
        }
        return null
    }

    private fun macAddress(frame: ByteArray, offset: Int): String {
        if (offset + 6 > frame.size) return ""
        return buildString(17) {
            for (index in 0 until 6) {
                if (index > 0) append(':')
                append(String.format("%02X", frame[offset + index].toInt() and 0xFF))
            }
        }
    }

    private fun u16(bytes: ByteArray, offset: Int): Int {
        if (offset + 1 >= bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private const val TYPE_MANAGEMENT = 0
    private const val SUBTYPE_ASSOC_REQUEST = 0
    private const val SUBTYPE_REASSOC_REQUEST = 2
    private const val SUBTYPE_PROBE_REQUEST = 4
    private const val SUBTYPE_PROBE_RESPONSE = 5
    private const val SUBTYPE_BEACON = 8
    private const val MAC_HEADER_SIZE = 24
    private const val BEACON_FIXED_PARAMS = 12
    private const val ASSOC_FIXED_PARAMS = 4
    private const val IE_SSID = 0
    private const val RADIOTAP_MIN = 8
}
