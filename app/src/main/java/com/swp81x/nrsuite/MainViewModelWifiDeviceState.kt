package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.wifi.normalizeWifiChannelForAllowed
import kotlinx.coroutines.flow.update

/**
 * Keep channel and target state aligned with the currently connected radio.
 *
 * This is called by the UI whenever the primary device or its reported channel
 * capabilities change. Valid channel values are intentionally preserved, so a
 * reconnect of the same device does not reset a user's setup. When a different
 * device becomes primary, stale scan/target selections are cleared and the
 * device revision changes, which also resets screen-local channel state.
 */
internal fun MainViewModel.normalizeWifiStateForConnectedDeviceImpl(
    deviceIdentity: String?,
    allowedChannels: List<Int>,
) {
    val identity = deviceIdentity?.takeIf { it.isNotBlank() } ?: return
    if (allowedChannels.isEmpty()) return

    val previousIdentity = lastWifiDeviceIdentity
    val previousChannels = lastWifiAllowedChannels
    val deviceChanged = previousIdentity != null && previousIdentity != identity
    val capabilitiesChanged = previousChannels != null && previousChannels != allowedChannels
    lastWifiDeviceIdentity = identity
    lastWifiAllowedChannels = allowedChannels

    if (deviceChanged || capabilitiesChanged) {
        _networks.value = emptyList()
        _targetNetworks.value = emptyList()
        _channelAnalyzerNetworks.value = emptyList()
        _deauthDetectorTargets.value = emptyList()
        _deauthDetectorSelectedTarget.value = null
        _deauthTarget.value = ""
        _wpsAttemptDetector.update {
            it.copy(
                scanCandidates = emptyList(),
                monitoredChannels = emptyList(),
                selectedTargetCount = 0,
                sessions = emptyList(),
                lockSnapshots = emptyMap(),
                lockTransitionBssids = emptySet(),
                error = null,
            )
        }
        _wifiDeviceRevision.update { it + 1 }
    }

    fun normalized(current: Int): Int =
        normalizeWifiChannelForAllowed(current, allowedChannels)

    _beaconChannel.value = normalized(_beaconChannel.value)
    _deauthChannel.value = normalized(_deauthChannel.value)
    _portalChannel.value = normalized(_portalChannel.value)
    _probeMonitorChannel.value = normalized(_probeMonitorChannel.value)
    _clientPresenceChannel.value = normalized(_clientPresenceChannel.value)
    _hiddenApChannel.value = normalized(_hiddenApChannel.value)
    _deauthDetectorChannel.value = normalized(_deauthDetectorChannel.value)

    if (_deauthDetectorSelectedTarget.value?.channel?.let { it !in allowedChannels } == true) {
        _deauthDetectorSelectedTarget.value = null
    }

    clientPresenceSavedChannel = normalized(clientPresenceSavedChannel)
    hiddenApSavedChannel = normalized(hiddenApSavedChannel)

    val wpsState = _wpsAttemptDetector.value
    if (!wpsState.running && !wpsState.starting && !wpsState.scanningTargets) {
        _wpsAttemptDetector.update {
            it.copy(
                scanCandidates = it.scanCandidates.filter { target ->
                    target.channel in allowedChannels
                },
                monitoredChannels = it.monitoredChannels.filter { channel ->
                    channel in allowedChannels
                },
            )
        }
    }
}
