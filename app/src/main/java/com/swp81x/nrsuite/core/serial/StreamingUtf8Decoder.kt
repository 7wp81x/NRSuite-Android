package com.swp81x.nrsuite.core.serial

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/**
 * UTF-8 decoder for chunked USB serial reads.
 *
 * USB reads can split a multi-byte UTF-8 character across two buffers. A plain
 * `String(bytes, Charsets.UTF_8)` would emit replacement characters in that
 * case, so keep incomplete byte sequences until the next read.
 */
class StreamingUtf8Decoder {
    private val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    private val charBuffer = CharBuffer.allocate(8_192)
    private var pendingBytes = ByteArray(0)

    fun decode(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): String {
        if (length <= 0) return ""

        val incoming = bytes.copyOfRange(offset, offset + length)
        val inputBytes = if (pendingBytes.isEmpty()) {
            incoming
        } else {
            pendingBytes + incoming
        }
        pendingBytes = ByteArray(0)

        val input = ByteBuffer.wrap(inputBytes)
        val output = StringBuilder()
        while (true) {
            charBuffer.clear()
            val result = decoder.decode(input, charBuffer, false)
            charBuffer.flip()
            output.append(charBuffer)

            when {
                result.isOverflow -> continue
                result.isUnderflow -> break
                else -> result.throwException()
            }
        }

        if (input.hasRemaining()) {
            pendingBytes = ByteArray(input.remaining()).also(input::get)
        }
        return output.toString()
    }

    fun finish(): String {
        if (pendingBytes.isEmpty()) return ""

        val input = ByteBuffer.wrap(pendingBytes)
        pendingBytes = ByteArray(0)
        val output = StringBuilder()
        while (true) {
            charBuffer.clear()
            val result = decoder.decode(input, charBuffer, true)
            charBuffer.flip()
            output.append(charBuffer)

            when {
                result.isOverflow -> continue
                result.isUnderflow -> break
                else -> result.throwException()
            }
        }
        return output.toString()
    }
}
