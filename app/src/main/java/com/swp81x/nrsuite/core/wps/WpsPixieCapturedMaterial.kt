package com.swp81x.nrsuite.core.wps

/**
 * WPS M1/M2/M3 values recoverable from a capture or a firmware serial log.
 * AuthKey is intentionally separate because it is not present on the wire; it
 * must come from the registrar side (serial log, bridge event, or manual hex
 * input).
 */
data class WpsPixieCapturedMaterial(
    val pke: ByteArray,
    val pkr: ByteArray,
    val eNonce: ByteArray,
    val rNonce: ByteArray,
    val eHash1: ByteArray,
    val eHash2: ByteArray,
    val bssid: String? = null,
    val ssid: String? = null,
    val channel: Int? = null,
) {
    fun validate() {
        require(pke.size == WpsPixieMaterial.PUBLIC_KEY_LENGTH) {
            "PKE must be ${WpsPixieMaterial.PUBLIC_KEY_LENGTH} bytes"
        }
        require(pkr.size == WpsPixieMaterial.PUBLIC_KEY_LENGTH) {
            "PKR must be ${WpsPixieMaterial.PUBLIC_KEY_LENGTH} bytes"
        }
        require(eNonce.size == WpsPixieMaterial.NONCE_LENGTH) {
            "E-Nonce must be ${WpsPixieMaterial.NONCE_LENGTH} bytes"
        }
        require(rNonce.size == WpsPixieMaterial.NONCE_LENGTH) {
            "R-Nonce must be ${WpsPixieMaterial.NONCE_LENGTH} bytes"
        }
        require(eHash1.size == WpsPixieMaterial.HASH_LENGTH) {
            "E-Hash1 must be ${WpsPixieMaterial.HASH_LENGTH} bytes"
        }
        require(eHash2.size == WpsPixieMaterial.HASH_LENGTH) {
            "E-Hash2 must be ${WpsPixieMaterial.HASH_LENGTH} bytes"
        }
    }

    fun withAuthKey(authKey: ByteArray): WpsPixieMaterial {
        validate()
        require(authKey.size == WpsPixieMaterial.HASH_LENGTH) {
            "AuthKey must be ${WpsPixieMaterial.HASH_LENGTH} bytes"
        }
        return WpsPixieMaterial(
            pke = pke,
            pkr = pkr,
            eNonce = eNonce,
            rNonce = rNonce,
            eHash1 = eHash1,
            eHash2 = eHash2,
            authKey = authKey,
            bssid = bssid,
            ssid = ssid,
            channel = channel,
        )
    }
}

internal fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
