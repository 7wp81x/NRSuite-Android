package com.swp81x.nrsuite.core.update

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ChecksumUtils {
    suspend fun fetchText(url: String): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "NRSuite-Android")
                setRequestProperty("Accept", "text/plain,application/octet-stream,*/*")
            }
            if (connection.responseCode !in 200..299) return@withContext null
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Throwable) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun sha256(file: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    fun expectedHashFor(sumsText: String, fileName: String): String? {
        return sumsText.lineSequence()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isBlank() || trimmed.startsWith("#")) return@mapNotNull null
                val parts = trimmed.split(Regex("\\s+"), limit = 2)
                if (parts.size < 2) return@mapNotNull null
                val hash = parts[0].trim()
                val name = parts[1].trim().removePrefix("*").substringAfterLast('/')
                if (name.equals(fileName, ignoreCase = true)) hash.lowercase() else null
            }
            .firstOrNull()
    }
}
