package com.swp81x.nrsuite.core.wps

import com.swp81x.nrsuite.core.pcap.PcapReader
import com.swp81x.nrsuite.core.wifi.PcapSsidParser
import java.io.InputStream

/**
 * Extracts the first complete WPS registrar M1/M2/M3 tuple from a radiotap +
 * 802.11 capture.
 *
 * AuthKey cannot be derived from a PCAP: it depends on the registrar's DH
 * private key. The caller must supply it from the firmware serial log or a
 * bridge event.
 */
object WpsPixiePcapParser {
    fun parse(input: InputStream): WpsPixieCapturedMaterial? {
        val m1ByNonce = LinkedHashMap<String, M1>()
        val m2ByRegistrarNonce = LinkedHashMap<String, M2>()
        val ssidByBssid = LinkedHashMap<String, String>()
        var found: WpsPixieCapturedMaterial? = null

        PcapReader(input).forEachPacket { frame ->
            PcapSsidParser.parse(frame)?.let { detected ->
                if (detected.bssid.isNotBlank()) {
                    ssidByBssid[detected.bssid.uppercase()] = detected.ssid
                }
            }

            val parsed = parseWscFrame(frame) ?: return@forEachPacket true
            when (parsed.messageType) {
                WPS_MSG_M1 -> {
                    val eNonce = parsed.attribute(ATTR_ENROLLEE_NONCE) ?: return@forEachPacket true
                    val pke = parsed.attribute(ATTR_PUBLIC_KEY) ?: return@forEachPacket true
                    if (eNonce.size != 16 || pke.size != 192) return@forEachPacket true
                    val key = eNonce.toHexString()
                    m1ByNonce[key] = M1(
                        eNonce = eNonce,
                        pke = pke,
                        bssid = parsed.bssid,
                    )
                }

                WPS_MSG_M2 -> {
                    val eNonce = parsed.attribute(ATTR_ENROLLEE_NONCE) ?: return@forEachPacket true
                    val rNonce = parsed.attribute(ATTR_REGISTRAR_NONCE) ?: return@forEachPacket true
                    val pkr = parsed.attribute(ATTR_PUBLIC_KEY) ?: return@forEachPacket true
                    if (eNonce.size != 16 || rNonce.size != 16 || pkr.size != 192) {
                        return@forEachPacket true
                    }
                    val m1 = m1ByNonce[eNonce.toHexString()] ?: return@forEachPacket true
                    val bssid = parsed.bssid ?: m1.bssid
                    m2ByRegistrarNonce[rNonce.toHexString()] = M2(
                        m1 = m1,
                        rNonce = rNonce,
                        pkr = pkr,
                        bssid = bssid,
                    )
                }

                WPS_MSG_M3 -> {
                    val rNonce = parsed.attribute(ATTR_REGISTRAR_NONCE) ?: return@forEachPacket true
                    val eHash1 = parsed.attribute(ATTR_E_HASH1) ?: return@forEachPacket true
                    val eHash2 = parsed.attribute(ATTR_E_HASH2) ?: return@forEachPacket true
                    if (rNonce.size != 16 || eHash1.size != 32 || eHash2.size != 32) {
                        return@forEachPacket true
                    }
                    val m2 = m2ByRegistrarNonce[rNonce.toHexString()] ?: return@forEachPacket true
                    val bssid = parsed.bssid ?: m2.bssid ?: m2.m1.bssid
                    found = WpsPixieCapturedMaterial(
                        pke = m2.m1.pke,
                        pkr = m2.pkr,
                        eNonce = m2.m1.eNonce,
                        rNonce = m2.rNonce,
                        eHash1 = eHash1,
                        eHash2 = eHash2,
                        bssid = bssid,
                        ssid = bssid?.let { ssidByBssid[it.uppercase()] },
                    ).also { it.validate() }
                    return@forEachPacket false
                }
            }
            true
        }

        return found
    }

    private data class M1(
        val eNonce: ByteArray,
        val pke: ByteArray,
        val bssid: String?,
    )

    private data class M2(
        val m1: M1,
        val rNonce: ByteArray,
        val pkr: ByteArray,
        val bssid: String?,
    )

    private data class ParsedWsc(
        val messageType: Int,
        val attributes: List<WpsTlv>,
        val bssid: String?,
    ) {
        fun attribute(type: Int): ByteArray? =
            attributes.firstOrNull { it.type == type }?.value
    }

    private data class WpsTlv(
        val type: Int,
        val value: ByteArray,
    )

