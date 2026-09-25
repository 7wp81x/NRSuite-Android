package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.defense.AlertConfidence
import com.swp81x.nrsuite.core.defense.OuiRule
import com.swp81x.nrsuite.core.defense.OuiRuleAction
import com.swp81x.nrsuite.core.defense.RogueApAlert
import com.swp81x.nrsuite.core.defense.RogueApCategory
import com.swp81x.nrsuite.core.defense.TrustedNetwork
import com.swp81x.nrsuite.core.defense.matchOuiRule
import com.swp81x.nrsuite.core.defense.securityRank
import com.swp81x.nrsuite.core.history.HistoryLevel
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

// Rogue AP detector settings and Android-side scanning/classification.
// OUI rules and the trusted-network registry share the same SharedPreferences
// store so the detector has one configuration source.

private const val PREF_OUI_RULES = "rogue_ap_oui_rules"
private const val PREF_TRUSTED_NETWORKS = "rogue_ap_trusted_networks"
private const val ROGUE_AP_SCAN_INTERVAL_MS = 15_000L
private const val ROGUE_AP_BASELINE_SETTLE_MS = 350L

private data class ObservedAp(
    val ssid: String,
    val bssid: String,
    val channel: Int,
    val rssi: Int,
    val security: String,
)

private fun JSONObject.toObservedAp(): ObservedAp? {
    val ssid = optString("ssid").trim()
    if (ssid.isBlank()) return null
    val bssid = optString("bssid").trim().uppercase()
    if (bssid.isBlank()) return null
    return ObservedAp(
        ssid = ssid,
        bssid = bssid,
        channel = optInt("channel", 0),
        rssi = optInt("rssi", -100),
        security = optString("security", "?"),
    )
}

internal fun MainViewModel.loadOuiRulesImpl() {
    val raw = preferences.getString(PREF_OUI_RULES, null) ?: return
    runCatching {
        val array = JSONArray(raw)
        val rules = buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val id = obj.optString("id").ifBlank { UUID.randomUUID().toString() }
                val ouiPrefix = obj.optString("ouiPrefix")
                val label = obj.optString("label")
                val action = runCatching {
                    OuiRuleAction.valueOf(obj.optString("action"))
                }.getOrDefault(OuiRuleAction.WHITELIST)
                if (ouiPrefix.isNotBlank()) {
                    add(OuiRule(id, ouiPrefix, label, action))
                }
            }
        }
        _ouiRules.value = rules
    }
}

private fun MainViewModel.persistOuiRulesImpl() {
    val array = JSONArray()
    _ouiRules.value.forEach { rule ->
        array.put(
            JSONObject().apply {
                put("id", rule.id)
                put("ouiPrefix", rule.ouiPrefix)
                put("label", rule.label)
                put("action", rule.action.name)
            }
        )
    }
    preferences.edit().putString(PREF_OUI_RULES, array.toString()).apply()
}

internal fun MainViewModel.addOuiRuleImpl(
    ouiPrefix: String,
    label: String,
    action: OuiRuleAction,
) {
    val rule = OuiRule(
        id = UUID.randomUUID().toString(),
        ouiPrefix = ouiPrefix,
        label = label,
        action = action,
    )
    _ouiRules.update { it + rule }
    persistOuiRulesImpl()
}

internal fun MainViewModel.deleteOuiRuleImpl(id: String) {
    _ouiRules.update { rules -> rules.filterNot { it.id == id } }
    persistOuiRulesImpl()
}

internal fun MainViewModel.loadTrustedNetworksImpl() {
    val raw = preferences.getString(PREF_TRUSTED_NETWORKS, null) ?: return
    runCatching {
        val array = JSONArray(raw)
        val networks = buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val ssid = obj.optString("ssid")
                val bssid = obj.optString("bssid").uppercase()
                if (ssid.isBlank() || bssid.isBlank()) continue
                add(
                    TrustedNetwork(
                        ssid = ssid,
                        bssid = bssid,
                        channel = obj.optInt("channel", 0),
                        security = obj.optString("security", "?"),
                        addedAt = obj.optString("addedAt"),
                    )
                )
            }
        }
        _trustedNetworks.value = networks
    }
}

