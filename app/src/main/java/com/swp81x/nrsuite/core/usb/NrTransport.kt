package com.swp81x.nrsuite.core.usb

import java.io.IOException

/**
 * Byte-stream transport used by [com.swp81x.nrsuite.core.session.NrSession].
 * Implementations do not know about NRSuite frames; they only move bytes.
 */
interface NrTransport {
    val isOpen: Boolean

    @Throws(IOException::class)
    fun open()

    @Throws(IOException::class)
    fun read(buffer: ByteArray, timeoutMs: Int): Int

    @Throws(IOException::class)
    fun write(data: ByteArray, timeoutMs: Int)

    @Throws(IOException::class)
    fun close()
}
