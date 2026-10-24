package com.swp81x.nrsuite.core.wifi

enum class WifiBand(
    val wireName: String,
    val label: String,
) {
    AUTO("auto", "Auto"),
    BAND_2_4("2g", "2.4 GHz"),
    BAND_5("5g", "5 GHz"),
}
