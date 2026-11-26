package com.swp81x.nrsuite.core.serial

/** Direction of a line in a raw serial monitor session. */
enum class SerialLineDirection {
    RX,
    TX,
}

/**
 * One logical line captured by the raw USB serial monitor.
 *
 * [timestamp] is retained even when timestamps are hidden so that display,
 * copy, and export can be switched without losing information.
 */
data class SerialLogLine(
    val timestamp: String,
    val text: String,
    val direction: SerialLineDirection = SerialLineDirection.RX,
)

/**
 * Assembles text chunks into logical lines while preserving line-ending
 * semantics across chunk boundaries.
 *
 * USB serial reads are arbitrary byte chunks, so treating every read as a
 * completed line makes normal firmware output appear fragmented.
 */
class SerialLineAccumulator {
    private val currentLine = StringBuilder()
    private var previousWasCarriageReturn = false

    fun append(chunk: String): List<String> {
        if (chunk.isEmpty()) return emptyList()

        val completed = mutableListOf<String>()
        for (character in chunk) {
            when (character) {
                '\r' -> {
                    completed += currentLine.toString()
                    currentLine.setLength(0)
                    previousWasCarriageReturn = true
                }

                '\n' -> {
                    if (!previousWasCarriageReturn) {
                        completed += currentLine.toString()
                    }
                    currentLine.setLength(0)
                    previousWasCarriageReturn = false
                }

                else -> {
                    currentLine.append(character)
                    previousWasCarriageReturn = false
                }
            }
        }
        return completed
    }

    fun partialLine(): String = currentLine.toString()

    fun drainPartialLine(): String {
        val text = currentLine.toString()
        currentLine.setLength(0)
        previousWasCarriageReturn = false
        return text
    }

    fun clear() {
        currentLine.setLength(0)
        previousWasCarriageReturn = false
    }
}

internal fun formatSerialLogLine(
    line: SerialLogLine,
    showTimestamps: Boolean,
    hexMode: Boolean,
): String = buildString {
    if (showTimestamps) {
        append('[')
        append(line.timestamp)
        append("] ")
    }
    if (line.direction == SerialLineDirection.TX) {
        append("> ")
    }
    append(if (hexMode) line.text.toHexDump() else line.text)
}

internal fun formatSerialLogText(
    lines: List<SerialLogLine>,
    partialLine: SerialLogLine?,
    showTimestamps: Boolean,
    hexMode: Boolean,
): String {
    val renderedLines = lines.map { line ->
        formatSerialLogLine(line, showTimestamps, hexMode)
    }
    val renderedPartial = partialLine
        ?.takeIf { it.text.isNotEmpty() }
        ?.let { formatSerialLogLine(it, showTimestamps, hexMode) }
    return (renderedLines + listOfNotNull(renderedPartial)).joinToString("\n")
}

private fun String.toHexDump(): String =
    toByteArray(Charsets.UTF_8).joinToString(" ") { byte ->
        (byte.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')
    }
