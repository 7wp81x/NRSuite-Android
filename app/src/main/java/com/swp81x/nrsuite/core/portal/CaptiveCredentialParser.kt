package com.swp81x.nrsuite.core.portal

import org.json.JSONObject

/**
 * Common password field names used by captive-portal templates.
 *
 * Order matters: the first non-blank alias wins so a template that submits
 * both `password` and a hidden `key` field still prefers the real password.
 */
private val PASSWORD_FIELD_ALIASES = listOf(
    "password",
    "pass",
    "passwd",
    "pwd",
    "passphrase",
    "wpa",
    "wpa_key",
    "wpa_passphrase",
    "wifi_password",
    "psk",
    "key",
)

fun extractCaptivePassword(data: JSONObject?): String {
    if (data == null) return ""
    val actualKeys = data.keys().asSequence().toList()
    for (alias in PASSWORD_FIELD_ALIASES) {
        val key = actualKeys.firstOrNull { it.equals(alias, ignoreCase = true) } ?: continue
        val value = data.optString(key)
        if (value.isNotBlank()) return value
    }
    return ""
}
