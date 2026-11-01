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
    val capturedHandshakes by viewModel.autoHandshake.captures.collectAsState()
    val selectedSession by viewModel.crackerSelectedSession.collectAsState()
    val selectedCapturedPath by viewModel.crackerSelectedCapturedPath.collectAsState()
    val sourceType by viewModel.crackerSourceType.collectAsState()
    val customPcapName by viewModel.crackerCustomPcapName.collectAsState()
    val imported22000Name by viewModel.crackerImported22000Name.collectAsState()
    val targets by viewModel.crackerTargets.collectAsState()
    val targetsLoading by viewModel.crackerTargetsLoading.collectAsState()
    val targetMessage by viewModel.crackerTargetMessage.collectAsState()
    val manualMode by viewModel.crackerManualMode.collectAsState()
    val manualSsid by viewModel.crackerManualSsid.collectAsState()
    val wordlist by viewModel.crackerWordlist.collectAsState()
    val running by viewModel.crackerRunning.collectAsState()
    val paused by viewModel.crackerPaused.collectAsState()
    val tested by viewModel.crackerTested.collectAsState()
    val speed by viewModel.crackerSpeed.collectAsState()
    val result by viewModel.crackerResult.collectAsState()
    val status by viewModel.crackerStatus.collectAsState()
    val currentTargetIndex by viewModel.crackerCurrentTargetIndex.collectAsState()
    val selectedTargetCount by viewModel.crackerSelectedTargetCount.collectAsState()
    val currentTargetSsid by viewModel.crackerCurrentTargetSsid.collectAsState()
    val progressFraction by viewModel.crackerProgressFraction.collectAsState()
    val progressKnown by viewModel.crackerProgressKnown.collectAsState()
    val savedSessions by viewModel.crackerSessions.collectAsState()

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

    val hash22000Picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                viewModel.app.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.setCrackerImported22000(uri, null)
        }
    }

    WpaCrackerScreen(
        sessions = credentialSessions,
        capturedHandshakes = capturedHandshakes,
        selectedSession = selectedSession,
        selectedCapturedPath = selectedCapturedPath,
        sourceType = sourceType,
        customPcapName = customPcapName,
        imported22000Name = imported22000Name,
        targets = targets,
        targetsLoading = targetsLoading,
        targetMessage = targetMessage,
        manualMode = manualMode,
        manualSsid = manualSsid,
        wordlistName = wordlist?.name,
        running = running,
        paused = paused,
        tested = tested,
        speed = speed,
        result = result,
        status = status,
        currentTargetIndex = currentTargetIndex,
        selectedTargetCount = selectedTargetCount,
        currentTargetSsid = currentTargetSsid,
        progressFraction = progressFraction,
        progressKnown = progressKnown,
        savedSessions = savedSessions,
        onSourceSelected = viewModel::selectCrackerSource,
        onSelectSession = viewModel::selectCrackerSession,
        onSelectCapturedHandshake = viewModel::selectCrackerCapturedHandshake,
        onChooseCustomPcap = {
            pcapPicker.launch(
                arrayOf(
                    "application/vnd.tcpdump.pcap",
                    "application/octet-stream",
                    "*/*",
                ),
            )
        },
        onChoose22000 = {
            hash22000Picker.launch(
                arrayOf("application/octet-stream", "text/plain", "*/*"),
            )
        },
        onClearSource = viewModel::clearCrackerCustomPcap,
        onManualModeChange = viewModel::setCrackerManualMode,
        onManualSsidChange = viewModel::setCrackerManualSsid,
        onToggleTarget = viewModel::toggleCrackerTarget,
        onSelectAllTargets = viewModel::selectAllCrackerTargets,
        onChooseWordlist = { wordlistPicker.launch(arrayOf("text/plain", "*/*")) },
        onClearWordlist = viewModel::clearCrackerWordlist,
        onStart = viewModel::startCracker,
        onPause = viewModel::pauseCracker,
        onResume = viewModel::resumeCracker,
        onStop = viewModel::stopCracker,
        onResumeSession = viewModel::resumeCrackerSession,
        onDeleteSession = viewModel::deleteCrackerSession,
        modifier = modifier,
    )
}
