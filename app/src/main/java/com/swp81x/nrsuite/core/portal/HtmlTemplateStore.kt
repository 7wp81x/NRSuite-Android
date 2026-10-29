package com.swp81x.nrsuite.core.portal

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class HtmlTemplate(
    val id: String,
    val name: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val createdAt: Long,
    val updatedAt: Long,
)

class HtmlTemplateStore(
    private val context: Context,
    private val rootDir: File,
) {
    private val templatesDir = File(rootDir, "templates").apply { mkdirs() }
    private val indexFile = File(rootDir, "index.json")

    fun load(): List<HtmlTemplate> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(indexFile.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val name = item.optString("name")
                    val fileName = item.optString("fileName")
                    if (id.isBlank() || name.isBlank() || fileName.isBlank()) continue
                    val file = File(templatesDir, fileName)
                    if (!file.exists()) continue
                    val updatedAt = item.optLong("updatedAt")
                    add(
                        HtmlTemplate(
                            id = id,
                            name = name,
                            fileName = fileName,
                            sizeBytes = file.length(),
                            sha256 = item.optString("sha256").takeIf { it.isNotBlank() }
                                ?: sha256Hex(file.readBytes()),
                            createdAt = item.optLong("createdAt", updatedAt),
                            updatedAt = updatedAt,
                        )
                    )
                }
            }.sortedWith(
                compareBy<HtmlTemplate> { it.name.lowercase() }
                    .thenByDescending { it.updatedAt },
            )
        }.getOrDefault(emptyList())
    }

    fun import(name: String, input: InputStream): HtmlTemplate {
        val cleanName = sanitizeName(name)
        val bytes = input.use { it.readBytes() }
        require(bytes.isNotEmpty()) { "Template file is empty." }
        require(bytes.size <= MAX_TEMPLATE_BYTES) {
            "Template exceeds the ${MAX_TEMPLATE_BYTES / 1024} KB firmware limit."
        }
        val sha256 = sha256Hex(bytes)
        val duplicate = load().firstOrNull { it.sha256.equals(sha256, ignoreCase = true) }
        require(duplicate == null) {
            "Duplicate HTML content is already saved as '${duplicate?.name}'."
        }

        val id = UUID.randomUUID().toString()
        val fileName = "$id.html"
        File(templatesDir, fileName).writeBytes(bytes)
        val now = System.currentTimeMillis()
        val template = HtmlTemplate(
            id = id,
            name = cleanName,
            fileName = fileName,
            sizeBytes = bytes.size.toLong(),
            sha256 = sha256,
            createdAt = now,
            updatedAt = now,
        )
        persist(load().filterNot { it.id == id } + template)
        return template
    }

    fun rename(id: String, newName: String) {
        val cleanName = sanitizeName(newName)
        val templates = load()
        val target = templates.firstOrNull { it.id == id } ?: return
        val updated = templates.map {
            if (it.id == id) {
                it.copy(name = cleanName, updatedAt = System.currentTimeMillis())
            } else {
                it
            }
        }
        persist(updated)
        require(target.name.isNotBlank()) { "Template name is required." }
    }

    fun delete(id: String) {
        val templates = load()
        val target = templates.firstOrNull { it.id == id } ?: return
        File(templatesDir, target.fileName).delete()
        persist(templates.filterNot { it.id == id })
    }

    fun uri(template: HtmlTemplate): Uri {
        val file = File(templatesDir, template.fileName)
        require(file.exists()) { "Template file is missing." }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    private fun persist(templates: List<HtmlTemplate>) {
        val array = JSONArray()
        templates.forEach { template ->
            array.put(
                JSONObject().apply {
                    put("id", template.id)
                    put("name", template.name)
                    put("fileName", template.fileName)
                    put("sizeBytes", template.sizeBytes)
                    put("sha256", template.sha256)
                    put("createdAt", template.createdAt)
                    put("updatedAt", template.updatedAt)
                }
            )
        }
        val temp = File(rootDir, "index.json.tmp")
        temp.writeText(array.toString())
        if (!temp.renameTo(indexFile)) {
            indexFile.writeText(array.toString())
            temp.delete()
        }
    }

    private fun sanitizeName(name: String): String {
        val clean = name.substringAfterLast('/').substringAfterLast('\\').substringAfterLast(':').trim()
        require(clean.isNotBlank()) { "Template name is required." }
        require(clean.length <= 80) { "Template name must be 80 characters or fewer." }
        return clean
    }

    companion object {
        const val MAX_TEMPLATE_BYTES = 24 * 1024
        const val WARN_TEMPLATE_BYTES = 16 * 1024

        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }
    }
}
