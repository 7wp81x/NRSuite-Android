package com.swp81x.nrsuite.core.ir

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class IrZipExtractionResult(
    val totalIrFiles: Int,
    val extractedFiles: Int,
    val skippedFiles: Int,
    val totalBytes: Long,
)

object IrZipPathPolicy {
    private const val MAX_PATH_LENGTH = 240
    private const val MAX_DEPTH = 12
    private val unsafeCharacters = Regex("[\\u0000-\\u001f<>:\"\\\\|?*]")

    fun safeSegments(entryName: String): List<String>? {
        if (entryName.isBlank()) return null
        if (entryName.startsWith("/") || entryName.startsWith("\\")) return null
        if ('\\' in entryName) return null
        if (entryName.contains("//") || entryName.endsWith("/")) return null
        val rawSegments = entryName.split('/')
        if (rawSegments.any { it.isEmpty() } || rawSegments.size > MAX_DEPTH) return null
        val safe = rawSegments.map { raw ->
            if (raw == "." || raw == "..") return null
            val cleaned = raw
                .replace(unsafeCharacters, "_")
                .trim()
                .trimEnd('.', ' ')
            if (cleaned.isBlank() || cleaned == "." || cleaned == "..") return null
            cleaned
        }
        if (safe.joinToString("/").length > MAX_PATH_LENGTH) return null
        return safe
    }

    fun isIrFile(segments: List<String>): Boolean =
        segments.lastOrNull()?.endsWith(".ir", ignoreCase = true) == true

    fun commonFlipperRoot(entryPaths: List<List<String>>): String? {
        val firstSegments = entryPaths.mapNotNull { it.firstOrNull() }.distinct()
        if (firstSegments.size != 1) return null
        val first = firstSegments.single()
        return first.takeIf {
            it.startsWith("Flipper-IRDB", ignoreCase = true) &&
                entryPaths.all { path -> path.size > 1 }
        }
    }
}

/**
 * Writes an IR ZIP into SAF storage using bulk child creation instead of
 * DocumentFile.findFile()/createFile() for every entry. Directory discovery
 * is cached and only DocumentsContract createDocument/openOutputStream calls
 * touch the provider per item. Files are written by a small worker pool.
 */