private fun MainViewModel.persistTrustedNetworksImpl() {
    val array = JSONArray()
    _trustedNetworks.value.forEach { network ->
        array.put(
            JSONObject().apply {
                put("ssid", network.ssid)
                put("bssid", network.bssid)
                put("channel", network.channel)
                put("security", network.security)
                put("addedAt", network.addedAt)
            }
        )
    }
    preferences.edit().putString(PREF_TRUSTED_NETWORKS, array.toString()).apply()
}

internal fun MainViewModel.captureRogueApBaselineImpl() {
    val activeSession = session
    if (activeSession == null) {
        appendLog("Connect to a device before capturing a trusted baseline.")
        return
    }
    if (_rogueApScanning.value) return
    if (!ensureRadioIdle("Rogue AP baseline scan")) return

    _rogueApScanning.value = true
    scope.launch {
        appendLog("Capturing trusted AP baseline...")
        _networks.value = emptyList()
        val count = runCatching { activeSession.scanWifi(timeoutMs = 30_000) }.getOrNull()
        delay(ROGUE_AP_BASELINE_SETTLE_MS)
        _rogueApScanning.value = false

        if (count == null || count < 0) {
            appendLog("Trusted baseline scan failed or timed out.")
            return@launch
        }

        val baseline = _networks.value
            .mapNotNull { it.toObservedAp() }
            .distinctBy { it.bssid }
            .map { ap ->
                TrustedNetwork(
                    ssid = ap.ssid,
                    bssid = ap.bssid,
                    channel = ap.channel,
                    security = ap.security,
                    addedAt = timestampNow(),
                )
            }

        if (baseline.isEmpty()) {
            appendLog("Trusted baseline scan found no visible APs.")
            return@launch
        }

        _trustedNetworks.value = baseline
        persistTrustedNetworksImpl()
        appendLog("Trusted baseline captured: ${baseline.size} AP(s).")
        addHistory(
            "rogue_ap",
            "Trusted baseline captured: ${baseline.size} AP(s)",
            HistoryLevel.SUCCESS,
        )
    }
}

internal fun MainViewModel.clearRogueApBaselineImpl() {
    _trustedNetworks.value = emptyList()
    persistTrustedNetworksImpl()
    appendLog("Trusted AP baseline cleared.")
}

internal fun MainViewModel.startRogueApDetectorImpl() {
    val activeSession = session
    if (activeSession == null) {
        appendLog("Connect to a device before starting the Rogue AP detector.")
        return
    }
    if (_rogueApRunning.value) return
    if (_trustedNetworks.value.isEmpty()) {
        _actionError.value = "Capture a trusted AP baseline before starting the Rogue AP detector."
        return
    }
    if (!ensureRadioIdle("Rogue AP Detector")) return

    _rogueApRunning.value = true
    _rogueApAlerts.value = emptyList()
    updateForegroundService()
    appendLog("Rogue AP detector started; rescanning every ${ROGUE_AP_SCAN_INTERVAL_MS / 1000}s.")
    addHistory("rogue_ap", "Rogue AP detector started", HistoryLevel.SUCCESS)

    rogueApScanJob?.cancel()
    rogueApScanJob = scope.launch {
        while (isActive && _rogueApRunning.value) {
            performRogueApScan(activeSession)
            delay(ROGUE_AP_SCAN_INTERVAL_MS)
        }
    }
}

internal fun MainViewModel.stopRogueApDetectorImpl() {
    if (!_rogueApRunning.value) return
    _rogueApRunning.value = false
    rogueApScanJob?.cancel()
    rogueApScanJob = null
    _rogueApScanning.value = false
    updateForegroundService()
    appendLog("Rogue AP detector stopped.")
    addHistory("rogue_ap", "Rogue AP detector stopped", HistoryLevel.INFO)
}

