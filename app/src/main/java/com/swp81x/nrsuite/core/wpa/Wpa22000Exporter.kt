package com.swp81x.nrsuite.core.wpa

/**
 * Builds a Hashcat mode 22000 WPA-PBKDF2-PMKID+EAPOL line for an M1/M2 pair.
 *
 * Format:
 * WPA*02*MIC*AP_MAC*STA_MAC*ESSID_HEX*ANONCE*EAPOL_HEX*MESSAGE_PAIR
 *
 * The SNonce is carried inside the retained M2 EAPOL frame. MESSAGE_PAIR is
 * 00 for an M1/M2 exchange with the M2 EAPOL frame retained. The EAPOL field
 * is copied from M2 with the 16 MIC bytes zeroed.
 */
object Wpa22000Exporter {
    fun buildLine(handshake: WpaHandshake, ssid: String): String? {
        if (!handshake.isComplete) return null
        val bssid = handshake.bssid ?: return null
        val station = handshake.station ?: return null
        val aNonce = handshake.aNonce ?: return null
        val mic = handshake.mic ?: return null
        val eapol = handshake.eapolM2?.copyOf() ?: return null
        val ssidBytes = ssid.toByteArray(Charsets.UTF_8)
        if (bssid.size != 6 || station.size != 6) return null
        if (aNonce.size != 32) return null
        if (mic.size != 16) return null
        if (ssidBytes.isEmpty() || ssidBytes.size > 32) return null
        if (eapol.size !in (MIC_OFFSET + 16)..MAX_EAPOL_BYTES) return null
        if (handshake.keyDescriptorVersion !in 1..3) return null

        for (index in MIC_OFFSET until MIC_OFFSET + 16) {
            eapol[index] = 0
        }

        return listOf(
            "WPA",
            "02",
            mic.toHex(),
            bssid.toHex(),
            station.toHex(),
            ssidBytes.toHex(),
            aNonce.toHex(),
            eapol.toHex(),
            MESSAGE_PAIR_M1M2,
        ).joinToString("*")
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }

    private const val MIC_OFFSET = 81
    private const val MAX_EAPOL_BYTES = 256
    private const val MESSAGE_PAIR_M1M2 = "00"
}
