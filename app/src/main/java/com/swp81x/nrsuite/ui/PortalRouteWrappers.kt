package com.swp81x.nrsuite.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.swp81x.nrsuite.MainViewModel
import org.json.JSONObject

@Composable
internal fun PortalModuleRoute(
    viewModel: MainViewModel,
    connected: Boolean,
    allowedChannels: List<Int>,
    onChooseHtml: () -> Unit,
    onImportHtmlTemplate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val running by viewModel.portalRunning.collectAsState()
    val mode by viewModel.portalMode.collectAsState()
    val htmlSize by viewModel.portalHtmlSize.collectAsState()
    val htmlComplete by viewModel.portalHtmlComplete.collectAsState()
    val activeSsid by viewModel.portalSsid.collectAsState()
    val activeChannel by viewModel.portalChannel.collectAsState()
    val views by viewModel.portalViews.collectAsState()
    val clients by viewModel.portalClients.collectAsState()
    val capturedData by viewModel.portalCapturedData.collectAsState()
    val credentials by viewModel.portalCredentials.collectAsState()
    val selectedHtmlName by viewModel.portalHtmlName.collectAsState()
    val selectedHtmlSize by viewModel.portalHtmlSelectedSize.collectAsState()
    val htmlUploading by viewModel.portalHtmlUploading.collectAsState()
    val htmlProgress by viewModel.portalHtmlUploadProgress.collectAsState()
    val eventLog by viewModel.portalEventLog.collectAsState()
    val templates by viewModel.htmlTemplates.collectAsState()
    val templateMessage by viewModel.htmlTemplateMessage.collectAsState()
    val templateMessageIsError by viewModel.htmlTemplateMessageIsError.collectAsState()

    PortalScreen(
        connected = connected,
        running = running && mode == "portal",
        htmlSize = htmlSize,
        htmlComplete = htmlComplete,
        activeSsid = activeSsid,
        activeChannel = activeChannel,
        portalViews = views,
        portalClients = clients,
        capturedData = capturedData,
        credentials = credentials,
        selectedHtmlName = selectedHtmlName,
        selectedHtmlSize = selectedHtmlSize,
        htmlUploading = htmlUploading,
        htmlProgress = htmlProgress,
        eventLog = eventLog,
        onChooseHtml = onChooseHtml,
        htmlTemplates = templates,
        htmlTemplateMessage = templateMessage,
        htmlTemplateMessageIsError = templateMessageIsError,
        onImportHtmlTemplate = onImportHtmlTemplate,
        onUseHtmlTemplate = viewModel::useHtmlTemplateForPortal,
        onRenameHtmlTemplate = viewModel::renameHtmlTemplate,
        onDeleteHtmlTemplate = viewModel::deleteHtmlTemplate,
        onClearEventLog = viewModel::clearPortalEventLog,
        onStart = viewModel::startPortal,
        onStop = viewModel::stopPortal,
        allowedChannels = allowedChannels,
        modifier = modifier,
    )
}

@Composable
internal fun EvilTwinModuleRoute(
    viewModel: MainViewModel,
    connected: Boolean,
    scanning: Boolean,
    networks: List<JSONObject>,
    allowedChannels: List<Int>,
    onChooseHtml: () -> Unit,
    onImportHtmlTemplate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val running by viewModel.portalRunning.collectAsState()
    val mode by viewModel.portalMode.collectAsState()
    val handshake by viewModel.portalHandshake.collectAsState()
    val results by viewModel.evilTwinResults.collectAsState()
    val eventLog by viewModel.evilTwinEventLog.collectAsState()
    val selectedHtmlName by viewModel.evilTwinHtmlName.collectAsState()
    val selectedHtmlSize by viewModel.evilTwinHtmlSelectedSize.collectAsState()
    val htmlUploading by viewModel.evilTwinHtmlUploading.collectAsState()
    val htmlProgress by viewModel.evilTwinHtmlUploadProgress.collectAsState()
    val htmlComplete by viewModel.evilTwinHtmlComplete.collectAsState()
    val templates by viewModel.htmlTemplates.collectAsState()
    val templateMessage by viewModel.htmlTemplateMessage.collectAsState()
    val templateMessageIsError by viewModel.htmlTemplateMessageIsError.collectAsState()

    EvilTwinScreen(
        connected = connected,
        running = running && mode == "evil_twin",
        scanning = scanning,
        networks = networks,
        allowedChannels = allowedChannels,
        handshake = handshake,
        results = results,
        eventLog = eventLog,
        selectedHtmlName = selectedHtmlName,
        selectedHtmlSize = selectedHtmlSize,
        htmlUploading = htmlUploading,
        htmlProgress = htmlProgress,
        htmlComplete = htmlComplete,
        onClearEventLog = viewModel::clearEvilTwinEventLog,
        onScanWifi = viewModel::scanWifiForTargets,
        onChooseHtml = onChooseHtml,
        htmlTemplates = templates,
        htmlTemplateMessage = templateMessage,
        htmlTemplateMessageIsError = templateMessageIsError,
        onImportHtmlTemplate = onImportHtmlTemplate,
        onUseHtmlTemplate = viewModel::useHtmlTemplateForEvilTwin,
        onRenameHtmlTemplate = viewModel::renameHtmlTemplate,
        onDeleteHtmlTemplate = viewModel::deleteHtmlTemplate,
        onStart = viewModel::startEvilTwin,
        onStop = viewModel::stopPortal,
        onClearPasswords = viewModel::clearEvilTwinPasswords,
        modifier = modifier,
    )
}
