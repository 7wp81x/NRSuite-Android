package com.swp81x.nrsuite.core.update

private data class FirmwareChipAssetMatcher(
    val chip: String,
    val tokens: List<String>,
)

// Specific chips must be checked before the classic ESP32 fallback.
private val FIRMWARE_CHIP_ASSET_MATCHERS = listOf(
    FirmwareChipAssetMatcher("ESP32-C5", listOf("esp32c5", "esp32-c5")),
    FirmwareChipAssetMatcher("ESP32-C3", listOf("esp32c3", "esp32-c3")),
    FirmwareChipAssetMatcher("ESP32-S3", listOf("esp32s3", "esp32-s3")),
    FirmwareChipAssetMatcher("ESP32-S2", listOf("esp32s2", "esp32-s2")),
    FirmwareChipAssetMatcher(
        "ESP32",
        listOf("esp32-generic", "esp32-devkit", "esp32dev"),
    ),
)

/**
 * Returns the chip targeted by a firmware file name.
 *
 * Release assets may be named either `nrsuite-esp32c5.bin`,
 * `nrsuite-esp32-c5.bin`, or include a version tag after the chip token.
 */
fun firmwareChipFromAssetName(name: String?): String? {
    val normalized = name?.lowercase()?.substringAfterLast('/') ?: return null
    if (!normalized.endsWith(".bin")) return null
    return FIRMWARE_CHIP_ASSET_MATCHERS
        .firstOrNull { matcher -> matcher.tokens.any { it in normalized } }
        ?.chip
}

fun firmwareChipFromFirmwareName(name: String?): String? =
    firmwareChipFromAssetName(name)

fun firmwareAssetSuffixForChip(chip: String?): String? = when (chip) {
    "ESP32-C5" -> "esp32-c5"
    "ESP32-C3" -> "esp32c3"
    "ESP32-S3" -> "esp32s3"
    "ESP32-S2" -> "esp32s2"
    "ESP32" -> "esp32-generic"
    else -> null
}

fun firmwareAssetMatchesChip(assetName: String?, chip: String?): Boolean {
    if (chip.isNullOrBlank()) return false
    return firmwareChipFromAssetName(assetName) == chip
}

fun ReleaseInfo.compatibleFirmwareAssets(chip: String?): List<ReleaseAsset> {
    if (chip.isNullOrBlank()) return emptyList()
    return assets.filter { firmwareAssetMatchesChip(it.name, chip) }
}
