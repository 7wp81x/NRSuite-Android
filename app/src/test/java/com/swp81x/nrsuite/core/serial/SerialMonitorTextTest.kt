package com.swp81x.nrsuite.core.serial

import org.junit.Assert.assertEquals
import org.junit.Test

class SerialMonitorTextTest {
    @Test
    fun timestampCanBeHiddenForDisplayAndExport() {
        val line = SerialLogLine(
            timestamp = "12:34:56.789",
            text = "firmware ready",
        )

        assertEquals(
            "firmware ready",
            formatSerialLogLine(line, showTimestamps = false, hexMode = false),
        )
        assertEquals(
            "[12:34:56.789] firmware ready",
            formatSerialLogLine(line, showTimestamps = true, hexMode = false),
        )
    }

    @Test
    fun transmittedLinesAreMarkedAndHexModeIsSupported() {
        val line = SerialLogLine(
            timestamp = "12:34:56.789",
            text = "AT",
            direction = SerialLineDirection.TX,
        )

        assertEquals(
            "> 41 54",
            formatSerialLogLine(line, showTimestamps = false, hexMode = true),
        )
    }

    @Test
    fun partialLineIsIncludedInRenderedLog() {
        val complete = SerialLogLine("12:34:56.789", "one")
        val partial = SerialLogLine("12:34:56.790", "two")

        assertEquals(
            "one\ntwo",
            formatSerialLogText(
                lines = listOf(complete),
                partialLine = partial,
                showTimestamps = false,
                hexMode = false,
            ),
        )
    }

    @Test
    fun accumulatorPreservesLinesAcrossChunkBoundaries() {
        val accumulator = SerialLineAccumulator()

        assertEquals(emptyList<String>(), accumulator.append("hel"))
        assertEquals("hel", accumulator.partialLine())
        assertEquals(listOf("hello"), accumulator.append("lo\r"))
        assertEquals(listOf("world"), accumulator.append("\nworld\r"))
        assertEquals("", accumulator.partialLine())
        assertEquals(emptyList<String>(), accumulator.append("tail"))
        assertEquals("tail", accumulator.partialLine())
    }

    @Test
    fun accumulatorPreservesBlankLines() {
        val accumulator = SerialLineAccumulator()

        assertEquals(listOf("", "", "a"), accumulator.append("\r\n\na\n"))
        assertEquals("", accumulator.partialLine())
    }

    @Test
    fun utf8DecoderPreservesCharactersSplitAcrossReads() {
        val decoder = StreamingUtf8Decoder()
        val euro = "€".toByteArray(Charsets.UTF_8)

        assertEquals("", decoder.decode(euro.copyOfRange(0, 1)))
        assertEquals("€", decoder.decode(euro.copyOfRange(1, euro.size)))
    }

    @Test
    fun clearDiscardsUncommittedData() {
        val accumulator = SerialLineAccumulator()
        accumulator.append("unfinished")

        accumulator.clear()

        assertEquals("", accumulator.partialLine())
        assertEquals(emptyList<String>(), accumulator.append(""))
    }
}
