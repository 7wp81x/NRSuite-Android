package com.swp81x.nrsuite.core.ir

/**
 * Parser for the Flipper Zero .ir text format.
 *
 * A file may contain many signal blocks. Each block normally starts with a
 * `name:` line and contains either raw timings or parsed protocol fields.
 */
object IrFileParser {
    fun parse(content: String): List<IrSignal> {
        val blocks = mutableListOf<LinkedHashMap<String, String>>()
        var current = linkedMapOf<String, String>()

        fun flush() {
            if (current.isNotEmpty()) {
                blocks += LinkedHashMap(current)
                current = linkedMapOf()
            }
        }

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            if (line.startsWith("#")) {
                // Tolerate the old "# name:" variant used by a few exporters.
                val commentValue = line.removePrefix("#").trim()
                if (commentValue.startsWith("name:", ignoreCase = true)) {
                    flush()
                    current["name"] = commentValue.substringAfter(':').trim()
                }
                return@forEach
            }

            val separator = line.indexOf(':')
            if (separator <= 0) return@forEach
            val key = line.substring(0, separator).trim().lowercase()
            val value = line.substring(separator + 1).trim()

            if (key == "name" && current.isNotEmpty()) flush()
            if (key == "type" && current.containsKey("type")) flush()
            current[key] = value
        }
        flush()

        return blocks.mapIndexedNotNull { index, block -> blockToSignal(block, index) }
    }

    private fun blockToSignal(block: Map<String, String>, index: Int): IrSignal? {
        val name = block["name"]?.takeIf { it.isNotBlank() } ?: "Signal ${index + 1}"
        val rawData = block["data"]
            ?.split(Regex("\\s+"))
            ?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.isNotEmpty() }
            .orEmpty()
        val declaredType = block["type"]?.lowercase()
        val type = if (declaredType == "raw" || rawData.isNotEmpty()) {
            IrSignalType.RAW
        } else {
            IrSignalType.PARSED
        }

        val protocol = block["protocol"]?.trim()?.takeIf { it.isNotBlank() }
        val address = block["address"]?.let(::parseHexField)
        val command = block["command"]?.let(::parseHexField)
        if (type == IrSignalType.PARSED && (protocol == null || command == null)) {
            return null
        }
        if (type == IrSignalType.RAW && rawData.isEmpty()) return null

        val frequency = block["frequency"]?.toIntOrNull()?.takeIf { it in 1..1_000_000 }
            ?: 38000
        val dutyCycle = block["duty_cycle"]?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
        val bits = block["bits"]?.toIntOrNull()?.takeIf { it > 0 && it <= 64 }

        return IrSignal(
            name = name,
            type = type,
            frequencyHz = frequency,
            dutyCycle = dutyCycle,
            protocol = protocol,
            address = address,
            command = command,
            bits = bits,
            rawTimingsUs = rawData,
        )
    }

    /**
     * Flipper's address/command fields are commonly written as little-endian
     * hex bytes: `07 00 00 00`. Also accept a plain hex integer.
     */
    internal fun parseHexField(value: String): Long? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        val tokens = trimmed.split(Regex("\\s+"))
        if (tokens.size > 1) {
            var result = 0L
            tokens.forEachIndexed { index, token ->
                val byte = token.removePrefix("0x").removePrefix("0X").toIntOrNull(16)
                    ?: return null
                if (byte !in 0..0xFF || index >= 8) return null
                result = result or (byte.toLong() shl (index * 8))
            }
            return result
        }

        val hasPrefix = trimmed.startsWith("0x", ignoreCase = true)
        val compact = trimmed.removePrefix("0x").removePrefix("0X")
        // Flipper accepts compact 8-digit byte strings such as "07000000".
        // Interpret those as bytes in textual order, little-endian numerically.
        if (!hasPrefix && compact.length == 8) {
            var result = 0L
            compact.chunked(2).forEachIndexed { index, byteText ->
                val byte = byteText.toIntOrNull(16) ?: return null
                result = result or (byte.toLong() shl (index * 8))
            }
            return result
        }
        return compact.toLongOrNull(16)
    }
}
