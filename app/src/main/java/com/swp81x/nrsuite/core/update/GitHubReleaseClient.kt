package com.swp81x.nrsuite.core.update

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

sealed interface GitHubReleasesResult {
    data class Success(
        val releases: List<ReleaseInfo>,
        val etag: String?,
    ) : GitHubReleasesResult

    data object NotModified : GitHubReleasesResult

    data class Failure(val message: String) : GitHubReleasesResult
}

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
    suspend fun fetchLatest(repo: String, etag: String?): GitHubReleaseResult {
        return when (val result = fetchReleases(repo, etag)) {
            is GitHubReleasesResult.Success -> {
                val latest = result.releases.maxWithOrNull { left, right ->
                    VersionComparator.compare(left.tag, right.tag)
                } ?: return GitHubReleaseResult.NotModified
                GitHubReleaseResult.Success(latest, result.etag)
            }
            GitHubReleasesResult.NotModified -> GitHubReleaseResult.NotModified
            is GitHubReleasesResult.Failure -> GitHubReleaseResult.Failure(result.message)
        }
    }

    suspend fun fetchReleases(repo: String, etag: String?): GitHubReleasesResult =
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
                    -> GitHubReleasesResult.NotModified
                    in 200..299 -> {
                        val body = connection.inputStream.bufferedReader().use { it.readText() }
                        GitHubReleasesResult.Success(
                            releases = parseReleases(body),
                            etag = connection.getHeaderField("ETag"),
                        )
                    }
                    HttpURLConnection.HTTP_FORBIDDEN,
                    HttpURLConnection.HTTP_UNAUTHORIZED,
                    -> {
                        val remaining = connection.getHeaderField("X-RateLimit-Remaining")
                        val reset = connection.getHeaderField("X-RateLimit-Reset")
                        GitHubReleasesResult.Failure(
                            "GitHub rate limit or access denied" +
                                (remaining?.let { " · remaining=$it" } ?: "") +
                                (reset?.let { " · reset=$it" } ?: ""),
                        )
                    }
                    else -> GitHubReleasesResult.Failure("GitHub returned HTTP $code")
                }
            } catch (t: Throwable) {
                GitHubReleasesResult.Failure(t.message ?: "Update check failed")
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

        val assets = mutableListOf<ReleaseAsset>()
        json.optJSONArray("assets")?.let { array ->
            for (index in 0 until array.length()) {
                val assetJson = array.optJSONObject(index) ?: continue
                val name = assetJson.optString("name").takeIf { it.isNotBlank() } ?: continue
                val url = assetJson.optString("browser_download_url").takeIf { it.isNotBlank() }
                    ?: continue
                assets += ReleaseAsset(
                    name = name,
                    url = url,
                    contentType = assetJson.optString("content_type").takeIf { it.isNotBlank() },
                )
            }
        }

        var downloadUrl: String? = null
        var apkUrl: String? = null
        assets.forEach { asset ->
            if (downloadUrl == null) downloadUrl = asset.url
            if (apkUrl == null &&
                (asset.name.endsWith(".apk", ignoreCase = true) ||
                    asset.contentType == "application/vnd.android.package-archive")
            ) {
                apkUrl = asset.url
            }
        }

        return ReleaseInfo(
            tag = tag,
            name = json.optString("name").takeIf { it.isNotBlank() },
            htmlUrl = htmlUrl,
            downloadUrl = downloadUrl,
            apkUrl = apkUrl,
            publishedAt = json.optString("published_at").takeIf { it.isNotBlank() },
            assets = assets,
        )
    }
}
