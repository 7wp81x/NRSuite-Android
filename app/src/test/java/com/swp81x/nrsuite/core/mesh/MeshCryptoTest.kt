package com.swp81x.nrsuite.core.mesh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MeshCryptoTest {
    @Test
    fun hkdfSha256MatchesRfc5869TestCase1() {
        val ikm = hex("0b".repeat(22))
        val salt = hex("000102030405060708090a0b0c")
        val info = hex("f0f1f2f3f4f5f6f7f8f9")
        val expected = hex(
            "3cb25f25faacd57a90434f64d0362f2a" +
                "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
                "34007208d5b887185865"
        )

        assertArrayEquals(
            expected,
            MeshCrypto.hkdfSha256(ikm, salt, info, 42),
        )
    }

    @Test
    fun deriveKeysUsesDistinctInfoLabels() {
        val keys = MeshCrypto.deriveKeys("correct horse battery staple")

        assertEquals(32, keys.authKey.size)
        assertEquals(32, keys.transportKey.size)
        assertArrayEquals(
            MeshCrypto.hkdfSha256(
                "correct horse battery staple".toByteArray(Charsets.UTF_8),
                MeshCrypto.SALT.toByteArray(Charsets.UTF_8),
                MeshCrypto.AUTH_INFO.toByteArray(Charsets.UTF_8),
                32,
            ),
            keys.authKey,
        )
    }

    @Test
    fun hmacSha256MatchesKnownVector() {
        val key = "key".toByteArray(Charsets.UTF_8)
        val message = "The quick brown fox jumps over the lazy dog".toByteArray(Charsets.UTF_8)
        val expected = hex("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8")

        assertArrayEquals(expected, MeshCrypto.hmacSha256(key, message))
    }

    private fun hex(value: String): ByteArray =
        value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
