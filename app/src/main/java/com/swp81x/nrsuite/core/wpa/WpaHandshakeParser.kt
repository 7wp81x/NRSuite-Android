package com.swp81x.nrsuite.core.wpa

/**
 * Extracts WPA2 4-way handshake material from radiotap + 802.11 EAPOL-Key
 * frames. This is intentionally narrow: it targets M1 (ANonce) and M2
 * (SNonce + MIC) which are enough for offline passphrase verification.
 */
object WpaHandshakeParser {
    fun parse(frame: ByteArray, state: WpaHandshake): WpaHandshake {
        if (frame.size < 8) return state

        val radiotapLength = (frame[2].toInt() and 0xFF) or
            ((frame[3].toInt() and 0xFF) shl 8)
        val macBase = radiotapLength
        if (frame.size < macBase + 24) return state

        val fc0 = frame[macBase].toInt() and 0xFF
        val frameType = (fc0 and 0x0C) shr 2
        if (frameType != 2) return state

        val toDs = (frame[macBase + 1].toInt() and 0x01) != 0
        val fromDs = (frame[macBase + 1].toInt() and 0x02) != 0
        var headerLength = if (toDs && fromDs) 30 else 24
        val subtype = (fc0 shr 4) and 0x0F
        if (subtype in 8..11) headerLength += 2

        val llcBase = macBase + headerLength
        val searchEnd = minOf(llcBase + 20, frame.size - 1)
        var ethIndex = -1
        for (index in llcBase until searchEnd) {
            if (frame[index] == 0x88.toByte() && frame[index + 1] == 0x8E.toByte()) {
                ethIndex = index
                break
            }
        }
        if (ethIndex < 0) return state

        val eapolStart = ethIndex + 2
        if (frame.size < eapolStart + 99) return state

        val packetType = frame[eapolStart + 1].toInt() and 0xFF
        if (packetType != 3) return state // EAPOL-Key

        val descriptorType = frame[eapolStart + 4].toInt() and 0xFF
        if (descriptorType != 2) return state // RSN

        val keyInfo = ((frame[eapolStart + 5].toInt() and 0xFF) shl 8) or
            (frame[eapolStart + 6].toInt() and 0xFF)
        val keyDescriptorVersion = keyInfo and 0x07
        val isPairwise = (keyInfo and (1 shl 3)) != 0
        val isAck = (keyInfo and (1 shl 7)) != 0
        val isMic = (keyInfo and (1 shl 8)) != 0
        val isSecure = (keyInfo and (1 shl 9)) != 0
        val isError = (keyInfo and (1 shl 10)) != 0

        if (!isPairwise || isError) return state

        val addr1 = frame.copyOfRange(macBase + 4, macBase + 10)
        val addr2 = frame.copyOfRange(macBase + 10, macBase + 16)
        val addr3 = frame.copyOfRange(macBase + 16, macBase + 22)

        val nonce = frame.copyOfRange(eapolStart + 17, eapolStart + 49)
        val mic = frame.copyOfRange(eapolStart + 81, eapolStart + 97)

        return when {
            isAck && !isMic -> {
                state.bssid = if (addr2.contentEquals(addr3)) addr2 else addr3
                state.station = addr1
                state.aNonce = nonce
                state
            }

            !isAck && isMic && !isSecure -> {
                state.bssid = if (addr1.contentEquals(addr3)) addr1 else addr3
                state.station = addr2
                state.sNonce = nonce
                state.mic = mic
                state.keyDescriptorVersion = keyDescriptorVersion

                val eapolLength = ((frame[eapolStart + 2].toInt() and 0xFF) shl 8) or
                    (frame[eapolStart + 3].toInt() and 0xFF)
                val end = (eapolStart + 4 + eapolLength).coerceAtMost(frame.size)
                state.eapolM2 = frame.copyOfRange(eapolStart, end)
                state
            }

            else -> state
        }
    }
}
