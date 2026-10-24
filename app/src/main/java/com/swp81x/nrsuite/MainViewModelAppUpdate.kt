package com.swp81x.nrsuite

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.swp81x.nrsuite.core.download.DownloadState
import com.swp81x.nrsuite.core.download.NrSuiteDownloadManager
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.update.AppUpdateDownloadState
import java.io.File
import kotlinx.coroutines.launch

private const val PREF_APP_UPDATE_APK_PATH = "update_app_apk_path"
private const val PREF_APP_UPDATE_APK_TAG = "update_app_apk_tag"

internal fun MainViewModel.refreshAppUpdateDownloadStateImpl() {
    if (_appUpdateDownload.value is AppUpdateDownloadState.Downloading) return
    val latestTag = _appUpdate.value?.latest?.tag
    val path = preferences.getString(PREF_APP_UPDATE_APK_PATH, null)
    val tag = preferences.getString(PREF_APP_UPDATE_APK_TAG, null)
    val file = path?.let(::File)
    _appUpdateDownload.value = if (
        latestTag != null &&
        file != null &&
        tag == latestTag &&
        file.exists() &&
        file.length() > 0L
    ) {
        AppUpdateDownloadState.ReadyToInstall
    } else {
        AppUpdateDownloadState.Idle
    }
}

internal fun MainViewModel.downloadAppUpdateImpl() {
    val update = _appUpdate.value ?: run {
        appendLog("No app update is available to download.", level = LogLevel.ERROR)
        return
    }
    if (_appUpdateDownload.value is AppUpdateDownloadState.Downloading) return

    val url = update.latest.apkUrl
    if (url.isNullOrBlank()) {
        _appUpdateDownload.value = AppUpdateDownloadState.Failed("Release has no APK asset")
        appendLog("App update release has no downloadable APK asset.", level = LogLevel.ERROR)
        return
    }

    val tag = sanitizeVersionForFile(update.latest.tag)
    val destination = NrSuiteDownloadManager.destinationFile(app, "updates/nrsuite-$tag.apk")
    _appUpdateDownload.value = AppUpdateDownloadState.Downloading(percent = null)

    scope.launch {
        runCatching {
            val downloadId = NrSuiteDownloadManager.enqueue(
                context = app,
                url = url,
                destination = destination,
                title = "NRSuite update",
                description = "Downloading NRSuite ${update.latest.tag}",
            )
            NrSuiteDownloadManager.observe(app, downloadId).collect { state ->
                when (state) {
                    is DownloadState.Pending -> {
                        _appUpdateDownload.value = AppUpdateDownloadState.Downloading(percent = null)
                    }
                    is DownloadState.Running -> {
                        _appUpdateDownload.value = AppUpdateDownloadState.Downloading(state.percent)
                    }
                    is DownloadState.Paused -> {
                        _appUpdateDownload.value = AppUpdateDownloadState.Downloading(state.percent)
                    }
                    DownloadState.Completed -> {
                        if (destination.exists() && destination.length() > 0L) {
                            purgeOldAppUpdateFiles(destination)
                            preferences.edit()
                                .putString(PREF_APP_UPDATE_APK_PATH, destination.absolutePath)
                                .putString(PREF_APP_UPDATE_APK_TAG, update.latest.tag)
                                .apply()
                            _appUpdateDownload.value = AppUpdateDownloadState.ReadyToInstall
                            appendLog("App update downloaded: ${update.latest.tag}", tag = "update")
                        } else {
                            _appUpdateDownload.value =
                                AppUpdateDownloadState.Failed("Downloaded APK is empty")
                        }
                    }
                    is DownloadState.Failed -> {
                        _appUpdateDownload.value = AppUpdateDownloadState.Failed(state.reason)
                        appendLog("App update download failed: ${state.reason}", level = LogLevel.ERROR)
                    }
                }
            }
        }.onFailure { error ->
            val message = error.message ?: "App update download failed"
            _appUpdateDownload.value = AppUpdateDownloadState.Failed(message)
            appendLog("App update download failed: $message", level = LogLevel.ERROR)
        }
    }
}

internal fun MainViewModel.installAppUpdateImpl() {
    val path = preferences.getString(PREF_APP_UPDATE_APK_PATH, null)
    val tag = preferences.getString(PREF_APP_UPDATE_APK_TAG, null)
    val expectedTag = _appUpdate.value?.latest?.tag
    val file = path?.let(::File)
    if (file == null || !file.exists() || file.length() <= 0L || expectedTag == null || tag != expectedTag) {
        _appUpdateDownload.value = AppUpdateDownloadState.Failed("Downloaded APK is missing")
        appendLog("App update APK is missing; download it again.", level = LogLevel.ERROR)
        return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        !app.packageManager.canRequestPackageInstalls()
    ) {
        appendLog("Allow NRSuite to install unknown apps, then tap Install again.", tag = "update")
        val settingsIntent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${app.packageName}"),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { app.startActivity(settingsIntent) }
        return
    }

    runCatching {
        val uri = FileProvider.getUriForFile(
            app,
            "${app.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        app.startActivity(intent)
    }.onFailure { error ->
        _appUpdateDownload.value =
            AppUpdateDownloadState.Failed(error.message ?: "Could not open installer")
        appendLog("Could not open app installer: ${error.message}", level = LogLevel.ERROR)
    }
}

private fun sanitizeVersionForFile(raw: String): String =
    raw.replace(Regex("[^A-Za-z0-9._-]"), "_")

private fun purgeOldAppUpdateFiles(current: File) {
    val parent = current.parentFile ?: return
    parent.listFiles()?.forEach { file ->
        if (file.isFile && file.name != current.name &&
            file.extension.equals("apk", ignoreCase = true)
        ) {
            runCatching { file.delete() }
        }
    }
}
