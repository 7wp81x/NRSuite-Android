package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.defense.AlertConfidence
import com.swp81x.nrsuite.core.defense.NearbyAp
import com.swp81x.nrsuite.core.defense.isLikelyInfrastructureVendor
import com.swp81x.nrsuite.core.defense.OuiRule
import com.swp81x.nrsuite.core.defense.OuiRuleAction
import com.swp81x.nrsuite.core.defense.RogueApAlert
import com.swp81x.nrsuite.core.defense.RogueApCategory
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
// OUI rules and the detector configuration share the app's SharedPreferences store.

private const val PREF_OUI_RULES = "rogue_ap_oui_rules"
private const val ROGUE_AP_SCAN_INTERVAL_MS = 5_000L
private const val ROGUE_AP_BASELINE_SETTLE_MS = 200L
private const val ROGUE_AP_SCAN_TIMEOUT_MS = 15_000L

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

internal fun MainViewModel.startRogueApDetectorImpl() {
    val activeSession = session
    if (activeSession == null) {
        appendLog("Connect to a device before starting the Rogue AP detector.")
        return
    }
    if (_rogueApRunning.value) return
    if (!ensureRadioIdle("Rogue AP Detector")) return

    _rogueApRunning.value = true
    _rogueApAlerts.value = emptyList()
    updateForegroundService()
    appendLog("Rogue AP detector started; automatic nearby comparison every ${ROGUE_AP_SCAN_INTERVAL_MS / 1000}s.")
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
    val count = runCatching { activeSession.scanWifi(timeoutMs = ROGUE_AP_SCAN_TIMEOUT_MS) }.getOrNull()
    delay(ROGUE_AP_BASELINE_SETTLE_MS)
    _rogueApScanning.value = false
    _rogueApLastScanAt.value = timeHmNow()

    if (count == null || count < 0) {
        appendLog("Rogue AP scan failed or timed out.")
        return
    }

    val observed = _networks.value.mapNotNull { it.toObservedAp() }
    val newAlerts = classifyNearbyDuplicates(observed)
    val alertsByBssid = newAlerts.associateBy { it.bssid }
    _rogueApNearby.value = observed
        .map { ap ->
            val alert = alertsByBssid[ap.bssid]
            val vendor = ouiDatabaseRepository.lookup(ap.bssid)?.vendor
            val ouiRule = matchOuiRule(ap.bssid, _ouiRules.value)
            NearbyAp(
                ssid = ap.ssid,
                bssid = ap.bssid,
                channel = ap.channel,
                rssi = ap.rssi,
                security = ap.security,
                vendor = vendor,
                likelyInfrastructureVendor = isLikelyInfrastructureVendor(vendor) ||
                    ouiRule?.action == OuiRuleAction.WHITELIST,
                suspicious = alert != null,
                category = alert?.category,
                confidence = alert?.confidence,
            )
        }
        .sortedByDescending { it.rssi }

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

private fun MainViewModel.classifyNearbyDuplicates(
    observed: List<ObservedAp>,
): List<RogueApAlert> {
    val rules = _ouiRules.value
    val alerts = mutableListOf<RogueApAlert>()

    fun isProtected(ap: ObservedAp): Boolean {
        val rule = matchOuiRule(ap.bssid, rules)
        if (rule?.action == OuiRuleAction.BLACKLIST) return false
        if (rule?.action == OuiRuleAction.WHITELIST) return true
        val vendor = ouiDatabaseRepository.lookup(ap.bssid)?.vendor
        return isLikelyInfrastructureVendor(vendor)
    }

    observed.groupBy { it.ssid.lowercase() }.values.forEach { group ->
        group.forEach { ap ->
            val ouiRule = matchOuiRule(ap.bssid, rules)
            val action = ouiRule?.action
            val reasons = mutableListOf<String>()

            if (action == OuiRuleAction.BLACKLIST) {
                val ruleLabel = ouiRule.label.takeIf { it.isNotBlank() }
                    ?: ouiRule.ouiPrefix
                reasons += "Blacklisted OUI: $ruleLabel"
            }

            if (group.size >= 2) {
                val highestSecurity = group.maxOf { securityRank(it.security) }
                val thisSecurity = securityRank(ap.security)
                if (thisSecurity >= 0 &&
                    thisSecurity < highestSecurity &&
                    action != OuiRuleAction.WHITELIST
                ) {
                    reasons += "Lower security than other APs advertising SSID '${ap.ssid}'"
                }

                val prefixes = group.mapNotNull { ouiPrefixOf(it.bssid) }.distinct()
                if (prefixes.size > 1) {
                    val thisProtected = isProtected(ap)
                    val anotherProtected = group.any {
                        it.bssid != ap.bssid && isProtected(it)
                    }
                    val anyProtected = group.any { isProtected(it) }
                    if (!thisProtected && (anotherProtected || !anyProtected)) {
                        reasons += "Different OUI from other APs advertising SSID '${ap.ssid}'"
                    }
                }
            }

            if (reasons.isEmpty()) return@forEach

            val category = when {
                reasons.any { it.startsWith("Lower security") } ->
                    RogueApCategory.SECURITY_DOWNGRADE
                reasons.any { it.startsWith("Different OUI") } -> RogueApCategory.EVIL_TWIN
                else -> RogueApCategory.UNKNOWN_ROGUE
            }
            val confidence = when {
                reasons.size > 1 -> AlertConfidence.HIGH
                category == RogueApCategory.UNKNOWN_ROGUE -> AlertConfidence.LOW
                else -> AlertConfidence.MEDIUM
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
                vendor = ouiDatabaseRepository.lookup(ap.bssid)?.vendor,
                detectedAt = timeHmNow(),
            )
        }
    }

    return alerts
}

private fun ouiPrefixOf(mac: String): String? {
    val parts = mac.trim().uppercase().replace("-", ":").split(":")
    if (parts.size < 3) return null
    if (parts.take(3).any { it.length != 2 || it.toIntOrNull(16) == null }) return null
    return parts.take(3).joinToString(":")
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
