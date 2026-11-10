package com.swp81x.nrsuite.core.update

/**
 * Deterministic release data for debug/UI testing.
 *
 * Enabled with:
 *   ./gradlew :app:assembleDebug -Pnrsuite.mockReleaseData=true
 *
 * No network request is made while this is enabled. Asset URLs are intentionally
 * non-downloadable; this data exists to exercise release filtering, board
 * profile selection, and update UI states.
 */
internal object MockReleaseData {
    const val APP_TAG = "v1.0.0-rc.3"
    const val FIRMWARE_TAG = "v1.0.0-rc.2"

    private fun asset(name: String): ReleaseAsset = ReleaseAsset(
        name = name,
        url = "https://example.invalid/mock-firmware/$name",
        contentType = "application/octet-stream",
    )

    private fun firmwareAssets(): List<ReleaseAsset> = listOf(
        asset("nrsuite-esp32classic.bin"),
        asset("nrsuite-esp32c3.bin"),
        asset("nrsuite-esp32c5.bin"),
        asset("nrsuite-esp32s2.bin"),
        asset("nrsuite-esp32s3.bin"),
        asset("nrsuite-esp32s3-psram.bin"),
        asset("nrsuite-esp32s3-n4r8.bin"),
        asset("nrsuite-esp32s3-n8r8.bin"),
        asset("nrsuite-esp32s3-n16.bin"),
        asset("nrsuite-esp32s3-n16r8.bin"),
        asset("nrsuite-esp32c5-n8r4.bin"),
        asset("nrsuite-esp32c5-n16r4.bin"),
    )

    fun appRelease(): ReleaseInfo = ReleaseInfo(
        tag = APP_TAG,
        name = "NRSuite Android 1.0.0-rc.3 (mock)",
        htmlUrl = "https://example.invalid/NRSuite-Android/releases/tag/$APP_TAG",
        downloadUrl = "https://example.invalid/NRSuite-Android-$APP_TAG.apk",
        apkUrl = "https://example.invalid/NRSuite-Android-$APP_TAG.apk",
        publishedAt = "2026-11-09T12:00:00Z",
        assets = listOf(
            ReleaseAsset(
                name = "NRSuite-Android-1.0.0-rc.3-rc.apk",
                url = "https://example.invalid/NRSuite-Android-1.0.0-rc.3-rc.apk",
                contentType = "application/vnd.android.package-archive",
            ),
            ReleaseAsset(
                name = "checksums.txt",
                url = "https://example.invalid/checksums.txt",
                contentType = "text/plain",
            ),
        ),
    )

    fun firmwareReleases(): List<ReleaseInfo> = listOf(
        ReleaseInfo(
            tag = FIRMWARE_TAG,
            name = "NRSuite Firmware 1.0.0-rc.2 (mock)",
            htmlUrl = "https://example.invalid/NRSuite-firmware/releases/tag/$FIRMWARE_TAG",
            downloadUrl = null,
            apkUrl = null,
            publishedAt = "2026-11-09T12:00:00Z",
            assets = firmwareAssets(),
        ),
        ReleaseInfo(
            tag = "v1.0.0-rc.1",
            name = "NRSuite Firmware 1.0.0-rc.1 (mock)",
            htmlUrl = "https://example.invalid/NRSuite-firmware/releases/tag/v1.0.0-rc.1",
            downloadUrl = null,
            apkUrl = null,
            publishedAt = "2026-11-01T12:00:00Z",
            assets = listOf(
                asset("rc-nrsuite-esp32classic-v1.0.0-rc.1.bin"),
                asset("rc-nrsuite-esp32c3-v1.0.0-rc.1.bin"),
                asset("rc-nrsuite-esp32c5-v1.0.0-rc.1.bin"),
                asset("rc-nrsuite-esp32s2-v1.0.0-rc.1.bin"),
                asset("rc-nrsuite-esp32s3-v1.0.0-rc.1.bin"),
                asset("rc-nrsuite-esp32s3-psram-v1.0.0-rc.1.bin"),
            ),
        ),
        ReleaseInfo(
            tag = "v1.0.0-beta.3.2.0",
            name = "NRSuite Firmware 1.0.0-beta.3.2.0 (mock)",
            htmlUrl = "https://example.invalid/NRSuite-firmware/releases/tag/v1.0.0-beta.3.2.0",
            downloadUrl = null,
            apkUrl = null,
            publishedAt = "2026-10-01T12:00:00Z",
            assets = listOf(
                asset("beta-nrsuite-esp32classic-v1.0.0-beta.3.2.0.bin"),
                asset("beta-nrsuite-esp32c3-v1.0.0-beta.3.2.0.bin"),
                asset("beta-nrsuite-esp32c5-v1.0.0-beta.3.2.0.bin"),
                asset("beta-nrsuite-esp32s2-v1.0.0-beta.3.2.0.bin"),
                asset("beta-nrsuite-esp32s3-v1.0.0-beta.3.2.0.bin"),
                asset("beta-nrsuite-esp32s3-psram-v1.0.0-beta.3.2.0.bin"),
            ),
        ),
    )

    fun latestFirmwareRelease(): ReleaseInfo =
        firmwareReleases().maxWithOrNull { left, right ->
            VersionComparator.compare(left.tag, right.tag)
        } ?: firmwareReleases().first()
}
