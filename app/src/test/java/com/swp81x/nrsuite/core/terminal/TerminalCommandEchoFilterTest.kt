package com.swp81x.nrsuite.core.terminal

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalCommandEchoFilterTest {
    @Test
    fun `removes command echo while preserving surrounding terminal output`() {
        val filter = TerminalCommandEchoFilter()
        filter.expect("  printf 'hello\\n'")

        val result = filter.process(
            "prompt$   printf 'hello\\n'\r\nhello\r\nprompt$ "
                .toByteArray(Charsets.UTF_8),
        )

        assertEquals("prompt$ hello\r\nprompt$ ", result.toString(Charsets.UTF_8))
    }

    @Test
    fun `removes command echo split across output chunks`() {
        val filter = TerminalCommandEchoFilter()
        filter.expect("  ls -lah")

        val first = filter.process("prompt$   ls".toByteArray(Charsets.UTF_8))
        val second = filter.process(" -lah\r\ntotal 0\r\nprompt$ ".toByteArray(Charsets.UTF_8))

        assertEquals("prompt$ ", first.toString(Charsets.UTF_8))
        assertEquals("total 0\r\nprompt$ ", second.toString(Charsets.UTF_8))
    }

    @Test
    fun `handles multiple private commands in order`() {
        val filter = TerminalCommandEchoFilter()
        filter.expect("  first")
        filter.expect("  second")

        val result = filter.process(
            "  first\r\none\r\n  second\r\ntwo\r\n".toByteArray(Charsets.UTF_8),
        )

        assertEquals("one\r\ntwo\r\n", result.toString(Charsets.UTF_8))
    }

    @Test
    fun `passes data through unchanged when no command is expected`() {
        val input = "ordinary output\r\n".toByteArray(Charsets.UTF_8)
        val result = TerminalCommandEchoFilter().process(input)
        assertArrayEquals(input, result)
    }
}
