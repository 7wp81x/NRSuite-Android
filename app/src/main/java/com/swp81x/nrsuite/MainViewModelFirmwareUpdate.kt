package com.swp81x.nrsuite

import androidx.core.content.FileProvider
import com.swp81x.nrsuite.core.download.DownloadState
import com.swp81x.nrsuite.core.download.NrSuiteDownloadManager
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.update.FirmwareAssetDownloadState
import java.io.File
import kotlinx.coroutines.launch

internal fun MainViewModel.downloadFirmwareAssetImpl(
    releaseTag: String,
    assetName: String,
) {
    val release = _firmwareReleases.value.firstOrNull { it.tag == releaseTag } ?: run {
        _firmwareAssetDownload.value =
            FirmwareAssetDownloadState.Failed("Firmware release $releaseTag not found")
        return
    }
    val asset = release.assets.firstOrNull { it.name == assetName } ?: run {
        _firmwareAssetDownload.value =
            FirmwareAssetDownloadState.Failed("Firmware asset $assetName not found")
        return
    }

    if (_firmwareAssetDownload.value is FirmwareAssetDownloadState.Downloading) return

    val destination = NrSuiteDownloadManager.destinationFile(
        app,
        "updates/firmware/${sanitizeFirmwareName(releaseTag)}-${sanitizeFirmwareName(assetName)}",
    )
    if (destination.exists() && destination.length() > 0L) {
        selectDownloadedFirmwareImpl(destination)
        _firmwareAssetDownload.value = FirmwareAssetDownloadState.Downloaded(destination.name)
        return
    }

    _firmwareAssetDownload.value = FirmwareAssetDownloadState.Downloading(percent = null)
    scope.launch {
        runCatching {
            val downloadId = NrSuiteDownloadManager.enqueue(
                context = app,
                url = asset.url,
                destination = destination,
                title = "NRSuite firmware",
                description = "Downloading ${asset.name}",
            )
            NrSuiteDownloadManager.observe(app, downloadId).collect { state ->
                when (state) {
                    is DownloadState.Pending -> {
                        _firmwareAssetDownload.value =
                            FirmwareAssetDownloadState.Downloading(percent = null)
                    }
                    is DownloadState.Running -> {
                        _firmwareAssetDownload.value =
                            FirmwareAssetDownloadState.Downloading(state.percent)
                    }
                    DownloadState.Completed -> {
                        if (destination.exists() && destination.length() > 0L) {
                            selectDownloadedFirmwareImpl(destination)
                            _firmwareAssetDownload.value =
                                FirmwareAssetDownloadState.Downloaded(destination.name)
                            appendLog("Firmware downloaded: ${asset.name}", tag = "update")
                        } else {
                            _firmwareAssetDownload.value =
                                FirmwareAssetDownloadState.Failed("Downloaded firmware is empty")
                        }
                    }
                    is DownloadState.Failed -> {
                        _firmwareAssetDownload.value =
                            FirmwareAssetDownloadState.Failed(state.reason)
                        appendLog(
                            "Firmware download failed: ${state.reason}",
                            level = LogLevel.ERROR,
                        )
                    }
                }
            }
        }.onFailure { error ->
            val message = error.message ?: "Firmware download failed"
            _firmwareAssetDownload.value = FirmwareAssetDownloadState.Failed(message)
            appendLog("Firmware download failed: $message", level = LogLevel.ERROR)
        }
    }
}

internal fun MainViewModel.selectDownloadedFirmwareImpl(file: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(
            app,
            "${app.packageName}.fileprovider",
            file,
        )
        _firmwareFlashUri.value = uri
        _firmwareFlashName.value = file.name
        _firmwareFlashSize.value = file.length()
        _firmwareFlashProgress.value = 0
        _firmwareFlashStatus.value = null
        appendLog("Firmware image selected: ${file.name}")
    }.onFailure { error ->
        _firmwareAssetDownload.value =
            FirmwareAssetDownloadState.Failed(error.message ?: "Could not select firmware")
        appendLog(
            "Could not select downloaded firmware: ${error.message}",
            level = LogLevel.ERROR,
        )
    }
}

private fun sanitizeFirmwareName(raw: String): String =
    raw.replace(Regex("[^A-Za-z0-9._-]"), "_")
