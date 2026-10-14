package com.swp81x.nrsuite.core.update

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed interface GitHubReleaseResult {
    data class Success(
        val release: ReleaseInfo,
        val etag: String?,
    ) : GitHubReleaseResult

    data object NotModified : GitHubReleaseResult

    data class Failure(val message: String) : GitHubReleaseResult
}

object GitHubReleaseClient {
    suspend fun fetchLatest(repo: String, etag: String?): GitHubReleaseResult =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("https://api.github.com/repos/$repo/releases/latest")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "NRSuite-Android")
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
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
                        val json = JSONObject(body)
                        val tag = json.optString("tag_name").takeIf { it.isNotBlank() }
                            ?: return@withContext GitHubReleaseResult.Failure("Release has no tag")
                        val htmlUrl = json.optString("html_url").takeIf { it.isNotBlank() }
                            ?: return@withContext GitHubReleaseResult.Failure("Release has no URL")
                        val assets = json.optJSONArray("assets")
                        var downloadUrl: String? = null
                        if (assets != null) {
                            for (index in 0 until assets.length()) {
                                val asset = assets.optJSONObject(index) ?: continue
                                val candidate = asset.optString("browser_download_url")
                                if (candidate.isNotBlank()) {
                                    downloadUrl = candidate
                                    break
                                }
                            }
                        }
                        val release = ReleaseInfo(
                            tag = tag,
                            name = json.optString("name").takeIf { it.isNotBlank() },
                            htmlUrl = htmlUrl,
                            downloadUrl = downloadUrl,
                            publishedAt = json.optString("published_at").takeIf { it.isNotBlank() },
                        )
                        val responseEtag = connection.getHeaderField("ETag")
                        GitHubReleaseResult.Success(release, responseEtag)
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
}
