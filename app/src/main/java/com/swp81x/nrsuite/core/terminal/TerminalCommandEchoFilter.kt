package com.swp81x.nrsuite.core.terminal

import java.io.ByteArrayOutputStream

/**
 * Removes the terminal driver's local echo for commands injected by NRSuite.
 *
 * A command written to an interactive PTY is echoed back before the shell runs
 * it. That echo exposes generated startup helpers and long shortcut wrappers to
 * the user. This filter waits for the exact command echo (including the CRLF
 * appended by the terminal line discipline) and removes it from the SSH output
 * stream before it reaches the terminal emulator.
 */
internal class TerminalCommandEchoFilter {
    private val expectedEchoes = ArrayDeque<ByteArray>()
    private var pending = ByteArray(0)

    fun expect(command: String) {
        expectedEchoes.addLast((command + "\r\n").toByteArray(Charsets.UTF_8))
    }

    fun clear() {
        expectedEchoes.clear()
        pending = ByteArray(0)
    }

    fun process(data: ByteArray, offset: Int = 0, count: Int = data.size): ByteArray {
        if (count <= 0) return ByteArray(0)
        if (expectedEchoes.isEmpty()) {
            return data.copyOfRange(offset, offset + count)
        }

        pending = pending + data.copyOfRange(offset, offset + count)
        val output = ByteArrayOutputStream(pending.size)

        while (expectedEchoes.isNotEmpty()) {
            val expected = expectedEchoes.first()
            val matchIndex = indexOf(pending, expected)
            if (matchIndex >= 0) {
                output.write(pending, 0, matchIndex)
                pending = pending.copyOfRange(matchIndex + expected.size, pending.size)
                expectedEchoes.removeFirst()
                continue
            }

            // Preserve only the longest suffix that could still be the start
            // of the expected echo when the SSH stream splits the command
            // across multiple reads. Everything before it is safe to display.
            val keepBytes = longestSuffixMatchingExpectedPrefix(pending, expected)
            val flushBytes = pending.size - keepBytes
            if (flushBytes > 0) {
                output.write(pending, 0, flushBytes)
                pending = pending.copyOfRange(flushBytes, pending.size)
            }
            break
        }

        if (expectedEchoes.isEmpty() && pending.isNotEmpty()) {
            output.write(pending)
            pending = ByteArray(0)
        }
        return output.toByteArray()
    }

    private fun longestSuffixMatchingExpectedPrefix(buffer: ByteArray, expected: ByteArray): Int {
        val maxLength = minOf(buffer.size, expected.size - 1)
        for (length in maxLength downTo 1) {
            val start = buffer.size - length
            var matches = true
            for (index in 0 until length) {
                if (buffer[start + index] != expected[index]) {
                    matches = false
                    break
                }
            }
            if (matches) return length
        }
        return 0
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || haystack.size < needle.size) return -1
        val lastStart = haystack.size - needle.size
        for (start in 0..lastStart) {
            var matches = true
            for (index in needle.indices) {
                if (haystack[start + index] != needle[index]) {
                    matches = false
                    break
                }
            }
            if (matches) return start
        }
        return -1
    }
}
