package com.swp81x.nrsuite.core.portal

/** A bounded, best-effort record of a client seen by the captive portal. */
data class PortalVisitor(
    val mac: String = "unknown",
    val ip: String = "unknown",
    val userAgent: String = "",
    val requestType: String = "",
    val lastSeen: String = "",
    val visitCount: Int = 1,
) {
    val identityKey: String
        get() = if (mac.isNotBlank() && !mac.equals("unknown", ignoreCase = true)) {
            "mac:${mac.uppercase()}"
        } else {
            "ip:${ip.ifBlank { "unknown" }}"
        }
}

/**
 * Merge a visitor event into a bounded newest-last list. A known MAC is the
 * primary identity; IP is only the fallback when the firmware cannot resolve
 * the association table entry.
 */
fun mergePortalVisitor(
    current: List<PortalVisitor>,
    incoming: PortalVisitor,
    limit: Int = 100,
): List<PortalVisitor> {
    require(limit > 0) { "limit must be positive" }
    val incomingMac = incoming.mac.ifBlank { "unknown" }
    val incomingIp = incoming.ip.ifBlank { "unknown" }
    val macKnown = !incomingMac.equals("unknown", ignoreCase = true)

    // If a later request cannot resolve the MAC, retain the MAC learned from
    // the same IP instead of creating a second visitor row.
    val previous = if (macKnown) {
        current.lastOrNull { it.identityKey == "mac:${incomingMac.uppercase()}" }
    } else {
        current.lastOrNull {
            it.ip.equals(incomingIp, ignoreCase = true) &&
                !it.mac.equals("unknown", ignoreCase = true)
        } ?: current.lastOrNull { it.identityKey == "ip:$incomingIp" }
    }
    val normalized = incoming.copy(
        mac = if (macKnown) incomingMac else previous?.mac ?: "unknown",
        ip = incomingIp,
    )
    val merged = normalized.copy(
        visitCount = (previous?.visitCount ?: 0) + 1,
    )
    return (current.filterNot { it.identityKey == normalized.identityKey } + merged)
        .takeLast(limit)
}
