package com.swp81x.nrsuite.core.update

/**
 * User-selectable firmware targets shown in the local flashing review step.
 *
 * Keep this list centralized so the flasher UI cannot silently drop a
 * supported chip while the asset matcher and build targets continue to
 * support it.
 */
const val FIRMWARE_TARGET_ESP32_S3 = "ESP32-S3"
const val FIRMWARE_TARGET_ESP32_S3_PSRAM = "ESP32-S3 (PSRAM)"

val SUPPORTED_FIRMWARE_CHIPS: List<String> = listOf(
    "ESP32",
    "ESP32-C5",
    "ESP32-S2",
    FIRMWARE_TARGET_ESP32_S3,
    FIRMWARE_TARGET_ESP32_S3_PSRAM,
    "ESP32-C3",
)

private data class FirmwareChipAssetMatcher(
    val chip: String,
    val tokens: List<String>,
)

// Specific chips/variants must be checked before their base-chip fallback.
private val FIRMWARE_CHIP_ASSET_MATCHERS = listOf(
    FirmwareChipAssetMatcher(
        FIRMWARE_TARGET_ESP32_S3_PSRAM,
        listOf("esp32s3-psram", "esp32-s3-psram", "esp32s3psram"),
    ),
    FirmwareChipAssetMatcher("ESP32-C5", listOf("esp32c5", "esp32-c5")),
    FirmwareChipAssetMatcher("ESP32-C3", listOf("esp32c3", "esp32-c3")),
    FirmwareChipAssetMatcher(FIRMWARE_TARGET_ESP32_S3, listOf("esp32s3", "esp32-s3")),
    FirmwareChipAssetMatcher("ESP32-S2", listOf("esp32s2", "esp32-s2")),
    FirmwareChipAssetMatcher(
        "ESP32",
        listOf(
            "esp32classic",
            "esp32-classic",
            "esp32-generic",
            "esp32-devkit",
            "esp32dev",
        ),
    ),
)

/**
 * Returns the firmware target described by a firmware file name.
 *
 * Release assets may be named either `nrsuite-esp32c5.bin`,
 * `nrsuite-esp32s3-psram.bin`, `nrsuite-esp32classic.bin`, or include a
 * version tag after the chip token. Legacy `-c5` and `-generic` names remain
 * recognized for older releases.
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

/** Base hardware family used for ROM flashing and encrypted-flash support. */
fun firmwareHardwareChip(target: String?): String? = when (target) {
    FIRMWARE_TARGET_ESP32_S3_PSRAM -> FIRMWARE_TARGET_ESP32_S3
    else -> target
}

/** Select the S3 firmware variant matching the connected device's capabilities. */
fun firmwareTargetForConnectedDevice(
    chip: String?,
    features: Set<String>,
): String? = when {
    chip == FIRMWARE_TARGET_ESP32_S3 && "psram" in features ->
        FIRMWARE_TARGET_ESP32_S3_PSRAM
    else -> chip
}

/**
 * Ordered target candidates for update downloads. A PSRAM S3 prefers its
 * dedicated image but can still fall back to the base S3 image from older
 * releases.
 */
fun firmwareTargetCandidatesForDevice(
    chip: String?,
    features: Set<String>,
): List<String> {
    val target = firmwareTargetForConnectedDevice(chip, features) ?: return emptyList()
    return if (target == FIRMWARE_TARGET_ESP32_S3_PSRAM) {
        listOf(FIRMWARE_TARGET_ESP32_S3_PSRAM, FIRMWARE_TARGET_ESP32_S3)
    } else {
        listOf(target)
    }
}

fun firmwareAssetSuffixForChip(chip: String?): String? = when (chip) {
    "ESP32-C5" -> "esp32-c5"
    "ESP32-C3" -> "esp32c3"
    FIRMWARE_TARGET_ESP32_S3 -> "esp32s3"
    FIRMWARE_TARGET_ESP32_S3_PSRAM -> "esp32-s3-psram"
    "ESP32-S2" -> "esp32s2"
    "ESP32" -> "esp32classic"
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

fun ReleaseInfo.compatibleFirmwareAssetsForDevice(
    chip: String?,
    features: Set<String>,
): List<ReleaseAsset> {
    val targets = firmwareTargetCandidatesForDevice(chip, features)
    return targets
        .flatMap { target -> compatibleFirmwareAssets(target) }
        .distinctBy { it.name }
}
