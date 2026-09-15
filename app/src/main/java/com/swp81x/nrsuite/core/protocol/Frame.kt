package com.swp81x.nrsuite.core.protocol

/**
 * NRSuite bridge frame types.
 *
 * The wire format is:
 * [0xAD 0xDE][type 1B][id 1B][payload length 4B LE][payload NB]
 */
enum class FrameType(val code: Int) {
    COMMAND(0x01),
    RESPONSE(0x02),
    EVENT(0x03),
    PCAP(0x04),
    ACK(0x05),
    HTML(0x06);

    companion object {
        fun fromCode(code: Int): FrameType? = entries.firstOrNull { it.code == code }
    }
}

data class Frame(
    val type: FrameType,
    val id: Int,
    val payload: ByteArray,
) {
    val payloadAsString: String
        get() = payload.toString(Charsets.UTF_8)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Frame) return false
        return type == other.type &&
            id == other.id &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + id
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