private suspend fun MainViewModel.performRogueApScan(activeSession: com.swp81x.nrsuite.core.session.NrSession) {
    _rogueApScanning.value = true
    _networks.value = emptyList()
    val count = runCatching { activeSession.scanWifi(timeoutMs = 30_000) }.getOrNull()
    delay(ROGUE_AP_BASELINE_SETTLE_MS)
    _rogueApScanning.value = false
    _rogueApLastScanAt.value = timeHmNow()

    if (count == null || count < 0) {
        appendLog("Rogue AP scan failed or timed out.")
        return
    }

    val observed = _networks.value.mapNotNull { it.toObservedAp() }
    val newAlerts = classifyRogueAps(observed)
    if (newAlerts.isEmpty()) return

    val existingKeys = _rogueApAlerts.value
        .map { "${it.bssid}|${it.category}" }
        .toSet()
    val merged = mergeRogueApAlerts(_rogueApAlerts.value, newAlerts)
    _rogueApAlerts.value = merged

    newAlerts
        .filter { "${it.bssid}|${it.category}" !in existingKeys }
        .forEach { alert ->
            appendLog(
                "Rogue AP detected: ${alert.category.name} ${alert.ssid} " +
                    "(${alert.bssid}) ch ${alert.channel} ${alert.rssi} dBm"
            )
        }
    addHistory(
        "rogue_ap",
        "${newAlerts.size} suspicious AP observation(s)",
        HistoryLevel.ERROR,
    )
}

private fun MainViewModel.classifyRogueAps(observed: List<ObservedAp>): List<RogueApAlert> {
    val trusted = _trustedNetworks.value
    val rules = _ouiRules.value
    val alerts = mutableListOf<RogueApAlert>()

    observed.forEach { ap ->
        val trustedForSsid = trusted.filter { it.ssid.equals(ap.ssid, ignoreCase = true) }
        val sameBssid = trustedForSsid.firstOrNull {
            it.bssid.equals(ap.bssid, ignoreCase = true)
        }
        val mismatchedSsid = trustedForSsid.isNotEmpty() && sameBssid == null
        val channelMismatch = sameBssid != null && sameBssid.channel != ap.channel
        val securityDowngrade = sameBssid != null &&
            securityRank(ap.security) >= 0 &&
            securityRank(ap.security) < securityRank(sameBssid.security)

        val ouiRule = matchOuiRule(ap.bssid, rules)
        val blacklisted = ouiRule?.action == OuiRuleAction.BLACKLIST
        val ouiSuspicious = blacklisted && sameBssid == null
        val reasons = mutableListOf<String>()

        if (mismatchedSsid) {
            reasons += "SSID appears on an untrusted BSSID"
        }
        if (channelMismatch) {
            reasons += "Channel mismatch (expected ch ${sameBssid?.channel}, saw ch ${ap.channel})"
        }
        if (securityDowngrade) {
            reasons += "Security downgrade (expected ${sameBssid?.security}, saw ${ap.security})"
        }
        if (ouiSuspicious) {
            val ruleLabel = ouiRule?.label?.takeIf { it.isNotBlank() }
                ?: ouiRule?.ouiPrefix
                ?: "unknown"
            reasons += "Blacklisted OUI: $ruleLabel"
        }

        val category = when {
            mismatchedSsid && securityDowngrade -> RogueApCategory.FAKE_PORTAL
            mismatchedSsid -> RogueApCategory.EVIL_TWIN
            channelMismatch -> RogueApCategory.UNKNOWN_ROGUE
            ouiSuspicious -> RogueApCategory.UNKNOWN_ROGUE
            else -> null
        } ?: return@forEach

        val confidence = when {
            category == RogueApCategory.FAKE_PORTAL -> AlertConfidence.HIGH
            mismatchedSsid && (channelMismatch || ouiSuspicious) -> AlertConfidence.HIGH
            mismatchedSsid -> AlertConfidence.MEDIUM
            ouiSuspicious -> AlertConfidence.MEDIUM
            else -> AlertConfidence.LOW
        }

        alerts += RogueApAlert(
            id = UUID.randomUUID().toString(),
            ssid = ap.ssid,
            bssid = ap.bssid,
            channel = ap.channel,
            rssi = ap.rssi,
            category = category,
            reasons = reasons,
            confidence = confidence,
            detectedAt = timeHmNow(),
        )
    }

    return alerts
}

private fun mergeRogueApAlerts(
    existing: List<RogueApAlert>,
    incoming: List<RogueApAlert>,
): List<RogueApAlert> {
    val byKey = existing.associateBy { it.bssid }.toMutableMap()
    incoming.forEach { alert ->
        byKey[alert.bssid] = alert
    }
    return byKey.values
        .sortedByDescending { it.detectedAt }
        .take(200)
}
