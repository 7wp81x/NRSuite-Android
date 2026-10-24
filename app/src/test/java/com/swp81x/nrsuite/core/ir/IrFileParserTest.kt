package com.swp81x.nrsuite.core.ir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IrFileParserTest {
    @Test
    fun parsesRawAndParsedFlipperSignals() {
        val content = """
            # Flipper IR sample
            name: Power
            type: parsed
            protocol: NEC
            address: 07 00 00 00
            command: 02 00 00 00
            frequency: 38000

            name: Custom
            type: raw
            frequency: 40000
            duty_cycle: 0.33
            data: 2400 600 1200 600 600 600

            name: Vol+
            type: parsed
            protocol: Samsung32
            address: 07 00 00 00
            command: 02 00 00 00
        """.trimIndent()

        val signals = IrFileParser.parse(content)

        assertEquals(3, signals.size)
        assertEquals(IrSignalType.PARSED, signals[0].type)
        assertEquals("NEC", signals[0].protocol)
        assertEquals(7L, signals[0].address)
        assertEquals(2L, signals[0].command)
        assertEquals(IrSignalType.RAW, signals[1].type)
        assertEquals(40000, signals[1].frequencyHz)
        assertEquals(6, signals[1].rawTimingsUs.size)
        assertEquals("Vol+", signals[2].name)
        assertTrue(IrFileParser.parse("").isEmpty())
    }

    @Test
    fun parsesLittleEndianHexFields() {
        assertEquals(0x12345678L, IrFileParser.parseHexField("78 56 34 12"))
        assertEquals(0x2AL, IrFileParser.parseHexField("2A"))
        assertEquals(0x1234L, IrFileParser.parseHexField("0x1234"))
        assertEquals(0x00000007L, IrFileParser.parseHexField("07000000"))
        assertEquals(null, IrFileParser.parseHexField("not hex"))
    }

    @Test
    fun rejectsParsedSignalWithoutCommand() {
        val content = """
            name: Broken
            type: parsed
            protocol: NEC
            address: 00 00 00 00
        """.trimIndent()

        assertTrue(IrFileParser.parse(content).isEmpty())
    }
}
