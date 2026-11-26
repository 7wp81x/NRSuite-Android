package com.swp81x.nrsuite.core.wpa

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvilTwinHandshakeIntegrationTest {

    @Test
    fun `tracked evil twin m1 m2 can be verified without a pcap file`() {
        val bssid = byteArrayOf(
            0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(),
            0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte(),
        )
        val station = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66)
        val aNonce = ByteArray(32) { (it + 1).toByte() }
        val sNonce = ByteArray(32) { (it + 33).toByte() }
        val ssid = "TestNetwork"
        val password = "correct-horse"
        val replayCounter = 42L

        val pmk = WpaHandshakeVerifier.derivePmk(password, ssid)
        val ptk = WpaHandshakeVerifier.derivePtk(pmk, bssid, station, aNonce, sNonce)
        val kck = ptk.copyOfRange(0, 16)
        val eapolM2 = eapolBytes(
            keyInfo = byteArrayOf(0x01, 0x0A),
            replayCounter = replayCounter,
            nonce = sNonce,
        )
        val mic = hmacSha1(kck, eapolM2).copyOf(16)
        mic.copyInto(eapolM2, destinationOffset = MIC_OFFSET)

        val tracker = WpaHandshakeTracker()
        tracker.onFrame(
            dataFrame(
                address1 = station,
                address2 = bssid,
                address3 = bssid,
                eapol = eapolBytes(
                    keyInfo = byteArrayOf(0x00, 0x88.toByte()),
                    replayCounter = replayCounter,
                    nonce = aNonce,
                ),
            ),
        )
        val update = tracker.onFrame(
            dataFrame(
                address1 = bssid,
                address2 = station,
                address3 = bssid,
                eapol = eapolM2,
            ),
        )

        assertTrue(update.valid)
        val handshake = update.completeHandshake
        assertNotNull(handshake)
        assertTrue(WpaHandshakeVerifier.verify(handshake!!, ssid, password))
        assertFalse(WpaHandshakeVerifier.verify(handshake, ssid, "wrong-password"))
    }

    private fun hmacSha1(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        return mac.doFinal(data)
    }

    private fun eapolBytes(
        keyInfo: ByteArray,
        replayCounter: Long,
        nonce: ByteArray,
    ): ByteArray = ByteArray(99).apply {
        this[0] = 1
        this[1] = 3
        this[2] = 0
        this[3] = 95
        this[4] = 2
        keyInfo.copyInto(this, 5)
        writeU64Le(this, 9, replayCounter)
        nonce.copyInto(this, 17)
    }

    private fun dataFrame(
        address1: ByteArray,
        address2: ByteArray,
        address3: ByteArray,
        eapol: ByteArray,
    ): ByteArray {
        val radiotap = ByteArray(8).apply { this[2] = 8 }
        val macHeader = ByteArray(24).apply {
            this[0] = 0x08
            address1.copyInto(this, 4)
            address2.copyInto(this, 10)
            address3.copyInto(this, 16)
        }
        val llcSnap = byteArrayOf(
            0xAA.toByte(), 0xAA.toByte(), 0x03, 0x00, 0x00, 0x00,
            0x88.toByte(), 0x8E.toByte(),
        )
        return radiotap + macHeader + llcSnap + eapol
    }

    private fun writeU64Le(target: ByteArray, offset: Int, value: Long) {
        for (index in 0 until 8) {
            target[offset + index] = ((value ushr (index * 8)) and 0xFF).toByte()
        }
    }

    private companion object {
        const val MIC_OFFSET = 81
    }
}
