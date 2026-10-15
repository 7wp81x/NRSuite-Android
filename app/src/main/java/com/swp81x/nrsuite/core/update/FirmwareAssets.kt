package com.swp81x.nrsuite.core.update

fun firmwareAssetSuffixForChip(chip: String?): String? = when (chip) {
    "ESP32-C3" -> "esp32c3.bin"
    "ESP32-S3" -> "esp32s3.bin"
    "ESP32-S2" -> "esp32s2.bin"
    "ESP32" -> "esp32-generic.bin"
    else -> null
}

fun ReleaseInfo.compatibleFirmwareAssets(chip: String?): List<ReleaseAsset> {
    val suffix = firmwareAssetSuffixForChip(chip) ?: return emptyList()
    return assets.filter { it.name.endsWith(suffix, ignoreCase = true) }
}
