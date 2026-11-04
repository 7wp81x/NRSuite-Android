package com.swp81x.nrsuite.core.terminal

private val shortcutParameterPattern =
    Regex("""\{\{\s*([A-Za-z0-9_.-]+)\s*\}\}""")

fun terminalShortcutParameterNames(command: String): List<String> =
    shortcutParameterPattern.findAll(command)
        .map { it.groupValues[1] }
        .distinct()
        .toList()

fun resolveTerminalShortcutCommand(
    command: String,
    parameterValues: Map<String, String>,
): String = shortcutParameterPattern.replace(command) { match ->
    parameterValues[match.groupValues[1]] ?: match.value
}

fun terminalShortcutParameterLabel(name: String): String =
    name.replace('_', ' ')
        .replace('-', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.replaceFirstChar { it.uppercase() }
        }
        .ifBlank { name }
