package com.swp81x.nrsuite.core.update

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

sealed interface GitHubReleaseResult {
    data class Success(
        val release: ReleaseInfo,
        val etag: String?,
    ) : GitHubReleaseResult

    /** No changed release list, or no published non-draft release exists. */
    data object NotModified : GitHubReleaseResult

    data class Failure(val message: String) : GitHubReleaseResult
}

object GitHubReleaseClient {
    suspend fun fetchLatest(repo: String, etag: String?): GitHubReleaseResult =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("https://api.github.com/repos/$repo/releases?per_page=30")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "NRSuite-Android")
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
                    if (!etag.isNullOrBlank()) {
                        setRequestProperty("If-None-Match", etag)
                    }
                }

                when (val code = connection.responseCode) {
                    HttpURLConnection.HTTP_NOT_MODIFIED,
                    HttpURLConnection.HTTP_NOT_FOUND,
                    -> GitHubReleaseResult.NotModified
                    in 200..299 -> {
                        val body = connection.inputStream.bufferedReader().use { it.readText() }
                        val releases = parseReleases(body)
                        if (releases.isEmpty()) {
                            GitHubReleaseResult.NotModified
                        } else {
                            val latest = releases.maxWithOrNull { left, right ->
                                VersionComparator.compare(left.tag, right.tag)
                            } ?: releases.first()
                            GitHubReleaseResult.Success(
                                release = latest,
                                etag = connection.getHeaderField("ETag"),
                            )
                        }
                    }
                    HttpURLConnection.HTTP_FORBIDDEN,
                    HttpURLConnection.HTTP_UNAUTHORIZED,
                    -> {
                        val remaining = connection.getHeaderField("X-RateLimit-Remaining")
                        val reset = connection.getHeaderField("X-RateLimit-Reset")
                        GitHubReleaseResult.Failure(
                            "GitHub rate limit or access denied" +
                                (remaining?.let { " · remaining=$it" } ?: "") +
                                (reset?.let { " · reset=$it" } ?: ""),
                        )
                    }
                    else -> GitHubReleaseResult.Failure("GitHub returned HTTP $code")
                }
            } catch (t: Throwable) {
                GitHubReleaseResult.Failure(t.message ?: "Update check failed")
            } finally {
                connection?.disconnect()
            }
        }

    private fun parseReleases(body: String): List<ReleaseInfo> {
        val array = JSONArray(body)
        val releases = mutableListOf<ReleaseInfo>()
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            if (json.optBoolean("draft", false)) continue
            parseRelease(json)?.let { releases += it }
        }
        return releases
    }

    private fun parseRelease(json: JSONObject): ReleaseInfo? {
        val tag = json.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
        val htmlUrl = json.optString("html_url").takeIf { it.isNotBlank() } ?: return null

        val assets = json.optJSONArray("assets")
        var downloadUrl: String? = null
        var apkUrl: String? = null
        if (assets != null) {
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val candidate = asset.optString("browser_download_url")
                if (candidate.isNotBlank() && downloadUrl == null) {
                    downloadUrl = candidate
                }
                val name = asset.optString("name")
                val contentType = asset.optString("content_type")
                if (candidate.isNotBlank() && apkUrl == null &&
                    (name.endsWith(".apk", ignoreCase = true) ||
                        contentType == "application/vnd.android.package-archive")
                ) {
                    apkUrl = candidate
                }
            }
        }

        return ReleaseInfo(
            tag = tag,
            name = json.optString("name").takeIf { it.isNotBlank() },
            htmlUrl = htmlUrl,
            downloadUrl = downloadUrl,
            apkUrl = apkUrl,
            publishedAt = json.optString("published_at").takeIf { it.isNotBlank() },
        )
    }
}
