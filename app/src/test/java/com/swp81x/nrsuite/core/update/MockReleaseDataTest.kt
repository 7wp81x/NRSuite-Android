package com.swp81x.nrsuite.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MockReleaseDataTest {
    @Test
    fun `mock firmware release exposes every planned board asset`() {
        val release = MockReleaseData.firmwareReleases().first()
        val targets = release.assets.mapNotNull { firmwareChipFromAssetName(it.name) }.toSet()

        assertTrue(targets.contains("ESP32-S3 (PSRAM)"))
        assertTrue(targets.contains("ESP32-S3 (N4R8)"))
        assertTrue(targets.contains("ESP32-S3 (N8R8)"))
        assertTrue(targets.contains("ESP32-S3 (N16)"))
        assertTrue(targets.contains("ESP32-S3 (N16R8)"))
        assertTrue(targets.contains("ESP32-C5 (N8R4)"))
        assertTrue(targets.contains("ESP32-C5 (N16R4)"))
        assertEquals(
            1,
            release.assets.count {
                firmwareChipFromAssetName(it.name) == "ESP32-S3 (PSRAM)"
            },
        )
    }

    @Test
    fun `mock latest firmware is the rc release`() {
        assertEquals("v1.0.0-rc.2", MockReleaseData.latestFirmwareRelease().tag)
        assertEquals("v1.0.0-rc.3", MockReleaseData.appRelease().tag)
        assertNotNull(MockReleaseData.appRelease().apkUrl)
    }

    @Test
    fun `mock release filtering keeps beta and rc ordering testable`() {
        val tags = MockReleaseData.firmwareReleases().map { it.tag }
        assertTrue(tags.contains("v1.0.0-beta.3.2.0"))
        assertTrue(tags.contains("v1.0.0-rc.1"))
        assertTrue(tags.contains("v1.0.0-rc.2"))
    }
}
