package com.swp81x.nrsuite.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.swp81x.nrsuite.MainViewModel

@Composable
internal fun WpaCrackerRoute(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val credentialSessions by viewModel.credentialSessions.collectAsState()
    val selectedSession by viewModel.crackerSelectedSession.collectAsState()
    val wordlists by viewModel.crackerWordlists.collectAsState()
    val customPcapName by viewModel.crackerCustomPcapName.collectAsState()
    val customPcapValid by viewModel.crackerCustomPcapValid.collectAsState()
    val customPcapValidating by viewModel.crackerCustomPcapValidating.collectAsState()
    val customPcapMessage by viewModel.crackerCustomPcapMessage.collectAsState()
    val customSsidOptions by viewModel.crackerCustomSsidOptions.collectAsState()
    val customSsidSelected by viewModel.crackerCustomSsidSelected.collectAsState()
    val customSsidManual by viewModel.crackerCustomSsidManual.collectAsState()
    val customSsid by viewModel.crackerCustomSsid.collectAsState()
    val running by viewModel.crackerRunning.collectAsState()
    val paused by viewModel.crackerPaused.collectAsState()
    val tested by viewModel.crackerTested.collectAsState()
    val speed by viewModel.crackerSpeed.collectAsState()
    val result by viewModel.crackerResult.collectAsState()
    val status by viewModel.crackerStatus.collectAsState()

    val wordlistPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                viewModel.app.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.setCrackerWordlist(uri, null)
        }
    }

    val pcapPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                viewModel.app.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.setCrackerCustomPcap(uri, null)
        }
    }

    val exportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportCracker22000(uri)
        }
    }

    WpaCrackerScreen(
        sessions = credentialSessions,
        selectedSession = selectedSession,
        customPcapName = customPcapName,
        customPcapValid = customPcapValid,
        customPcapValidating = customPcapValidating,
        customPcapMessage = customPcapMessage,
        customSsidOptions = customSsidOptions,
        customSsidSelected = customSsidSelected,
        customSsidManualEnabled = customSsidManual,
        customSsid = customSsid,
        wordlists = wordlists.map { it.name },
        running = running,
        paused = paused,
        tested = tested,
        speed = speed,
        result = result,
        status = status,
        onSelectSession = viewModel::selectCrackerSession,
        onChooseCustomPcap = {
            pcapPicker.launch(
                arrayOf(
                    "application/vnd.tcpdump.pcap",
                    "application/octet-stream",
                    "*/*",
                ),
            )
        },
        onClearCustomPcap = viewModel::clearCrackerCustomPcap,
        onSelectCustomSsid = viewModel::selectCrackerCustomSsid,
        onUseManualCustomSsid = viewModel::useManualCrackerSsid,
        onCustomSsidChange = viewModel::setCrackerCustomSsid,
        onChooseWordlist = { wordlistPicker.launch(arrayOf("text/plain", "*/*")) },
        onRemoveWordlist = viewModel::removeCrackerWordlist,
        onClearWordlists = viewModel::clearCrackerWordlist,
        onStart = viewModel::startCracker,
        onPause = viewModel::pauseCracker,
        onResume = viewModel::resumeCracker,
        onStop = viewModel::stopCracker,
        onExport22000 = {
            exportPicker.launch("NRSuite-handshake-${System.currentTimeMillis()}.22000")
        },
        modifier = modifier,
    )
}
