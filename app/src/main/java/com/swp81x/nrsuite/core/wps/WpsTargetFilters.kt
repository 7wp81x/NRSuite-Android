package com.swp81x.nrsuite.core.wps

import com.swp81x.nrsuite.core.wifi.NetworkTarget

/**
 * WPS Pixie Dust can only act on WPA/WPA2-PSK-style WPS targets. Open, WEP,
 * enterprise, and WPA3-only APs may still advertise WPS, but they are not
 * actionable Pixie targets for this workflow.
 */
internal fun isActionableWpsPixieTarget(target: NetworkTarget): Boolean {
    if (!target.wps) return false
    val security = target.security.uppercase()
    if ("OPEN" in security || "NONE" in security) return false
    if ("WEP" in security || "ENTERPRISE" in security) return false
    if ("WPA3" in security && "WPA2" !in security) return false
    return "WPA2" in security || "WPA-PSK" in security
}

internal fun filterWpsPixieTargets(
    targets: List<NetworkTarget>,
    wpsOnly: Boolean,
): List<NetworkTarget> = if (wpsOnly) {
    targets.filter(::isActionableWpsPixieTarget)
} else {
    targets
}
