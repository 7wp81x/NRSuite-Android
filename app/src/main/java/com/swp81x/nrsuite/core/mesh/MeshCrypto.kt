package com.swp81x.nrsuite.core.mesh

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class MeshDerivedKeys(
    val authKey: ByteArray,
    val transportKey: ByteArray,
)

/**
 * HKDF-SHA256 and HMAC helpers for the NRSuite Mesh Foundation.
 *
 * The operator passphrase never leaves the app. The app derives the two keys
 * below and provisions only those keys to the ESP32 over USB.
 */
object MeshCrypto {
    const val SALT = "NRSuite Mesh v1"
    const val AUTH_INFO = "nrsuite-mesh-auth"
    const val TRANSPORT_INFO = "nrsuite-mesh-transport"
    const val KEY_LENGTH = 32

    private const val HMAC_ALGORITHM = "HmacSHA256"

    fun deriveKeys(passphrase: String): MeshDerivedKeys {
        require(passphrase.isNotEmpty()) { "Mesh passphrase must not be empty" }

        val ikm = passphrase.toByteArray(Charsets.UTF_8)
        val salt = SALT.toByteArray(Charsets.UTF_8)
        val prk = hkdfExtract(salt, ikm)

        return MeshDerivedKeys(
            authKey = hkdfExpand(prk, AUTH_INFO.toByteArray(Charsets.UTF_8), KEY_LENGTH),
            transportKey = hkdfExpand(prk, TRANSPORT_INFO.toByteArray(Charsets.UTF_8), KEY_LENGTH),
        )
    }

    fun hkdfSha256(
        ikm: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        require(length > 0)
        return hkdfExpand(hkdfExtract(salt, ikm), info, length)
    }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(key, HMAC_ALGORITHM))
        return mac.doFinal(data)
    }

    fun base64Encode(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    fun base64Decode(value: String): ByteArray? =
        runCatching { Base64.getDecoder().decode(value) }.getOrNull()

    private fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
        // RFC 5869: PRK = HMAC-Hash(salt, IKM)
        val effectiveSalt = if (salt.isEmpty()) ByteArray(KEY_LENGTH) else salt
        return hmacSha256(effectiveSalt, ikm)
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val output = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1

        while (offset < length) {
            val mac = Mac.getInstance(HMAC_ALGORITHM)
            mac.init(SecretKeySpec(prk, HMAC_ALGORITHM))
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            previous = mac.doFinal()

            val copyLength = minOf(previous.size, length - offset)
            previous.copyInto(output, destinationOffset = offset, endIndex = copyLength)
            offset += copyLength
            counter++
        }

        return output
    }
}
