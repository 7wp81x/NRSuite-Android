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
internal fun Pcap22000Route(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.pcap22000State.collectAsState()
    val pcapPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    viewModel.app.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
            viewModel.addPcap22000Inputs(uris)
        }
    }
    val exportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? ->
        if (uri != null) viewModel.exportPcap22000(uri)
    }

    Pcap22000Screen(
        state = state,
        onAddPcaps = {
            pcapPicker.launch(
                arrayOf(
                    "application/vnd.tcpdump.pcap",
                    "application/octet-stream",
                    "*/*",
                ),
            )
        },
        onRemoveInput = viewModel::removePcap22000Input,
        onClearInputs = viewModel::clearPcap22000Inputs,
        onSetSsidOverride = viewModel::setPcap22000SsidOverride,
        onRemoveSsidOverride = viewModel::removePcap22000SsidOverride,
        onExport = {
            exportPicker.launch("NRSuite-handshakes-${System.currentTimeMillis()}.22000")
        },
        modifier = modifier,
    )
}
