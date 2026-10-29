package com.swp81x.nrsuite.core.wps

import com.swp81x.nrsuite.core.pcap.PcapWriter
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WpsPixieImportTest {
    @Test
    fun parsesFirmwareSerialLogMaterialAndAuthKey() {
        val log = """
            PARSER SELF-TEST
              E-Hash1 (32): ${"11".repeat(32)}
              E-Hash2 (32): ${"22".repeat(32)}
              Registrar public key (192): ${"33".repeat(192)}
              AuthKey (32): ${"44".repeat(32)}
            REGISTRAR SELF-TEST: PASS
            LIVE: received M1 (EAP id=22)
              M1 E-Nonce (16): 93462c26b00e3f301a7de0a0efdd6e22
              M1 PKE (192): ${"aa".repeat(192)}
              Registrar R-Nonce (16): 9d7af085e417106fe04db83eda97c757
              Registrar public key (192): ${"bb".repeat(192)}
              AuthKey (32): 07ddfa15dff87bdf54a9ea0b7b73faed2fc4067acebca9851b134acb1f5f7d36
              KeyWrapKey (16): 0286ad0d2728be9605f4801808a19dc8
            LIVE: received M3
              E-Hash1 (32): 4245b739300fffeb4ded595b287e6543a44467e3b2e9ebe84bddc3f485e67d04
              E-Hash2 (32): b4089d3e958058ce476f03060c72f7ec296856870eae71cf7a724f907946b603
            LIVE: scan[0] SSID=FAMILYUBE_nomap BSSID=B0:4E:26:CC:2E:F8 channel=3 RSSI=-82 dBm
        """.trimIndent()

        val parsed = WpsPixieSerialParser.parse(log)
        assertNotNull(parsed.material)
        assertNotNull(parsed.authKey)
        assertNotNull(parsed.keyWrapKey)
        assertEquals("93462c26b00e3f301a7de0a0efdd6e22", parsed.material!!.eNonce.toHexString())
        assertEquals("9d7af085e417106fe04db83eda97c757", parsed.material!!.rNonce.toHexString())
        assertEquals("b0:4e:26:cc:2e:f8", parsed.material!!.bssid?.lowercase())
        assertEquals("FAMILYUBE_nomap", parsed.material!!.ssid)
        assertEquals(3, parsed.material!!.channel)
        assertEquals(
            "07ddfa15dff87bdf54a9ea0b7b73faed2fc4067acebca9851b134acb1f5f7d36",
            parsed.authKey!!.toHexString(),
        )
    }

    @Test
    fun parsesCompleteM1M2M3TupleFromClassicPcap() {
        val file = File.createTempFile("pixie-material", ".pcap")
        val eNonce = hex("00112233445566778899aabbccddeeff")
        val rNonce = hex("ffeeddccbbaa99887766554433221100")
        val bssid = hex("b04e26cc2ef8")
        val station = hex("22c18c2dd801")
        try {
            PcapWriter(file).use { writer ->
                writer.writePacket(wscDataFrame(bssid, station, eapWscWpsMessage(
                    messageType = 0x04,
                    attributes = listOf(
                        attr(0x101a, eNonce),
                        attr(0x1032, ByteArray(192) { (it and 0xFF).toByte() }),
                    ),
                )))
                writer.writePacket(wscDataFrame(bssid, station, eapWscWpsMessage(
                    messageType = 0x05,
                    attributes = listOf(
                        attr(0x101a, eNonce),
                        attr(0x1039, rNonce),
                        attr(0x1032, ByteArray(192) { ((it + 1) and 0xFF).toByte() }),
                    ),
                )))
                writer.writePacket(wscDataFrame(bssid, station, eapWscWpsMessage(
                    messageType = 0x07,
                    attributes = listOf(
                        attr(0x1039, rNonce),
                        attr(0x1014, ByteArray(32) { 0x11.toByte() }),
                        attr(0x1015, ByteArray(32) { 0x22.toByte() }),
                    ),
                )))
            }

            val material = file.inputStream().use { WpsPixiePcapParser.parse(it) }
            assertNotNull(material)
            assertEquals(eNonce.toHexString(), material!!.eNonce.toHexString())
            assertEquals(rNonce.toHexString(), material.rNonce.toHexString())
            assertEquals("B0:4E:26:CC:2E:F8", material.bssid)
            assertEquals(192, material.pke.size)
            assertEquals(192, material.pkr.size)
        } finally {
            file.delete()
        }
    }

    private fun wscDataFrame(bssid: ByteArray, station: ByteArray, eapPayload: ByteArray): ByteArray {
        val header = ByteArray(26)
        header[0] = 0x88.toByte() // QoS Data
        header[1] = 0x02 // FromDS
        station.copyInto(header, 4)
        bssid.copyInto(header, 10)
        bssid.copyInto(header, 16)
        val llc = byteArrayOf(
            0xAA.toByte(), 0xAA.toByte(), 0x03, 0x00, 0x00, 0x00, 0x88.toByte(), 0x8E.toByte(),
        )
        val frame = ByteArray(8 + header.size + llc.size + eapPayload.size)
        // Minimal radiotap: version 0, pad 0, length 8 LE, present flags 0.
        frame[2] = 8
        header.copyInto(frame, 8)
        llc.copyInto(frame, 8 + header.size)
        eapPayload.copyInto(frame, 8 + header.size + llc.size)
        return frame
    }

    private fun eapWscWpsMessage(messageType: Int, attributes: List<ByteArray>): ByteArray {
        val wps = mutableListOf<ByteArray>()
        wps += attr(0x104a, byteArrayOf(0x10))
        wps += attr(0x1022, byteArrayOf(messageType.toByte()))
        wps += attributes
        val wpsBytes = wps.fold(ByteArray(0)) { acc, bytes -> acc + bytes }
        val eapBody = byteArrayOf(
            0xFE.toByte(), 0x00, 0x37, 0x2A,
            0x00, 0x00, 0x00, 0x01,
            0x04, 0x00,
        ) + wpsBytes
        val eapLength = 4 + eapBody.size
        val eap = ByteArray(eapLength)
        eap[0] = 0x01 // EAP Request
        eap[1] = 1 // EAP identifier
        eap[2] = ((eapLength ushr 8) and 0xFF).toByte()
        eap[3] = (eapLength and 0xFF).toByte()
        eapBody.copyInto(eap, 4)
        val eapol = ByteArray(4 + eap.size)
        eapol[0] = 0x01
        eapol[1] = 0x00
        eapol[2] = ((eap.size ushr 8) and 0xFF).toByte()
        eapol[3] = (eap.size and 0xFF).toByte()
        eap.copyInto(eapol, 4)
        return eapol
    }

    private fun attr(type: Int, value: ByteArray): ByteArray {
        val output = ByteArray(4 + value.size)
        output[0] = ((type ushr 8) and 0xFF).toByte()
        output[1] = (type and 0xFF).toByte()
        output[2] = ((value.size ushr 8) and 0xFF).toByte()
        output[3] = (value.size and 0xFF).toByte()
        value.copyInto(output, 4)
        return output
    }

    private fun hex(value: String): ByteArray =
        ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
}
