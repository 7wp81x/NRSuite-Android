package com.swp81x.nrsuite.core.defense

private val INFRASTRUCTURE_VENDOR_KEYWORDS = listOf(
    "tenda",
    "tp-link",
    "tplink",
    "d-link",
    "dlink",
    "netgear",
    "asus",
    "linksys",
    "ubiquiti",
    "mikrotik",
    "huawei",
    "xiaomi",
    "zte",
    "aruba",
    "cisco",
    "meraki",
    "ruckus",
    "arris",
    "actiontec",
    "belkin",
    "buffalo",
    "edimax",
    "engenius",
    "extreme",
    "fortinet",
    "juniper",
    "netcomm",
    "netis",
    "phicomm",
    "sercomm",
    "sagemcom",
    "technicolor",
    "totolink",
    "trendnet",
    "zyxel",
    "avm",
    "fritz",
    "google",
    "amazon",
    "eero",
    "nest",
    "samsung",
    "motorola",
    "calix",
    "adtran",
    "sophos",
    "watchguard",
    "sonicwall",
    "palo alto",
)

/**
 * Heuristic only: vendor names commonly associated with consumer/SMB access
 * points and routers. This lowers OUI-mismatch suspicion; it is not an
 * authentication or security guarantee.
 */
fun isLikelyInfrastructureVendor(vendor: String?): Boolean {
    val normalized = vendor?.lowercase() ?: return false
    return INFRASTRUCTURE_VENDOR_KEYWORDS.any { keyword -> keyword in normalized }
}
