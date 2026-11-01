package com.swp81x.nrsuite.core.credentials

/**
 * Standard Wi-Fi QR payload for a WPA/WPA2/WPA3-PSK credential.
 *
 * The QR intentionally contains no WPS PIN. Only generate or display it after
 * an explicit user action because the payload exposes the password.
 */
object WifiQrPayload {
    fun build(
        ssid: String,
        password: String,
        hidden: Boolean = false,
        security: String = "WPA",
    ): String = buildString {
        append("WIFI:T:")
        append(security.ifBlank { "WPA" })
        append(";S:")
        append(escape(ssid))
        append(";P:")
        append(escape(password))
        append(";H:")
        append(if (hidden) "true" else "false")
        append(";;")
    }

    /**
     * Escape the characters that are structural in the WIFI: URI scheme.
     * See the common ZXing Wi-Fi QR convention.
     */
    fun escape(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '\\', ';', ',', '"', ':' -> {
                    append('\\')
                    append(char)
                }
                else -> append(char)
            }
        }
    }
}
