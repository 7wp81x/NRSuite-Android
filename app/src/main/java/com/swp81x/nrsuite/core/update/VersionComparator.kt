package com.swp81x.nrsuite.core.update

/**
 * Small dependency-free version comparator for release tags such as:
 *   1.0.0
 *   1.0.0-beta.2
 *   v1.0.0-beta.2.4
 *
 * Leading "v" and build metadata after "+" are ignored.
 */
object VersionComparator {
    fun isNewer(candidate: String, current: String): Boolean =
        compare(candidate, current) > 0

    fun compare(a: String, b: String): Int {
        val left = ParsedVersion.parse(a)
        val right = ParsedVersion.parse(b)

        val maxParts = maxOf(left.numbers.size, right.numbers.size)
        for (index in 0 until maxParts) {
            val difference = left.numbers.getOrElse(index) { 0 } -
                right.numbers.getOrElse(index) { 0 }
            if (difference != 0) return difference.compareTo(0)
        }

        return comparePreRelease(left.preRelease, right.preRelease)
    }

    private fun comparePreRelease(left: List<String>?, right: List<String>?): Int {
        if (left == null && right == null) return 0
        if (left == null) return 1 // release > pre-release
        if (right == null) return -1

        val maxSize = maxOf(left.size, right.size)
        for (index in 0 until maxSize) {
            val l = left.getOrNull(index)
            val r = right.getOrNull(index)
            if (l == null) return -1
            if (r == null) return 1

            val lNumber = l.toIntOrNull()
            val rNumber = r.toIntOrNull()
            val result = when {
                lNumber != null && rNumber != null -> lNumber.compareTo(rNumber)
                lNumber != null -> -1 // numeric identifiers are lower than alphanumeric
                rNumber != null -> 1
                else -> l.compareTo(r, ignoreCase = true)
            }
            if (result != 0) return result
        }
        return 0
    }

    private data class ParsedVersion(
        val numbers: List<Int>,
        val preRelease: List<String>?,
    ) {
        companion object {
            fun parse(raw: String): ParsedVersion {
                val trimmed = raw.trim().removePrefix("v").removePrefix("V")
                val withoutBuild = trimmed.substringBefore("+")
                val mainPart = withoutBuild.substringBefore("-")
                val preReleasePart = withoutBuild.substringAfter("-", missingDelimiterValue = "")
                val numbers = mainPart.split(".").map { it.toIntOrNull() ?: 0 }
                val preRelease = if (preReleasePart.isBlank()) {
                    null
                } else {
                    preReleasePart.split(".")
                }
                return ParsedVersion(numbers = numbers, preRelease = preRelease)
            }
        }
    }
}
