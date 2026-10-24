package com.swp81x.nrsuite.core.ir

/**
 * Converts Flipper-style parsed IR signals into raw mark/space timings.
 *
 * The protocol constants and field layouts are based on the documented
 * Flipper Zero infrared protocol encoders. Raw signals are passed through.
 */
object IrProtocolEncoder {
    private const val MAX_PATTERN_LENGTH = 4096

    fun supportedProtocols(): List<String> = listOf(
        "NEC",
        "NECext",
        "NEC42",
        "NEC42ext",
        "Samsung32",
        "SIRC",
        "SIRC15",
        "SIRC20",
        "RC5",
        "RC5X",
        "RC6",
        "Kaseikyo",
        "RCA",
        "Pioneer",
        "JVC",
    )

    fun encode(signal: IrSignal): IrEncodeResult {
        if (signal.type == IrSignalType.RAW) {
            val timings = signal.rawTimingsUs
            if (timings.isEmpty() || timings.any { it <= 0 }) {
                return IrEncodeResult.Failure("Raw signal has no valid timings.")
            }
            if (timings.size > MAX_PATTERN_LENGTH) {
                return IrEncodeResult.Failure("Raw signal is too long for phone IR transmission.")
            }
            val pattern = timings.toMutableList()
            // ConsumerIrManager receives alternating mark/space pairs. Flipper
            // raw captures often end on a mark, so add a safe trailing gap.
            if (pattern.size % 2 != 0) pattern += 45_000
            return IrEncodeResult.Success(
                timingsUs = pattern.toIntArray(),
                frequencyHz = signal.frequencyHz.coerceIn(1, 1_000_000),
            )
        }

        val protocol = signal.protocol?.trim()?.uppercase()
            ?: return IrEncodeResult.Failure("Parsed signal has no protocol.")
        val address = signal.address ?: 0L
        val command = signal.command
            ?: return IrEncodeResult.Failure("Parsed signal has no command.")
        val encoded = runCatching {
            when (protocol) {
                "NEC", "NECEXT" -> nec(address, command, extended = protocol == "NECEXT")
                "NEC42" -> nec42(address, command, extended = false)
                "NEC42EXT" -> nec42(address, command, extended = true)
                "SAMSUNG", "SAMSUNG32" -> samsung32(address, command)
                "SIRC", "SIRC12", "SONY" -> sirc(address, command, 12)
                "SIRC15" -> sirc(address, command, 15)
                "SIRC20" -> sirc(address, command, 20)
                "RC5" -> rc5(address, command, extended = false)
                "RC5X" -> rc5(address, command, extended = true)
                "RC6" -> rc6(address, command)
                "KASEIKYO", "PANASONIC" -> kaseikyo(address, command)
                "RCA" -> rca(address, command)
                "PIONEER" -> pioneer(address, command)
                "JVC" -> jvc(address, command)
                else -> return IrEncodeResult.Failure("Unsupported parsed protocol: $protocol")
            }
        }.getOrElse { error ->
            return IrEncodeResult.Failure(error.message ?: "Could not encode $protocol.")
        }

        if (encoded.isEmpty() || encoded.any { it <= 0 }) {
            return IrEncodeResult.Failure("Protocol encoder produced invalid timings.")
        }
        val normalized = if (encoded.size % 2 == 0) encoded else encoded + 45_000
        if (normalized.size > MAX_PATTERN_LENGTH) {
            return IrEncodeResult.Failure("Encoded signal is too long for phone IR transmission.")
        }
        return IrEncodeResult.Success(
            timingsUs = normalized,
            frequencyHz = signal.frequencyHz.coerceIn(1, 1_000_000),
        )
    }

    private fun nec(address: Long, command: Long, extended: Boolean): IntArray {
        val a = address and if (extended) 0xFFFF else 0xFF
        val c = command and 0xFF
        val bits = mutableListOf<Int>()
        if (extended) {
            bits += lsbBits(a, 16)
            bits += lsbBits(command and 0xFFFF, 16)
        } else {
            bits += lsbBits(a, 8)
            bits += lsbBits(a.inv() and 0xFF, 8)
            bits += lsbBits(c, 8)
            bits += lsbBits(c.inv() and 0xFF, 8)
        }
        return pdwm(9000, 4500, bits, oneSpace = 1690, zeroSpace = 560, footerMark = 560, footerSpace = 41_000)
    }

