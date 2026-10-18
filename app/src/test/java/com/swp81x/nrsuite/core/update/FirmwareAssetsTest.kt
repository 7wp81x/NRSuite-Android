package com.swp81x.nrsuite.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirmwareAssetsTest {
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
    }

    @Test
    fun recognizesExistingChipAssetNames() {
        assertEquals("ESP32-C3", firmwareChipFromAssetName("beta-nrsuite-esp32c3-v1.bin"))
        assertEquals("ESP32-S3", firmwareChipFromAssetName("nrsuite-esp32s3.bin"))
        assertEquals("ESP32-S2", firmwareChipFromAssetName("nrsuite-esp32-s2.bin"))
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
}
