package com.swp81x.nrsuite.core.eapol

data class EapolHandshake(
    var m1: Boolean = false,
    var m2: Boolean = false,
    var m3: Boolean = false,
    var m4: Boolean = false,
) {
    val isComplete: Boolean
        get() = m1 && m2 && m3 && m4
}

/**
 * Pure parser for EAPOL 4-way handshake messages inside radiotap + 802.11
 * data frames. Mirrors the Termux `nrsuite_lib/eapol.py` logic.
 */
object EapolParser {
    fun parse(payload: ByteArray, state: EapolHandshake): EapolHandshake {
        if (payload.size < 4) return state

        val radiotapLength = u16(payload, 2)
        if (payload.size < radiotapLength + 34) return state

        val macBase = radiotapLength
        val fc0 = payload[macBase].toInt() and 0xFF
        val frameType = (fc0 and 0x0C) shr 2
        if (frameType != 2) return state

        val frameControl1 = payload[macBase + 1].toInt() and 0xFF
        val toDs = (frameControl1 and 0x01) != 0
        val fromDs = (frameControl1 and 0x02) != 0
        var headerLength = if (toDs && fromDs) 30 else 24
        val subtype = (fc0 shr 4) and 0x0F
        if (subtype in 8..11) {
            headerLength += 2
        }

        val llcBase = macBase + headerLength
        val searchEnd = minOf(llcBase + 20, payload.size - 1)
        var ethIndex = -1
        for (index in llcBase until searchEnd) {
            if (payload[index] == 0x88.toByte() && payload[index + 1] == 0x8E.toByte()) {
                ethIndex = index
                break
            }
        }
        if (ethIndex < 0) return state

        val eapolStart = ethIndex + 2
        if (payload.size < eapolStart + 8) return state

        val packetType = payload[eapolStart + 1].toInt() and 0xFF
        if (packetType != 1 && packetType != 3) return state

        val keyInfo = ((payload[eapolStart + 5].toInt() and 0xFF) shl 8) or
            (payload[eapolStart + 6].toInt() and 0xFF)

        val isPairwise = (keyInfo and (1 shl 3)) != 0
        val isAck = (keyInfo and (1 shl 7)) != 0
        val isMic = (keyInfo and (1 shl 8)) != 0
        val isSecure = (keyInfo and (1 shl 9)) != 0
        val isError = (keyInfo and (1 shl 10)) != 0

        if (!isPairwise || isError) return state

        when {
            isAck && !isMic -> if (state.m1.not()) state.m1 = true
            !isAck && isMic && !isSecure -> if (state.m1 && state.m2.not()) state.m2 = true
            isAck && isMic && isSecure -> if (state.m2 && state.m3.not()) state.m3 = true
            !isAck && isMic && isSecure -> if (state.m3 && state.m4.not()) state.m4 = true
        }

        return state
    }

    private fun u16(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }
}