    private fun nec42(address: Long, command: Long, extended: Boolean): IntArray {
        val bits = mutableListOf<Int>()
        if (extended) {
            val a = address and 0x3FFFFFF
            val c = command and 0xFFFF
            bits += lsbBits(a, 26)
            bits += lsbBits(c, 16)
        } else {
            val a = address and 0x1FFF
            val c = command and 0xFF
            bits += lsbBits(a, 13)
            bits += lsbBits(a.inv() and 0x1FFF, 13)
            bits += lsbBits(c, 8)
            bits += lsbBits(c.inv() and 0xFF, 8)
        }
        return pdwm(9000, 4500, bits, oneSpace = 1690, zeroSpace = 560, footerMark = 560, footerSpace = 41_000)
    }

    private fun samsung32(address: Long, command: Long): IntArray {
        val a = address and 0xFF
        val c = command and 0xFF
        val bits = mutableListOf<Int>()
        bits += lsbBits(a, 8)
        bits += lsbBits(a, 8)
        bits += lsbBits(c, 8)
        bits += lsbBits(c.inv() and 0xFF, 8)
        return pdwm(4500, 4500, bits, oneSpace = 1650, zeroSpace = 550, footerMark = 550, footerSpace = 46_000, bitMark = 550)
    }

    private fun sirc(address: Long, command: Long, bitCount: Int): IntArray {
        val commandBits = lsbBits(command and 0x7F, 7)
        val addressBits = when (bitCount) {
            12 -> lsbBits(address and 0x1F, 5)
            15 -> lsbBits(address and 0xFF, 8)
            else -> lsbBits(address and 0x1FFF, 13)
        }
        val dataBits = commandBits + addressBits
        val oneFrame = mutableListOf<Int>()
        oneFrame += 2400
        oneFrame += 600
        dataBits.forEach { bit ->
            oneFrame += if (bit == 1) 1200 else 600
            oneFrame += 600
        }
        // Sony expects the frame to be transmitted at least three times.
        oneFrame.removeLast()
        val used = oneFrame.sum()
        oneFrame += (45_000 - used).coerceAtLeast(0)
        return (oneFrame + oneFrame + oneFrame).toIntArray()
    }

    private fun kaseikyo(address: Long, command: Long): IntArray {
        val id = ((address shr 24) and 0x03).toInt()
        val vendorId = ((address shr 8) and 0xFFFF).toInt()
        val genre1 = ((address shr 4) and 0x0F).toInt()
        val genre2 = (address and 0x0F).toInt()
        val cmd = command.toInt() and 0x3FF

        val data = IntArray(6)
        data[0] = vendorId and 0xFF
        data[1] = (vendorId shr 8) and 0xFF
        val vendorParity = ((data[0] xor data[1]) and 0x0F) xor
            (((data[0] xor data[1]) shr 4) and 0x0F)
        data[2] = (vendorParity and 0x0F) or (genre1 shl 4)
        data[3] = (genre2 and 0x0F) or ((cmd and 0x0F) shl 4)
        data[4] = (id shl 6) or ((cmd shr 4) and 0x3F)
        data[5] = data[2] xor data[3] xor data[4]

        val bits = mutableListOf<Int>()
        data.forEach { bits += lsbBits(it.toLong(), 8) }
        return pdwm(3456, 1728, bits, oneSpace = 1296, zeroSpace = 432, footerMark = 432, footerSpace = 74_000, bitMark = 432)
    }

    private fun rca(address: Long, command: Long): IntArray {
        val a = (address and 0x0F).toInt()
        val c = (command and 0xFF).toInt()
        val data = ((a and 0x0F) shl 20) or
            (c shl 12) or
            ((a.inv() and 0x0F) shl 8) or
            (c.inv() and 0xFF)
        val bits = (23 downTo 0).map { index -> (data shr index) and 1 }
        return pdwm(3680, 3680, bits, oneSpace = 1840, zeroSpace = 920, footerMark = 460, footerSpace = 7_360, bitMark = 460)
    }

