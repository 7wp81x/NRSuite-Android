package com.swp81x.nrsuite.core.flasher

import java.io.ByteArrayOutputStream

/**
 * SLIP encoding used by the ESP32 ROM bootloader.
 */
object SlipCodec {
    const val END: Int = 0xC0
    const val ESC: Int = 0xDB
    const val ESC_END: Int = 0xDC
    const val ESC_ESC: Int = 0xDD

    fun encode(packet: ByteArray): ByteArray {
        val output = ByteArrayOutputStream(packet.size + 8)
        output.write(END)
        for (byte in packet) {
            when (byte.toInt() and 0xFF) {
                END -> {
                    output.write(ESC)
                    output.write(ESC_END)
                }
                ESC -> {
                    output.write(ESC)
                    output.write(ESC_ESC)
                }
                else -> output.write(byte.toInt() and 0xFF)
            }
        }
        output.write(END)
        return output.toByteArray()
    }
}

/**
 * Incremental SLIP decoder. Feed it any byte chunks and it returns complete
 * unescaped packets as they arrive.
 */
class SlipDecoder {
    private val current = ByteArrayOutputStream()
    private var inPacket = false
    private var escaped = false

    fun reset() {
        current.reset()
        inPacket = false
        escaped = false
    }

    fun feed(data: ByteArray): List<ByteArray> {
        val packets = mutableListOf<ByteArray>()
        for (byte in data) {
            val value = byte.toInt() and 0xFF
            if (escaped) {
                when (value) {
                    SlipCodec.ESC_END -> current.write(SlipCodec.END)
                    SlipCodec.ESC_ESC -> current.write(SlipCodec.ESC)
                    else -> current.write(value)
                }
                escaped = false
                continue
            }

            when (value) {
                SlipCodec.END -> {
                    if (inPacket && current.size() > 0) {
                        packets += current.toByteArray()
                    }
                    current.reset()
                    inPacket = true
                }
                SlipCodec.ESC -> {
                    if (inPacket) escaped = true
                }
                else -> {
                    if (inPacket) current.write(value)
                }
            }
        }
        return packets
    }
}
