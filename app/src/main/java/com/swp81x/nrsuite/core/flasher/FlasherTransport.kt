package com.swp81x.nrsuite.core.flasher

/**
 * Minimal serial transport used by the ROM-bootloader flasher.
 *
 * This is intentionally separate from [com.swp81x.nrsuite.core.usb.NrTransport]:
 * flashing needs direct DTR/RTS control and does not use NRSuite framing.
 */
interface FlasherTransport {
    val isOpen: Boolean

    fun open()

    fun close()

    fun read(buffer: ByteArray, timeoutMs: Int): Int

    fun write(data: ByteArray, timeoutMs: Int)

    fun flushInput()

    fun setDtr(value: Boolean)

    fun setRts(value: Boolean)
}
