package com.swp81x.nrsuite.core.wps

/**
 * WPS registrar-session material required for an offline Pixie Dust PIN search.
 *
 * The firmware captures this during a Registrar-side M1/M2/M3 exchange:
 *   - PKE and E-Nonce come from M1.
 *   - PKR, R-Nonce and AuthKey are produced by the Registrar M2 step.
 *   - E-Hash1 and E-Hash2 come from the enrollee's M3.
 *
 * All byte arrays are raw big-endian/opaque WPS values. Hex parsing is provided
 * for bridge events and tests.
 */
data class WpsPixieMaterial(
    val pke: ByteArray,
    val pkr: ByteArray,
    val eNonce: ByteArray,
    val rNonce: ByteArray,
    val eHash1: ByteArray,
    val eHash2: ByteArray,
    val authKey: ByteArray,
    val bssid: String? = null,
    val ssid: String? = null,
    val channel: Int? = null,
) {
    fun validate() {
        require(pke.size == PUBLIC_KEY_LENGTH) { "PKE must be $PUBLIC_KEY_LENGTH bytes" }
        require(pkr.size == PUBLIC_KEY_LENGTH) { "PKR must be $PUBLIC_KEY_LENGTH bytes" }
        require(eNonce.size == NONCE_LENGTH) { "E-Nonce must be $NONCE_LENGTH bytes" }
        require(rNonce.size == NONCE_LENGTH) { "R-Nonce must be $NONCE_LENGTH bytes" }
        require(eHash1.size == HASH_LENGTH) { "E-Hash1 must be $HASH_LENGTH bytes" }
        require(eHash2.size == HASH_LENGTH) { "E-Hash2 must be $HASH_LENGTH bytes" }
        require(authKey.size == HASH_LENGTH) { "AuthKey must be $HASH_LENGTH bytes" }
    }

    companion object {
        const val PUBLIC_KEY_LENGTH = 192
        const val NONCE_LENGTH = 16
        const val HASH_LENGTH = 32

        fun fromHex(
            pke: String,
            pkr: String,
            eNonce: String,
            rNonce: String,
            eHash1: String,
            eHash2: String,
            authKey: String,
            bssid: String? = null,
            ssid: String? = null,
            channel: Int? = null,
        ): WpsPixieMaterial = WpsPixieMaterial(
            pke = hexToBytes(pke),
            pkr = hexToBytes(pkr),
            eNonce = hexToBytes(eNonce),
            rNonce = hexToBytes(rNonce),
            eHash1 = hexToBytes(eHash1),
            eHash2 = hexToBytes(eHash2),
            authKey = hexToBytes(authKey),
            bssid = bssid,
            ssid = ssid,
            channel = channel,
        ).also { it.validate() }

        fun hexToBytes(hex: String): ByteArray {
            val normalized = hex.filterNot { it.isWhitespace() || it == ':' || it == '-' }
            require(normalized.length % 2 == 0) { "Hex string must have an even length" }
            return ByteArray(normalized.length / 2) { index ->
                normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }
    }
}

data class WpsPixieResult(
    val pin: String,
    val mode: WpsPixieMode,
    val psk1: ByteArray,
    val psk2: ByteArray,
    val eS1: ByteArray,
    val eS2: ByteArray,
    val seedN1: UInt? = null,
)

enum class WpsPixieMode(val label: String) {
    RT_MT_CL("RT/MT/CL"),
    RT_MT_CL_ZERO_SECRET_NONCES("RT/MT/CL (zero secret nonces)"),
    RT_MT_CL_NONCE_SECRET_NONCES("RT/MT/CL (E-Nonce secret nonces)"),
    ECOS_SIMPLEST("eCos simplest"),
    ECOS_SIMPLE("eCos simple"),
    RTL("RTL/glibc"),
    ECOS_KNUTH("eCos Knuth"),
}