    private fun pioneer(address: Long, command: Long): IntArray {
        val a = (address and 0xFF).toInt()
        val c = (command and 0xFF).toInt()
        val data = intArrayOf(
            a,
            a.inv() and 0xFF,
            c,
            c.inv() and 0xFF,
            0,
        )
        val bits = mutableListOf<Int>()
        data.forEach { bits += lsbBits(it.toLong(), 8) }
        return pdwm(8500, 4225, bits.take(33), oneSpace = 1500, zeroSpace = 500, footerMark = 500, footerSpace = 26_000, bitMark = 500)
    }

    private fun jvc(address: Long, command: Long): IntArray {
        val bits = mutableListOf<Int>()
        bits += lsbBits(address and 0xFF, 8)
        bits += lsbBits(command and 0xFF, 8)
        return pdwm(8400, 4200, bits, oneSpace = 1725, zeroSpace = 525, footerMark = 525, footerSpace = 40_000, bitMark = 525)
    }

    /**
     * RC5/RC5X use Manchester coding. A fixed toggle value is used because a
     * standalone .ir signal does not preserve the remote's toggle history.
     */
    private fun rc5(address: Long, command: Long, extended: Boolean): IntArray {
        val a = (address and 0x1F).toInt()
        val c = (command and 0x7F).toInt()
        val fieldBit = if (c < 0x40) 1 else 0
        val packed = (fieldBit shl 11) or (a shl 6) or (c and 0x3F)
        val fieldAndPayload = packed.toString(2).padStart(12, '0')
        val toggle = '0'
        // Start bit + field bit + toggle + remaining address/command bits.
        val bits = "1" + fieldAndPayload[0] + toggle + fieldAndPayload.substring(1)
        val halfLevels = mutableListOf<Boolean>()
        bits.forEach { ch ->
            val one = ch == '1'
            // RC5: 1 => space then mark, 0 => mark then space.
            halfLevels += !one
            halfLevels += one
        }
        if (halfLevels.size <= 1) return IntArray(0)
        val output = mutableListOf<Int>()
        var currentLevel = halfLevels[1]
        var currentDuration = 889
        for (index in 2 until halfLevels.size) {
            if (halfLevels[index] == currentLevel) {
                currentDuration += 889
            } else {
                output += currentDuration
                currentLevel = halfLevels[index]
                currentDuration = 889
            }
        }
        output += currentDuration
        val used = output.sum()
        val gap = 114_000 - used
        if (gap > 0) {
            if (output.size % 2 == 0) {
                output[output.lastIndex] = output.last() + gap
            } else {
                output += gap
            }
        }
        return output.toIntArray()
    }

    private fun rc6(address: Long, command: Long): IntArray {
        val t = 444
        val leaderMark = 2664
        val leaderSpace = 888
        val signalFree = 2664
        val payload = ((address and 0xFF) shl 8) or (command and 0xFF)
        val payloadBits = payload.toString(2).padStart(16, '0')
        // RC6 mode 0: start(1), mode(000), toggle, 16-bit payload.
        val bits = "10000" + payloadBits
        val output = mutableListOf<Int>()
        var lastWasMark = false
        fun addSegment(isMark: Boolean, durationUs: Int) {
            if (durationUs <= 0) return
            if (output.isNotEmpty() && lastWasMark == isMark) {
                output[output.lastIndex] = output.last() + durationUs
            } else {
                output += durationUs
            }
            lastWasMark = isMark
        }
        addSegment(true, leaderMark)
        addSegment(false, leaderSpace)
        bits.forEachIndexed { index, ch ->
            val duration = if (index == 4) 2 * t else t
            val one = ch == '1'
            addSegment(one, duration)
            addSegment(!one, duration)
        }
        addSegment(false, signalFree)
        return output.toIntArray()
    }

    private fun pdwm(
        headerMark: Int,
        headerSpace: Int,
        bits: List<Int>,
        oneSpace: Int,
        zeroSpace: Int,
        footerMark: Int,
        footerSpace: Int = 0,
        bitMark: Int = 560,
    ): IntArray {
        val output = mutableListOf<Int>()
        output += headerMark
        output += headerSpace
        bits.forEach { bit ->
            output += bitMark
            output += if (bit == 1) oneSpace else zeroSpace
        }
        if (footerMark > 0) output += footerMark
        if (footerSpace > 0) output += footerSpace
        return output.toIntArray()
    }

    private fun lsbBits(value: Long, count: Int): List<Int> {
        return (0 until count).map { index -> ((value shr index) and 1L).toInt() }
    }
}
