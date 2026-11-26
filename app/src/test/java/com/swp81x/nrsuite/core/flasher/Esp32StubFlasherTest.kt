package com.swp81x.nrsuite.core.flasher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class Esp32StubFlasherTest {
    @Test
    fun `embedded esp32 stub segments decode`() {
        assertTrue(Esp32StubFlasher.text.size > 3_000)
        assertTrue(Esp32StubFlasher.data.isNotEmpty())
        assertEquals(0, Esp32StubFlasher.text.size % 4)
        assertEquals(0, Esp32StubFlasher.data.size % 4)
    }

    @Test
    fun `embedded stub bytes match esptool 5_4_0 esp32 segment`() {
        assertEquals(3_540, Esp32StubFlasher.text.size)
        assertEquals(156, Esp32StubFlasher.data.size)
        assertEquals(
            "b2dff40d626d2e83234ba17f79ab968cf5505cf989d15f9954ed477777eeddad",
            sha256(Esp32StubFlasher.text),
        )
        assertEquals(
            "67e2e29deda649edc6921f9c7149f2c18f0497b94828553ec743873aea219c73",
            sha256(Esp32StubFlasher.data),
        )
        assertEquals(0x400BE000L, Esp32StubFlasher.TEXT_START)
        assertEquals(0x3FFDEBACL, Esp32StubFlasher.DATA_START)
        assertEquals(0x400BE658L, Esp32StubFlasher.ENTRY)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
