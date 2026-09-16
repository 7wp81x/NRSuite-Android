package com.swp81x.nrsuite.core.wpa

import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object WpaHandshakeVerifier {
    private const val MIC_OFFSET = 81
    private val PRF_LABEL = "Pairwise key expansion".toByteArray(Charsets.US_ASCII)

    fun verify(handshake: WpaHandshake, ssid: String, passphrase: String): Boolean {
        if (!handshake.isComplete) return false
        if (handshake.keyDescriptorVersion != 2) return false
        if (ssid.isBlank() || passphrase.length !in 8..63) return false

        val pmk = derivePmk(passphrase, ssid)
        val ptk = derivePtk(
            pmk = pmk,
            bssid = handshake.bssid!!,
            station = handshake.station!!,
            aNonce = handshake.aNonce!!,
            sNonce = handshake.sNonce!!,
        )
        val kck = ptk.copyOfRange(0, 16)

        val eapol = handshake.eapolM2!!.copyOf()
        if (eapol.size < MIC_OFFSET + 16) return false
        for (i in 0 until 16) eapol[MIC_OFFSET + i] = 0

        val computedMic = hmacSha1(kck, eapol).copyOfRange(0, 16)
        return computedMic.contentEquals(handshake.mic)
    }

    fun derivePmk(passphrase: String, ssid: String): ByteArray {
        val spec = PBEKeySpec(passphrase.toCharArray(), ssid.toByteArray(Charsets.UTF_8), 4096, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
    }

    fun derivePtk(
        pmk: ByteArray,
        bssid: ByteArray,
        station: ByteArray,
        aNonce: ByteArray,
        sNonce: ByteArray,
    ): ByteArray {
        val macLow = if (compareBytes(bssid, station) < 0) bssid else station
        val macHigh = if (compareBytes(bssid, station) < 0) station else bssid
        val nonceLow = if (compareBytes(aNonce, sNonce) < 0) aNonce else sNonce
        val nonceHigh = if (compareBytes(aNonce, sNonce) < 0) sNonce else aNonce

        val data = macLow + macHigh + nonceLow + nonceHigh
        val result = ByteArray(64)
        for (i in 0..3) {
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(pmk, "HmacSHA1"))
            mac.update(PRF_LABEL)
            mac.update(0)
            mac.update(data)
            mac.update(i.toByte())
            val block = mac.doFinal()
            System.arraycopy(block, 0, result, i * 20, minOf(20, result.size - i * 20))
        }
        return result
    }

    private fun hmacSha1(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        return mac.doFinal(data)
    }

    private fun compareBytes(a: ByteArray, b: ByteArray): Int {
        val size = minOf(a.size, b.size)
        for (i in 0 until size) {
            val diff = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (diff != 0) return diff
        }
        return a.size - b.size
    }
}
