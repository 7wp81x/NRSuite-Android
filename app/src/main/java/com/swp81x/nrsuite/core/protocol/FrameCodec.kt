package com.swp81x.nrsuite.core.protocol

/**
 * Encoder and streaming decoder for the NRSuite bridge protocol.
 *
 * This class is deliberately pure Kotlin/Java so it can be unit-tested without
 * an Android device. It mirrors the limits used by the ESP32 firmware
 * (`PROTO_MAX_CHUNK = 1024`).
 */
object FrameCodec {
    const val MAGIC_0: Int = 0xAD
    const val MAGIC_1: Int = 0xDE
    const val HEADER_SIZE: Int = 8
    const val MAX_PAYLOAD_SIZE: Int = 1024

    fun encode(type: FrameType, id: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        require(id in 0..0xFF) { "Frame id must fit in one byte (0..255), was $id" }
        require(payload.size <= MAX_PAYLOAD_SIZE) {
            "Payload is ${payload.size} bytes; protocol max is $MAX_PAYLOAD_SIZE"
        }

        return ByteArray(HEADER_SIZE + payload.size).also { out ->
            out[0] = MAGIC_0.toByte()
            out[1] = MAGIC_1.toByte()
            out[2] = type.code.toByte()
            out[3] = id.toByte()
            out[4] = (payload.size and 0xFF).toByte()
            out[5] = ((payload.size ushr 8) and 0xFF).toByte()
            out[6] = ((payload.size ushr 16) and 0xFF).toByte()
            out[7] = ((payload.size ushr 24) and 0xFF).toByte()
            payload.copyInto(out, destinationOffset = HEADER_SIZE)
        }
    }
}

/**
 * Incremental frame decoder. Feed it any number of bytes from the USB stream;
 * it returns every complete valid frame it can parse and retains partial data.
 */
class FrameDecoder {
    private var buffer: ByteArray = ByteArray(0)

    fun reset() {
        buffer = ByteArray(0)
    }

    fun feed(data: ByteArray): List<Frame> {
        if (data.isNotEmpty()) {
            buffer += data
        }

        val frames = mutableListOf<Frame>()
        var offset = 0

        while (offset < buffer.size) {
            val start = findMagic(offset)
            if (start < 0) {
                // Keep a trailing 0xAD in case the next read supplies 0xDE.
                val retain = if (buffer.isNotEmpty() && buffer[buffer.size - 1] == FrameCodec.MAGIC_0.toByte()) {
                    buffer.size - 1
                } else {
                    buffer.size
                }
                offset = retain
                break
            }

            if (start > offset) {
                offset = start
            }
            if (buffer.size - offset < FrameCodec.HEADER_SIZE) {
                break
            }

            val typeCode = buffer[offset + 2].toInt() and 0xFF
            val id = buffer[offset + 3].toInt() and 0xFF
            val payloadLength =
                (buffer[offset + 4].toInt() and 0xFF) or
                    ((buffer[offset + 5].toInt() and 0xFF) shl 8) or
                    ((buffer[offset + 6].toInt() and 0xFF) shl 16) or
                    ((buffer[offset + 7].toInt() and 0xFF) shl 24)

            if (payloadLength < 0 || payloadLength > FrameCodec.MAX_PAYLOAD_SIZE) {
                // Invalid length: skip one byte and resynchronize.
                offset += 1
                continue
            }

            val totalLength = FrameCodec.HEADER_SIZE + payloadLength
            if (buffer.size - offset < totalLength) {
                break
            }

            val type = FrameType.fromCode(typeCode)
            if (type == null) {
                offset += 1
                continue
            }

            val payloadStart = offset + FrameCodec.HEADER_SIZE
            frames += Frame(
                type = type,
                id = id,
                payload = buffer.copyOfRange(payloadStart, payloadStart + payloadLength),
            )
            offset += totalLength
        }

        buffer = buffer.copyOfRange(offset.coerceAtMost(buffer.size), buffer.size)
        return frames
    }

    private fun findMagic(from: Int): Int {
        var index = from
        while (index + 1 < buffer.size) {
            if (buffer[index] == FrameCodec.MAGIC_0.toByte() &&
                buffer[index + 1] == FrameCodec.MAGIC_1.toByte()
            ) {
                return index
            }
            index++
        }
        return -1
    }
}
