package com.swp81x.nrsuite.core.ir

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class IrDatabaseDownloadResult(
    val file: File,
    val archiveVersion: String,
    val sizeBytes: Long,
)

class IrDatabaseDownloader(
    private val cacheDirectory: File,
) {
    @Volatile
    private var activeConnection: HttpURLConnection? = null

    fun cancel() {
        runCatching { activeConnection?.disconnect() }
        activeConnection = null
    }

    suspend fun download(
        url: String,
        destination: File,
        maxArchiveBytes: Long = MAX_ARCHIVE_BYTES,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
        onRetry: (attempt: Int, maxAttempts: Int, reason: String) -> Unit,
    ): IrDatabaseDownloadResult = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()
        cacheDirectory.mkdirs()
        val part = File(destination.parentFile, "${destination.name}.part")
        var lastFailure: Throwable? = null

        for (attempt in 1..MAX_ATTEMPTS) {
            currentCoroutineContext().ensureActive()
            try {
                val result = downloadAttempt(
                    url = url,
                    part = part,
                    maxArchiveBytes = maxArchiveBytes,
                    onProgress = onProgress,
                )
                if (destination.exists()) destination.delete()
                if (!part.renameTo(destination)) {
                    throw IOException("Could not finalize the downloaded IR database archive.")
                }
                onProgress(result.sizeBytes, result.sizeBytes)
                return@withContext IrDatabaseDownloadResult(
                    file = destination,
                    archiveVersion = result.archiveVersion,
                    sizeBytes = result.sizeBytes,
                )
            } catch (t: Throwable) {
                currentCoroutineContext().ensureActive()
                lastFailure = t
                if (t !is IOException || attempt >= MAX_ATTEMPTS) throw t
                val delayMs = RETRY_DELAYS_MS[(attempt - 1).coerceAtMost(RETRY_DELAYS_MS.lastIndex)]
                onRetry(attempt, MAX_ATTEMPTS, t.message ?: "Connection interrupted")
                delay(delayMs)
            } finally {
                activeConnection = null
            }
        }
        throw lastFailure ?: IOException("IR database download failed.")
    }

    private suspend fun downloadAttempt(
        url: String,
        part: File,
        maxArchiveBytes: Long,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): AttemptResult {
        var existingBytes = if (part.exists()) part.length() else 0L
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/zip,application/octet-stream,*/*")
            setRequestProperty("Accept-Encoding", "identity")
            if (existingBytes > 0L) {
                setRequestProperty("Range", "bytes=$existingBytes-")
            }
        }
        activeConnection = connection
        try {
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // The server ignored our Range request, so restart cleanly.
                if (part.exists() && !part.delete()) {
                    throw IOException("Could not reset the partial IR database download.")
                }
                existingBytes = 0L
            } else if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                if (existingBytes <= 0L) {
                    throw IOException("Server returned an unexpected partial download response.")
                }
            } else if (responseCode == 416 && existingBytes > 0L) {
                val total = parseContentRangeTotal(connection.getHeaderField("Content-Range"))
                if (total > 0L && existingBytes == total) {
                    return AttemptResult(
                        archiveVersion = archiveVersion(connection),
                        sizeBytes = existingBytes,
                    )
                }
                if (part.delete()) existingBytes = 0L
                throw IOException("Partial download could not be resumed; retrying from the beginning.")
            } else if (responseCode in RETRYABLE_HTTP_CODES) {
                throw IOException("IR database server returned HTTP $responseCode.")
            } else if (responseCode !in 200..299) {
                throw IOException("IR database download failed with HTTP $responseCode.")
            }

            val contentLength = connection.contentLengthLong
            val totalBytes = if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                parseContentRangeTotal(connection.getHeaderField("Content-Range"))
                    .takeIf { it > 0L }
                    ?: if (contentLength >= 0L) existingBytes + contentLength else -1L
            } else {
                contentLength
            }
            if (totalBytes > maxArchiveBytes) {
                throw IOException("IR database archive is larger than the allowed limit.")
            }
            if (existingBytes > 0L && totalBytes > 0L && existingBytes == totalBytes) {
                return AttemptResult(
                    archiveVersion = archiveVersion(connection),
                    sizeBytes = existingBytes,
                )
            }

            onProgress(existingBytes, totalBytes)
            var downloadedBytes = existingBytes
            var lastReportAt = 0L
            connection.inputStream.buffered(BUFFER_BYTES).use { input ->
                java.io.FileOutputStream(part, existingBytes > 0L).buffered(BUFFER_BYTES).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        downloadedBytes += read
                        if (downloadedBytes > maxArchiveBytes) {
                            throw IOException("IR database archive exceeded the allowed size.")
                        }
                        output.write(buffer, 0, read)
                        val now = System.currentTimeMillis()
                        if (now - lastReportAt >= PROGRESS_UPDATE_MS) {
                            lastReportAt = now
                            onProgress(downloadedBytes, totalBytes)
                        }
                    }
                }
            }
            if (totalBytes > 0L && downloadedBytes < totalBytes) {
                throw IOException(
                    "Download stopped after $downloadedBytes of $totalBytes bytes; resuming.",
                )
            }
            return AttemptResult(
                archiveVersion = archiveVersion(connection),
                sizeBytes = downloadedBytes,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun archiveVersion(connection: HttpURLConnection): String =
        connection.getHeaderField("ETag")
            ?.removePrefix("W/")
            ?.trim('"')
            ?.takeIf { it.isNotBlank() }
            ?: connection.getHeaderField("Last-Modified")
                ?.takeIf { it.isNotBlank() }
                ?: "main"

    private fun parseContentRangeTotal(value: String?): Long {
        val total = value?.substringAfterLast('/')?.trim()
        return total?.toLongOrNull() ?: -1L
    }

    private data class AttemptResult(
        val archiveVersion: String,
        val sizeBytes: Long,
    )

    companion object {
        const val DEFAULT_URL =
            "https://github.com/7wp81x/Flipper-IRDB/archive/refs/heads/main.zip"
        const val MAX_ARCHIVE_BYTES = 1L * 1024L * 1024L * 1024L
        private const val MAX_ATTEMPTS = 6
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val BUFFER_BYTES = 64 * 1024
        private const val PROGRESS_UPDATE_MS = 250L
        private const val USER_AGENT =
            "NRSuite/1.0 (Android) Flipper-IRDB-downloader"
        private val RETRY_DELAYS_MS = longArrayOf(2_000L, 4_000L, 8_000L, 12_000L, 20_000L)
        private val RETRYABLE_HTTP_CODES = setOf(408, 425, 429, 500, 502, 503, 504)
    }
}
