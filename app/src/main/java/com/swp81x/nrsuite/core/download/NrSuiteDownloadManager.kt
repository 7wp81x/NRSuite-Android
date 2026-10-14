package com.swp81x.nrsuite.core.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

sealed interface DownloadState {
    data class Pending(
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = -1L,
    ) : DownloadState

    data class Running(
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : DownloadState {
        val percent: Float?
            get() = if (totalBytes > 0L) {
                (downloadedBytes * 100f / totalBytes).coerceIn(0f, 100f)
            } else {
                null
            }
    }

    data object Completed : DownloadState

    data class Failed(
        val reason: String,
    ) : DownloadState
}

object NrSuiteDownloadManager {
    /** Downloads to the app-specific external files directory by default. */
    fun destinationFile(
        context: Context,
        relativePath: String,
    ): File {
        val root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        return File(root, relativePath)
    }

    /**
     * Enqueues a background system download. The system DownloadManager owns
     * the notification, retry, and process-death behavior.
     */
    fun enqueue(
        context: Context,
        url: String,
        destination: File,
        title: String,
        description: String,
    ): Long {
        destination.parentFile?.mkdirs()
        if (destination.exists()) destination.delete()

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(title)
            setDescription(description)
            setMimeType("text/csv")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
            setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
            )
            // IEEE rejects the default DownloadManager user-agent with HTTP 418.
            // Use a standard Android browser UA for compatibility with public servers.
            addRequestHeader(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
            )
            addRequestHeader("Accept", "text/csv,application/octet-stream,*/*")
            addRequestHeader("Accept-Encoding", "identity")
            setDestinationUri(Uri.fromFile(destination))
        }

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request)
    }

    fun cancel(context: Context, downloadId: Long) {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.remove(downloadId)
    }

    fun observe(context: Context, downloadId: Long): Flow<DownloadState> = flow {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        while (currentCoroutineContext().isActive) {
            val state = query(manager, downloadId)
            emit(state)
            if (state is DownloadState.Completed || state is DownloadState.Failed) break
            delay(1000)
        }
    }.flowOn(Dispatchers.IO)

    private fun query(manager: DownloadManager, downloadId: Long): DownloadState {
        val query = DownloadManager.Query().setFilterById(downloadId)
        manager.query(query).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) {
                return DownloadState.Failed("Download record not found")
            }
            val status = cursor.getInt(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS),
            )
            val downloaded = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
            )
            val total = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
            )
            return when (status) {
                DownloadManager.STATUS_PENDING -> DownloadState.Pending(downloaded, total)
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PAUSED,
                -> DownloadState.Running(downloaded, total)
                DownloadManager.STATUS_SUCCESSFUL -> DownloadState.Completed
                DownloadManager.STATUS_FAILED -> {
                    val reasonCode = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON),
                    )
                    DownloadState.Failed(reasonLabel(reasonCode))
                }
                else -> DownloadState.Pending(downloaded, total)
            }
        }
    }

    private fun reasonLabel(code: Int): String = when (code) {
        DownloadManager.ERROR_CANNOT_RESUME -> "Cannot resume download"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage device not found"
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "Destination file already exists"
        DownloadManager.ERROR_FILE_ERROR -> "File error"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP data error"
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Insufficient storage space"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Unhandled HTTP response"
        DownloadManager.ERROR_UNKNOWN -> "Unknown download error"
        else -> "Download failed ($code)"
    }
}
