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
    val starting by viewModel.portalStarting.collectAsState()
    val stopping by viewModel.portalStopping.collectAsState()
    val mode by viewModel.portalMode.collectAsState()
    val htmlSize by viewModel.portalHtmlSize.collectAsState()
    val htmlComplete by viewModel.portalHtmlComplete.collectAsState()
    val metrics by viewModel.portalMetrics.collectAsState()
    val visitors by viewModel.portalVisitors.collectAsState()
    val credentials by viewModel.portalCredentials.collectAsState()
    val selectedHtmlName by viewModel.portalHtmlName.collectAsState()
    val selectedHtmlSize by viewModel.portalHtmlSelectedSize.collectAsState()
    val htmlUploading by viewModel.portalHtmlUploading.collectAsState()
    val htmlProgress by viewModel.portalHtmlUploadProgress.collectAsState()
    val eventLog by viewModel.portalEventLog.collectAsState()
    val templates by viewModel.htmlTemplates.collectAsState()
    val templateMessage by viewModel.htmlTemplateMessage.collectAsState()
    val templateMessageIsError by viewModel.htmlTemplateMessageIsError.collectAsState()
    val firmwareNoticeEnabled by viewModel.portalFirmwareNoticeEnabled.collectAsState()
    val dnsHijackEnabled by viewModel.portalDnsHijackEnabled.collectAsState()

    PortalScreen(
        connected = connected,
        running = running && mode == "portal",
        starting = starting && mode == "portal",
        stopping = stopping && mode == "portal",
        htmlSize = htmlSize,
        htmlComplete = htmlComplete,
        activeSsid = metrics.ssid,
        activeChannel = metrics.channel,
        portalViews = metrics.pageViews,
        portalClients = metrics.clientsAssociated,
        capturedData = metrics.capturedPosts,
        credentials = credentials,
        visitors = visitors,
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
        onClearVisitors = viewModel::clearPortalVisitors,
        onStart = viewModel::startPortal,
        onStop = viewModel::stopPortal,
        allowedChannels = allowedChannels,
        firmwareNoticeEnabled = firmwareNoticeEnabled,
        onFirmwareNoticeEnabledChange = viewModel::setPortalFirmwareNoticeEnabled,
        dnsHijackEnabled = dnsHijackEnabled,
        onDnsHijackEnabledChange = viewModel::setPortalDnsHijackEnabled,
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
    val starting by viewModel.portalStarting.collectAsState()
    val stopping by viewModel.portalStopping.collectAsState()
    val mode by viewModel.portalMode.collectAsState()
    val handshake by viewModel.portalHandshake.collectAsState()
    val results by viewModel.evilTwinResults.collectAsState()
    val metrics by viewModel.evilTwinMetrics.collectAsState()
    val visitors by viewModel.evilTwinVisitors.collectAsState()
    val eventLog by viewModel.evilTwinEventLog.collectAsState()
    val selectedHtmlName by viewModel.evilTwinHtmlName.collectAsState()
    val selectedHtmlSize by viewModel.evilTwinHtmlSelectedSize.collectAsState()
    val htmlUploading by viewModel.evilTwinHtmlUploading.collectAsState()
    val htmlProgress by viewModel.evilTwinHtmlUploadProgress.collectAsState()
    val htmlComplete by viewModel.evilTwinHtmlComplete.collectAsState()
    val templates by viewModel.htmlTemplates.collectAsState()
    val templateMessage by viewModel.htmlTemplateMessage.collectAsState()
    val templateMessageIsError by viewModel.htmlTemplateMessageIsError.collectAsState()
    val firmwareNoticeEnabled by viewModel.portalFirmwareNoticeEnabled.collectAsState()
    val dnsHijackEnabled by viewModel.portalDnsHijackEnabled.collectAsState()
    val saveHandshakePcap by viewModel.evilTwinSavePcap.collectAsState()

    EvilTwinScreen(
        connected = connected,
        running = running && mode == "evil_twin",
        starting = starting && mode == "evil_twin",
        stopping = stopping && mode == "evil_twin",
        scanning = scanning,
        networks = networks,
        allowedChannels = allowedChannels,
        handshake = handshake,
        results = results,
        metrics = metrics,
        visitors = visitors,
        eventLog = eventLog,
        selectedHtmlName = selectedHtmlName,
        selectedHtmlSize = selectedHtmlSize,
        htmlUploading = htmlUploading,
        htmlProgress = htmlProgress,
        htmlComplete = htmlComplete,
        onClearEventLog = viewModel::clearEvilTwinEventLog,
        onClearVisitors = viewModel::clearEvilTwinVisitors,
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
        firmwareNoticeEnabled = firmwareNoticeEnabled,
        onFirmwareNoticeEnabledChange = viewModel::setPortalFirmwareNoticeEnabled,
        dnsHijackEnabled = dnsHijackEnabled,
        onDnsHijackEnabledChange = viewModel::setPortalDnsHijackEnabled,
        saveHandshakePcap = saveHandshakePcap,
        onSaveHandshakePcapChange = viewModel::setEvilTwinSavePcap,
        modifier = modifier,
    )
}
