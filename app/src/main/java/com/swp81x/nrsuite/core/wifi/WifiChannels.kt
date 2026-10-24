package com.swp81x.nrsuite.core.wifi

val WIFI_2G_CHANNELS: List<Int> = (1..14).toList()

val WIFI_5G_CHANNELS: List<Int> = listOf(
    36, 40, 44, 48,
    52, 56, 60, 64,
    100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 144,
    149, 153, 157, 161, 165, 169, 173, 177,
)

val WIFI_AUTO_CHANNELS: List<Int> = WIFI_2G_CHANNELS + WIFI_5G_CHANNELS

/**
 * Regulatory domains supported by the ESP-IDF Wi-Fi stack.
 *
 * The selected value must match the user's actual location. The firmware
 * restricts AP, beacon, and raw-TX channels using the active country mask.
 */
val WIFI_REGULATORY_COUNTRIES: List<Pair<String, String>> = listOf(
    "01" to "01 · World safe",
    "AT" to "AT · Austria",
    "AU" to "AU · Australia",
    "BE" to "BE · Belgium",
    "BG" to "BG · Bulgaria",
    "BR" to "BR · Brazil",
    "CA" to "CA · Canada",
    "CH" to "CH · Switzerland",
    "CN" to "CN · China",
    "CY" to "CY · Cyprus",
    "CZ" to "CZ · Czechia",
    "DE" to "DE · Germany",
    "DK" to "DK · Denmark",
    "EE" to "EE · Estonia",
    "ES" to "ES · Spain",
    "FI" to "FI · Finland",
    "FR" to "FR · France",
    "GB" to "GB · United Kingdom",
    "GR" to "GR · Greece",
    "HK" to "HK · Hong Kong",
    "HR" to "HR · Croatia",
    "HU" to "HU · Hungary",
    "IE" to "IE · Ireland",
    "IN" to "IN · India",
    "IS" to "IS · Iceland",
    "IT" to "IT · Italy",
    "JP" to "JP · Japan",
    "KR" to "KR · South Korea",
    "LI" to "LI · Liechtenstein",
    "LT" to "LT · Lithuania",
    "LU" to "LU · Luxembourg",
    "LV" to "LV · Latvia",
    "MT" to "MT · Malta",
    "MX" to "MX · Mexico",
    "NL" to "NL · Netherlands",
    "NO" to "NO · Norway",
    "NZ" to "NZ · New Zealand",
    "PL" to "PL · Poland",
    "PT" to "PT · Portugal",
    "RO" to "RO · Romania",
    "SE" to "SE · Sweden",
    "SI" to "SI · Slovenia",
    "SK" to "SK · Slovakia",
    "TW" to "TW · Taiwan",
    "US" to "US · United States",
)

fun isValidWifiChannel(channel: Int): Boolean = channel in WIFI_2G_CHANNELS || channel in WIFI_5G_CHANNELS

fun is5GhzWifiChannel(channel: Int): Boolean = channel in WIFI_5G_CHANNELS

fun bandForWifiChannel(channel: Int): WifiBand = when {
    channel in WIFI_5G_CHANNELS -> WifiBand.BAND_5
    else -> WifiBand.BAND_2_4
}

fun channelsForBand(
    band: WifiBand,
    allowed2gChannels: List<Int>,
    allowed5gChannels: List<Int>,
): List<Int> = when (band) {
    WifiBand.AUTO -> (allowed2gChannels + allowed5gChannels).distinct()
    WifiBand.BAND_2_4 -> allowed2gChannels
    WifiBand.BAND_5 -> allowed5gChannels
}

fun normalizeWifiChannel(channel: Int, fallback: Int = 1): Int =
    if (isValidWifiChannel(channel)) channel else fallback

fun normalizeWifiChannel(
    channel: Int,
    allowedChannels: List<Int>,
    fallback: Int = allowedChannels.firstOrNull() ?: 1,
): Int = if (channel in allowedChannels) channel else fallback

fun nextWifiChannel(channel: Int, forward: Boolean): Int =
    nextWifiChannel(channel, forward, WIFI_AUTO_CHANNELS)

fun nextWifiChannel(channel: Int, forward: Boolean, allowedChannels: List<Int>): Int {
    if (allowedChannels.isEmpty()) return channel
    val index = allowedChannels.indexOf(channel)
    if (index < 0) return allowedChannels.first()
    val next = if (forward) (index + 1) % allowedChannels.size
    else (index - 1 + allowedChannels.size) % allowedChannels.size
    return allowedChannels[next]
}
