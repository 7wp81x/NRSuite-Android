package com.swp81x.nrsuite

import android.net.Uri
import android.provider.OpenableColumns
import com.swp81x.nrsuite.core.log.LogLevel
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Resolve a user-visible SAF document name. [Uri.lastPathSegment] is only a
 * fallback because it commonly contains document-provider paths such as
 * "primary:Download/page.html".
 */
internal fun MainViewModel.resolveSafDisplayName(uri: Uri, fallback: String): String {
    val queryName = runCatching {
        app.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()
    return safeSafBasename(queryName ?: uri.lastPathSegment, fallback)
}

internal fun safeSafBasename(raw: String?, fallback: String): String {
    val candidate = raw
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        ?.substringAfterLast(':')
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    return candidate ?: fallback
}

internal fun MainViewModel.querySafSize(uri: Uri): Long? = runCatching {
    app.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.SIZE),
        null,
        null,
        null,
    )?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
            cursor.getLong(index)
        } else {
            null
        }
    }
}.getOrNull()

internal fun MainViewModel.loadHtmlTemplatesImpl() {
    scope.launch(Dispatchers.IO) {
        runCatching { htmlTemplateStore.load() }
            .onSuccess { templates -> _htmlTemplates.value = templates }
            .onFailure { error ->
                _htmlTemplateMessage.value = error.message ?: error.javaClass.simpleName
                _htmlTemplateMessageIsError.value = true
            }
    }
}

internal fun MainViewModel.importHtmlTemplateImpl(uri: Uri, name: String?) {
    val displayName = name?.let { safeSafBasename(it, "HTML template.html") }
        ?: resolveSafDisplayName(uri, "HTML template.html")
    scope.launch(Dispatchers.IO) {
        try {
            val stream = app.contentResolver.openInputStream(uri)
                ?: throw IOException("Could not open the selected HTML file.")
            val template = htmlTemplateStore.import(displayName, stream)
            val refreshed = htmlTemplateStore.load()
            _htmlTemplates.value = refreshed
            _htmlTemplateMessage.value = "Imported '${template.name}' (${template.sizeBytes} bytes)."
            _htmlTemplateMessageIsError.value = false
            appendLog("HTML template imported: ${template.name} (${template.sizeBytes} bytes).")
        } catch (error: Throwable) {
            _htmlTemplateMessage.value = error.message ?: error.javaClass.simpleName
            _htmlTemplateMessageIsError.value = true
            appendLog(
                "HTML template import failed: ${error.message ?: error.javaClass.simpleName}",
                level = LogLevel.ERROR,
            )
        }
    }
}

internal fun MainViewModel.renameHtmlTemplateImpl(id: String, name: String) {
    scope.launch(Dispatchers.IO) {
        try {
            val previous = _htmlTemplates.value.firstOrNull { it.id == id }
            htmlTemplateStore.rename(id, name)
            val refreshed = htmlTemplateStore.load()
            _htmlTemplates.value = refreshed
            val renamed = refreshed.firstOrNull { it.id == id }
            if (previous != null && renamed != null) {
                if (_portalHtmlName.value == previous.name) {
                    _portalHtmlName.value = renamed.name
                }
                if (_evilTwinHtmlName.value == previous.name) {
                    _evilTwinHtmlName.value = renamed.name
                }
                _htmlTemplateMessage.value = "Renamed '${previous.name}' to '${renamed.name}'."
                _htmlTemplateMessageIsError.value = false
                appendLog("HTML template renamed: ${previous.name} -> ${renamed.name}.")
            }
        } catch (error: Throwable) {
            _htmlTemplateMessage.value = error.message ?: error.javaClass.simpleName
            _htmlTemplateMessageIsError.value = true
            appendLog(
                "HTML template rename failed: ${error.message ?: error.javaClass.simpleName}",
                level = LogLevel.ERROR,
            )
        }
    }
}

internal fun MainViewModel.deleteHtmlTemplateImpl(id: String) {
    scope.launch(Dispatchers.IO) {
        runCatching {
            val template = _htmlTemplates.value.firstOrNull { it.id == id }
            htmlTemplateStore.delete(id)
            val refreshed = htmlTemplateStore.load()
            _htmlTemplates.value = refreshed
            if (template != null && _portalHtmlName.value == template.name) {
                _portalHtmlUri.value = null
                _portalHtmlName.value = null
                _portalHtmlSelectedSize.value = null
                _portalHtmlComplete.value = false
            }
            if (template != null && _evilTwinHtmlName.value == template.name) {
                _evilTwinHtmlUri.value = null
                _evilTwinHtmlName.value = null
                _evilTwinHtmlSelectedSize.value = null
                _evilTwinHtmlComplete.value = false
            }
            _htmlTemplateMessage.value = template?.let { "Deleted '${it.name}'." } ?: "Template deleted."
            _htmlTemplateMessageIsError.value = false
        }.onFailure { error ->
            _htmlTemplateMessage.value = error.message ?: error.javaClass.simpleName
            _htmlTemplateMessageIsError.value = true
        }
    }
}

internal fun MainViewModel.useHtmlTemplateForPortalImpl(id: String) {
    val template = _htmlTemplates.value.firstOrNull { it.id == id } ?: return
    runCatching { htmlTemplateStore.uri(template) }
        .onSuccess { uri ->
            _portalHtmlUri.value = uri
            _portalHtmlName.value = template.name
            _portalHtmlSelectedSize.value = template.sizeBytes
            _portalHtmlUploading.value = false
            _portalHtmlUploadProgress.value = 0
            _portalHtmlComplete.value = false
            _htmlTemplateMessage.value = "Portal will use '${template.name}'."
            _htmlTemplateMessageIsError.value = false
            appendLog("Portal HTML template selected: ${template.name}.")
        }
        .onFailure { error ->
            _htmlTemplateMessage.value = error.message ?: error.javaClass.simpleName
            _htmlTemplateMessageIsError.value = true
        }
}

internal fun MainViewModel.useHtmlTemplateForEvilTwinImpl(id: String) {
    val template = _htmlTemplates.value.firstOrNull { it.id == id } ?: return
    runCatching { htmlTemplateStore.uri(template) }
        .onSuccess { uri ->
            _evilTwinHtmlUri.value = uri
            _evilTwinHtmlName.value = template.name
            _evilTwinHtmlSelectedSize.value = template.sizeBytes
            _evilTwinHtmlUploading.value = false
            _evilTwinHtmlUploadProgress.value = 0
            _evilTwinHtmlComplete.value = false
            _htmlTemplateMessage.value = "Evil Twin will use '${template.name}'."
            _htmlTemplateMessageIsError.value = false
            appendLog("Evil Twin HTML template selected: ${template.name}.")
        }
        .onFailure { error ->
            _htmlTemplateMessage.value = error.message ?: error.javaClass.simpleName
            _htmlTemplateMessageIsError.value = true
        }
}
