package com.swp81x.nrsuite

import androidx.core.content.FileProvider
import com.swp81x.nrsuite.core.download.DownloadState
import com.swp81x.nrsuite.core.download.NrSuiteDownloadManager
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.update.FirmwareAssetDownloadState
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.update.compatibleFirmwareAssets
import java.io.File
import kotlinx.coroutines.launch

internal fun MainViewModel.downloadFirmwareUpdateForCurrentDeviceImpl() {
    val connected = session?.state?.value as? ConnectionState.Connected ?: run {
        appendLog("Connect a device before downloading a firmware update.", level = LogLevel.ERROR)
        return
    }
    val chip = connected.chip ?: run {
        appendLog("Could not determine the connected chip for firmware update.", level = LogLevel.ERROR)
        return
    }
    val release = _firmwareUpdate.value?.latest
        ?: _firmwareReleases.value.firstOrNull { it.compatibleFirmwareAssets(chip).isNotEmpty() }
        ?: run {
            appendLog("No firmware release is available for $chip.", level = LogLevel.ERROR)
            return
        }
    val asset = release.compatibleFirmwareAssets(chip).firstOrNull() ?: run {
        appendLog("No firmware asset matches $chip in ${release.tag}.", level = LogLevel.ERROR)
        return
    }
    downloadFirmwareAssetImpl(release.tag, asset.name)
}

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
        downloadedFirmwarePath = destination.absolutePath
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
                            downloadedFirmwarePath = destination.absolutePath
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

internal fun MainViewModel.prepareFirmwareFlasherForUpdateImpl() {
    val file = downloadedFirmwarePath?.let(::File)
    if (file != null && file.exists() && file.length() > 0L) {
        selectDownloadedFirmwareImpl(file)
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
