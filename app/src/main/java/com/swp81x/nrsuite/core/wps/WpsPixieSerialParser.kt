package com.swp81x.nrsuite.core.wps

/**
 * Extracts WPS Pixie Dust material from the experimental firmware serial log.
 *
 * The firmware prints all values needed by the offline cracker across a
 * successful M1/M2/M3 run. AuthKey and KeyWrapKey are registrar-side values and
 * are therefore available only from this log/bridge event, not from a PCAP.
 */
object WpsPixieSerialParser {
    data class Parsed(
        val material: WpsPixieCapturedMaterial?,
        val authKey: ByteArray?,
        val keyWrapKey: ByteArray?,
    )

    private val pkeRegex = Regex("""M1 PKE\s*\(\d+\):\s*([0-9a-fA-F]{384})""")
    private val eNonceRegex = Regex("""M1 E-Nonce\s*\(\d+\):\s*([0-9a-fA-F]{32})""")
    private val pkrRegex = Regex("""Registrar public key\s*\(\d+\):\s*([0-9a-fA-F]{384})""")
    private val rNonceRegex = Regex("""Registrar R-Nonce\s*\(\d+\):\s*([0-9a-fA-F]{32})""")
    private val eHash1Regex = Regex("""E-Hash1\s*\(\d+\):\s*([0-9a-fA-F]{64})""")
    private val eHash2Regex = Regex("""E-Hash2\s*\(\d+\):\s*([0-9a-fA-F]{64})""")
    private val authKeyRegex = Regex("""AuthKey\s*\(\d+\):\s*([0-9a-fA-F]{64})""")
    private val keyWrapKeyRegex = Regex("""KeyWrapKey\s*\(\d+\):\s*([0-9a-fA-F]{32})""")
    private val bssidRegex = Regex("""BSSID=([0-9a-fA-F]{2}(?::[0-9a-fA-F]{2}){5})""")
    private val ssidRegex = Regex("""SSID=([^\s]+).*?BSSID=""")
    private val channelRegex = Regex("""channel=(\d+)""")

    private fun Regex.lastValue(text: String): String? =
        findAll(text).lastOrNull()?.groupValues?.getOrNull(1)

    fun parse(text: String): Parsed {
        // Logs begin with deterministic parser/crypto self-tests whose fields
        // can look like a real transaction. Always select the last occurrence:
        // the live exchange is printed after the self-tests and, if the log
        // contains multiple attempts, the most recent attempt is the useful one.
        val pke = pkeRegex.lastValue(text)
        val pkr = pkrRegex.lastValue(text)
        val eNonce = eNonceRegex.lastValue(text)
        val rNonce = rNonceRegex.lastValue(text)
        val eHash1 = eHash1Regex.lastValue(text)
        val eHash2 = eHash2Regex.lastValue(text)

        val material = if (
            pke != null && pkr != null && eNonce != null &&
            rNonce != null && eHash1 != null && eHash2 != null
        ) {
            WpsPixieCapturedMaterial(
                pke = WpsPixieMaterial.hexToBytes(pke),
                pkr = WpsPixieMaterial.hexToBytes(pkr),
                eNonce = WpsPixieMaterial.hexToBytes(eNonce),
                rNonce = WpsPixieMaterial.hexToBytes(rNonce),
                eHash1 = WpsPixieMaterial.hexToBytes(eHash1),
                eHash2 = WpsPixieMaterial.hexToBytes(eHash2),
                bssid = bssidRegex.lastValue(text)?.uppercase(),
                ssid = ssidRegex.lastValue(text),
                channel = channelRegex.lastValue(text)?.toIntOrNull(),
            ).also { it.validate() }
        } else {
            null
        }

        return Parsed(
            material = material,
            authKey = authKeyRegex.lastValue(text)?.let(WpsPixieMaterial::hexToBytes),
            keyWrapKey = keyWrapKeyRegex.lastValue(text)?.let(WpsPixieMaterial::hexToBytes),
        )
    }
}
