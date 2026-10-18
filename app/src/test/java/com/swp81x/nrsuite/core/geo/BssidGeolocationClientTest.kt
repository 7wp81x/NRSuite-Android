package com.swp81x.nrsuite.core.geo

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BssidGeolocationClientTest {
    @Test
    fun normalizesCommonBssidFormats() {
        assertEquals(
            "AA:BB:CC:DD:EE:FF",
            normalizeBssid("aa-bb-cc-dd-ee-ff"),
        )
        assertEquals(
            "AA:BB:CC:DD:EE:FF",
            normalizeBssid("AA:BB:CC:DD:EE:FF"),
        )
        assertEquals(
            "AA:BB:CC:DD:EE:FF",
            normalizeBssid("AABBCCDDEEFF"),
        )
        assertNull(normalizeBssid("not-a-mac"))
    }

    @Test
    fun decodesWlocLocationForExpectedBssid() {
        val bssid = "AA:BB:CC:DD:EE:FF"
        val location = fieldVarint(1, 1_234_567_890L) +
            fieldVarint(2, -1_234_567_890L) +
            fieldVarint(3, 25L)
        val device = fieldBytes(1, bssid.toByteArray()) + fieldBytes(2, location)
        val payload = fieldBytes(2, device)
        val response = ByteArray(10) + payload

        val result = BssidGeolocationClient.decodeWlocResponse(response, bssid, "global")

        assertEquals(bssid, result?.bssid)
        assertEquals(12.3456789, result?.latitude ?: 0.0, 0.00000001)
        assertEquals(-12.3456789, result?.longitude ?: 0.0, 0.00000001)
        assertEquals(25.0, result?.accuracyMeters ?: 0.0, 0.0001)
        assertEquals("global", result?.source)
    }

    @Test
    fun ignoresResponseForDifferentBssid() {
        val location = fieldVarint(1, 1_000_000_000L) + fieldVarint(2, 2_000_000_000L)
        val device = fieldBytes(1, "11:22:33:44:55:66".toByteArray()) + fieldBytes(2, location)
        val response = ByteArray(10) + fieldBytes(2, device)

        assertNull(
            BssidGeolocationClient.decodeWlocResponse(
                response,
                "AA:BB:CC:DD:EE:FF",
                "global",
            )
        )
    }

    private fun fieldVarint(field: Int, value: Long): ByteArray {
        return varint((field shl 3).toLong()) + varint(value)
    }

    private fun fieldBytes(field: Int, value: ByteArray): ByteArray {
        return varint(((field shl 3) or 2).toLong()) + varint(value.size.toLong()) + value
    }

    private fun varint(value: Long): ByteArray {
        val output = ByteArrayOutputStream()
        var remaining = value
        while (true) {
            if ((remaining and 0x7fL.inv()) == 0L) {
                output.write(remaining.toInt())
                break
            }
            output.write(((remaining and 0x7fL) or 0x80L).toInt())
            remaining = remaining ushr 7
        }
        return output.toByteArray()
    }
}
