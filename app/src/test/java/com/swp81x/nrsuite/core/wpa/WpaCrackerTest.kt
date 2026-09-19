package com.swp81x.nrsuite.core.wpa

import javax.crypto.Mac
import kotlinx.coroutines.runBlocking
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WpaCrackerTest {

    @Test
    fun `finds the known password in a wordlist`() {
        val bssid = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte())
        val station = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66)
        val aNonce = ByteArray(32) { (it + 1).toByte() }
        val sNonce = ByteArray(32) { (it + 33).toByte() }
        val ssid = "TestNetwork"
        val password = "correct-horse"

        val pmk = WpaHandshakeVerifier.derivePmk(password, ssid)
        val ptk = WpaHandshakeVerifier.derivePtk(pmk, bssid, station, aNonce, sNonce)
        val kck = ptk.copyOfRange(0, 16)
        val eapolM2 = ByteArray(100)
        val mic = hmacSha1(kck, eapolM2).copyOf(16)
        System.arraycopy(mic, 0, eapolM2, MIC_OFFSET, 16)

        val handshake = WpaHandshake(
            m1Bssid = bssid,
            m1Station = station,
            aNonce = aNonce,
            m1ReplayCounter = 1L,
            m2Bssid = bssid,
            m2Station = station,
            sNonce = sNonce,
            eapolM2 = eapolM2,
            mic = mic,
            m2ReplayCounter = 1L,
            keyDescriptorVersion = 2,
        )
        assertTrue(handshake.isComplete)

        val wordlist = "wrong-password\nanother-wrong\n$password\n".reader().buffered()
        val found = runBlocking {
            WpaCracker.crack(
                handshake = handshake,
                ssid = ssid,
                wordlist = wordlist,
                shouldStop = { false },
                onProgress = {},
            )
        }

        assertEquals(password, found)
    }

    private fun hmacSha1(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        return mac.doFinal(data)
    }

    companion object {
        private const val MIC_OFFSET = 81
    }
}
