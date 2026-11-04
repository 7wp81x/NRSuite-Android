package com.swp81x.nrsuite.core.terminal

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TerminalShortcutRunMode(val label: String) {
    CURRENT_SESSION("Current session"),
    NEW_SESSION("New session"),
    NEW_TEMPORARY_SESSION("New temporary session"),
    INSERT_ONLY("Insert only"),
    BACKGROUND("Background task"),
}

data class TerminalShortcut(
    val id: Long,
    val name: String,
    val command: String,
    val description: String = "",
    val category: String = "",
    val runMode: TerminalShortcutRunMode = TerminalShortcutRunMode.CURRENT_SESSION,
    val confirmation: Boolean = true,
    val pinHome: Boolean = false,
)

class TerminalShortcutStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        "nrsuite_terminal_shortcuts",
        Context.MODE_PRIVATE,
    )
    private val _shortcuts = MutableStateFlow(loadShortcuts())
    val shortcuts: StateFlow<List<TerminalShortcut>> = _shortcuts.asStateFlow()

    private val _categories = MutableStateFlow(loadCategories())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    fun upsert(shortcut: TerminalShortcut) {
        val current = _shortcuts.value.toMutableList()
        val index = current.indexOfFirst { it.id == shortcut.id }
        if (index >= 0) current[index] = shortcut else current += shortcut
        _shortcuts.value = current
        if (shortcut.category.isNotBlank() && shortcut.category !in _categories.value) {
            _categories.value = _categories.value + shortcut.category
        }
        persist()
    }

    fun delete(id: Long) {
        _shortcuts.value = _shortcuts.value.filterNot { it.id == id }
        persist()
    }

    fun addCategory(name: String) {
        val normalized = name.trim()
        if (normalized.isBlank() || normalized in _categories.value) return
        _categories.value = _categories.value + normalized
        persist()
    }

    fun deleteCategory(name: String) {
        _categories.value = _categories.value.filterNot { it == name }
        _shortcuts.value = _shortcuts.value.map {
            if (it.category == name) it.copy(category = "") else it
        }
        persist()
    }

    private fun persist() {
        val shortcutsJson = JSONArray().apply {
            _shortcuts.value.forEach { shortcut ->
                put(
                    JSONObject().apply {
                        put("id", shortcut.id)
                        put("name", shortcut.name)
                        put("command", shortcut.command)
                        put("description", shortcut.description)
                        put("category", shortcut.category)
                        put("runMode", shortcut.runMode.name)
                        put("confirmation", shortcut.confirmation)
                        put("pinHome", shortcut.pinHome)
                    },
                )
            }
        }
        val categoriesJson = JSONArray().apply {
            _categories.value.forEach(::put)
        }
        preferences.edit()
            .putString(KEY_SHORTCUTS, shortcutsJson.toString())
            .putString(KEY_CATEGORIES, categoriesJson.toString())
            .apply()
    }

    private fun loadShortcuts(): List<TerminalShortcut> {
        val raw = preferences.getString(KEY_SHORTCUTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        TerminalShortcut(
                            id = item.optLong("id", System.currentTimeMillis() + i),
                            name = item.optString("name"),
                            command = item.optString("command"),
                            description = item.optString("description"),
                            category = item.optString("category"),
                            runMode = runCatching {
                                TerminalShortcutRunMode.valueOf(
                                    item.optString("runMode"),
                                )
                            }.getOrDefault(TerminalShortcutRunMode.CURRENT_SESSION),
                            confirmation = item.optBoolean("confirmation", true),
                            pinHome = item.optBoolean("pinHome", false),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun loadCategories(): List<String> {
        val raw = preferences.getString(KEY_CATEGORIES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) add(array.getString(i))
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val KEY_SHORTCUTS = "shortcuts"
        const val KEY_CATEGORIES = "categories"
    }
}
