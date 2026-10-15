package com.swp81x.nrsuite.core.update

import org.json.JSONArray
import org.json.JSONObject

data class ReleaseAsset(
    val name: String,
    val url: String,
    val contentType: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("url", url)
        put("content_type", contentType ?: "")
    }

    companion object {
        fun fromJson(json: JSONObject): ReleaseAsset? {
            val name = json.optString("name").takeIf { it.isNotBlank() } ?: return null
            val url = json.optString("url").takeIf { it.isNotBlank() } ?: return null
            return ReleaseAsset(
                name = name,
                url = url,
                contentType = json.optString("content_type").takeIf { it.isNotBlank() },
            )
        }
    }
}

data class ReleaseInfo(
    val tag: String,
    val name: String?,
    val htmlUrl: String,
    val downloadUrl: String?,
    val apkUrl: String?,
    val publishedAt: String?,
    val assets: List<ReleaseAsset> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("tag", tag)
        put("name", name ?: "")
        put("html_url", htmlUrl)
        put("download_url", downloadUrl ?: "")
        put("apk_url", apkUrl ?: "")
        put("published_at", publishedAt ?: "")
        put("assets", JSONArray().apply {
            assets.forEach { put(it.toJson()) }
        })
    }

    companion object {
        fun fromJson(json: JSONObject): ReleaseInfo? {
            val tag = json.optString("tag").takeIf { it.isNotBlank() } ?: return null
            val htmlUrl = json.optString("html_url").takeIf { it.isNotBlank() } ?: return null
            val assets = mutableListOf<ReleaseAsset>()
            json.optJSONArray("assets")?.let { array ->
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)
                        ?.let { ReleaseAsset.fromJson(it) }
                        ?.let { assets += it }
                }
            }
            return ReleaseInfo(
                tag = tag,
                name = json.optString("name").takeIf { it.isNotBlank() },
                htmlUrl = htmlUrl,
                downloadUrl = json.optString("download_url").takeIf { it.isNotBlank() },
                apkUrl = json.optString("apk_url").takeIf { it.isNotBlank() },
                publishedAt = json.optString("published_at").takeIf { it.isNotBlank() },
                assets = assets,
            )
        }
    }
}

data class AppUpdateInfo(
    val currentVersion: String,
    val latest: ReleaseInfo,
)

data class FirmwareUpdateInfo(
    val currentVersion: String,
    val latest: ReleaseInfo,
)

sealed interface AppUpdateDownloadState {
    data object Idle : AppUpdateDownloadState
    data class Downloading(val percent: Float?) : AppUpdateDownloadState
    data object ReadyToInstall : AppUpdateDownloadState
    data class Failed(val message: String) : AppUpdateDownloadState
}

sealed interface FirmwareAssetDownloadState {
    data object Idle : FirmwareAssetDownloadState
    data class Downloading(val percent: Float?) : FirmwareAssetDownloadState
    data class Downloaded(val fileName: String) : FirmwareAssetDownloadState
    data class Failed(val message: String) : FirmwareAssetDownloadState
}