    private fun parseWscFrame(frame: ByteArray): ParsedWsc? {
        if (frame.size < RADIOTAP_HEADER_MIN + WLAN_HEADER_MIN) return null
        val radiotapLength = u16Le(frame, 2)
        if (radiotapLength < RADIOTAP_HEADER_MIN || radiotapLength + WLAN_HEADER_MIN > frame.size) {
            return null
        }
        val macBase = radiotapLength
        val fc0 = frame[macBase].toInt() and 0xFF
        val fc1 = frame[macBase + 1].toInt() and 0xFF
        if ((fc0 and 0x0C) != 0x08) return null // Data frame only.

        val subtype = (fc0 ushr 4) and 0x0F
        var headerLength = WLAN_HEADER_MIN
        val toDs = (fc1 and 0x01) != 0
        val fromDs = (fc1 and 0x02) != 0
        if (toDs && fromDs) headerLength += 6
        if ((subtype and 0x08) != 0) headerLength += 2
        if ((fc1 and 0x80) != 0) headerLength += 4

        val llcBase = macBase + headerLength
        if (llcBase + LLC_SNAP_LENGTH + 4 > frame.size) return null
        var snapOffset = -1
        val searchEnd = minOf(llcBase + 20, frame.size - 2)
        for (offset in llcBase..searchEnd) {
            if (frame[offset] == 0x88.toByte() && frame[offset + 1] == 0x8E.toByte()) {
                snapOffset = offset
                break
            }
        }
        if (snapOffset < 0) return null

        val eapol = snapOffset + 2
        if (eapol + 4 > frame.size) return null
        if ((frame[eapol + 1].toInt() and 0xFF) != 0x00) return null
        val eapolLength = u16Be(frame, eapol + 2)
        if (eapol + 4 + eapolLength > frame.size) return null
        val eap = eapol + 4
        if (eap + 5 > frame.size) return null
        if ((frame[eap + 4].toInt() and 0xFF) != EAP_TYPE_EXPANDED) return null
        if (eap + 14 > frame.size) return null
        if (frame[eap + 5].toInt() != 0x00 ||
            frame[eap + 6].toInt() != 0x37 ||
            frame[eap + 7].toInt() != 0x2A
        ) {
            return null
        }
        if (frame[eap + 8].toInt() != 0x00 ||
            frame[eap + 9].toInt() != 0x00 ||
            frame[eap + 10].toInt() != 0x00 ||
            frame[eap + 11].toInt() != 0x01
        ) {
            return null
        }
        if ((frame[eap + 12].toInt() and 0xFF) != WSC_OPCODE_MSG) return null

        val eapEnd = minOf(eapol + 4 + eapolLength, frame.size)
        val attrs = parseTlvs(frame, eap + 14, eapEnd) ?: return null
        val messageType = attrs.firstOrNull { it.type == ATTR_MESSAGE_TYPE }
            ?.value?.firstOrNull()?.toInt()?.and(0xFF) ?: return null
        val bssid = macAddress(frame, macBase + 16)
        return ParsedWsc(messageType = messageType, attributes = attrs, bssid = bssid)
    }

    private fun parseTlvs(frame: ByteArray, start: Int, end: Int): List<WpsTlv>? {
        val output = mutableListOf<WpsTlv>()
        var offset = start
        while (offset + 4 <= end) {
            val type = u16Be(frame, offset)
            val length = u16Be(frame, offset + 2)
            offset += 4
            if (offset + length > end) return null
            output += WpsTlv(type, frame.copyOfRange(offset, offset + length))
            offset += length
        }
        return output
    }

    private fun macAddress(frame: ByteArray, offset: Int): String? {
        if (offset + 6 > frame.size) return null
        return buildString(17) {
            for (index in 0 until 6) {
                if (index > 0) append(':')
                append(String.format("%02X", frame[offset + index].toInt() and 0xFF))
            }
        }
    }

    private fun u16Le(bytes: ByteArray, offset: Int): Int {
        if (offset + 1 >= bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun u16Be(bytes: ByteArray, offset: Int): Int {
        if (offset + 1 >= bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 8) or
            (bytes[offset + 1].toInt() and 0xFF)
    }

    private const val RADIOTAP_HEADER_MIN = 8
    private const val WLAN_HEADER_MIN = 24
    private const val LLC_SNAP_LENGTH = 8
    private const val EAP_TYPE_EXPANDED = 0xFE
    private const val WSC_OPCODE_MSG = 0x04
    private const val WPS_MSG_M1 = 0x04
    private const val WPS_MSG_M2 = 0x05
    private const val WPS_MSG_M3 = 0x07
    private const val ATTR_MESSAGE_TYPE = 0x1022
    private const val ATTR_ENROLLEE_NONCE = 0x101A
    private const val ATTR_REGISTRAR_NONCE = 0x1039
    private const val ATTR_PUBLIC_KEY = 0x1032
    private const val ATTR_E_HASH1 = 0x1014
    private const val ATTR_E_HASH2 = 0x1015
}
