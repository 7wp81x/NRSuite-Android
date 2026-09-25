package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.defense.OuiRule
import com.swp81x.nrsuite.core.defense.OuiRuleAction
import java.util.UUID
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

// Rogue AP detector settings. OUI rules are persisted in the same
// SharedPreferences store used by other lightweight app settings; when the
// TrustedNetwork registry lands it should live alongside this key so the
// Rogue AP detector has one configuration source.

private const val PREF_OUI_RULES = "rogue_ap_oui_rules"

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
