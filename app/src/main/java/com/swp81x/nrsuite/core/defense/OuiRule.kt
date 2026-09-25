package com.swp81x.nrsuite.core.defense

enum class OuiRuleAction { WHITELIST, BLACKLIST }

data class OuiRule(
    val id: String,
    val ouiPrefix: String,      // normalized "XX:XX:XX", uppercase
    val label: String,          // user's own note, e.g. "Local PisoWiFi boards"
    val action: OuiRuleAction,
)

fun normalizeOuiPrefix(input: String): String? {
    val cleaned = input.trim().uppercase().replace("-", ":")
    val parts = cleaned.split(":")
    if (parts.size != 3) return null
    if (parts.any { it.length != 2 || it.toIntOrNull(16) == null }) return null
    return parts.joinToString(":")
}

fun matchOuiRule(mac: String, rules: List<OuiRule>): OuiRule? {
    val prefix = mac.trim().uppercase().split(":").take(3).joinToString(":")
    return rules.firstOrNull { it.ouiPrefix == prefix }
}
