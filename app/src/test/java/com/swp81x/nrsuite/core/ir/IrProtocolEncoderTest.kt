package com.swp81x.nrsuite.core.ir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IrProtocolEncoderTest {
    @Test
    fun passesThroughRawSignals() {
        val signal = IrSignal(
            name = "raw",
            type = IrSignalType.RAW,
            frequencyHz = 38000,
            rawTimingsUs = listOf(9000, 4500, 560, 560),
        )

        val result = IrProtocolEncoder.encode(signal) as IrEncodeResult.Success

        assertEquals(38000, result.frequencyHz)
        assertTrue(result.timingsUs.contentEquals(intArrayOf(9000, 4500, 560, 560)))
    }

    @Test
    fun encodesNecHeaderAndFooter() {
        val signal = IrSignal(
            name = "Power",
            type = IrSignalType.PARSED,
            protocol = "NEC",
            address = 0x00,
            command = 0x08,
            frequencyHz = 38000,
        )

        val result = IrProtocolEncoder.encode(signal) as IrEncodeResult.Success

        assertEquals(9000, result.timingsUs[0])
        assertEquals(4500, result.timingsUs[1])
        assertEquals(560, result.timingsUs[result.timingsUs.lastIndex - 1])
        assertEquals(68, result.timingsUs.size)
    }

    @Test
    fun encodesSamsungAndSircFrames() {
        val samsung = IrProtocolEncoder.encode(
            IrSignal(
                name = "Samsung",
                type = IrSignalType.PARSED,
                protocol = "Samsung32",
                address = 0x07,
                command = 0x02,
            ),
        ) as IrEncodeResult.Success
        assertEquals(4500, samsung.timingsUs[0])
        assertEquals(4500, samsung.timingsUs[1])
        assertEquals(68, samsung.timingsUs.size)

        val sirc = IrProtocolEncoder.encode(
            IrSignal(
                name = "Sony",
                type = IrSignalType.PARSED,
                protocol = "SIRC",
                address = 0x01,
                command = 0x15,
            ),
        ) as IrEncodeResult.Success
        assertEquals(2400, sirc.timingsUs[0])
        assertEquals(600, sirc.timingsUs[1])
        assertEquals(78, sirc.timingsUs.size)
    }

    @Test
    fun padsOddRawPatternToMarkSpacePair() {
        val signal = IrSignal(
            name = "odd raw",
            type = IrSignalType.RAW,
            rawTimingsUs = listOf(9000, 4500, 560),
        )

        val result = IrProtocolEncoder.encode(signal) as IrEncodeResult.Success

        assertEquals(4, result.timingsUs.size)
        assertEquals(45_000, result.timingsUs.last())
    }

    @Test
    fun encodesRc5AndRc6WithoutErrors() {
        val rc5 = IrProtocolEncoder.encode(
            IrSignal(
                name = "RC5",
                type = IrSignalType.PARSED,
                protocol = "RC5",
                address = 0x10,
                command = 0x0C,
            ),
        ) as IrEncodeResult.Success
        assertTrue(rc5.timingsUs.isNotEmpty())
        assertEquals(0, rc5.timingsUs.size % 2)

        val rc6 = IrProtocolEncoder.encode(
            IrSignal(
                name = "RC6",
                type = IrSignalType.PARSED,
                protocol = "RC6",
                address = 0x00,
                command = 0x0C,
            ),
        ) as IrEncodeResult.Success
        assertEquals(2664, rc6.timingsUs[0])
        assertTrue(rc6.timingsUs.isNotEmpty())
    }

    @Test
    fun reportsUnsupportedProtocol() {
        val result = IrProtocolEncoder.encode(
            IrSignal(
                name = "Unknown",
                type = IrSignalType.PARSED,
                protocol = "MYSTERY",
                address = 1,
                command = 2,
            ),
        )

        assertTrue(result is IrEncodeResult.Failure)
    }
}
