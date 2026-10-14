package com.swp81x.nrsuite.core.update

import org.json.JSONObject

data class ReleaseInfo(
    val tag: String,
    val name: String?,
    val htmlUrl: String,
    val downloadUrl: String?,
    val publishedAt: String?,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("tag", tag)
        put("name", name ?: "")
        put("html_url", htmlUrl)
        put("download_url", downloadUrl ?: "")
        put("published_at", publishedAt ?: "")
    }

    companion object {
        fun fromJson(json: JSONObject): ReleaseInfo? {
            val tag = json.optString("tag").takeIf { it.isNotBlank() } ?: return null
            val htmlUrl = json.optString("html_url").takeIf { it.isNotBlank() } ?: return null
            return ReleaseInfo(
                tag = tag,
                name = json.optString("name").takeIf { it.isNotBlank() },
                htmlUrl = htmlUrl,
                downloadUrl = json.optString("download_url").takeIf { it.isNotBlank() },
                publishedAt = json.optString("published_at").takeIf { it.isNotBlank() },
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
