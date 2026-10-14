package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.download.DownloadState
import com.swp81x.nrsuite.core.download.NrSuiteDownloadManager
import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.oui.OUI_CSV_URL
import com.swp81x.nrsuite.core.oui.OuiDatabaseStatus
import java.io.File
import kotlinx.coroutines.launch

internal const val PREF_OUI_DB_PROMPTED = "oui_db_prompted"

internal fun MainViewModel.loadOuiDatabaseImpl() {
    scope.launch {
        // Migrate the old internal-files copy so existing installs do not
        // have to redownload just because OUI storage moved to app external files.
        val target = ouiDatabaseRepository.file
        if (!target.exists()) {
            val legacy = File(app.filesDir, "oui/oui.csv")
            if (legacy.exists()) {
                runCatching {
                    target.parentFile?.mkdirs()
                    legacy.copyTo(target, overwrite = true)
                }
            }
        }
        ouiDatabaseRepository.load()
        if (ouiDatabaseRepository.status.value is OuiDatabaseStatus.NotDownloaded &&
            !preferences.getBoolean(PREF_OUI_DB_PROMPTED, false)
        ) {
            _requiresOuiDatabase.value = true
        }
    }
}

internal fun MainViewModel.downloadOuiDatabaseImpl() {
    _requiresOuiDatabase.value = false
    preferences.edit().putBoolean(PREF_OUI_DB_PROMPTED, true).apply()
    appendLog("Starting OUI/vendor database download...")

    scope.launch {
        val destination = NrSuiteDownloadManager.destinationFile(app, "oui/oui.csv")
        ouiDatabaseRepository.markDownloading()

        runCatching {
            val downloadId = NrSuiteDownloadManager.enqueue(
                context = app,
                url = OUI_CSV_URL,
                destination = destination,
                title = "OUI/vendor database",
                description = "Downloading the IEEE OUI registry",
            )

            NrSuiteDownloadManager.observe(app, downloadId).collect { state ->
                when (state) {
                    is DownloadState.Pending -> {
                        ouiDatabaseRepository.updateDownloadProgress(null)
                    }
                    is DownloadState.Running -> {
                        ouiDatabaseRepository.updateDownloadProgress(state.percent)
                    }
                    DownloadState.Completed -> {
                        ouiDatabaseRepository.updateDownloadProgress(null)
                        ouiDatabaseRepository.load()
                        when (val status = ouiDatabaseRepository.status.value) {
                            is OuiDatabaseStatus.Ready -> {
                                appendLog("OUI/vendor database ready: ${status.vendorCount} prefixes.")
                                addHistory(
                                    "oui_db",
                                    "OUI/vendor database updated: ${status.vendorCount} prefixes",
                                    HistoryLevel.SUCCESS,
                                )
                            }
                            is OuiDatabaseStatus.Error -> {
                                appendLog(
                                    "OUI/vendor database download completed but could not be loaded: ${status.message}",
                                    level = LogLevel.ERROR,
                                )
                            }
                            else -> Unit
                        }
                    }
                    is DownloadState.Failed -> {
                        ouiDatabaseRepository.reportDownloadError(state.reason)
                        appendLog("OUI/vendor database update failed: ${state.reason}", level = LogLevel.ERROR)
                    }
                }
            }
        }.onFailure { error ->
            val message = error.message ?: "OUI database download failed"
            ouiDatabaseRepository.reportDownloadError(message)
            appendLog("OUI/vendor database update failed: $message", level = LogLevel.ERROR)
        }
    }
}

internal fun MainViewModel.onOuiDatabasePromptShownImpl() {
    _requiresOuiDatabase.value = false
    preferences.edit().putBoolean(PREF_OUI_DB_PROMPTED, true).apply()
}

internal fun MainViewModel.lookupMacImpl(mac: String) {
    _macLookupResult.value = ouiDatabaseRepository.detailedLookup(mac)
}
