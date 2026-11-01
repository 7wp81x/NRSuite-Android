package com.swp81x.nrsuite.core.portal

import java.io.InputStream
import java.io.OutputStream
import java.io.ByteArrayOutputStream

/**
 * Applies simple local substitutions to an HTML template before it is sent to
 * the firmware. The template token is intentionally plain text so templates
 * remain inspectable and editable outside the app.
 */
object PortalHtmlRenderer {
    const val SSID_TOKEN = "#SSID#"

    fun render(bytes: ByteArray, ssid: String): ByteArray {
        val output = ByteArrayOutputStream(bytes.size)
        bytes.inputStream().use { input ->
            render(input, output, ssid)
        }
        return output.toByteArray()
    }

    /**
     * Streams the source HTML into the destination while replacing #SSID#.
     * Only a chunk and the possible token-prefix overlap are held in memory.
     */
    fun render(input: InputStream, output: OutputStream, ssid: String) {
        val reader = input.reader(Charsets.UTF_8)
        val writer = output.writer(Charsets.UTF_8)
        val escapedSsid = escapeHtml(ssid)
        val buffer = CharArray(4 * 1024)
        val pending = StringBuilder()

        while (true) {
            val read = reader.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            pending.append(buffer, 0, read)
            drainPending(pending, escapedSsid, writer, eof = false)
        }
        drainPending(pending, escapedSsid, writer, eof = true)
        writer.flush()
    }

    fun escapeHtml(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(char)
            }
        }
    }

    private fun drainPending(
        pending: StringBuilder,
        escapedSsid: String,
        writer: java.io.Writer,
        eof: Boolean,
    ) {
        var searchStart = 0
        while (true) {
            val tokenIndex = pending.indexOf(SSID_TOKEN, searchStart)
            if (tokenIndex < 0) break
            writer.write(pending.substring(searchStart, tokenIndex))
            writer.write(escapedSsid)
            searchStart = tokenIndex + SSID_TOKEN.length
        }

        if (eof) {
            writer.write(pending.substring(searchStart))
            pending.clear()
            return
        }

        // Keep a possible partial token at the end of the buffer for the next
        // read. Only the token length minus one characters can be partial.
        val minKeep = maxOf(searchStart, pending.length - (SSID_TOKEN.length - 1))
        var safeStart = pending.length
        for (start in minKeep..pending.length) {
            val suffix = pending.substring(start)
            if (suffix.isEmpty() || SSID_TOKEN.startsWith(suffix)) {
                safeStart = start
                break
            }
        }
        writer.write(pending.substring(searchStart, safeStart))
        pending.delete(0, safeStart)
    }
}
