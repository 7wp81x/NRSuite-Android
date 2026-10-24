package com.swp81x.nrsuite.core.ir

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class IrRemoteIndexStore(
    val root: File,
) {
    private val indexFile = File(root, "ir_remote_index.json")

    @Synchronized
    fun load(): List<IrRemoteSummary> {
        root.mkdirs()
        if (!indexFile.exists()) return emptyList()
        val raw = runCatching { indexFile.readText() }.getOrNull() ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val remotes = mutableListOf<IrRemoteSummary>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val remote = runCatching {
                val id = obj.optString("id").takeIf { it.isNotBlank() }
                    ?: return@runCatching null
                val name = obj.optString("name").takeIf { it.isNotBlank() }
                    ?: return@runCatching null
                val documentUri = obj.optString("document_uri").takeIf { it.isNotBlank() }
                    ?: return@runCatching null
                val source = runCatching {
                    IrRemoteSource.valueOf(obj.optString("source").uppercase())
                }.getOrDefault(IrRemoteSource.IMPORTED)
                val protocolsArray = obj.optJSONArray("protocols")
                val protocols = if (protocolsArray == null) emptyList() else {
                    (0 until protocolsArray.length()).mapNotNull { protocolsArray.optString(it).takeIf(String::isNotBlank) }
                }
                val buttonNamesArray = obj.optJSONArray("button_names")
                val buttonNames = if (buttonNamesArray == null) emptyList() else {
                    (0 until buttonNamesArray.length()).mapNotNull {
                        buttonNamesArray.optString(it).takeIf(String::isNotBlank)
                    }
                }
                IrRemoteSummary(
                    id = id,
                    name = name,
                    fileName = obj.optString("file_name", "$name.ir"),
                    relativePath = obj.optString("relative_path", "$name.ir"),
                    documentUri = documentUri,
                    source = source,
                    category = obj.optString("category", "Imported"),
                    brand = obj.optString("brand"),
                    favorite = obj.optBoolean("favorite", false),
                    lastUsedAtMs = obj.optLong("last_used_at_ms", 0L),
                    buttonCount = obj.optInt("button_count", 0).coerceAtLeast(0),
                    protocols = protocols,
                    buttonNames = buttonNames,
                    lastModifiedMs = obj.optLong("last_modified_ms", 0L),
                    sizeBytes = obj.optLong("size_bytes", 0L),
                )
            }.getOrNull()
            if (remote != null) remotes += remote
        }
        return remotes
    }

    @Synchronized
    fun save(remotes: List<IrRemoteSummary>) {
        root.mkdirs()
        val array = JSONArray()
        remotes.forEach { remote ->
            array.put(JSONObject().apply {
                put("id", remote.id)
                put("name", remote.name)
                put("file_name", remote.fileName)
                put("relative_path", remote.relativePath)
                put("document_uri", remote.documentUri)
                put("source", remote.source.name.lowercase())
                put("category", remote.category)
                put("brand", remote.brand)
                put("favorite", remote.favorite)
                put("last_used_at_ms", remote.lastUsedAtMs)
                put("button_count", remote.buttonCount)
                put("protocols", JSONArray().apply {
                    remote.protocols.forEach { put(it) }
                })
                put("button_names", JSONArray().apply {
                    remote.buttonNames.forEach { put(it) }
                })
                put("last_modified_ms", remote.lastModifiedMs)
                put("size_bytes", remote.sizeBytes)
            })
        }
        val temp = File(root, "${indexFile.name}.tmp")
        temp.writeText(array.toString(2))
        if (indexFile.exists() && !indexFile.delete()) {
            temp.delete()
            return
        }
        if (!temp.renameTo(indexFile)) temp.delete()
    }
}
