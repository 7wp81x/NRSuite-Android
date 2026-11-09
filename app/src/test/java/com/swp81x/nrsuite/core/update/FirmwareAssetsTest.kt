package com.swp81x.nrsuite.core.update

import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirmwareAssetsTest {
    @Test
    fun supportedChipListIncludesC5AndAllFirmwareTargets() {
        assertEquals(
            listOf(
                "ESP32",
                "ESP32-C5",
                "ESP32-S2",
                "ESP32-S3",
                "ESP32-S3 (PSRAM)",
                "ESP32-C3",
            ),
            SUPPORTED_FIRMWARE_CHIPS,
        )
        assertEquals(1, SUPPORTED_FIRMWARE_CHIPS.count { it == "ESP32-C5" })
        assertEquals(1, SUPPORTED_FIRMWARE_CHIPS.count { it == "ESP32-S3 (PSRAM)" })
    }

    @Test
    fun recognizesC5AssetNamesWithAndWithoutVersionSuffix() {
        assertEquals("ESP32-C5", firmwareChipFromAssetName("nrsuite-esp32-c5.bin"))
        assertEquals(
            "ESP32-C5",
            firmwareChipFromAssetName("beta-nrsuite-esp32-c5-v1.0.0-beta.3.bin"),
        )
        assertEquals(
            "ESP32-C5",
            firmwareChipFromAssetName("nrsuite-esp32c5.bin"),
        )
        assertEquals(
            "ESP32-C3",
            firmwareChipFromAssetName("nrsuite-esp32c3-v1.0.0-rc.1.bin"),
        )
    }

    @Test
    fun recognizesExistingChipAssetNames() {
        assertEquals("ESP32-C3", firmwareChipFromAssetName("beta-nrsuite-esp32c3-v1.bin"))
        assertEquals("ESP32-S3", firmwareChipFromAssetName("nrsuite-esp32s3.bin"))
        assertEquals(
            "ESP32-S3 (PSRAM)",
            firmwareChipFromAssetName("nrsuite-esp32s3-psram.bin"),
        )
        assertEquals(
            "ESP32-S3 (PSRAM)",
            firmwareChipFromAssetName("beta-nrsuite-esp32s3-psram-v1.0.0.bin"),
        )
        assertEquals("ESP32-S2", firmwareChipFromAssetName("nrsuite-esp32-s2.bin"))
        assertEquals("ESP32", firmwareChipFromAssetName("nrsuite-esp32classic.bin"))
        assertEquals("ESP32", firmwareChipFromAssetName("nrsuite-esp32-generic.bin"))
        assertEquals(null, firmwareChipFromAssetName("checksums.txt"))
    }

    @Test
    fun filtersReleaseAssetsByChip() {
        val release = ReleaseInfo(
            tag = "v1.0.0",
            name = null,
            htmlUrl = "https://example.invalid",
            downloadUrl = null,
            apkUrl = null,
            publishedAt = null,
            assets = listOf(
                ReleaseAsset("nrsuite-esp32c5-v1.0.0.bin", "https://example.invalid/c5.bin"),
                ReleaseAsset("nrsuite-esp32c3-v1.0.0.bin", "https://example.invalid/c3.bin"),
                ReleaseAsset("checksums.txt", "https://example.invalid/checksums.txt"),
            ),
        )

        assertEquals(
            listOf("nrsuite-esp32c5-v1.0.0.bin"),
            release.compatibleFirmwareAssets("ESP32-C5").map { it.name },
        )
        assertTrue(firmwareAssetMatchesChip("nrsuite-esp32c5.bin", "ESP32-C5"))
        assertFalse(firmwareAssetMatchesChip("nrsuite-esp32c3.bin", "ESP32-C5"))
    }

    @Test
    fun recognizesS3FlashAndPsramVariantsBeforeGenericS3() {
        assertEquals("ESP32-S3 (N16R8)", firmwareChipFromAssetName("nrsuite-esp32s3-n16r8-v1.0.0.bin"))
        assertEquals("ESP32-S3 (N8R8)", firmwareChipFromAssetName("rc-nrsuite-esp32-s3-n8r8-v1.0.0-rc.1.bin"))
        assertEquals("ESP32-S3 (N4R8)", firmwareChipFromAssetName("beta-nrsuite-esp32s3-n4r8.bin"))
        assertEquals("ESP32-S3 (N16)", firmwareChipFromAssetName("nrsuite-esp32s3-n16.bin"))
        assertEquals("ESP32-S3 (PSRAM)", firmwareChipFromAssetName("nrsuite-esp32s3-psram.bin"))
        assertEquals("ESP32-S3", firmwareChipFromAssetName("nrsuite-esp32s3.bin"))
        assertEquals("ESP32-C5 (N16R4)", firmwareChipFromAssetName("nrsuite-esp32c5-n16r4.bin"))
        assertEquals("ESP32-C5", firmwareChipFromAssetName("nrsuite-esp32c5.bin"))
    }

    @Test
    fun firmwareAssetsAreFilteredToOneBoardFamily() {
        val assets = listOf(
            ReleaseAsset("nrsuite-esp32s3.bin", "https://example.invalid/s3.bin"),
            ReleaseAsset("nrsuite-esp32s3-n16r8.bin", "https://example.invalid/n16r8.bin"),
            ReleaseAsset("nrsuite-esp32c5.bin", "https://example.invalid/c5.bin"),
            ReleaseAsset("nrsuite-esp32classic.bin", "https://example.invalid/classic.bin"),
        )
        assertEquals(
            listOf("nrsuite-esp32s3.bin", "nrsuite-esp32s3-n16r8.bin"),
            firmwareAssetsForFamily(assets, FirmwareBoardFamily.ESP32_S3).map { it.name },
        )
        assertEquals(
            listOf("nrsuite-esp32c5.bin"),
            firmwareAssetsForFamily(assets, FirmwareBoardFamily.ESP32_C5).map { it.name },
        )
        assertTrue(firmwareAssetsForFamily(assets, null).isEmpty())
    }

    @Test
    fun boardProfilesMapToDistinctFirmwareTargets() {
        val s3 = firmwareBoardFamilyForId("esp32-s3")
        assertEquals("ESP32-S3", s3?.universalTarget)
        val profiles = firmwareBoardProfilesForFamily("esp32-s3")
        assertEquals(6, profiles.size)
        assertEquals(
            "ESP32-S3 (N16R8)",
            profiles.firstOrNull { it.id == "esp32-s3-n16r8" }?.firmwareTarget,
        )
        assertEquals(
            s3,
            firmwareBoardFamilyForTarget("ESP32-S3 (N16R8)"),
        )
        assertNull(firmwareBoardProfileForTarget("ESP32-S3 (unknown)"))
    }

    @Test
    fun psramDevicePrefersPsramAssetAndFallsBackToBaseS3() {
        val release = ReleaseInfo(
            tag = "v1.0.0",
            name = null,
            htmlUrl = "https://example.invalid",
            downloadUrl = null,
            apkUrl = null,
            publishedAt = null,
            assets = listOf(
                ReleaseAsset("nrsuite-esp32s3-v1.0.0.bin", "https://example.invalid/s3.bin"),
                ReleaseAsset(
                    "nrsuite-esp32s3-psram-v1.0.0.bin",
                    "https://example.invalid/s3-psram.bin",
                ),
            ),
        )

        assertEquals(
            listOf("nrsuite-esp32s3-psram-v1.0.0.bin", "nrsuite-esp32s3-v1.0.0.bin"),
            release
                .compatibleFirmwareAssetsForDevice("ESP32-S3", setOf("psram"))
                .map { it.name },
        )
        assertEquals(
            listOf("nrsuite-esp32s3-v1.0.0.bin"),
            release
                .compatibleFirmwareAssetsForDevice("ESP32-S3", emptySet())
                .map { it.name },
        )
        assertEquals("ESP32-S3", firmwareHardwareChip("ESP32-S3 (PSRAM)"))
    }
}
