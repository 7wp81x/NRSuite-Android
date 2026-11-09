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
        "ESP32-S3 (N16R8)",
        listOf("esp32s3-n16r8", "esp32-s3-n16r8", "esp32s3n16r8"),
    ),
    FirmwareChipAssetMatcher(
        "ESP32-S3 (N8R8)",
        listOf("esp32s3-n8r8", "esp32-s3-n8r8", "esp32s3n8r8"),
    ),
    FirmwareChipAssetMatcher(
        "ESP32-S3 (N4R8)",
        listOf("esp32s3-n4r8", "esp32-s3-n4r8", "esp32s3n4r8"),
    ),
    FirmwareChipAssetMatcher(
        "ESP32-S3 (N16)",
        listOf("esp32s3-n16", "esp32-s3-n16", "esp32s3n16"),
    ),
    FirmwareChipAssetMatcher(
        FIRMWARE_TARGET_ESP32_S3_PSRAM,
        listOf("esp32s3-psram", "esp32-s3-psram", "esp32s3psram", "esp32s3-n4r2", "esp32-s3-n4r2"),
    ),
    FirmwareChipAssetMatcher(
        "ESP32-C5 (N16R4)",
        listOf("esp32c5-n16r4", "esp32-c5-n16r4", "esp32c5n16r4"),
    ),
    FirmwareChipAssetMatcher(
        "ESP32-C5 (N8R4)",
        listOf("esp32c5-n8r4", "esp32-c5-n8r4", "esp32c5n8r4"),
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
    FIRMWARE_TARGET_ESP32_S3_PSRAM,
    "ESP32-S3 (N4R8)",
    "ESP32-S3 (N8R8)",
    "ESP32-S3 (N16)",
    "ESP32-S3 (N16R8)",
    -> FIRMWARE_TARGET_ESP32_S3
    "ESP32-C5 (N8R4)",
    "ESP32-C5 (N16R4)",
    -> "ESP32-C5"
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

enum class FirmwareBoardFamily(
    val id: String,
    val displayName: String,
    val universalTarget: String,
) {
    ESP32("esp32", "ESP32", "ESP32"),
    ESP32_C3("esp32-c3", "ESP32-C3", "ESP32-C3"),
    ESP32_C5("esp32-c5", "ESP32-C5", "ESP32-C5"),
    ESP32_S2("esp32-s2", "ESP32-S2", "ESP32-S2"),
    ESP32_S3("esp32-s3", "ESP32-S3", "ESP32-S3"),
}

/**
 * A selectable, validated board layout profile.
 *
 * These are deliberately not free-form flash/PSRAM combinations. The selected
 * profile determines the firmware target string, partition layout, and which
 * release asset names are considered compatible.
 */
data class FirmwareBoardProfile(
    val id: String,
    val family: FirmwareBoardFamily,
    val displayName: String,
    val firmwareTarget: String,
    val flashDescription: String,
    val psramDescription: String,
    val partitionDescription: String,
    val universalSafeFallback: Boolean = false,
    val expectsPsram: Boolean = false,
    val releaseAvailable: Boolean = true,
    val validated: Boolean = true,
) {
    /** Family is already shown in the Target board dropdown. */
    val variantLabel: String
        get() = displayName
            .removePrefix(family.displayName)
            .trim()
            .ifBlank { displayName }
}

val FIRMWARE_BOARD_PROFILES: List<FirmwareBoardProfile> = listOf(
    FirmwareBoardProfile(
        id = "esp32-universal",
        family = FirmwareBoardFamily.ESP32,
        displayName = "Universal / Safe fallback",
        firmwareTarget = "ESP32",
        flashDescription = "4 MB flash",
        psramDescription = "No PSRAM",
        partitionDescription = "3 MB application + 1 MB FAT",
        universalSafeFallback = true,
    ),
    FirmwareBoardProfile(
        id = "esp32-c3-universal",
        family = FirmwareBoardFamily.ESP32_C3,
        displayName = "Universal / Safe fallback",
        firmwareTarget = "ESP32-C3",
        flashDescription = "4 MB flash",
        psramDescription = "No PSRAM",
        partitionDescription = "3 MB application + 768 KB SPIFFS + portal",
        universalSafeFallback = true,
    ),
    FirmwareBoardProfile(
        id = "esp32-c5-universal",
        family = FirmwareBoardFamily.ESP32_C5,
        displayName = "Universal / Safe fallback",
        firmwareTarget = "ESP32-C5",
        flashDescription = "4 MB flash",
        psramDescription = "No PSRAM",
        partitionDescription = "3 MB application + 768 KB SPIFFS + portal",
        universalSafeFallback = true,
    ),
    FirmwareBoardProfile(
        id = "esp32-c5-n8r4",
        family = FirmwareBoardFamily.ESP32_C5,
        displayName = "ESP32-C5 N8R4",
        firmwareTarget = "ESP32-C5 (N8R4)",
        flashDescription = "8 MB flash",
        psramDescription = "4 MB PSRAM",
        partitionDescription = "3 MB application + expanded LittleFS + portal",
        expectsPsram = true,
        releaseAvailable = true,
        validated = false,
    ),
    FirmwareBoardProfile(
        id = "esp32-c5-n16r4",
        family = FirmwareBoardFamily.ESP32_C5,
        displayName = "ESP32-C5 N16R4",
        firmwareTarget = "ESP32-C5 (N16R4)",
        flashDescription = "16 MB flash",
        psramDescription = "4 MB PSRAM",
        partitionDescription = "3 MB application + expanded LittleFS + portal",
        expectsPsram = true,
        releaseAvailable = true,
        validated = false,
    ),
    FirmwareBoardProfile(
        id = "esp32-s2-universal",
        family = FirmwareBoardFamily.ESP32_S2,
        displayName = "Universal / Safe fallback",
        firmwareTarget = "ESP32-S2",
        flashDescription = "4 MB flash",
        psramDescription = "No PSRAM",
        partitionDescription = "3 MB application + 1 MB FAT",
        universalSafeFallback = true,
    ),
    FirmwareBoardProfile(
        id = "esp32-s3-universal",
        family = FirmwareBoardFamily.ESP32_S3,
        displayName = "Universal / Safe fallback",
        firmwareTarget = "ESP32-S3",
        flashDescription = "4 MB flash",
        psramDescription = "No PSRAM assumption",
        partitionDescription = "3 MB application + 1 MB FAT",
        universalSafeFallback = true,
    ),
    FirmwareBoardProfile(
        id = "esp32-s3-n4r2",
        family = FirmwareBoardFamily.ESP32_S3,
        displayName = "ESP32-S3 N4R2",
        firmwareTarget = "ESP32-S3 (PSRAM)",
        flashDescription = "4 MB flash",
        psramDescription = "2 MB PSRAM",
        partitionDescription = "3 MB application + 1 MB FAT",
        expectsPsram = true,
    ),
    FirmwareBoardProfile(
        id = "esp32-s3-n4r8",
        family = FirmwareBoardFamily.ESP32_S3,
        displayName = "ESP32-S3 N4R8",
        firmwareTarget = "ESP32-S3 (N4R8)",
        flashDescription = "4 MB flash",
        psramDescription = "8 MB PSRAM",
        partitionDescription = "3 MB application + 1 MB FAT",
        expectsPsram = true,
        releaseAvailable = true,
        validated = true,
    ),
    FirmwareBoardProfile(
        id = "esp32-s3-n8r8",
        family = FirmwareBoardFamily.ESP32_S3,
        displayName = "ESP32-S3 N8R8",
        firmwareTarget = "ESP32-S3 (N8R8)",
        flashDescription = "8 MB flash",
        psramDescription = "8 MB PSRAM",
        partitionDescription = "4 MB application + ~3.9 MB FAT",
        expectsPsram = true,
        releaseAvailable = true,
        validated = true,
    ),
    FirmwareBoardProfile(
        id = "esp32-s3-n16",
        family = FirmwareBoardFamily.ESP32_S3,
        displayName = "ESP32-S3 N16",
        firmwareTarget = "ESP32-S3 (N16)",
        flashDescription = "16 MB flash",
        psramDescription = "No PSRAM",
        partitionDescription = "6 MB application + ~9.9 MB FAT",
        releaseAvailable = true,
        validated = true,
    ),
    FirmwareBoardProfile(
        id = "esp32-s3-n16r8",
        family = FirmwareBoardFamily.ESP32_S3,
        displayName = "ESP32-S3 N16R8",
        firmwareTarget = "ESP32-S3 (N16R8)",
        flashDescription = "16 MB flash",
        psramDescription = "8 MB PSRAM",
        partitionDescription = "6 MB application + ~9.9 MB FAT",
        expectsPsram = true,
        releaseAvailable = true,
        validated = true,
    ),
)

fun firmwareBoardProfilesForFamily(familyId: String): List<FirmwareBoardProfile> =
    FIRMWARE_BOARD_PROFILES.filter { it.family.id == familyId }

fun firmwareBoardFamilyForId(id: String): FirmwareBoardFamily? =
    FIRMWARE_BOARD_FAMILIES.firstOrNull { it.id == id }

val FIRMWARE_BOARD_FAMILIES: List<FirmwareBoardFamily> =
    FirmwareBoardFamily.entries.sortedBy { it.displayName }

fun firmwareBoardProfileForTarget(target: String?): FirmwareBoardProfile? {
    val normalized = target?.trim().orEmpty()
    if (normalized.isBlank()) return null
    return FIRMWARE_BOARD_PROFILES.firstOrNull {
        it.firmwareTarget.equals(normalized, ignoreCase = true)
    }
}

fun firmwareBoardFamilyForTarget(target: String?): FirmwareBoardFamily? =
    firmwareBoardProfileForTarget(target)?.family


fun firmwareAssetsForFamily(
    assets: List<ReleaseAsset>,
    family: FirmwareBoardFamily?,
): List<ReleaseAsset> {
    if (family == null) return emptyList()
    return assets.filter { asset ->
        val target = firmwareChipFromAssetName(asset.name)
        firmwareBoardFamilyForTarget(target) == family
    }
}