class IrZipExtractor(
    private val contentResolver: ContentResolver,
) {
    suspend fun extract(
        zipFile: File,
        destinationDirectory: DocumentFile,
        onProgress: (extracted: Int, total: Int, currentPath: String?) -> Unit,
    ): IrZipExtractionResult = withContext(Dispatchers.IO) {
        val plans = buildPlans(zipFile)
        val directories = plans
            .flatMap { plan -> parentPaths(plan.relativeSegments) }
            .distinct()
            .sortedWith(compareBy<List<String>> { it.size }.thenBy { it.joinToString("/") })

        onProgress(0, plans.size, "Creating ${directories.size} folders...")
        val directoryUris = createDirectories(destinationDirectory, directories) { created, total, path ->
            onProgress(0, plans.size, "Creating folders $created/$total · $path")
        }

        val extracted = AtomicInteger(0)
        val skipped = AtomicInteger(0)
        val completed = AtomicInteger(0)
        val totalBytes = AtomicLong(0L)
        val progressMutex = Mutex()
        val workerCount = minOf(FILE_WORKERS, plans.size.coerceAtLeast(1))

        suspend fun report(plan: PlannedIrZipEntry) {
            val done = completed.incrementAndGet()
            progressMutex.withLock {
                onProgress(
                    done,
                    plans.size,
                    plan.relativeSegments.joinToString("/"),
                )
            }
        }

        coroutineScope {
            val queue = Channel<PlannedIrZipEntry>(Channel.UNLIMITED)
            launch {
                plans.forEach { queue.send(it) }
                queue.close()
            }
            repeat(workerCount) {
                launch(Dispatchers.IO) {
                    ZipFile(zipFile).use { zip ->
                        for (plan in queue) {
                            currentCoroutineContext().ensureActive()
                            var createdUri: Uri? = null
                            try {
                                val parentPath = plan.relativeSegments.dropLast(1).joinToString("/")
                                val parentUri = directoryUris[parentPath]
                                    ?: throw IOException("Missing destination folder for $parentPath")
                                val created = DocumentsContract.createDocument(
                                    contentResolver,
                                    parentUri,
                                    "application/octet-stream",
                                    plan.relativeSegments.last(),
                                ) ?: throw IOException("Could not create ${plan.fileName}")
                                createdUri = created
                                val entry = zip.getEntry(plan.entryName)
                                    ?: throw IOException("ZIP entry disappeared: ${plan.entryName}")
                                val output = contentResolver.openOutputStream(created, "w")
                                    ?: throw IOException("Could not open ${plan.fileName} for writing.")
                                val written = output.buffered(BUFFER_BYTES).use { target ->
                                    zip.getInputStream(entry).use { input ->
                                        copyWithLimit(input, target)
                                    }
                                }
                                totalBytes.addAndGet(written)
                                if (totalBytes.get() > MAX_TOTAL_EXTRACTED_BYTES) {
                                    throw IOException("IR ZIP exceeds the total extracted-size limit.")
                                }
                                extracted.incrementAndGet()
                            } catch (t: Throwable) {
                                currentCoroutineContext().ensureActive()
                                createdUri?.let { uri ->
                                    runCatching {
                                        DocumentsContract.deleteDocument(contentResolver, uri)
                                    }
                                }
                                skipped.incrementAndGet()
                                Log.w(
                                    "IrRemote",
                                    "IR ZIP entry failed: ${plan.entryName}: ${t.message}",
                                )
                            }
                            report(plan)
                        }
                    }
                }
            }
        }

        if (extracted.get() == 0) {
            throw IOException("No .ir files could be extracted from the ZIP.")
        }
        IrZipExtractionResult(
            totalIrFiles = plans.size,
            extractedFiles = extracted.get(),
            skippedFiles = skipped.get(),
            totalBytes = totalBytes.get(),
        )
    }

    private fun buildPlans(zipFile: File): List<PlannedIrZipEntry> {
        ZipFile(zipFile).use { zip ->
            val safeEntries = zip.entries().asSequence().mapNotNull { entry ->
                if (entry.isDirectory) return@mapNotNull null
                val segments = IrZipPathPolicy.safeSegments(entry.name) ?: run {
                    Log.w("IrRemote", "Skipping unsafe IR ZIP entry: ${entry.name}")
                    return@mapNotNull null
                }
                if (!IrZipPathPolicy.isIrFile(segments)) return@mapNotNull null
                if (entry.size > MAX_IR_FILE_BYTES) {
                    Log.w("IrRemote", "Skipping oversized IR ZIP entry: ${entry.name}")
                    return@mapNotNull null
                }
                entry.name to segments
            }.toList()
            if (safeEntries.size > MAX_IR_ZIP_FILES) {
                throw IOException("IR ZIP contains too many .ir files.")
            }
            if (safeEntries.isEmpty()) {
                throw IllegalArgumentException("The selected ZIP contains no usable .ir files.")
            }
            val rootToStrip = IrZipPathPolicy.commonFlipperRoot(safeEntries.map { it.second })
            return safeEntries.mapNotNull { (entryName, segments) ->
                val relativeSegments = if (
                    rootToStrip != null && segments.first().equals(rootToStrip, ignoreCase = true)
                ) {
                    segments.drop(1)
                } else {
                    segments
                }
                if (relativeSegments.isEmpty()) null else PlannedIrZipEntry(entryName, relativeSegments)
            }
        }
    }

    private suspend fun createDirectories(
        root: DocumentFile,
        paths: List<List<String>>,
        onProgress: (created: Int, total: Int, path: String) -> Unit,
    ): Map<String, Uri> {
        val uris = linkedMapOf("" to root.uri)
        val created = AtomicInteger(0)
        val total = paths.size
        val semaphore = Semaphore(DIRECTORY_WORKERS)
        var currentDepth = 0
        val byDepth = paths.groupBy { it.size }.toSortedMap()
        for ((depth, depthPaths) in byDepth) {
            if (depth != currentDepth) currentDepth = depth
            coroutineScope {
                depthPaths.map { path ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val parentPath = path.dropLast(1).joinToString("/")
                            val parentUri = uris[parentPath]
                                ?: throw IOException("Parent folder missing while creating $path")
                            val uri = DocumentsContract.createDocument(
                                contentResolver,
                                parentUri,
                                DocumentsContract.Document.MIME_TYPE_DIR,
                                path.last(),
                            ) ?: throw IOException("Could not create folder ${path.joinToString("/")}")
                            synchronized(uris) {
                                uris[path.joinToString("/")] = uri
                            }
                            val done = created.incrementAndGet()
                            onProgress(done, total, path.joinToString("/"))
                        }
                    }
                }.awaitAll()
            }
        }
        return uris
    }

    private fun parentPaths(segments: List<String>): List<List<String>> {
        val result = mutableListOf<List<String>>()
        for (depth in 1 until segments.size) {
            result += segments.take(depth)
        }
        return result
    }

    private fun copyWithLimit(
        input: java.io.InputStream,
        output: java.io.OutputStream,
    ): Long {
        val buffer = ByteArray(BUFFER_BYTES)
        var written = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            written += read
            if (written > MAX_IR_FILE_BYTES) {
                throw IOException("IR file exceeded the 8 MB limit.")
            }
            output.write(buffer, 0, read)
        }
        return written
    }

    private data class PlannedIrZipEntry(
        val entryName: String,
        val relativeSegments: List<String>,
    ) {
        val fileName: String
            get() = relativeSegments.last()
    }

    companion object {
        private const val MAX_IR_FILE_BYTES = 8L * 1024L * 1024L
        private const val MAX_TOTAL_EXTRACTED_BYTES = 2L * 1024L * 1024L * 1024L
        private const val MAX_IR_ZIP_FILES = 50_000
        private const val BUFFER_BYTES = 32 * 1024
        private const val DIRECTORY_WORKERS = 6
        private const val FILE_WORKERS = 8
    }
}
