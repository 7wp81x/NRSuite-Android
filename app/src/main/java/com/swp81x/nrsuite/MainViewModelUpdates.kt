package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.update.AppUpdateInfo
import com.swp81x.nrsuite.core.update.FirmwareUpdateInfo
import com.swp81x.nrsuite.core.update.GitHubReleaseClient
import com.swp81x.nrsuite.core.update.GitHubReleaseResult
import com.swp81x.nrsuite.core.update.ReleaseInfo
import com.swp81x.nrsuite.core.update.VersionComparator
import kotlinx.coroutines.launch
import org.json.JSONObject

internal const val PREF_AUTO_UPDATE_CHECK = "auto_update_check"

private const val PREF_APP_RELEASE = "update_app_release"
private const val PREF_FW_RELEASE = "update_fw_release"
private const val PREF_APP_ETAG = "update_app_etag"
private const val PREF_FW_ETAG = "update_fw_etag"
private const val PREF_LAST_APP_CHECK = "update_last_app_check"
private const val PREF_LAST_FW_CHECK = "update_last_fw_check"

private const val APP_REPO = "7wp81x/nrsuite-android"
private const val FIRMWARE_REPO = "7wp81x/nrsuite-firmware"
private const val UPDATE_CHECK_INTERVAL_MS = 60L * 60L * 1000L

internal fun MainViewModel.initUpdateChecksImpl() {
    refreshUpdateUiState()
    checkForUpdatesImpl(force = false)
}

internal fun MainViewModel.setAutoUpdateCheckImpl(enabled: Boolean) {
    _autoUpdateCheck.value = enabled
    preferences.edit().putBoolean(PREF_AUTO_UPDATE_CHECK, enabled).apply()
    if (enabled) {
        checkForUpdatesImpl(force = false)
    }
}

internal fun MainViewModel.checkForUpdatesImpl(force: Boolean = false) {
    if (!force && !_autoUpdateCheck.value) return
    if (_updateCheckInProgress.value) return

    refreshUpdateUiState()
    scope.launch {
        _updateCheckInProgress.value = true
        var error: String? = null
        try {
            val now = System.currentTimeMillis()

            val lastAppCheck = preferences.getLong(PREF_LAST_APP_CHECK, 0L)
            if (force || now - lastAppCheck >= UPDATE_CHECK_INTERVAL_MS) {
                val result = fetchAppRelease()
                if (result is GitHubReleaseResult.Failure) error = result.message
                preferences.edit().putLong(PREF_LAST_APP_CHECK, now).apply()
            }

            val firmwareVersion = currentFirmwareVersion()
            if (firmwareVersion != null) {
                val lastFirmwareCheck = preferences.getLong(PREF_LAST_FW_CHECK, 0L)
                if (force || now - lastFirmwareCheck >= UPDATE_CHECK_INTERVAL_MS) {
                    val result = fetchFirmwareRelease()
                    if (result is GitHubReleaseResult.Failure) error = result.message
                    preferences.edit().putLong(PREF_LAST_FW_CHECK, now).apply()
                }
            }

            _updateError.value = error
            if (error != null) {
                appendLog("Update check failed: $error", level = LogLevel.ERROR, tag = "update")
            }
        } finally {
            refreshUpdateUiState()
            _updateCheckInProgress.value = false
        }
    }
}

private suspend fun MainViewModel.fetchAppRelease(): GitHubReleaseResult {
    val etag = preferences.getString(PREF_APP_ETAG, null)
    val result = GitHubReleaseClient.fetchLatest(APP_REPO, etag)
    when (result) {
        is GitHubReleaseResult.Success -> {
            preferences.edit()
                .putString(PREF_APP_RELEASE, result.release.toJson().toString())
                .putString(PREF_APP_ETAG, result.etag)
                .apply()
            appendLog("App update check: latest ${result.release.tag}", tag = "update")
        }
        GitHubReleaseResult.NotModified -> {
            appendLog("App update check: not modified.", tag = "update")
        }
        is GitHubReleaseResult.Failure -> Unit
    }
    return result
}

private suspend fun MainViewModel.fetchFirmwareRelease(): GitHubReleaseResult {
    val etag = preferences.getString(PREF_FW_ETAG, null)
    val result = GitHubReleaseClient.fetchLatest(FIRMWARE_REPO, etag)
    when (result) {
        is GitHubReleaseResult.Success -> {
            preferences.edit()
                .putString(PREF_FW_RELEASE, result.release.toJson().toString())
                .putString(PREF_FW_ETAG, result.etag)
                .apply()
            appendLog("Firmware update check: latest ${result.release.tag}", tag = "update")
        }
        GitHubReleaseResult.NotModified -> {
            appendLog("Firmware update check: not modified.", tag = "update")
        }
        is GitHubReleaseResult.Failure -> Unit
    }
    return result
}

private fun MainViewModel.refreshUpdateUiState() {
    val installedVersion = installedAppVersion()
    val latestApp = readRelease(PREF_APP_RELEASE)
    _appUpdate.value = if (
        latestApp != null && VersionComparator.isNewer(latestApp.tag, installedVersion)
    ) {
        AppUpdateInfo(currentVersion = installedVersion, latest = latestApp)
    } else {
        null
    }

    val connectedFirmware = currentFirmwareVersion()
    val latestFirmware = readRelease(PREF_FW_RELEASE)
    _firmwareUpdate.value = if (
        connectedFirmware != null &&
        latestFirmware != null &&
        VersionComparator.isNewer(latestFirmware.tag, connectedFirmware)
    ) {
        FirmwareUpdateInfo(currentVersion = connectedFirmware, latest = latestFirmware)
    } else {
        null
    }
}

private fun MainViewModel.installedAppVersion(): String = runCatching {
    app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "0"
}.getOrDefault("0")

private fun MainViewModel.currentFirmwareVersion(): String? =
    (session?.state?.value as? ConnectionState.Connected)?.firmwareVersion

private fun MainViewModel.readRelease(key: String): ReleaseInfo? {
    val raw = preferences.getString(key, null)?.takeIf { it.isNotBlank() } ?: return null
    return runCatching { ReleaseInfo.fromJson(JSONObject(raw)) }.getOrNull()
}
