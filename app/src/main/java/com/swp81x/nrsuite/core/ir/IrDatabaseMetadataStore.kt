package com.swp81x.nrsuite.core.ir

import java.io.File
import org.json.JSONObject

class IrDatabaseMetadataStore(
    val root: File,
) {
    private val metadataFile = File(root, "ir_database_metadata.json")

    @Synchronized
    fun load(): IrDatabaseMetadata? {
        root.mkdirs()
        if (!metadataFile.exists()) return null
        val raw = runCatching { metadataFile.readText() }.getOrNull() ?: return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        return runCatching {
            IrDatabaseMetadata(
                sourceUrl = json.optString("source_url"),
                archiveVersion = json.optString("archive_version"),
                downloadedAtMs = json.optLong("downloaded_at_ms", 0L),
                indexedAtMs = json.optLong("indexed_at_ms", 0L),
                archiveBytes = json.optLong("archive_bytes", 0L),
                extractedFiles = json.optInt("extracted_files", 0),
                indexedRemotes = json.optInt("indexed_remotes", 0),
                destinationPath = json.optString("destination_path"),
            )
        }.getOrNull()
    }

    @Synchronized
    fun save(metadata: IrDatabaseMetadata) {
        root.mkdirs()
        val json = JSONObject().apply {
            put("source_url", metadata.sourceUrl)
            put("archive_version", metadata.archiveVersion)
            put("downloaded_at_ms", metadata.downloadedAtMs)
            put("indexed_at_ms", metadata.indexedAtMs)
            put("archive_bytes", metadata.archiveBytes)
            put("extracted_files", metadata.extractedFiles)
            put("indexed_remotes", metadata.indexedRemotes)
            put("destination_path", metadata.destinationPath)
        }
        val temp = File(root, "${metadataFile.name}.tmp")
        temp.writeText(json.toString(2))
        if (metadataFile.exists() && !metadataFile.delete()) {
            temp.delete()
            return
        }
        if (!temp.renameTo(metadataFile)) temp.delete()
    }

    @Synchronized
    fun clear() {
        metadataFile.delete()
    }
}
