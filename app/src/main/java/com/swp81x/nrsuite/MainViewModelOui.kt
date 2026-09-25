package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.oui.OuiDatabaseStatus
import kotlinx.coroutines.launch

internal const val PREF_OUI_DB_PROMPTED = "oui_db_prompted"

internal fun MainViewModel.loadOuiDatabaseImpl() {
    scope.launch {
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
    appendLog("Downloading OUI/vendor database...")

    scope.launch {
        ouiDatabaseRepository.download()
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
                appendLog("OUI/vendor database update failed: ${status.message}", level = com.swp81x.nrsuite.core.log.LogLevel.ERROR)
            }
            else -> Unit
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
